package com.maplecfo.app;

import com.maplecfo.agent.AgentResponse;
import com.maplecfo.agent.CfoAgent;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.command.CommandHistory;
import com.maplecfo.command.UndoableCommand;
import com.maplecfo.command.RecategorizeCommand;
import com.maplecfo.command.SetBudgetCommand;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Budget;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Debt;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.RecurringCharge;
import com.maplecfo.domain.Transaction;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.events.Alert;
import com.maplecfo.events.AlertService;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.events.FinanceEventListener;
import com.maplecfo.importer.ColumnMapping;
import com.maplecfo.importer.ImportResult;
import com.maplecfo.persistence.DebtRepository;
import com.maplecfo.persistence.ProfileRepository;
import com.maplecfo.planning.BudgetService;
import com.maplecfo.planning.ContributionCheck;
import com.maplecfo.planning.ContributionRoom;
import com.maplecfo.planning.DebtPayoffPlanner;
import com.maplecfo.planning.FireCalculator;
import com.maplecfo.planning.FireInputs;
import com.maplecfo.planning.FireProjection;
import com.maplecfo.planning.PayoffMethod;
import com.maplecfo.planning.PayoffPlan;
import com.maplecfo.planning.RecurringChargeDetector;
import com.maplecfo.planning.RegisteredAccountService;
import com.maplecfo.services.TransactionService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Facade pattern: the single entry point used by both the GUI and the CLI,
 * so both behave the same way. Every edit goes through {@link CommandHistory}
 * and can be undone. Methods are added here as features are implemented.
 */
public class MapleCfoFacade {

    private final TransactionService txService;
    private final BudgetService budgets;
    private final RegisteredAccountService registered;
    private final FireCalculator fire;
    private final DebtPayoffPlanner debtPlanner;
    private final DebtRepository debtRepo;
    private final ProfileRepository profileRepo;
    private final RecurringChargeDetector recurring;
    private final AlertService alerts;
    private final CommandHistory history;
    private final EventBus eventBus;
    private final Clock clock;
    private CfoAgent agent;

    public MapleCfoFacade(TransactionService txService, BudgetService budgets, RegisteredAccountService registered,
                          FireCalculator fire, DebtPayoffPlanner debtPlanner, DebtRepository debtRepo,
                          ProfileRepository profileRepo, RecurringChargeDetector recurring, AlertService alerts,
                          CommandHistory history, EventBus eventBus, Clock clock) {
        this.txService = txService;
        this.budgets = budgets;
        this.registered = registered;
        this.fire = fire;
        this.debtPlanner = debtPlanner;
        this.debtRepo = debtRepo;
        this.profileRepo = profileRepo;
        this.recurring = recurring;
        this.alerts = alerts;
        this.history = history;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ---------------------------------------------------------- F01 / F02 transactions

    public ImportResult importTransactions(Path p, String accountId, String profile) throws IOException {
        return txService.importFrom(p, accountId, profile);
    }

    public void saveMappingProfile(ColumnMapping m) {
        txService.saveMappingProfile(m);
    }

    public List<ColumnMapping> getMappingProfiles() {
        return txService.getMappingProfiles();
    }

    public List<Transaction> getTransactions(YearMonth m) {
        return m == null ? txService.findAll() : txService.findByMonth(m);
    }

    public List<Transaction> searchTransactions(String query) {
        return txService.search(query);
    }

    public List<Transaction> getTransactionsNeedingReview() {
        return txService.needsReview();
    }

    /** Accepts a full id or a unique prefix (the CLI shows the first 8 characters). */
    public String resolveTransactionId(String idOrPrefix) {
        if (txService.findById(idOrPrefix).isPresent()) {
            return idOrPrefix;
        }
        List<Transaction> matches = txService.findAll().stream()
                .filter(t -> t.getId().startsWith(idOrPrefix)).toList();
        if (matches.size() == 1) {
            return matches.get(0).getId();
        }
        throw new NoSuchElementException(matches.isEmpty()
                ? "No transaction with id " + idOrPrefix
                : "More than one transaction starts with " + idOrPrefix + "; type more characters");
    }

    public void recategorize(String txId, Category c) {
        history.run(new RecategorizeCommand(txService, resolveTransactionId(txId), c));
    }

    public boolean undoLastEdit() {
        return history.undo();
    }

    public boolean redoLastEdit() {
        return history.redo();
    }

    public String describeUndo() {
        return history.nextUndoDescription().orElse("nothing to undo");
    }

    // ---------------------------------------------------------- F04 budgets

    public void setBudget(Category c, Money limit, YearMonth m) {
        history.run(new SetBudgetCommand(budgets, new Budget(c, m, limit)));
    }

    public List<BudgetStatus> getBudgetStatus(YearMonth m) {
        return budgets.getStatus(m);
    }

    // ---------------------------------------------------------- F05 recurring charges

    /** Recurring charges found in the last six months of transactions. */
    public List<RecurringCharge> getRecurringCharges() {
        LocalDate to = today();
        return recurring.detect(txService.findAll().stream()
                .filter(t -> !t.getDate().isBefore(to.minusMonths(6)))
                .toList(), to);
    }

    public boolean isRecurringLowConfidence() {
        return recurring.isLowConfidence(txService.findAll());
    }

    // ---------------------------------------------------------- F06 registered accounts

    public ContributionCheck checkContribution(AccountType t, Money amt) {
        return registered.checkContribution(t, amt, today());
    }

    public ContributionRoom getContributionRoom(AccountType t, int year) {
        return registered.computeRoom(t, year);
    }

    public void recordContribution(ContributionRecord r) {
        registered.recordContribution(r);
    }

    public UserProfile getProfile() {
        return profileRepo.load();
    }

    public void saveProfile(UserProfile p) {
        profileRepo.save(p);
    }

    // ---------------------------------------------------------- F07 / F08 planning

    public FireProjection projectFire(FireInputs in) {
        return fire.project(in);
    }

    public void addDebt(String name, Money balance, double apr, Money minPayment) {
        debtRepo.save(new Debt(UUID.randomUUID().toString(), name, balance, apr, minPayment, null));
    }

    public List<Debt> getDebts() {
        return debtRepo.findAll();
    }

    public void removeDebt(String id) {
        debtRepo.delete(id);
    }

    public PayoffPlan planDebtPayoff(PayoffMethod m, Money extra) {
        debtPlanner.setStrategy(m.toStrategy());
        return debtPlanner.plan(extra);
    }

    // ---------------------------------------------------------- F10 AI CFO agent

    /** Connects the agent after construction (the agent's tools use the same services as this facade). */
    void attachAgent(CfoAgent agent) {
        this.agent = agent;
    }

    public AgentResponse askCfo(String message) {
        return requireAgent().handle(message);
    }

    /** Runs a change the AI proposed, through the undo history. */
    public String confirmProposedAction(String actionId) {
        UndoableCommand c = requireAgent().getPendingActions().take(actionId)
                .orElseThrow(() -> new NoSuchElementException("No pending action " + actionId));
        history.run(c);
        return c.describe();
    }

    public boolean dismissProposedAction(String actionId) {
        return requireAgent().getPendingActions().take(actionId).isPresent();
    }

    public java.util.Map<String, String> getPendingActions() {
        return requireAgent().getPendingActions().list();
    }

    public LlmClient getLlm() {
        return requireAgent().getLlm();
    }

    public void clearConversation() {
        requireAgent().getMemory().clear();
    }

    private CfoAgent requireAgent() {
        if (agent == null) {
            throw new IllegalStateException("The AI agent is not configured");
        }
        return agent;
    }

    // ---------------------------------------------------------- alerts and events

    public List<Alert> getAlerts() {
        return alerts.getActiveAlerts();
    }

    public void subscribe(EventType t, FinanceEventListener l) {
        eventBus.subscribe(t, l);
    }
}

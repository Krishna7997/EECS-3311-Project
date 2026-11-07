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
import com.maplecfo.reports.MonthlyReport;
import com.maplecfo.reports.ReportService;
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
    private ReportService reports;
    private com.maplecfo.planning.AccountService accounts;
    private com.maplecfo.planning.NetWorthService netWorth;
    private com.maplecfo.planning.GoalService goals;
    private com.maplecfo.planning.AffordabilityAnalyzer affordability;
    private com.maplecfo.automation.ReminderService reminders;
    private com.maplecfo.planning.RrspRoomEstimator rrspEstimator;

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

    /** Which saved bank format fits this file, by its columns (F01, also used by F13 auto-import). */
    public java.util.Optional<String> detectImportFormat(Path p) throws IOException {
        return txService.detectProfile(p).map(ColumnMapping::profileName);
    }

    /**
     * Imports a file after detecting its bank format automatically.
     *
     * @throws com.maplecfo.importer.MappingMismatchException when no saved format fits the file
     */
    public ImportResult importTransactionsAuto(Path p, String accountId) throws IOException {
        String format = detectImportFormat(p).orElseThrow(() -> new com.maplecfo.importer.MappingMismatchException(
                "MapleCFO could not recognise this file's columns. Choose the bank format yourself, "
                        + "or save a new mapping for this bank."));
        return importTransactions(p, accountId, format);
    }

    public void saveMappingProfile(ColumnMapping m) {
        txService.saveMappingProfile(m);
    }

    public List<ColumnMapping> getMappingProfiles() {
        return txService.getMappingProfiles();
    }

    // ---------------------------------------------------------- F16 AI reads an unknown bank format

    private com.maplecfo.importer.AiFormatReader formatReader;

    void attachFormatReader(com.maplecfo.importer.AiFormatReader reader) {
        this.formatReader = reader;
    }

    /**
     * Asks the AI how to read a file no saved format fits. Nothing is saved: show the proposal to the user and call
     * {@link #saveFormatAndImport} only if they accept it.
     *
     * @param name what to call the new format (e.g. the bank's name); a number is added if the name is taken
     */
    public com.maplecfo.importer.AiFormatReader.Proposal proposeImportFormat(Path p, String name) throws IOException {
        if (formatReader == null) throw new IllegalStateException("The AI format reader is not configured");
        String base = name == null || name.isBlank() ? "From " + p.getFileName() : name.trim();
        java.util.Set<String> taken = new java.util.HashSet<>();
        getMappingProfiles().forEach(m -> taken.add(m.profileName().toLowerCase()));
        String unique = base;
        for (int i = 2; taken.contains(unique.toLowerCase()); i++) unique = base + " (" + i + ")";
        return formatReader.propose(p, unique);
    }

    /** Saves the format the user accepted, then imports the file with it. */
    public ImportResult saveFormatAndImport(com.maplecfo.importer.AiFormatReader.Proposal proposal, Path p,
                                            String accountId) throws IOException {
        saveMappingProfile(proposal.mapping());
        return importTransactions(p, accountId, proposal.mapping().profileName());
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

    public boolean canRedo() {
        return history.canRedo();
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

    /** Recurring charges found in the last twelve months of transactions (so a price change earlier this year still shows). */
    public List<RecurringCharge> getRecurringCharges() {
        LocalDate to = today();
        return recurring.detect(txService.findAll().stream()
                .filter(t -> !t.getDate().isBefore(to.minusMonths(12)))
                .toList(), to);
    }

    public boolean isRecurringLowConfidence() {
        return recurring.isLowConfidence(txService.findAll());
    }

    // ---------------------------------------------------------- F06 registered accounts

    public ContributionCheck checkContribution(AccountType t, Money amt) {
        return registered.checkContribution(t, amt, today());
    }

    /** The same check as {@link #checkContribution} but without raising an alert (live preview while typing). */
    public ContributionCheck previewContribution(AccountType t, Money amt) {
        return registered.previewContribution(t, amt, today());
    }

    /** Logged contributions and withdrawals for one registered account type, newest first. */
    public List<ContributionRecord> getContributions(AccountType t) {
        return registered.getContributions(t).stream()
                .sorted(java.util.Comparator.comparing(ContributionRecord::date).reversed()).toList();
    }

    /** RRSP Home Buyers' Plan / Lifelong Learning Plan money still to be paid back. */
    public List<com.maplecfo.planning.RepaymentPlan> getRepaymentPlans() {
        return registered.repaymentPlans(today().getYear());
    }

    /** This year's [contributed, withdrawn] for one registered account type. */
    public Money[] getContributionTotals(AccountType t, int year) {
        return registered.yearTotals(t, year);
    }

    /** Transactions matching a filter (date range, account, category, in/out, amount, text), newest first. */
    public List<Transaction> findTransactions(com.maplecfo.services.TransactionFilter filter) {
        return filter.apply(txService.findAll());
    }

    // ---------------------------------------------------------- F17 AI monthly review

    private com.maplecfo.agent.MonthlyReviewService monthlyReview;

    private com.maplecfo.planning.BudgetPlanner budgetPlanner;

    void attachMonthlyReview(com.maplecfo.agent.MonthlyReviewService review, com.maplecfo.planning.BudgetPlanner planner) {
        this.monthlyReview = review;
        this.budgetPlanner = planner;
    }

    /** Suggested budgets for a month from the three months before it (rounded up to $10). */

    /**
     * The AI reviews a month and proposes next month's budgets. The proposals wait in {@link #getPendingActions()}
     * until the user confirms or dismisses each one.
     */
    public List<com.maplecfo.planning.BudgetPlanner.Suggestion> suggestBudgets(YearMonth month) {
        if (budgetPlanner == null) throw new IllegalStateException("The budget planner is not configured");
        return budgetPlanner.suggest(month);
    }

    public com.maplecfo.agent.MonthlyReviewService.MonthlyReview runMonthlyReview(YearMonth month) {
        if (monthlyReview == null) throw new IllegalStateException("The monthly review is not configured");
        return monthlyReview.review(month);
    }

    // ---------------------------------------------------------- F19 where to put extra money

    private com.maplecfo.planning.ExtraMoneyPlanner extraMoney;

    void attachExtraMoneyPlanner(com.maplecfo.planning.ExtraMoneyPlanner planner) {
        this.extraMoney = planner;
    }

    /** The split of an extra amount, computed from your own data (no AI involved). */
    public com.maplecfo.planning.ExtraMoneyPlanner.Plan planExtraMoney(Money amount) {
        if (extraMoney == null) throw new IllegalStateException("The extra-money planner is not configured");
        return extraMoney.plan(amount);
    }

    /** The AI agent explains the same plan in plain language (it calls the extra_money_plan tool). */
    public com.maplecfo.agent.AgentResponse explainExtraMoney(Money amount) {
        return askCfo("I have " + amount + " extra. Where could it go? Use the extra_money_plan tool with amount "
                + amount.getAmount().toPlainString() + " and explain the order and the trade-offs simply.");
    }

    // ---------------------------------------------------------- F18 plain-English transaction search

    private com.maplecfo.agent.TransactionQueryParser queryParser;

    void attachQueryParser(com.maplecfo.agent.TransactionQueryParser parser) {
        this.queryParser = parser;
    }

    /** "Uber Eats over $30 in October" as a checked filter (the AI fills it in; keyword rules in demo mode). */
    public com.maplecfo.agent.TransactionQueryParser.ParsedQuery understandTransactionQuery(String question) {
        if (queryParser == null) throw new IllegalStateException("The transaction search is not configured");
        java.util.Set<String> ids = new java.util.TreeSet<>();
        txService.findAll().forEach(t -> ids.add(t.getAccountId()));
        if (accounts != null) getAccounts().forEach(a -> ids.add(a.getId()));
        return queryParser.parse(question, List.copyOf(ids), today());
    }

    /** All imported transactions of one account, newest first. */
    public List<Transaction> getAccountActivity(String accountId) {
        return txService.findAll().stream().filter(t -> t.getAccountId().equalsIgnoreCase(accountId))
                .sorted(java.util.Comparator.comparing(Transaction::getDate).reversed()).toList();
    }

    /** Income, spending and savings between two dates (transfers are not spending). */
    public com.maplecfo.reports.PeriodSummary summarize(LocalDate from, LocalDate to) {
        return com.maplecfo.reports.PeriodSummary.of(txService.findAll(), from, to);
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
        if (settings != null) settings.put(FIRE_KEY, encodeFire(in));   // the page remembers your last plan
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

    // ---------------------------------------------------------- F03 accounts and net worth

    private com.maplecfo.planning.CashFlowForecaster cashFlow;
    private static final String FIRE_KEY = "fire.lastInputs";

    void attachCashFlow(com.maplecfo.planning.CashFlowForecaster cashFlow) {
        this.cashFlow = cashFlow;
    }

    /** The FIRE inputs used last time, if any (F07). */
    public java.util.Optional<FireInputs> lastFireInputs() {
        if (settings == null) return java.util.Optional.empty();
        return settings.get(FIRE_KEY).flatMap(MapleCfoFacade::decodeFire);
    }

    /**
     * FIRE inputs from your own data (F07): investments = TFSA, RRSP, FHSA and other investment accounts; saving
     * and spending per year = 12 x your recent monthly averages; age from your profile.
     */
    public FireInputs suggestFireInputs() {
        Money invested = accounts == null ? Money.zero() : getAccounts().stream()
                .filter(a -> a.getType().isRegistered() || a.getType() == com.maplecfo.domain.AccountType.NON_REGISTERED)
                .map(com.maplecfo.domain.Account::getBalance).reduce(Money.zero(), Money::plus);
        Money save = Money.zero();
        Money spend = Money.zero();
        if (cashFlow != null) {
            com.maplecfo.planning.CashFlowForecast f = cashFlow.forecast();
            if (f.hasData()) {
                save = f.averageSurplus().times(12).max(Money.zero());
                spend = f.averageSpending().times(12);
            }
        }
        if (!spend.isPositive()) {
            throw new IllegalStateException("Import a few months of transactions first, so MapleCFO knows your spending.");
        }
        Integer born = getProfile().getBirthYear();
        int age = born != null ? today().getYear() - born : 22;
        return new FireInputs(invested, save, spend, 0.05, 0.04, age);
    }

    static String encodeFire(FireInputs in) {
        return String.join(";", in.invested().getAmount().toPlainString(), in.yearlySaving().getAmount().toPlainString(),
                in.annualSpending().getAmount().toPlainString(), String.valueOf(in.realReturn()),
                String.valueOf(in.withdrawalRate()), String.valueOf(in.currentAge()),
                in.targetOverride() == null ? "" : in.targetOverride().getAmount().toPlainString(),
                in.targetAge() == null ? "" : String.valueOf(in.targetAge()));
    }

    static java.util.Optional<FireInputs> decodeFire(String s) {
        try {
            String[] p = s.split(";", -1);
            return java.util.Optional.of(new FireInputs(Money.of(p[0]), Money.of(p[1]), Money.of(p[2]),
                    Double.parseDouble(p[3]), Double.parseDouble(p[4]), Integer.parseInt(p[5]),
                    p[6].isEmpty() ? null : Money.of(p[6]), p[7].isEmpty() ? null : Integer.valueOf(p[7])));
        } catch (RuntimeException e) {
            return java.util.Optional.empty();   // old or damaged value: start fresh
        }
    }

    void attachPlanning(com.maplecfo.planning.AccountService accounts, com.maplecfo.planning.NetWorthService netWorth,
                        com.maplecfo.planning.GoalService goals,
                        com.maplecfo.planning.AffordabilityAnalyzer affordability) {
        this.accounts = accounts;
        this.netWorth = netWorth;
        this.goals = goals;
        this.affordability = affordability;
    }

    /** Adds an account and stores a net worth snapshot. Returns warnings such as a duplicate name. */
    public List<String> addAccount(com.maplecfo.domain.Account a) {
        List<String> warnings = accounts.addAccount(a);
        netWorth.recordSnapshot();
        return warnings;
    }

    public void updateAccountBalance(String id, Money balance) {
        accounts.updateBalance(id, balance);
        netWorth.recordSnapshot();
    }

    public void removeAccount(String id) {
        accounts.removeAccount(id);
        netWorth.recordSnapshot();
    }

    public List<com.maplecfo.domain.Account> getAccounts() {
        return accounts.getAccounts();
    }

    public com.maplecfo.domain.AccountGroup getAccountTree() {
        return accounts.buildAccountTree();
    }

    public com.maplecfo.planning.NetWorthSnapshot getNetWorth() {
        return netWorth.computeSnapshot();
    }

    public java.util.Map<LocalDate, Money> getNetWorthHistory() {
        return netWorth.getHistory();
    }

    // ---------------------------------------------------------- F09 savings goals

    /** Creates a goal through the undo history. */
    public void createGoal(com.maplecfo.domain.SavingsGoal g) {
        history.run(new com.maplecfo.command.CreateGoalCommand(goals, g));
    }

    public List<com.maplecfo.domain.SavingsGoal> getGoals() {
        return goals.getGoals();
    }

    public com.maplecfo.domain.SavingsGoal recordGoalDeposit(String id, Money amount) {
        return goals.recordDeposit(id, amount);
    }

    public com.maplecfo.planning.GoalForecast forecastGoal(String id) {
        return goals.forecast(id);
    }

    public List<com.maplecfo.planning.GoalForecast> forecastGoals() {
        return goals.forecastAll();
    }

    public void deleteGoal(String id) {
        goals.deleteGoal(id);
    }

    // ---------------------------------------------------------- F11 affordability

    /** "Can I afford this amount by this date?" */
    public com.maplecfo.planning.AffordabilityResult checkAffordability(Money amount, LocalDate date) {
        return affordability.analyze(amount, date);
    }

    // ---------------------------------------------------------- F13 / F14 automation

    void attachAutomation(com.maplecfo.automation.ReminderService reminders,
                          com.maplecfo.planning.RrspRoomEstimator rrspEstimator,
                          com.maplecfo.automation.limits.LimitsService limits) {
        this.reminders = reminders;
        this.rrspEstimator = rrspEstimator;
        this.limits = limits;
    }

    // ---------------------------------------------------------- F15 contribution limits updates

    private com.maplecfo.automation.limits.LimitsService limits;

    /** Checks online for newer TFSA/RRSP/FHSA limits (at most weekly unless forced). */
    public com.maplecfo.automation.limits.LimitsStatus refreshLimits(boolean force) {
        return limits.refreshIfStale(force ? java.time.Duration.ZERO : java.time.Duration.ofDays(7));
    }

    /** E.g. "bundled" or "cache", plus the newest TFSA year known. */
    public String describeLimits() {
        var l = limits.currentLimits();
        return "Using " + limits.currentSource() + " limits (TFSA up to " + l.latestTfsaYear() + ": "
                + l.tfsaLimitFor(l.latestTfsaYear()) + ").";
    }

    /**
     * Creates a watcher for a folder of bank CSVs. Each processed file raises an AUTO_IMPORTED alert.
     * Call scanOnce() for a single pass or start() to keep watching.
     */
    public com.maplecfo.automation.ImportFolderWatcher newImportWatcher(Path folder, String defaultAccountId) {
        return new com.maplecfo.automation.ImportFolderWatcher(folder, txService,
                () -> accounts.getAccounts().stream().map(com.maplecfo.domain.Account::getId).toList(),
                defaultAccountId,
                o -> eventBus.publish(com.maplecfo.events.FinanceEvent.of(EventType.AUTO_IMPORTED, o.message())));
    }

    // ---------------------------------------------------------- settings: import folder and monthly check-in

    static final String IMPORT_FOLDER_KEY = "import.folder";
    private com.maplecfo.persistence.SettingsRepository settings;
    private java.util.function.Supplier<Path> defaultImportFolder;

    private String inboxOverride;

    void attachSettings(com.maplecfo.persistence.SettingsRepository settings, java.util.function.Supplier<Path> defaultFolder,
                        String inboxOverride) {
        this.settings = settings;
        this.defaultImportFolder = defaultFolder;
        this.inboxOverride = inboxOverride;
    }

    /** The folder watched for bank CSVs (F13): MAPLECFO_INBOX if set, else the saved choice, else ~/.maplecfo/inbox. */
    public Path getImportFolder() {
        if (isImportFolderFromEnvironment()) {
            return Path.of(inboxOverride);
        }
        return settings.get(IMPORT_FOLDER_KEY).map(Path::of).orElseGet(defaultImportFolder);
    }

    public boolean isImportFolderFromEnvironment() {
        return inboxOverride != null && !inboxOverride.isBlank();
    }

    /** Saves a new import folder, creating it if needed. */
    public Path setImportFolder(Path folder) {
        if (folder == null || folder.toString().isBlank()) {
            throw new IllegalArgumentException("Choose a folder.");
        }
        Path p = folder.toAbsolutePath().normalize();
        if (java.nio.file.Files.exists(p) && !java.nio.file.Files.isDirectory(p)) {
            throw new IllegalArgumentException(p + " is a file, not a folder.");
        }
        try {
            java.nio.file.Files.createDirectories(p);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot use " + p + ": " + e.getMessage());
        }
        if (!java.nio.file.Files.isReadable(p)) {
            throw new IllegalArgumentException("MapleCFO cannot read " + p + ".");
        }
        settings.put(IMPORT_FOLDER_KEY, p.toString());
        return p;
    }

    public boolean isMonthlyCheckinDue() {
        return reminders.isMonthlyCheckinDue();
    }

    public void completeMonthlyCheckin() {
        reminders.completeMonthlyCheckin();
    }

    /** Raises any reminders that are due ("no import in 30 days", "summary ready", monthly check-in). */
    public List<String> checkReminders() {
        return reminders.checkDue();
    }

    public com.maplecfo.planning.RrspRoomEstimator.Estimate estimateRrspRoom(int year) {
        return rrspEstimator.estimate(year, getProfile().getPensionAdjustment());
    }

    // ---------------------------------------------------------- F12 monthly summary

    void attachReports(ReportService reports) {
        this.reports = reports;
    }

    public MonthlyReport generateMonthlyReport(YearMonth m) {
        return reports.buildMonthlyReport(m);
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

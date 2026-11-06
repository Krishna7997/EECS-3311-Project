package com.maplecfo.app;

import com.maplecfo.agent.CfoAgent;
import com.maplecfo.agent.ConversationMemory;
import com.maplecfo.agent.PendingActionStore;
import com.maplecfo.agent.PromptBuilder;
import com.maplecfo.agent.ResponseValidator;
import com.maplecfo.agent.ToolRegistry;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.agent.llm.LlmClientFactory;
import com.maplecfo.agent.tools.BudgetStatusTool;
import com.maplecfo.agent.tools.ContributionRoomTool;
import com.maplecfo.agent.tools.DebtPayoffTool;
import com.maplecfo.agent.tools.DemoRules;
import com.maplecfo.agent.tools.FireProjectionTool;
import com.maplecfo.agent.tools.ProposeActionTool;
import com.maplecfo.agent.tools.RecurringChargesTool;
import com.maplecfo.agent.tools.SpendingSummaryTool;
import com.maplecfo.automation.limits.LimitsService;
import com.maplecfo.categorization.CategorizationService;
import com.maplecfo.categorization.LlmCategorizer;
import com.maplecfo.categorization.RuleBasedCategorizer;
import com.maplecfo.reports.ReportService;
import com.maplecfo.reports.ReportSummarizer;
import com.maplecfo.command.CommandHistory;
import com.maplecfo.events.AlertService;
import com.maplecfo.events.EventBus;
import com.maplecfo.importer.ColumnMapping;
import com.maplecfo.importer.DefaultProfiles;
import com.maplecfo.persistence.DebtRepository;
import com.maplecfo.persistence.MappingProfileRepository;
import com.maplecfo.persistence.ProfileRepository;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import com.maplecfo.persistence.sqlite.SqliteBudgetRepository;
import com.maplecfo.persistence.sqlite.SqliteCategoryRuleRepository;
import com.maplecfo.persistence.sqlite.SqliteContributionRepository;
import com.maplecfo.persistence.sqlite.SqliteDebtRepository;
import com.maplecfo.persistence.sqlite.SqliteMappingProfileRepository;
import com.maplecfo.persistence.sqlite.SqliteProfileRepository;
import com.maplecfo.persistence.sqlite.SqliteTransactionRepository;
import com.maplecfo.persistence.TransactionRepository;
import com.maplecfo.agent.tools.AffordabilityTool;
import com.maplecfo.agent.tools.GoalForecastTool;
import com.maplecfo.planning.AffordabilityAnalyzer;
import com.maplecfo.agent.tools.NetWorthTool;
import com.maplecfo.persistence.sqlite.SqliteAccountRepository;
import com.maplecfo.persistence.sqlite.SqliteGoalRepository;
import com.maplecfo.persistence.sqlite.SqliteNetWorthHistoryRepository;
import com.maplecfo.planning.AccountService;
import com.maplecfo.planning.CashFlowForecaster;
import com.maplecfo.planning.GoalService;
import com.maplecfo.planning.NetWorthService;
import com.maplecfo.planning.AvalancheStrategy;
import com.maplecfo.planning.BudgetService;
import com.maplecfo.planning.DebtPayoffPlanner;
import com.maplecfo.planning.FireCalculator;
import com.maplecfo.planning.RecurringChargeDetector;
import com.maplecfo.planning.RegisteredAccountService;
import com.maplecfo.services.TransactionService;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * Builds the whole application once (composition root): repositories,
 * services, event subscriptions and the facade. The GUI and the CLI both
 * start from here.
 */
public final class AppContext {

    /** Default database location: ~/.maplecfo/maplecfo.db (override with MAPLECFO_DB). */
    public static Path defaultDatabasePath() {
        String env = System.getenv("MAPLECFO_DB");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(System.getProperty("user.home"), ".maplecfo", "maplecfo.db");
    }

    private AppContext() {
    }

    public static MapleCfoFacade create(Path databaseFile) {
        return create(DatabaseManager.forFile(databaseFile), Clock.systemDefaultZone(), AppConfig.load());
    }

    public static MapleCfoFacade create(DatabaseManager db, Clock clock) {
        return create(db, clock, AppConfig.of(java.util.Map.of()));
    }

    public static MapleCfoFacade create(DatabaseManager db, Clock clock, AppConfig config) {
        return create(db, clock, LlmClientFactory.create(config, DemoRules.forMapleCfo()));
    }

    public static MapleCfoFacade create(DatabaseManager db, Clock clock, LlmClient llm) {
        EventBus bus = new EventBus();
        AlertService alerts = new AlertService();
        alerts.register(bus);

        TransactionRepository txRepo = new SqliteTransactionRepository(db);
        MappingProfileRepository mappingRepo = new SqliteMappingProfileRepository(db);
        for (ColumnMapping preset : DefaultProfiles.all()) {
            if (mappingRepo.findByName(preset.profileName()).isEmpty()) {
                mappingRepo.save(preset);
            }
        }
        PromptBuilder prompts = new PromptBuilder();
        ResponseValidator validator = new ResponseValidator();
        CategorizationService categorizer = new CategorizationService(RuleBasedCategorizer.withDefaultRules(),
                List.of(new LlmCategorizer(llm, prompts)));
        categorizer.useRuleRepository(new SqliteCategoryRuleRepository(db));
        TransactionService txService = new TransactionService(mappingRepo, categorizer, txRepo, bus);

        BudgetService budgets = new BudgetService(new SqliteBudgetRepository(db), txRepo, bus);
        budgets.register();

        ProfileRepository profileRepo = new SqliteProfileRepository(db);
        RegisteredAccountService registered = new RegisteredAccountService(LimitsService.bundledOnly(), profileRepo,
                new SqliteContributionRepository(db), bus);

        DebtRepository debtRepo = new SqliteDebtRepository(db);
        DebtPayoffPlanner planner = new DebtPayoffPlanner(debtRepo, new AvalancheStrategy(), clock);

        FireCalculator fire = new FireCalculator();
        RecurringChargeDetector recurring = new RecurringChargeDetector();
        ReportService reports = new ReportService(new ReportSummarizer(llm, prompts, validator), txService, budgets,
                recurring);
        MapleCfoFacade facade = new MapleCfoFacade(txService, budgets, registered, fire, planner, debtRepo,
                profileRepo, recurring, alerts, new CommandHistory(), bus, clock);
        facade.attachReports(reports);

        AccountService accountService = new AccountService(new SqliteAccountRepository(db), bus);
        NetWorthService netWorth = new NetWorthService(accountService, new SqliteNetWorthHistoryRepository(db), clock);
        CashFlowForecaster cashFlow = new CashFlowForecaster(txService, recurring, clock);
        GoalService goals = new GoalService(new SqliteGoalRepository(db), cashFlow, bus, clock);
        AffordabilityAnalyzer affordability = new AffordabilityAnalyzer(accountService, cashFlow, goals, clock);
        facade.attachPlanning(accountService, netWorth, goals, affordability);

        PendingActionStore pending = new PendingActionStore();
        ToolRegistry tools = new ToolRegistry();
        tools.register(new SpendingSummaryTool(txService, clock));
        tools.register(new BudgetStatusTool(budgets, clock));
        tools.register(new RecurringChargesTool(txService, recurring, clock));
        tools.register(new ContributionRoomTool(registered, clock));
        tools.register(new FireProjectionTool(fire, profileRepo::load, clock));
        tools.register(new DebtPayoffTool(planner));
        tools.register(new NetWorthTool(netWorth, accountService));
        tools.register(new GoalForecastTool(goals));
        tools.register(new AffordabilityTool(affordability, clock));
        tools.register(new ProposeActionTool(pending, budgets, clock));
        facade.attachAgent(new CfoAgent(llm, tools, new ConversationMemory(10), prompts,
                validator, pending, profileRepo::load, clock));
        return facade;
    }
}

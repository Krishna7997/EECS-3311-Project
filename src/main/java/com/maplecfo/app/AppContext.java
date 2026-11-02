package com.maplecfo.app;

import com.maplecfo.automation.limits.LimitsService;
import com.maplecfo.categorization.CategorizationService;
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
import com.maplecfo.planning.AvalancheStrategy;
import com.maplecfo.planning.BudgetService;
import com.maplecfo.planning.DebtPayoffPlanner;
import com.maplecfo.planning.FireCalculator;
import com.maplecfo.planning.RecurringChargeDetector;
import com.maplecfo.planning.RegisteredAccountService;
import com.maplecfo.services.TransactionService;

import java.nio.file.Path;
import java.time.Clock;

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
        return create(DatabaseManager.forFile(databaseFile), Clock.systemDefaultZone());
    }

    public static MapleCfoFacade create(DatabaseManager db, Clock clock) {
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
        CategorizationService categorizer = CategorizationService.rulesOnly();
        categorizer.useRuleRepository(new SqliteCategoryRuleRepository(db));
        TransactionService txService = new TransactionService(mappingRepo, categorizer, txRepo, bus);

        BudgetService budgets = new BudgetService(new SqliteBudgetRepository(db), txRepo, bus);
        budgets.register();

        ProfileRepository profileRepo = new SqliteProfileRepository(db);
        RegisteredAccountService registered = new RegisteredAccountService(LimitsService.bundledOnly(), profileRepo,
                new SqliteContributionRepository(db), bus);

        DebtRepository debtRepo = new SqliteDebtRepository(db);
        DebtPayoffPlanner planner = new DebtPayoffPlanner(debtRepo, new AvalancheStrategy(), clock);

        return new MapleCfoFacade(txService, budgets, registered, new FireCalculator(), planner, debtRepo,
                profileRepo, new RecurringChargeDetector(), alerts, new CommandHistory(), bus, clock);
    }
}

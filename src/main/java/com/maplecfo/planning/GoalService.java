package com.maplecfo.planning;

import com.maplecfo.domain.Money;
import com.maplecfo.domain.SavingsGoal;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.events.FinanceEvent;
import com.maplecfo.persistence.GoalRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** F09 savings goals: create, deposit, and forecast whether each goal is on track. */
public class GoalService {

    private final GoalRepository goals;
    private final CashFlowForecaster cashFlow;
    private final EventBus eventBus;
    private final Clock clock;

    public GoalService(GoalRepository goals, CashFlowForecaster cashFlow, EventBus eventBus, Clock clock) {
        this.goals = goals;
        this.cashFlow = cashFlow;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    /** Validates and saves a new goal (the SavingsGoal constructor already rejects a target of zero or less). */
    public void createGoal(SavingsGoal g) {
        if (g.getDeadline() != null && !g.getDeadline().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("The deadline must be in the future");
        }
        if (goals.findById(g.getId()).isPresent()) {
            throw new IllegalArgumentException("A goal with id '" + g.getId() + "' already exists");
        }
        goals.save(g);
        eventBus.publish(FinanceEvent.of(EventType.GOAL_UPDATED, g));
    }

    /** Puts a goal back exactly as it was (used by undo). */
    public void restoreGoal(SavingsGoal g) {
        goals.save(g);
        eventBus.publish(FinanceEvent.of(EventType.GOAL_UPDATED, g));
    }

    public void deleteGoal(String id) {
        goals.delete(id);
        eventBus.publish(FinanceEvent.of(EventType.GOAL_UPDATED, null));
    }

    public SavingsGoal recordDeposit(String id, Money amount) {
        SavingsGoal g = require(id);
        g.deposit(amount);
        goals.save(g);
        eventBus.publish(FinanceEvent.of(EventType.GOAL_UPDATED, g));
        return g;
    }

    public List<SavingsGoal> getGoals() {
        return goals.findAll();
    }

    public Optional<SavingsGoal> find(String id) {
        return goals.findById(id);
    }

    public GoalForecast forecast(String id) {
        return forecast(require(id), cashFlow.forecast());
    }

    public List<GoalForecast> forecastAll() {
        CashFlowForecast cf = cashFlow.forecast();
        return goals.findAll().stream().map(g -> forecast(g, cf)).toList();
    }

    /** Monthly amount a goal needs to meet its deadline; zero when done or with no deadline. */
    public Money requiredMonthly(SavingsGoal g) {
        if (g.isComplete() || g.getDeadline() == null) {
            return Money.zero();
        }
        long months = monthsLeft(g);
        return g.remaining().dividedBy(Math.max(1, months));
    }

    private GoalForecast forecast(SavingsGoal g, CashFlowForecast cf) {
        long months = monthsLeft(g);
        if (g.isComplete()) {
            return new GoalForecast(g, months, Money.zero(), Money.zero(), null, true, Money.zero(), cf.lowConfidence());
        }
        Money required = requiredMonthly(g);
        // Other unfinished goals also need money each month; share the surplus fairly.
        Money othersNeed = Money.zero();
        for (SavingsGoal other : goals.findAll()) {
            if (!other.getId().equals(g.getId())) {
                othersNeed = othersNeed.plus(requiredMonthly(other));
            }
        }
        Money available = cf.averageSurplus().minus(othersNeed).max(Money.zero());
        LocalDate today = LocalDate.now(clock);
        LocalDate completion = null;
        if (available.isPositive()) {
            long monthsNeeded = (long) Math.ceil(g.remaining().getAmount().doubleValue()
                    / available.getAmount().doubleValue());
            completion = today.plusMonths(monthsNeeded);
        }
        boolean deadlinePassed = g.getDeadline() != null && !g.getDeadline().isAfter(today);
        boolean onTrack;
        if (g.getDeadline() == null) {
            onTrack = available.isPositive();
        } else {
            onTrack = !deadlinePassed && !available.isLessThan(required);
        }
        Money shortfall = onTrack ? Money.zero() : required.minus(available).max(Money.zero());
        return new GoalForecast(g, months, required, available, completion, onTrack, shortfall, cf.lowConfidence());
    }

    private long monthsLeft(SavingsGoal g) {
        if (g.getDeadline() == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.MONTHS.between(LocalDate.now(clock), g.getDeadline()));
    }

    private SavingsGoal require(String id) {
        return goals.findById(id).orElseThrow(() -> new NoSuchElementException("No goal " + id));
    }
}

package com.maplecfo.planning;

import com.maplecfo.domain.Debt;
import com.maplecfo.domain.Money;
import com.maplecfo.persistence.DebtRepository;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * F08 debt payoff planner. Strategy pattern: the "context" role.
 * The simulation is identical for every strategy; only the payoff order,
 * supplied by the current {@link PayoffStrategy}, changes.
 *
 * <p>Each month: interest is added to every debt, every debt gets its minimum
 * payment, and everything left of the monthly budget (the sum of all minimums
 * plus the extra amount) goes to the first unpaid debt in the strategy's order.
 * When a debt is paid off its minimum payment rolls into the next one,
 * because the monthly budget stays the same.
 */
public class DebtPayoffPlanner {

    /** 50 years: stop simulating plans that would never finish. */
    public static final int MAX_MONTHS = 600;

    private final DebtRepository debtRepo;
    private final Clock clock;
    private PayoffStrategy strategy;

    public DebtPayoffPlanner(DebtRepository debtRepo, PayoffStrategy strategy, Clock clock) {
        this.debtRepo = Objects.requireNonNull(debtRepo, "debtRepo");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DebtPayoffPlanner(DebtRepository debtRepo, PayoffStrategy strategy) {
        this(debtRepo, strategy, Clock.systemDefaultZone());
    }

    public void setStrategy(PayoffStrategy s) {
        this.strategy = Objects.requireNonNull(s, "strategy");
    }

    public PayoffStrategy getStrategy() {
        return strategy;
    }

    public PayoffPlan plan(Money extraMonthly) {
        Objects.requireNonNull(extraMonthly, "extraMonthly");
        if (extraMonthly.isNegative()) {
            throw new IllegalArgumentException("Extra monthly payment cannot be negative");
        }
        List<Debt> debts = debtRepo.findAll().stream().filter(d -> d.getBalance().isPositive()).toList();
        return simulate(strategy.orderDebts(debts), extraMonthly);
    }

    private PayoffPlan simulate(List<Debt> ordered, Money extra) {
        YearMonth start = YearMonth.now(clock);
        List<String> warnings = new ArrayList<>();
        if (ordered.isEmpty()) {
            return new PayoffPlan(strategy.name(), 0, start, Money.zero(), Money.zero(),
                    List.of(), List.of(), List.of("You have no debts. You are debt-free!"));
        }

        // Work on copies of the balances so the real Debt objects are not changed.
        Map<Debt, Money> balances = new LinkedHashMap<>();
        Money monthlyBudget = extra;
        for (Debt d : ordered) {
            balances.put(d, d.getBalance());
            monthlyBudget = monthlyBudget.plus(d.getMinPayment());
            if (!d.getMinPayment().isGreaterThan(d.monthlyInterest())) {
                warnings.add(d.getName() + ": the minimum payment does not cover the monthly interest, "
                        + "so this balance never shrinks on minimum payments alone.");
            }
        }

        Money totalInterest = Money.zero();
        Money totalPaid = Money.zero();
        List<PayoffPlan.MonthRow> schedule = new ArrayList<>();
        List<PayoffPlan.DebtPayoff> payoffs = new ArrayList<>();

        for (int month = 1; month <= MAX_MONTHS; month++) {
            Money monthInterest = Money.zero();
            for (Map.Entry<Debt, Money> e : balances.entrySet()) {
                if (e.getValue().isPositive()) {
                    Money interest = e.getValue().times(e.getKey().getApr() / 100.0 / 12.0);
                    e.setValue(e.getValue().plus(interest));
                    monthInterest = monthInterest.plus(interest);
                }
            }

            Money available = monthlyBudget;
            // 1) minimum payments
            for (Map.Entry<Debt, Money> e : balances.entrySet()) {
                if (e.getValue().isPositive()) {
                    Money pay = e.getKey().getMinPayment().min(e.getValue()).min(available);
                    e.setValue(e.getValue().minus(pay));
                    available = available.minus(pay);
                }
            }
            // 2) everything left goes to debts in the strategy's order
            for (Map.Entry<Debt, Money> e : balances.entrySet()) {
                if (available.isPositive() && e.getValue().isPositive()) {
                    Money pay = available.min(e.getValue());
                    e.setValue(e.getValue().minus(pay));
                    available = available.minus(pay);
                }
            }

            Money paidThisMonth = monthlyBudget.minus(available);
            totalInterest = totalInterest.plus(monthInterest);
            totalPaid = totalPaid.plus(paidThisMonth);

            Money remaining = Money.zero();
            for (Map.Entry<Debt, Money> e : balances.entrySet()) {
                remaining = remaining.plus(e.getValue());
                if (!e.getValue().isPositive() && payoffs.stream().noneMatch(p -> p.debtName().equals(e.getKey().getName()))) {
                    payoffs.add(new PayoffPlan.DebtPayoff(e.getKey().getName(), month, start.plusMonths(month)));
                }
            }
            schedule.add(new PayoffPlan.MonthRow(month, monthInterest, paidThisMonth, remaining));

            if (!remaining.isPositive()) {
                return new PayoffPlan(strategy.name(), month, start.plusMonths(month), totalInterest,
                        totalPaid, schedule, payoffs, warnings);
            }
        }
        warnings.add("These debts are not paid off within " + (MAX_MONTHS / 12)
                + " years. Increase the monthly payment.");
        return new PayoffPlan(strategy.name(), -1, null, totalInterest, totalPaid, schedule, payoffs, warnings);
    }
}

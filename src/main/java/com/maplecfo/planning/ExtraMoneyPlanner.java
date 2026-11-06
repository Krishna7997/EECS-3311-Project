package com.maplecfo.planning;

import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Debt;
import com.maplecfo.domain.Money;
import com.maplecfo.persistence.DebtRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * F19 (new in Stage 2): where could an extra amount of money go? All numbers are worked out here, in Java, from
 * the user's own data; the AI only explains the result. The order is the one commonly taught in Canadian
 * personal-finance education, not personal advice:
 * <ol>
 *   <li>debts charging {@value #HIGH_INTEREST_PERCENT}% or more, highest rate first</li>
 *   <li>an emergency fund of {@value #EMERGENCY_MONTHS} months of spending in chequing and savings</li>
 *   <li>FHSA room (when an FHSA is open), then TFSA room, then RRSP room</li>
 *   <li>lower-interest debts</li>
 * </ol>
 */
public class ExtraMoneyPlanner {

    public static final double HIGH_INTEREST_PERCENT = 8.0;
    public static final int EMERGENCY_MONTHS = 3;

    /** One place the money could go. */
    public record Step(String target, Money amount, String reason) {
    }

    /** The split of the amount, facts it was based on, and anything left over. */
    public record Plan(Money amount, List<Step> steps, List<String> facts, Money leftover) {
        public Plan {
            steps = List.copyOf(steps);
            facts = List.copyOf(facts);
        }

        /** Plain-language version of the plan (also the demo-mode answer). */
        public String summary() {
            StringBuilder sb = new StringBuilder("A common order for " + amount + ": ");
            List<String> parts = new ArrayList<>();
            for (Step s : steps) parts.add(s.amount() + " to " + s.target().substring(0, 1).toLowerCase() + s.target().substring(1));
            sb.append(parts.isEmpty() ? "nothing needed" : String.join(", then ", parts)).append('.');
            if (leftover.isPositive()) sb.append(" " + leftover + " is left over for your goals or other savings.");
            return sb.toString();
        }
    }

    private final RegisteredAccountService registered;
    private final DebtRepository debts;
    private final AccountService accounts;
    private final CashFlowForecaster cashFlow;
    private final Clock clock;

    public ExtraMoneyPlanner(RegisteredAccountService registered, DebtRepository debts, AccountService accounts,
                             CashFlowForecaster cashFlow, Clock clock) {
        this.registered = registered;
        this.debts = debts;
        this.accounts = accounts;
        this.cashFlow = cashFlow;
        this.clock = clock;
    }

    public Plan plan(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("Enter an amount greater than zero");
        }
        int year = LocalDate.now(clock).getYear();
        List<Step> steps = new ArrayList<>();
        List<String> facts = new ArrayList<>();
        Money left = amount;

        List<Debt> byRate = debts.findAll().stream().filter(d -> d.getBalance().isPositive())
                .sorted(Comparator.comparingDouble(Debt::getApr).reversed()).toList();
        for (Debt d : byRate) {
            facts.add(d.getName() + ": " + d.getBalance() + " owed at " + d.getApr() + "% a year");
        }

        // 1. high-interest debt
        for (Debt d : byRate) {
            if (d.getApr() < HIGH_INTEREST_PERCENT || !left.isPositive()) continue;
            Money pay = left.min(d.getBalance());
            steps.add(new Step("Pay down " + d.getName(), pay, d.getName() + " charges " + d.getApr()
                    + "% a year, so this saves about " + pay.times(d.getApr() / 100) + " of interest a year. "
                    + "Paying off high-interest debt is a guaranteed return."));
            left = left.minus(pay);
        }

        // 2. emergency fund
        CashFlowForecast cf = cashFlow.forecast();
        Money cash = accounts.getAccounts().stream()
                .filter(a -> a.getType() == AccountType.CHEQUING || a.getType() == AccountType.SAVINGS)
                .map(Account::getBalance).reduce(Money.zero(), Money::plus);
        if (cf.hasData()) {
            Money target = cf.averageSpending().times(EMERGENCY_MONTHS);
            facts.add("Cash in chequing and savings: " + cash + "; " + EMERGENCY_MONTHS + " months of spending: " + target);
            Money gap = target.minus(cash);
            if (gap.isPositive() && left.isPositive()) {
                Money keep = left.min(gap);
                steps.add(new Step("Emergency fund (savings)", keep, "Your cash covers less than " + EMERGENCY_MONTHS
                        + " months of spending (" + target + "). A cushion keeps a surprise bill off the credit card."));
                left = left.minus(keep);
            }
        } else {
            facts.add("Not enough history to know your monthly spending, so the emergency fund was not checked");
        }

        // 3. registered room: FHSA (if open), TFSA, RRSP
        for (AccountType type : List.of(AccountType.FHSA, AccountType.TFSA, AccountType.RRSP)) {
            if (!registered.missingProfileFields(type).isEmpty()) {
                facts.add(type + " room unknown (profile incomplete)");
                continue;
            }
            Money room = registered.computeRoom(type, year).available().max(Money.zero());
            facts.add(type + " room left in " + year + ": " + room);
            if (!room.isPositive() || !left.isPositive()) continue;
            Money put = left.min(room);
            steps.add(new Step("Contribute to your " + type, put, switch (type) {
                case FHSA -> "Contributions are deductible and withdrawals for a first home are tax-free.";
                case TFSA -> "Growth is tax-free and you can take the money out at any time.";
                default -> "Contributions lower your taxable income; you pay tax when you withdraw in retirement.";
            }));
            left = left.minus(put);
        }

        // 4. lower-interest debt
        for (Debt d : byRate) {
            if (d.getApr() >= HIGH_INTEREST_PERCENT || !left.isPositive()) continue;
            Money pay = left.min(d.getBalance());
            steps.add(new Step("Pay down " + d.getName(), pay, d.getName() + " charges " + d.getApr()
                    + "% a year (about " + pay.times(d.getApr() / 100) + " of interest a year on this amount)."));
            left = left.minus(pay);
        }
        return new Plan(amount, steps, facts, left);
    }
}

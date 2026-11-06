package com.maplecfo.planning;

import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.RecurringCharge;
import com.maplecfo.domain.Transaction;
import com.maplecfo.services.TransactionService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Works out the user's typical month from the last (up to) three complete
 * months of transactions. Used by goal forecasts (F09) and the
 * affordability check (F11).
 */
public class CashFlowForecaster {

    static final int MONTHS_TO_AVERAGE = 3;

    private final TransactionService txService;
    private final RecurringChargeDetector recurring;
    private final Clock clock;

    public CashFlowForecaster(TransactionService txService, RecurringChargeDetector recurring, Clock clock) {
        this.txService = txService;
        this.recurring = recurring;
        this.clock = clock;
    }

    public CashFlowForecast forecast() {
        LocalDate today = LocalDate.now(clock);
        YearMonth thisMonth = YearMonth.from(today);
        List<Transaction> all = txService.findAll();

        Money income = Money.zero();
        Money spending = Money.zero();
        int months = 0;
        for (int i = 1; i <= MONTHS_TO_AVERAGE; i++) {
            YearMonth m = thisMonth.minusMonths(i);
            List<Transaction> inMonth = all.stream()
                    .filter(t -> YearMonth.from(t.getDate()).equals(m))
                    .filter(t -> t.getCategory() != Category.TRANSFER)
                    .toList();
            if (inMonth.isEmpty()) {
                continue;
            }
            months++;
            for (Transaction t : inMonth) {
                if (t.isExpense()) {
                    spending = spending.plus(t.getAmount().abs());
                } else {
                    income = income.plus(t.getAmount());
                }
            }
        }

        Money fixed = Money.zero();
        List<Transaction> recent = all.stream().filter(t -> !t.getDate().isBefore(today.minusMonths(6))).toList();
        for (RecurringCharge c : recurring.detect(recent, today)) {
            // money moved to savings (e.g. a monthly TFSA transfer) is not a bill
            if (c.occurrences().stream().allMatch(t -> t.getCategory() == Category.TRANSFER)) continue;
            fixed = fixed.plus(c.yearlyCost().dividedBy(12));
        }

        if (months == 0) {
            return new CashFlowForecast(0, Money.zero(), Money.zero(), Money.zero(), fixed, true);
        }
        Money avgIncome = income.dividedBy(months);
        Money avgSpending = spending.dividedBy(months);
        return new CashFlowForecast(months, avgIncome, avgSpending, avgIncome.minus(avgSpending), fixed, months < 2);
    }
}

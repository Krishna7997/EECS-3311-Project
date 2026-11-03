package com.maplecfo.reports;

import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.RecurringCharge;
import com.maplecfo.domain.Transaction;
import com.maplecfo.planning.BudgetService;
import com.maplecfo.planning.RecurringChargeDetector;
import com.maplecfo.services.TransactionService;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * F12: gathers the month's figures from the other services (all computed by
 * Java code), then asks {@link ReportSummarizer} for the narrative.
 */
public class ReportService {

    private final ReportSummarizer summarizer;
    private final TransactionService txService;
    private final BudgetService budgets;
    private final RecurringChargeDetector recurring;

    public ReportService(ReportSummarizer summarizer, TransactionService txService, BudgetService budgets,
                         RecurringChargeDetector recurring) {
        this.summarizer = summarizer;
        this.txService = txService;
        this.budgets = budgets;
        this.recurring = recurring;
    }

    public MonthlyReport buildMonthlyReport(YearMonth m) {
        MonthlyReport figures = computeFigures(m);
        if (!figures.hasData()) {
            return figures.withNarrative("There are no transactions for " + m + " yet. Import a bank CSV first.", false);
        }
        return summarizer.summarize(figures);
    }

    MonthlyReport computeFigures(YearMonth m) {
        List<Transaction> txs = txService.findByMonth(m);
        Money income = Money.zero();
        Money spending = Money.zero();
        Map<Category, Money> byCategory = new EnumMap<>(Category.class);
        for (Transaction t : txs) {
            if (t.getCategory() == Category.TRANSFER) continue;
            if (t.isExpense()) {
                spending = spending.plus(t.getAmount().abs());
                byCategory.merge(t.getCategory(), t.getAmount().abs(), Money::plus);
            } else {
                income = income.plus(t.getAmount());
            }
        }
        Money saved = income.minus(spending);
        double rate = income.isPositive() ? saved.getAmount().doubleValue() / income.getAmount().doubleValue() * 100 : 0;

        Money lastMonth = Money.zero();
        for (Transaction t : txService.findByMonth(m.minusMonths(1))) {
            if (t.isExpense() && t.getCategory() != Category.TRANSFER) lastMonth = lastMonth.plus(t.getAmount().abs());
        }

        List<Transaction> history = txService.findAll().stream()
                .filter(t -> !YearMonth.from(t.getDate()).isAfter(m) && !YearMonth.from(t.getDate()).isBefore(m.minusMonths(5)))
                .toList();
        Money recurringMonthly = Money.zero();
        List<String> increases = new ArrayList<>();
        for (RecurringCharge c : recurring.detect(history)) {
            recurringMonthly = recurringMonthly.plus(c.yearlyCost().dividedBy(12));
            if (c.priceIncreased()) increases.add(c.merchant() + " now " + c.latestAmount());
        }
        List<BudgetStatus> statuses = budgets.getStatus(m);
        return new MonthlyReport(m, income, spending, saved, rate, byCategory, statuses, recurringMonthly, increases,
                spending.minus(lastMonth), txs.size(), "", false);
    }
}

package com.maplecfo.planning;

import com.maplecfo.automation.limits.LimitsService;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.services.TransactionService;

import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * F14 RRSP room estimate for users who do not have their CRA notice handy:
 * min(18% x last year's income, that year's RRSP dollar limit).
 * Income is taken from INCOME deposits; when only part of the year is imported it is scaled to 12 months.
 * Pay deposits are after tax, so this is a cautious (low) estimate the user must confirm.
 */
public class RrspRoomEstimator {

    public enum Status { ESTIMATED, UNKNOWN }

    public record Estimate(Status status, int year, Money incomeUsed, int monthsOfIncome, Money yearlyMax,
                           Money estimate, String explanation) {
    }

    private final TransactionService txService;
    private final LimitsService limits;

    public RrspRoomEstimator(TransactionService txService, LimitsService limits) {
        this.txService = txService;
        this.limits = limits;
    }

    public Estimate estimate(int year) {
        return estimate(year, Money.zero());
    }

    /**
     * @param pensionAdjustment last year's pension adjustment (T4 box 52), subtracted from the room;
     *                          zero for people without a workplace pension plan
     */
    public Estimate estimate(int year, Money pensionAdjustment) {
        Money pa = pensionAdjustment == null ? Money.zero() : pensionAdjustment;
        ContributionLimits l = limits.currentLimits();
        TreeMap<Integer, Money> dollar = new TreeMap<>(l.rrspDollarLimits());
        Map.Entry<Integer, Money> max = dollar.floorEntry(year) != null ? dollar.floorEntry(year) : dollar.firstEntry();
        Money yearlyMax = max.getValue();

        int incomeYear = year - 1;
        Money income = Money.zero();
        Set<YearMonth> months = new java.util.HashSet<>();
        for (Transaction t : txService.findAll()) {
            if (t.getDate().getYear() == incomeYear && t.getCategory() == Category.INCOME && !t.isExpense()) {
                income = income.plus(t.getAmount());
                months.add(YearMonth.from(t.getDate()));
            }
        }
        if (months.isEmpty()) {
            return new Estimate(Status.UNKNOWN, year, Money.zero(), 0, yearlyMax, Money.zero(),
                    "No pay deposits found for " + incomeYear + ". Enter the RRSP deduction limit from your "
                            + "CRA Notice of Assessment instead.");
        }
        Money annual = months.size() >= 12 ? income : income.dividedBy(months.size()).times(12);
        Money eighteen = annual.times(l.rrspEarnedIncomeRate());
        Money est = eighteen.min(yearlyMax).minus(pa).max(Money.zero());
        String how = "18% of " + annual + (months.size() < 12 ? " (" + months.size() + " months of " + incomeYear
                + " pay scaled to a year)" : " (" + incomeYear + " pay)") + " = " + eighteen + ", capped at "
                + yearlyMax + (pa.isPositive() ? ", minus your " + pa + " pension adjustment" : "")
                + ". Pay deposits are after tax, so your real room is likely higher; confirm with "
                + "your CRA Notice of Assessment.";
        return new Estimate(Status.ESTIMATED, year, annual, months.size(), yearlyMax, est, how);
    }

}

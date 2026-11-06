package com.maplecfo.planning;

import com.maplecfo.domain.Money;

import java.util.List;

/**
 * RRSP Home Buyers' Plan (HBP) or Lifelong Learning Plan (LLP) money that has to be paid back.
 * Each year you must repay at least 1/15 (HBP) or 1/10 (LLP) of what you took out; an amount you
 * don't repay that year is added to your income instead. Repayments do not use RRSP room.
 *
 * @param program            "Home Buyers' Plan" or "Lifelong Learning Plan"
 * @param firstRepaymentYear the first year a repayment is due
 * @param dueThisYear        still to repay this year to stay on schedule (zero before repayments start)
 */
public record RepaymentPlan(String program, Money withdrawn, Money repaid, Money balance, int firstRepaymentYear,
                            int years, Money yearlyPayment, Money dueThisYear, List<String> warnings) {

    public RepaymentPlan {
        warnings = List.copyOf(warnings);
    }

    public boolean isPaidOff() {
        return !balance.isPositive();
    }

    public String summary(int year) {
        if (isPaidOff()) return program + ": fully repaid (" + withdrawn + ").";
        if (year < firstRepaymentYear) {
            return program + ": " + balance + " to repay. Payments of " + yearlyPayment + " a year start in "
                    + firstRepaymentYear + ".";
        }
        return program + ": " + balance + " left to repay. "
                + (dueThisYear.isPositive() ? "Repay " + dueThisYear + " for " + year
                + " (designate it as a repayment on your tax return), or it is added to your income."
                : "You are on schedule for " + year + ".");
    }
}

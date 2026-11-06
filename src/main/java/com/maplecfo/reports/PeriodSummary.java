package com.maplecfo.reports;

import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.planning.CashFlowForecaster;

import java.time.LocalDate;
import java.util.List;

/**
 * Money in, money out and what was saved over a period, e.g. this month or this year so far.
 * Transfers between your own accounts are not spending; money moved to a TFSA, RRSP or FHSA is shown
 * separately because it is part of what you saved.
 *
 * @param income           money in (pay and other deposits)
 * @param spending         money out, transfers excluded
 * @param saved            income minus spending (negative when spending more than earning)
 * @param toRegistered     moved into a TFSA, RRSP or FHSA in the period
 * @param fromRegistered   taken out of a TFSA, RRSP or FHSA into this account in the period
 */
public record PeriodSummary(LocalDate from, LocalDate to, Money income, Money spending, Money saved,
                            Money toRegistered, Money fromRegistered) {

    public static PeriodSummary of(List<Transaction> all, LocalDate from, LocalDate to) {
        Money in = Money.zero();
        Money out = Money.zero();
        Money toReg = Money.zero();
        Money fromReg = Money.zero();
        for (Transaction t : all) {
            if (t.getDate().isBefore(from) || t.getDate().isAfter(to)) continue;
            if (t.getCategory() == Category.TRANSFER) {
                if (CashFlowForecaster.isRegistered(t.getDescription())) {
                    if (t.isExpense()) toReg = toReg.plus(t.getAmount().abs());
                    else fromReg = fromReg.plus(t.getAmount());
                }
                continue;
            }
            if (t.isExpense()) out = out.plus(t.getAmount().abs());
            else in = in.plus(t.getAmount());
        }
        return new PeriodSummary(from, to, in, out, in.minus(out), toReg, fromReg);
    }

    /** Share of income saved, in percent (0 when there was no income). */
    public long savingsRatePercent() {
        return income.isPositive() ? Math.round(saved.getAmount().doubleValue() / income.getAmount().doubleValue() * 100) : 0;
    }
}

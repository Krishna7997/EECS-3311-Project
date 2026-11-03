package com.maplecfo.planning;

import com.maplecfo.domain.Money;

/**
 * Average monthly cash flow from recent history (F09, F11).
 *
 * @param monthsOfHistory   complete months the averages are based on (0 to 3)
 * @param averageIncome     average money in per month (transfers excluded)
 * @param averageSpending   average money out per month (transfers excluded)
 * @param averageSurplus    income minus spending; negative when spending more than earning
 * @param fixedCostsPerMonth recurring bills per month (used as the safety buffer)
 * @param lowConfidence     true when fewer than 2 months of history are available
 */
public record CashFlowForecast(int monthsOfHistory, Money averageIncome, Money averageSpending,
                               Money averageSurplus, Money fixedCostsPerMonth, boolean lowConfidence) {

    public boolean hasData() {
        return monthsOfHistory > 0;
    }
}

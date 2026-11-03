package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.RecurringCharge;
import com.maplecfo.domain.Transaction;
import com.maplecfo.planning.RecurringChargeDetector;
import com.maplecfo.services.TransactionService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Subscriptions and other recurring charges from the last six months. */
public class RecurringChargesTool extends AbstractFinanceTool {

    private final TransactionService txService;
    private final RecurringChargeDetector detector;
    private final Clock clock;

    public RecurringChargesTool(TransactionService txService, RecurringChargeDetector detector, Clock clock) {
        this.txService = txService;
        this.detector = detector;
        this.clock = clock;
    }

    @Override public String name() { return "recurring_charges"; }

    @Override public String description() {
        return "Subscriptions and other recurring charges: merchant, how often, average amount, yearly cost, next date, price increases.";
    }

    @Override public JsonNode schema() { return schemaOf(); }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        LocalDate today = LocalDate.now(clock);
        List<Transaction> recent = txService.findAll().stream()
                .filter(t -> !t.getDate().isBefore(today.minusMonths(6))).toList();
        List<RecurringCharge> charges = detector.detect(recent, today);
        ObjectNode out = obj();
        ArrayNode list = out.putArray("charges");
        Money yearly = Money.zero();
        for (RecurringCharge c : charges) {
            ObjectNode o = list.addObject();
            o.put("merchant", c.merchant());
            o.put("frequency", c.frequency().name().toLowerCase());
            o.put("average_amount", c.averageAmount().toString());
            o.put("yearly_cost", c.yearlyCost().toString());
            o.put("next_date", c.nextExpectedDate().toString());
            o.put("price_increased", c.priceIncreased());
            yearly = yearly.plus(c.yearlyCost());
        }
        out.put("total_yearly_cost", yearly.toString());
        out.put("summary", charges.isEmpty() ? "No recurring charges were found."
                : charges.size() + " recurring charges costing " + yearly + " per year in total.");
        return out;
    }
}

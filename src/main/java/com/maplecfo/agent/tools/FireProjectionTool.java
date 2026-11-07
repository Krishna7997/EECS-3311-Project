package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.planning.FireCalculator;
import com.maplecfo.planning.FireInputs;
import com.maplecfo.planning.FireProjection;

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Supplier;

/** When the user could reach financial independence (FIRE). */
public class FireProjectionTool extends AbstractFinanceTool {

    private final FireCalculator calculator;
    private final Supplier<UserProfile> profile;
    private final Clock clock;

    public FireProjectionTool(FireCalculator calculator, Supplier<UserProfile> profile, Clock clock) {
        this.calculator = calculator;
        this.profile = profile;
        this.clock = clock;
    }

    @Override public String name() { return "fire_projection"; }

    @Override public String description() {
        return "Years and age until financial independence. Needs yearly spending and yearly saving; optional current "
                + "investments, real return (0.05 = 5%), withdrawal rate (0.04), the user's own target amount and the "
                + "age they want to retire at (then it returns the yearly saving needed). Also returns the Coast FIRE number.";
    }

    @Override public JsonNode schema() {
        return schemaOf("annual_spending:number!::Spending per year in dollars",
                "yearly_saving:number!::Amount saved per year",
                "invested:number::Investments today (default 0)",
                "real_return:number::Expected real return, e.g. 0.05",
                "withdrawal_rate:number::Safe withdrawal rate, e.g. 0.04",
                "target_amount:number::The user's own target instead of spending / withdrawal rate",
                "target_age:number::Age the user wants to retire at");
    }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        Integer birthYear = profile.get().getBirthYear();
        int age = birthYear == null ? 22 : LocalDate.now(clock).getYear() - birthYear;
        FireInputs in = new FireInputs(money(args, "invested", Money.zero()), money(args, "yearly_saving", Money.zero()),
                money(args, "annual_spending", Money.zero()), args.path("real_return").asDouble(0.05),
                args.path("withdrawal_rate").asDouble(0.04), age, money(args, "target_amount", null),
                args.hasNonNull("target_age") ? Integer.valueOf(args.get("target_age").asInt()) : null);
        FireProjection p = calculator.project(in);
        ObjectNode out = obj();
        out.put("target_amount", p.targetAmount().toString());
        out.put("current_age", age);
        out.put("years_to_fire", p.yearsToFire());
        out.put("fire_age", p.fireAge());
        out.put("safe_yearly_spending", p.safeYearlySpending().toString());
        out.put("coast_fire_number", p.coastNumber().toString());
        out.put("coast_age", p.coastAge());
        if (p.savingNeeded() != null) out.put("yearly_saving_needed_for_target_age", p.savingNeeded().toString());
        var sc = out.putArray("scenarios");
        p.scenarios().forEach(s -> sc.addObject().put("label", s.label()).put("yearly_saving", s.yearlySaving().toString())
                .put("years_to_fire", s.yearsToFire()));
        out.put("summary", p.reachable()
                ? "Target " + p.targetAmount() + ": reached in " + p.yearsToFire() + " years, at age " + p.fireAge() + "."
                : "Target " + p.targetAmount() + " is not reachable within 70 years with these inputs.");
        return out;
    }
}

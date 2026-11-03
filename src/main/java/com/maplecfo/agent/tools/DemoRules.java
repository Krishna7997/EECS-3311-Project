package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.llm.MockLlmClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keyword rules the offline mock uses to pick a tool when no API key is set (demo mode). */
public final class DemoRules {

    private static final Pattern AMOUNT = Pattern.compile("\\$?\\s?(\\d[\\d,]*(?:\\.\\d+)?)");

    private DemoRules() {
    }

    public static List<MockLlmClient.Rule> forMapleCfo() {
        List<MockLlmClient.Rule> rules = new ArrayList<>();
        rules.add(new MockLlmClient.Rule(q -> q.contains("afford"), "affordability", DemoRules::affordArgs));
        rules.add(new MockLlmClient.Rule(q -> q.contains("net worth") || q.contains("worth"), "net_worth", q -> MockLlmClient.args()));
        rules.add(new MockLlmClient.Rule(q -> q.contains("goal"), "goal_forecast", q -> MockLlmClient.args()));
        rules.add(new MockLlmClient.Rule(q -> q.matches(".*\\b(tfsa|rrsp|fhsa|room|contribut).*"), "contribution_room", DemoRules::roomArgs));
        rules.add(new MockLlmClient.Rule(q -> q.contains("budget"), "budget_status", DemoRules::monthArgs));
        rules.add(new MockLlmClient.Rule(q -> q.contains("subscription") || q.contains("recurring"), "recurring_charges", q -> MockLlmClient.args()));
        rules.add(new MockLlmClient.Rule(q -> q.contains("debt") || q.contains("loan") || q.contains("pay off"), "debt_payoff", q -> MockLlmClient.args()));
        rules.add(new MockLlmClient.Rule(q -> q.contains("spend") || q.contains("spent"), "spending_summary", DemoRules::monthArgs));
        return rules;
    }

    private static final String[] MONTHS = {"january", "february", "march", "april", "may", "june", "july",
            "august", "september", "october", "november", "december"};

    /** "october 2026", "in october" or "last month" become {"month":"2026-10"}. */
    public static ObjectNode monthArgs(String q) {
        ObjectNode a = MockLlmClient.args();
        java.time.YearMonth now = java.time.YearMonth.now();
        if (q.contains("last month")) {
            a.put("month", now.minusMonths(1).toString());
            return a;
        }
        for (int i = 0; i < MONTHS.length; i++) {
            Matcher m = Pattern.compile("\\b" + MONTHS[i] + "\\b(?:\\s+(\\d{4}))?").matcher(q);
            if (m.find()) {
                int year = m.group(1) != null ? Integer.parseInt(m.group(1))
                        : (i + 1 > now.getMonthValue() ? now.getYear() - 1 : now.getYear());
                a.put("month", java.time.YearMonth.of(year, i + 1).toString());
                return a;
            }
        }
        return a;
    }

    private static ObjectNode roomArgs(String q) {
        ObjectNode a = MockLlmClient.args();
        a.put("account", q.contains("rrsp") ? "RRSP" : q.contains("fhsa") ? "FHSA" : "TFSA");
        firstAmount(q).ifPresent(v -> a.put("amount", v));
        return a;
    }

    private static ObjectNode affordArgs(String q) {
        ObjectNode a = MockLlmClient.args();
        firstAmount(q).ifPresent(v -> a.put("amount", v));
        return a;
    }

    private static java.util.Optional<Double> firstAmount(String q) {
        Matcher m = AMOUNT.matcher(q);
        while (m.find()) {
            String digits = m.group(1).replace(",", "");
            double v = Double.parseDouble(digits);
            if (v >= 10) return java.util.Optional.of(v); // skip years-like small words? keep amounts >= $10
        }
        return java.util.Optional.empty();
    }
}

package com.maplecfo.agent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grounding guard (Stage 1 decision D2): every dollar amount in the agent's
 * answer must appear in a tool result (or in the user's own question).
 * This is a deterministic check on the AI's output.
 */
public class ResponseValidator {

    private static final Pattern DOLLARS = Pattern.compile("-?\\$\\s?(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d{1,2})?");
    private static final Pattern NUMBERS = Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?");
    private static final List<String> ADVICE_WORDS = List.of("stock", "etf", "fund", "crypto", "bitcoin", "invest in");

    /** Result of a check: the dollar amounts that could not be traced to evidence. */
    public record ValidationReport(boolean grounded, List<String> unsupportedAmounts) {
    }

    public ValidationReport validate(String answer, List<String> evidence) {
        Set<BigDecimal> known = new HashSet<>();
        for (String e : evidence) {
            Matcher m = NUMBERS.matcher(e);
            while (m.find()) {
                known.add(normalise(m.group()));
            }
        }
        List<String> unsupported = new ArrayList<>();
        for (String amount : extractAmounts(answer)) {
            BigDecimal v = normalise(amount.replace("$", "").replace("-", "").trim());
            if (!known.contains(v)) {
                unsupported.add(amount);
            }
        }
        return new ValidationReport(unsupported.isEmpty(), unsupported);
    }

    public List<String> extractAmounts(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = DOLLARS.matcher(text);
        while (m.find()) {
            out.add(m.group().replace(" ", ""));
        }
        return out;
    }

    /** Adds a short "education, not advice" note when the answer talks about investing. */
    public String appendDisclaimer(String answer) {
        String lower = answer.toLowerCase(Locale.ROOT);
        boolean investing = ADVICE_WORDS.stream().anyMatch(lower::contains);
        return investing ? answer + "\n\n(General education only, not financial advice.)" : answer;
    }

    private static BigDecimal normalise(String number) {
        return new BigDecimal(number.replace(",", "")).stripTrailingZeros();
    }
}

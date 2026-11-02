package com.maplecfo.ui.cli;

import java.util.ArrayList;
import java.util.List;

/** Formats results as plain-text tables and bars for the terminal. */
public class CliRenderer {

    /** Columns whose header starts with '>' are right-aligned (the '>' is not printed). */
    public String table(List<String> headers, List<List<String>> rows) {
        int n = headers.size();
        boolean[] right = new boolean[n];
        List<String> names = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            right[i] = headers.get(i).startsWith(">");
            names.add(right[i] ? headers.get(i).substring(1) : headers.get(i));
        }
        int[] width = new int[n];
        for (int i = 0; i < n; i++) {
            width[i] = names.get(i).length();
            for (List<String> r : rows) {
                width[i] = Math.max(width[i], i < r.size() ? r.get(i).length() : 0);
            }
        }
        StringBuilder sb = new StringBuilder();
        appendRow(sb, names, width, right);
        StringBuilder rule = new StringBuilder();
        for (int i = 0; i < n; i++) {
            rule.append("-".repeat(width[i])).append(i < n - 1 ? "  " : "");
        }
        sb.append(rule).append('\n');
        for (List<String> r : rows) {
            appendRow(sb, r, width, right);
        }
        return sb.toString();
    }

    private void appendRow(StringBuilder sb, List<String> cells, int[] width, boolean[] right) {
        for (int i = 0; i < width.length; i++) {
            String c = i < cells.size() ? cells.get(i) : "";
            String pad = " ".repeat(width[i] - c.length());
            sb.append(right[i] ? pad + c : c + pad);
            if (i < width.length - 1) sb.append("  ");
        }
        sb.append('\n');
    }

    /** A 20-character progress bar, e.g. "[##########----------]  50%". Over 100% shows "!". */
    public String bar(double percent) {
        int filled = (int) Math.round(Math.min(percent, 100) / 5.0);
        String b = "#".repeat(filled) + "-".repeat(20 - filled);
        return "[" + b + "] " + String.format("%3d%%", Math.round(percent)) + (percent > 100 ? " !" : "");
    }

    public String shortId(String id) {
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    public String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

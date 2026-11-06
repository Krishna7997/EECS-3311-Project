package com.maplecfo.automation.limits;

import com.maplecfo.domain.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F15 bot: reads the CRA "MP, DB, RRSP, DPSP, ALDA, TFSA limits, YMPE and the YAMPE" page and pulls out the
 * RRSP dollar limit and TFSA limit for each year. It finds the columns by their headings, so a reordered
 * table still works; if the headings are not found it returns nothing rather than guessing.
 */
public class CraLimitsScraper {

    public static final String CRA_URL = "https://www.canada.ca/en/revenue-agency/services/tax/"
            + "registered-plans-administrators/pspa/mp-rrsp-dpsp-tfsa-limits-ympe.html";

    public record Scraped(Map<Integer, Money> tfsa, Map<Integer, Money> rrsp) {
        public boolean isEmpty() {
            return tfsa.isEmpty() && rrsp.isEmpty();
        }
    }

    private static final Pattern ROW = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL = Pattern.compile("<t[hd][^>]*>(.*?)</t[hd]>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEY = Pattern.compile("\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]{2})?)");

    public Scraped parse(String html) {
        Map<Integer, Money> tfsa = new TreeMap<>();
        Map<Integer, Money> rrsp = new TreeMap<>();
        int tfsaCol = -1;
        int rrspCol = -1;
        Matcher rows = ROW.matcher(html);
        while (rows.find()) {
            List<String> cells = cells(rows.group(1));
            if (cells.isEmpty()) continue;
            if (tfsaCol < 0 || rrspCol < 0) {
                for (int i = 0; i < cells.size(); i++) {
                    String c = cells.get(i).toUpperCase();
                    if (c.contains("TFSA")) tfsaCol = i;
                    if (c.contains("RRSP")) rrspCol = i;
                }
                continue;
            }
            Integer year = year(cells.get(0));
            if (year == null) continue;
            money(cells, tfsaCol).ifPresent(m -> tfsa.put(year, m));
            money(cells, rrspCol).ifPresent(m -> rrsp.put(year, m));
        }
        return new Scraped(tfsa, rrsp);
    }

    private static List<String> cells(String rowHtml) {
        List<String> out = new ArrayList<>();
        Matcher m = CELL.matcher(rowHtml);
        while (m.find()) {
            out.add(m.group(1).replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replace("&#36;", "$")
                    .replaceAll("\\s+", " ").trim());
        }
        return out;
    }

    private static Integer year(String cell) {
        Matcher m = Pattern.compile("^(20\\d{2})\\b").matcher(cell);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static java.util.Optional<Money> money(List<String> cells, int col) {
        if (col < 0 || col >= cells.size()) return java.util.Optional.empty();
        Matcher m = MONEY.matcher(cells.get(col));
        if (!m.find()) return java.util.Optional.empty();
        try {
            BigDecimal v = new BigDecimal(m.group(1).replace(",", ""));
            return v.signum() > 0 ? java.util.Optional.of(Money.of(v)) : java.util.Optional.empty();
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}

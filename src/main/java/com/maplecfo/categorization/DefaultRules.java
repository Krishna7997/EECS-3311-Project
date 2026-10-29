package com.maplecfo.categorization;

import com.maplecfo.domain.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Built-in keyword rules for common Canadian merchants. */
final class DefaultRules {

    private DefaultRules() {
    }

    private static final Map<Category, List<String>> KEYWORDS = Map.ofEntries(
            Map.entry(Category.GROCERIES, List.of("loblaws", "no frills", "nofrills", "metro", "sobeys", "freshco",
                    "food basics", "farm boy", "t t supermarket", "costco", "fortinos", "zehrs", "real canadian superstore",
                    "longos", "save on foods", "iga")),
            Map.entry(Category.DINING, List.of("tim hortons", "starbucks", "mcdonald s", "mcdonalds", "uber eats",
                    "ubereats", "doordash", "skipthedishes", "skip the dishes", "subway", "a w", "pizza", "chipotle",
                    "harvey s", "second cup", "restaurant", "cafe")),
            Map.entry(Category.TRANSPORT, List.of("presto", "ttc", "go transit", "uber", "lyft", "stm", "translink",
                    "oc transpo", "via rail", "parking", "green p")),
            Map.entry(Category.GAS, List.of("petro canada", "esso", "shell", "pioneer", "ultramar", "husky", "chevron")),
            Map.entry(Category.RENT, List.of("rent", "landlord", "property management")),
            Map.entry(Category.UTILITIES, List.of("hydro", "toronto hydro", "enbridge", "rogers", "bell canada", "bell",
                    "fido", "telus", "koodo", "freedom mobile", "virgin plus", "alectra", "water bill")),
            Map.entry(Category.SUBSCRIPTIONS, List.of("netflix", "spotify", "disney plus", "amazon prime", "apple com bill",
                    "crave", "youtube premium", "audible", "chatgpt", "adobe", "goodlife", "gym")),
            Map.entry(Category.SHOPPING, List.of("amazon", "walmart", "best buy", "canadian tire", "winners", "ikea",
                    "shoppers drug mart", "dollarama", "indigo", "h m", "uniqlo", "apple store")),
            Map.entry(Category.TRAVEL, List.of("air canada", "westjet", "porter", "airbnb", "expedia", "booking com",
                    "hotel", "flair")),
            Map.entry(Category.INCOME, List.of("payroll", "salary", "direct deposit", "pay deposit", "cra deposit",
                    "gst credit", "osap")),
            Map.entry(Category.TRANSFER, List.of("e transfer", "etransfer", "interac", "transfer to", "transfer from",
                    "tfsa contribution", "rrsp contribution", "credit card payment", "payment thank you")));

    static List<CategoryRule> canadian() {
        List<CategoryRule> rules = new ArrayList<>();
        KEYWORDS.forEach((category, words) -> words.forEach(w -> rules.add(new CategoryRule(w, category, false))));
        return rules;
    }
}

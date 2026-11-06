package com.maplecfo.automation.limits;

/** Prints the bot's message (shown in the GitHub Actions log). */
public class ConsoleNotifier implements LimitsNotifier {

    @Override
    public void notify(String title, String body) {
        System.out.println("== " + title + " ==");
        System.out.println(body);
    }
}

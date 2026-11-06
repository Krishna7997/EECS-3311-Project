package com.maplecfo.automation.limits;

/** F15 bot: tells the maintainers what the weekly check found (GitHub issue, console, ...). */
public interface LimitsNotifier {

    void notify(String title, String body) throws java.io.IOException;
}

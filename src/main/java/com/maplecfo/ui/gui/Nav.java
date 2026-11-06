package com.maplecfo.ui.gui;

/** Lets a page send the user to another page, e.g. an empty state's "Import a CSV" button. */
interface Nav {

    String HOME = "home";
    String CHAT = "chat";
    String TRANSACTIONS = "transactions";
    String BUDGETS = "budgets";
    String SUBSCRIPTIONS = "subscriptions";
    String ACCOUNTS = "accounts";
    String GOALS = "goals";
    String AFFORD = "afford";
    String REGISTERED = "registered";
    String DEBT = "debt";
    String RETIREMENT = "retirement";
    String REPORT = "report";
    String SETTINGS = "settings";

    void go(String pageId);

    /** Restarts the automatic import after the folder was changed in Settings. */
    void importFolderChanged();

    /** Opens the AI chat and asks this question. */
    void ask(String question);
}

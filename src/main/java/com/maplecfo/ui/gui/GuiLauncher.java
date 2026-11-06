package com.maplecfo.ui.gui;

import javafx.application.Application;

/**
 * Starts the GUI. A separate class (that does not extend Application) is needed so the
 * GUI can start from the single shaded jar: java -jar target/maplecfo.jar gui
 */
public final class GuiLauncher {

    private GuiLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(MapleCfoApp.class, args);
    }
}

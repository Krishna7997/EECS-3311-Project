package com.maplecfo.ui.gui;

import javafx.scene.Node;

/** One tab of the main window. refresh() is called when the tab is shown and when data changes. */
interface View {

    String title();

    Node root();

    void refresh();
}

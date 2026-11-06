package com.maplecfo.ui.gui;

import javafx.scene.Node;

/** One page of the main window. refresh() is called when the page is shown and when data changes. */
interface View {

    String title();

    /** One line shown under the page title. */
    default String subtitle() {
        return "";
    }

    /** SVG path for the sidebar icon (see {@link Icons}). */
    default String icon() {
        return Icons.LIST;
    }

    Node root();

    void refresh();
}

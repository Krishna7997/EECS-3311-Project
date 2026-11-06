package com.maplecfo.ui.gui;

import javafx.scene.Node;

/** One page of the main window. refresh() is called when the page is shown and when data changes. */
interface View {

    /** Stable id used for navigation (see {@link Nav}). */
    String id();

    String title();

    /** One line under the page title saying what the page is for. */
    default String subtitle() {
        return "";
    }

    /** Sidebar group, e.g. "Money" or "Plan". */
    String section();

    /** SVG path for the sidebar icon (see {@link Icons}). */
    String icon();

    Node root();

    void refresh();
}

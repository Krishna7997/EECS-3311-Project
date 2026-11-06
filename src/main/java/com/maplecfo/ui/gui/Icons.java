package com.maplecfo.ui.gui;

import javafx.scene.layout.Region;

/** Simple line icons for the sidebar, drawn as SVG paths on a 24 x 24 grid. */
final class Icons {

    private Icons() {
    }

    static final String HOME = "M3 11 L12 3 L21 11 L21 21 L14 21 L14 14 L10 14 L10 21 L3 21 Z";
    static final String CHAT = "M4 4 H20 A2 2 0 0 1 22 6 V15 A2 2 0 0 1 20 17 H9 L4 21 V17 A2 2 0 0 1 2 15 V6 A2 2 0 0 1 4 4 Z";
    static final String LIST = "M3 5 H5 V7 H3 Z M8 5 H21 V7 H8 Z M3 11 H5 V13 H3 Z M8 11 H21 V13 H8 Z M3 17 H5 V19 H3 Z M8 17 H21 V19 H8 Z";
    static final String BUDGET = "M12 2 A10 10 0 1 0 22 12 H12 Z M14 0 A10 10 0 0 1 24 10 H14 Z";
    static final String WALLET = "M3 6 A2 2 0 0 1 5 4 H19 V7 H21 A1 1 0 0 1 22 8 V19 A1 1 0 0 1 21 20 H5 A2 2 0 0 1 3 18 Z M16 12 A1.5 1.5 0 1 0 16 15 A1.5 1.5 0 1 0 16 12 Z";
    static final String PLAN = "M3 20 H21 V22 H3 Z M4 13 H8 V18 H4 Z M10 8 H14 V18 H10 Z M16 3 H20 V18 H16 Z";
    static final String REPORT = "M6 2 H15 L20 7 V22 H6 Z M9 12 H17 V13.5 H9 Z M9 15.5 H17 V17 H9 Z M9 8.5 H13 V10 H9 Z";
    static final String LEAF = "M12 2 L14 7 L19 5 L17 10 L22 12 L17 14 L19 19 L14 17 L12 22 L10 17 L5 19 L7 14 L2 12 L7 10 L5 5 L10 7 Z";

    /** A square region painted in the shape of the path, so every icon takes exactly the same space. */
    static Region of(String path, double size) {
        Region r = new Region();
        r.setStyle("-fx-shape: \"" + path + "\"; -fx-scale-shape: true;");
        r.setMinSize(size, size);
        r.setPrefSize(size, size);
        r.setMaxSize(size, size);
        r.getStyleClass().add("icon");
        return r;
    }
}

package com.maplecfo.ui.gui;

import javafx.scene.layout.Region;

/** Simple solid icons drawn from SVG paths on a 24 x 24 grid. */
final class Icons {

    private Icons() {
    }

    static final String HOME = "M3 11 L12 3 L21 11 L21 21 L14 21 L14 14 L10 14 L10 21 L3 21 Z";
    static final String CHAT = "M4 4 H20 A2 2 0 0 1 22 6 V15 A2 2 0 0 1 20 17 H9 L4 21 V17 A2 2 0 0 1 2 15 V6 A2 2 0 0 1 4 4 Z";
    static final String LIST = "M3 5 H5 V7 H3 Z M8 5 H21 V7 H8 Z M3 11 H5 V13 H3 Z M8 11 H21 V13 H8 Z M3 17 H5 V19 H3 Z M8 17 H21 V19 H8 Z";
    static final String BUDGET = "M11 2 A10 10 0 1 0 22 13 H11 Z M13 0 A10 10 0 0 1 24 11 H13 Z";
    static final String REPEAT = "M4 7 H17 V4 L22 9 L17 14 V11 H6 V15 H3 V8 A1 1 0 0 1 4 7 Z M20 17 H7 V20 L2 15 L7 10 V13 H18 V9 H21 V16 A1 1 0 0 1 20 17 Z";
    static final String WALLET = "M3 6 A2 2 0 0 1 5 4 H19 V7 H21 A1 1 0 0 1 22 8 V19 A1 1 0 0 1 21 20 H5 A2 2 0 0 1 3 18 Z M16 12 A1.5 1.5 0 1 0 16 15 A1.5 1.5 0 1 0 16 12 Z";
    static final String FLAG = "M5 2 H7 V22 H5 Z M8 3 H20 L17 8 L20 13 H8 Z";
    static final String CART = "M2 3 H5 L8 15 H19 L22 6 H7 L6.5 4 Z M9 17 A2 2 0 1 0 9 21 A2 2 0 1 0 9 17 Z M18 17 A2 2 0 1 0 18 21 A2 2 0 1 0 18 17 Z";
    static final String SHIELD = "M12 2 L21 6 V12 C21 17 17 21 12 22 C7 21 3 17 3 12 V6 Z";
    static final String CARD = "M2 6 A2 2 0 0 1 4 4 H20 A2 2 0 0 1 22 6 V8 H2 Z M2 11 H22 V18 A2 2 0 0 1 20 20 H4 A2 2 0 0 1 2 18 Z M5 15 H10 V17 H5 Z";
    static final String PLAN = "M3 20 H21 V22 H3 Z M4 13 H8 V18 H4 Z M10 8 H14 V18 H10 Z M16 3 H20 V18 H16 Z";
    static final String REPORT = "M6 2 H15 L20 7 V22 H6 Z M9 12 H17 V13.5 H9 Z M9 15.5 H17 V17 H9 Z M9 8.5 H13 V10 H9 Z";
    static final String GEAR = "M3 6 H13 V8 H3 Z M17 6 H21 V8 H17 Z M13 4 H17 V10 H13 Z M3 16 H7 V18 H3 Z M11 16 H21 V18 H11 Z M7 14 H11 V20 H7 Z";
    static final String LEAF = "M12 2 L14 7 L19 5 L17 10 L22 12 L17 14 L19 19 L14 17 L12 22 L10 17 L5 19 L7 14 L2 12 L7 10 L5 5 L10 7 Z";
    static final String CHECK = "M9 16.2 L4.8 12 L3.4 13.4 L9 19 L21 7 L19.6 5.6 Z";
    static final String UPLOAD = "M11 16 V6.8 L7.4 10.4 L6 9 L12 3 L18 9 L16.6 10.4 L13 6.8 V16 Z M4 18 H20 V21 H4 Z";
    static final String PLUS = "M11 4 H13 V11 H20 V13 H13 V20 H11 V13 H4 V11 H11 Z";
    static final String SPARK = "M12 2 L14.2 9.8 L22 12 L14.2 14.2 L12 22 L9.8 14.2 L2 12 L9.8 9.8 Z";

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

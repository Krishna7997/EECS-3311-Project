package com.maplecfo.ui.gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.List;

/**
 * Lays its children out in equal columns, using as many columns as fit (each at least {@code minTileWidth}
 * wide). On a narrow window four KPI tiles become 2 x 2 instead of being squeezed until the numbers are cut off.
 */
final class ResponsiveGrid extends Pane {

    private final double minTileWidth;
    private final double gap;

    ResponsiveGrid(double minTileWidth, double gap, Node... children) {
        this.minTileWidth = minTileWidth;
        this.gap = gap;
        getChildren().addAll(children);
    }

    private int columns(double width) {
        int n = getManagedChildren().size();
        if (n == 0) return 1;
        int cols = Math.max(1, Math.min(n, (int) Math.floor((width + gap) / (minTileWidth + gap))));
        while (cols > 1 && n % cols != 0) {
            cols--; // 4 tiles: 4 or 2 x 2, never 3 + 1
        }
        return cols;
    }

    /** Height depends on width (more rows when narrow), so parents must give the width first. */
    @Override
    public javafx.geometry.Orientation getContentBias() {
        return javafx.geometry.Orientation.HORIZONTAL;
    }

    private double tileWidth(double width, int cols) {
        return (width - gap * (cols - 1)) / cols;
    }

    @Override
    protected double computePrefHeight(double width) {
        Insets in = getInsets();
        double w = (width < 0 ? getWidth() : width) - in.getLeft() - in.getRight();
        if (w <= 0) w = minTileWidth * Math.max(1, getManagedChildren().size());
        int cols = columns(w);
        double tw = tileWidth(w, cols);
        double total = 0;
        List<Node> kids = getManagedChildren();
        for (int i = 0; i < kids.size(); i += cols) {
            double rowH = 0;
            for (int j = i; j < Math.min(i + cols, kids.size()); j++) {
                rowH = Math.max(rowH, kids.get(j).prefHeight(tw));
            }
            total += rowH + (i > 0 ? gap : 0);
        }
        return total + in.getTop() + in.getBottom();
    }

    @Override
    protected double computePrefWidth(double height) {
        return minTileWidth * Math.max(1, getManagedChildren().size()) + gap * (getManagedChildren().size() - 1);
    }

    @Override
    protected double computeMinWidth(double height) {
        return minTileWidth;
    }

    @Override
    protected void layoutChildren() {
        Insets in = getInsets();
        double w = getWidth() - in.getLeft() - in.getRight();
        int cols = columns(w);
        double tw = tileWidth(w, cols);
        List<Node> kids = getManagedChildren();
        double y = in.getTop();
        for (int i = 0; i < kids.size(); i += cols) {
            double rowH = 0;
            for (int j = i; j < Math.min(i + cols, kids.size()); j++) {
                rowH = Math.max(rowH, kids.get(j).prefHeight(tw));
            }
            for (int j = i; j < Math.min(i + cols, kids.size()); j++) {
                kids.get(j).resizeRelocate(in.getLeft() + (j - i) * (tw + gap), y, tw, rowH);
            }
            y += rowH + gap;
        }
    }
}

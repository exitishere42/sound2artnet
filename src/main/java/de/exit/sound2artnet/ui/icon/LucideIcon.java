package de.exit.sound2artnet.ui.icon;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Standardkonforme Vektor-Icons aus dem Lucide Icon Set (lucide.dev).
 * Strikte Einhaltung der Zero-Emoji-Regel.
 */
public class LucideIcon extends Canvas {
    private String name;
    private Color color;
    private final double size;

    public LucideIcon(String name, double size, Color color) {
        super(size, size);
        this.name = name != null ? name : "circle";
        this.size = size;
        this.color = color != null ? color : Color.WHITE;
        draw();
    }

    public void setIcon(String name, Color color) {
        this.name = name;
        this.color = color;
        draw();
    }

    public void setColor(Color color) {
        this.color = color;
        draw();
    }

    public void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        double scale = size / 24.0;
        gc.setStroke(color);
        gc.setFill(color);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.setLineWidth(2.0 * scale);

        switch (name.toLowerCase()) {
            case "play" -> {
                double[] x = {7 * scale, 19 * scale, 7 * scale};
                double[] y = {4 * scale, 12 * scale, 20 * scale};
                gc.fillPolygon(x, y, 3);
                gc.strokePolygon(x, y, 3);
            }
            case "square" -> {
                gc.fillRect(5 * scale, 5 * scale, 14 * scale, 14 * scale);
                gc.strokeRect(5 * scale, 5 * scale, 14 * scale, 14 * scale);
            }
            case "activity" -> {
                double[] ptsX = {2, 6, 9, 14, 17, 22};
                double[] ptsY = {12, 12, 3, 21, 12, 12};
                for (int i = 0; i < ptsX.length - 1; i++) {
                    gc.strokeLine(ptsX[i] * scale, ptsY[i] * scale, ptsX[i + 1] * scale, ptsY[i + 1] * scale);
                }
            }
            case "music" -> {
                // Lucide music: path d="M9 18V5l12-2v13", circle cx="6" cy="18" r="3", circle cx="18" cy="16" r="3"
                gc.strokeLine(9 * scale, 18 * scale, 9 * scale, 5 * scale);
                gc.strokeLine(9 * scale, 5 * scale, 21 * scale, 3 * scale);
                gc.strokeLine(21 * scale, 3 * scale, 21 * scale, 16 * scale);
                gc.strokeOval(3 * scale, 15 * scale, 6 * scale, 6 * scale);
                gc.strokeOval(15 * scale, 13 * scale, 6 * scale, 6 * scale);
            }
            case "mic" -> {
                // Lucide mic
                gc.strokeRoundRect(9 * scale, 2 * scale, 6 * scale, 12 * scale, 6 * scale, 6 * scale);
                gc.strokeArc(5 * scale, 7 * scale, 14 * scale, 10 * scale, 180, 180, ArcType.OPEN);
                gc.strokeLine(12 * scale, 17 * scale, 12 * scale, 21 * scale);
                gc.strokeLine(8 * scale, 21 * scale, 16 * scale, 21 * scale);
            }
            case "trending-up" -> {
                double[] ptsX = {1, 8.5, 13.5, 23};
                double[] ptsY = {18, 10.5, 15.5, 6};
                for (int i = 0; i < ptsX.length - 1; i++) {
                    gc.strokeLine(ptsX[i] * scale, ptsY[i] * scale, ptsX[i + 1] * scale, ptsY[i + 1] * scale);
                }
                gc.strokeLine(17 * scale, 6 * scale, 23 * scale, 6 * scale);
                gc.strokeLine(23 * scale, 6 * scale, 23 * scale, 12 * scale);
            }
            case "refresh-cw" -> {
                gc.strokeArc(4 * scale, 4 * scale, 16 * scale, 16 * scale, 30, 280, ArcType.OPEN);
                double[] ax = {19 * scale, 19 * scale, 14 * scale};
                double[] ay = {4 * scale, 10 * scale, 7 * scale};
                gc.fillPolygon(ax, ay, 3);
            }
            case "sliders" -> {
                gc.strokeLine(6 * scale, 3 * scale, 6 * scale, 21 * scale);
                gc.strokeLine(12 * scale, 3 * scale, 12 * scale, 21 * scale);
                gc.strokeLine(18 * scale, 3 * scale, 18 * scale, 21 * scale);

                gc.strokeLine(3 * scale, 8 * scale, 9 * scale, 8 * scale);
                gc.strokeLine(9 * scale, 16 * scale, 15 * scale, 16 * scale);
                gc.strokeLine(15 * scale, 10 * scale, 21 * scale, 10 * scale);
            }
            case "circle" -> {
                gc.fillOval(5 * scale, 5 * scale, 14 * scale, 14 * scale);
            }
            case "plus" -> {
                gc.strokeLine(12 * scale, 5 * scale, 12 * scale, 19 * scale);
                gc.strokeLine(5 * scale, 12 * scale, 19 * scale, 12 * scale);
            }
            case "trash-2" -> {
                gc.strokeLine(3 * scale, 6 * scale, 21 * scale, 6 * scale);
                gc.strokeLine(10 * scale, 11 * scale, 10 * scale, 17 * scale);
                gc.strokeLine(14 * scale, 11 * scale, 14 * scale, 17 * scale);
                gc.strokeRoundRect(5 * scale, 6 * scale, 14 * scale, 15 * scale, 2 * scale, 2 * scale);
                gc.strokeLine(9 * scale, 6 * scale, 9 * scale, 3 * scale);
                gc.strokeLine(9 * scale, 3 * scale, 15 * scale, 3 * scale);
                gc.strokeLine(15 * scale, 3 * scale, 15 * scale, 6 * scale);
            }
            case "edit-3" -> {
                gc.strokeLine(12 * scale, 20 * scale, 20 * scale, 20 * scale);
                double[] px = {18 * scale, 21 * scale, 7 * scale, 4 * scale};
                double[] py = {3 * scale, 6 * scale, 20 * scale, 20 * scale};
                gc.strokePolygon(px, py, 4);
            }
            case "zap" -> {
                double[] zx = {13 * scale, 4 * scale, 11 * scale, 10 * scale, 19 * scale, 12 * scale};
                double[] zy = {2 * scale, 13 * scale, 13 * scale, 22 * scale, 11 * scale, 11 * scale};
                gc.strokePolygon(zx, zy, 6);
            }
            case "layers" -> {
                // Three layered polygons
                double[] lx1 = {12 * scale, 2 * scale, 12 * scale, 22 * scale};
                double[] ly1 = {2 * scale, 7 * scale, 12 * scale, 7 * scale};
                gc.strokePolygon(lx1, ly1, 4);
                gc.strokeLine(2 * scale, 12 * scale, 12 * scale, 17 * scale);
                gc.strokeLine(12 * scale, 17 * scale, 22 * scale, 12 * scale);
                gc.strokeLine(2 * scale, 17 * scale, 12 * scale, 22 * scale);
                gc.strokeLine(12 * scale, 22 * scale, 22 * scale, 17 * scale);
            }
            case "maximize" -> {
                // Lucide maximize: path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"
                gc.strokePolyline(new double[]{3 * scale, 3 * scale, 8 * scale}, new double[]{8 * scale, 3 * scale, 3 * scale}, 3);
                gc.strokePolyline(new double[]{16 * scale, 21 * scale, 21 * scale}, new double[]{3 * scale, 3 * scale, 8 * scale}, 3);
                gc.strokePolyline(new double[]{21 * scale, 21 * scale, 16 * scale}, new double[]{16 * scale, 21 * scale, 21 * scale}, 3);
                gc.strokePolyline(new double[]{8 * scale, 3 * scale, 3 * scale}, new double[]{21 * scale, 21 * scale, 16 * scale}, 3);
            }
            case "minimize" -> {
                // Lucide minimize: inward brackets
                gc.strokePolyline(new double[]{4 * scale, 9 * scale, 9 * scale}, new double[]{9 * scale, 9 * scale, 4 * scale}, 3);
                gc.strokePolyline(new double[]{20 * scale, 15 * scale, 15 * scale}, new double[]{9 * scale, 9 * scale, 4 * scale}, 3);
                gc.strokePolyline(new double[]{20 * scale, 15 * scale, 15 * scale}, new double[]{15 * scale, 15 * scale, 20 * scale}, 3);
                gc.strokePolyline(new double[]{4 * scale, 9 * scale, 9 * scale}, new double[]{15 * scale, 15 * scale, 20 * scale}, 3);
            }
            case "folder-open" -> {
                // Lucide folder-open
                gc.strokePolyline(new double[]{2 * scale, 2 * scale, 9 * scale, 12 * scale, 22 * scale, 22 * scale}, 
                                  new double[]{20 * scale, 5 * scale, 5 * scale, 8 * scale, 8 * scale, 12 * scale}, 6);
                double[] fx = {2 * scale, 6 * scale, 22 * scale, 18 * scale};
                double[] fy = {20 * scale, 11 * scale, 11 * scale, 20 * scale};
                gc.strokePolygon(fx, fy, 4);
            }
            case "file-text" -> {
                // Lucide file-text
                gc.strokePolyline(new double[]{14 * scale, 14 * scale, 20 * scale}, new double[]{2 * scale, 8 * scale, 8 * scale}, 3);
                gc.strokePolyline(new double[]{14 * scale, 4 * scale, 4 * scale, 20 * scale, 20 * scale, 14 * scale},
                                  new double[]{2 * scale, 2 * scale, 22 * scale, 22 * scale, 8 * scale, 2 * scale}, 6);
                gc.strokeLine(8 * scale, 13 * scale, 16 * scale, 13 * scale);
                gc.strokeLine(8 * scale, 17 * scale, 14 * scale, 17 * scale);
            }
            default -> {
                gc.strokeOval(4 * scale, 4 * scale, 16 * scale, 16 * scale);
            }
        }
    }
}

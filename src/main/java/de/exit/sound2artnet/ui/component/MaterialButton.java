package de.exit.sound2artnet.ui.component;

import de.exit.sound2artnet.ui.MaterialTheme;
import de.exit.sound2artnet.ui.icon.LucideIcon;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Material Design 2 Button mit Farbzuständen und Lucide-Icon.
 */
public class MaterialButton extends Button {
    private final LucideIcon icon;
    private Color baseBg;
    private Color baseFg;

    public MaterialButton(String text, String iconName, Color bg, Color fg,
                          double iconSize, double padH, double padV, double fontSize,
                          boolean bold, Runnable action) {
        this.baseBg = bg;
        this.baseFg = fg;

        this.icon = new LucideIcon(iconName, iconSize, fg);
        setGraphic(icon);
        setText(text);
        setCursor(Cursor.HAND);
        setFont(Font.font(MaterialTheme.FONT_FAMILY, bold ? FontWeight.BOLD : FontWeight.NORMAL, fontSize));

        updateStyle(bg, fg);

        setOnMouseEntered(e -> {
            Color hoverBg = bg.brighter();
            updateStyle(hoverBg, fg);
        });

        setOnMouseExited(e -> updateStyle(baseBg, baseFg));

        if (action != null) {
            setOnAction(e -> action.run());
        }
    }

    public void setAction(Runnable action) {
        setOnAction(e -> {
            if (action != null) {
                action.run();
            }
        });
    }

    public void updateColors(Color bg, Color fg, String iconName, String text) {
        this.baseBg = bg;
        this.baseFg = fg;
        if (iconName != null) {
            this.icon.setIcon(iconName, fg);
        } else {
            this.icon.setColor(fg);
        }
        if (text != null) {
            setText(text);
        }
        updateStyle(bg, fg);
    }

    private void updateStyle(Color bg, Color fg) {
        String hexBg = toHex(bg);
        String hexFg = toHex(fg);
        setStyle("-fx-background-color: " + hexBg + ";" +
                 "-fx-text-fill: " + hexFg + ";" +
                 "-fx-background-radius: 4px;" +
                 "-fx-border-radius: 4px;" +
                 "-fx-border-color: transparent;" +
                 "-fx-padding: 6px 12px;");
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }
}

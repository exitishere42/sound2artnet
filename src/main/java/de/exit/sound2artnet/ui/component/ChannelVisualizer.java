package de.exit.sound2artnet.ui.component;

import de.exit.sound2artnet.fixture.ChannelFunction;
import de.exit.sound2artnet.fixture.ChannelMapping;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.ui.MaterialTheme;
import de.exit.sound2artnet.ui.icon.LucideIcon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * 512-Kanal DMX-Visualizer mit 32 sichtbaren Kanälen im Viewport,
 * horizontalem Scrollen, Schnell-Sprungtasten und semantischer Fixture-Kanal-Anzeige.
 */
public class ChannelVisualizer extends VBox {
    public static final int TOTAL_CHANNELS = 512;
    public static final int VISIBLE_COUNT = 32;

    private final Canvas canvas;
    private final CanvasPane canvasPane;
    private final ScrollBar scrollBar;
    private final int[] prevChannels = new int[TOTAL_CHANNELS];
    private List<FixturePatch> currentPatches = List.of();

    private double channelWidth = 25.0;
    private double barWidth = 19.0;
    private double scrollOffsetChannels = 0.0;
    private int hoveredChannel = -1;

    private final Label lblTitle;
    private final ComboBox<Integer> cbUniverse;
    private int selectedUniverse = 0;
    private final Label lblBereich;

    public ChannelVisualizer() {
        setPadding(new Insets(10, 14, 10, 14));
        setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_1DP + 
                 "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + 
                 "; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px;");

        Arrays.fill(prevChannels, 0);

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 8, 0));

        LucideIcon iconSliders = new LucideIcon("sliders", 14, MaterialTheme.COLOR_TEXT_MED);
        lblTitle = new Label(de.exit.sound2artnet.util.I18n.get("visualizer.title"));
        lblTitle.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));

        cbUniverse = new ComboBox<>();
        cbUniverse.getItems().add(0);
        cbUniverse.setValue(0);
        cbUniverse.setPrefWidth(90);
        cbUniverse.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_2DP + "; -fx-font-size: 11px;");
        cbUniverse.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : "Uni " + item);
            }
        });
        cbUniverse.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : "Uni " + item);
            }
        });
        cbUniverse.setOnAction(e -> {
            if (cbUniverse.getValue() != null) {
                selectedUniverse = cbUniverse.getValue();
                render();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox jumpBox = new HBox(4);
        jumpBox.setAlignment(Pos.CENTER_RIGHT);

        lblBereich = new Label(de.exit.sound2artnet.util.I18n.get("visualizer.range"));
        lblBereich.setTextFill(MaterialTheme.COLOR_TEXT_DISABLED);
        lblBereich.setFont(Font.font("Segoe UI", 10));
        jumpBox.getChildren().add(lblBereich);

        int[][] jumps = {
            {1, 32}, {33, 64}, {65, 96}, {97, 128}, {129, 256}, {257, 512}
        };

        for (int[] j : jumps) {
            String labelText = j[0] + "-" + j[1];
            Button btnJump = new Button(labelText);
            btnJump.setCursor(Cursor.HAND);
            btnJump.setFont(Font.font("Segoe UI", 10));
            btnJump.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_2DP + 
                             "; -fx-text-fill: " + MaterialTheme.HEX_TEXT_HIGH + 
                             "; -fx-background-radius: 3px; -fx-padding: 2px 6px;");
            btnJump.setOnMouseEntered(e -> btnJump.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_4DP + "; -fx-text-fill: #FFFFFF; -fx-background-radius: 3px; -fx-padding: 2px 6px;"));
            btnJump.setOnMouseExited(e -> btnJump.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_2DP + "; -fx-text-fill: " + MaterialTheme.HEX_TEXT_HIGH + "; -fx-background-radius: 3px; -fx-padding: 2px 6px;"));
            btnJump.setOnAction(e -> scrollToChannel(j[0]));
            jumpBox.getChildren().add(btnJump);
        }

        header.getChildren().addAll(iconSliders, lblTitle, cbUniverse, spacer, jumpBox);

        // Canvas & Entkoppelter Container
        canvas = new Canvas(800, 150);
        canvasPane = new CanvasPane(canvas, this);
        VBox.setVgrow(canvasPane, Priority.ALWAYS);

        // ScrollBar
        scrollBar = new ScrollBar();
        scrollBar.setMin(0);
        scrollBar.setMax(TOTAL_CHANNELS - VISIBLE_COUNT);
        scrollBar.setVisibleAmount(VISIBLE_COUNT);
        scrollBar.setBlockIncrement(VISIBLE_COUNT);
        scrollBar.setUnitIncrement(1);
        scrollBar.setPadding(new Insets(4, 0, 0, 0));

        scrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            scrollOffsetChannels = newVal.doubleValue();
            render();
        });

        // Mausrad-Scrolling (horizontal)
        canvasPane.setOnScroll(e -> {
            double delta = e.getDeltaY() != 0 ? e.getDeltaY() : e.getDeltaX();
            if (delta != 0) {
                double step = (delta > 0 ? -3 : 3);
                double target = Math.max(0, Math.min(scrollBar.getMax(), scrollBar.getValue() + step));
                scrollBar.setValue(target);
            }
        });

        // Hover-Inspektion
        canvasPane.setOnMouseMoved(e -> {
            double x = e.getX();
            int ch = (int) Math.floor(scrollOffsetChannels + (x / channelWidth));
            if (ch >= 0 && ch < TOTAL_CHANNELS) {
                if (ch != hoveredChannel) {
                    hoveredChannel = ch;
                    render();
                }
            } else {
                if (hoveredChannel != -1) {
                    hoveredChannel = -1;
                    render();
                }
            }
        });

        canvasPane.setOnMouseExited(e -> {
            hoveredChannel = -1;
            render();
        });

        getChildren().addAll(header, canvasPane, scrollBar);
    }

    void handleResize(double w, double h) {
        channelWidth = w / (double) VISIBLE_COUNT;
        barWidth = Math.max(4.0, channelWidth - 6.0);
        render();
    }

    public void scrollToChannel(int channelNum) {
        double target = Math.max(0, Math.min(TOTAL_CHANNELS - VISIBLE_COUNT, channelNum - 1));
        scrollBar.setValue(target);
    }

    public void updateChannels(byte[] channels, List<FixturePatch> patches) {
        this.currentPatches = (patches != null) ? patches : List.of();
        boolean changed = false;
        for (int i = 0; i < TOTAL_CHANNELS && i < channels.length; i++) {
            int val = channels[i] & 0xFF;
            if (val != prevChannels[i]) {
                prevChannels[i] = val;
                changed = true;
            }
        }
        if (changed) {
            render();
        }
    }

    public void reset() {
        Arrays.fill(prevChannels, 0);
        render();
    }

    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        gc.setFill(MaterialTheme.COLOR_SURFACE_1DP);
        gc.fillRect(0, 0, w, h);

        double trackTop = 24;
        double trackBottom = h - 26;
        double trackH = trackBottom - trackTop;

        int startCh = (int) Math.floor(scrollOffsetChannels);
        int endCh = Math.min(TOTAL_CHANNELS, startCh + VISIBLE_COUNT + 1);

        gc.setTextAlign(TextAlignment.CENTER);

        for (int ch = startCh; ch < endCh; ch++) {
            double screenX = (ch - scrollOffsetChannels) * channelWidth;
            if (screenX + channelWidth < 0 || screenX > w) {
                continue;
            }

            int val = (ch < TOTAL_CHANNELS) ? prevChannels[ch] : 0;
            double colCenterX = screenX + (channelWidth / 2.0);
            double barX = screenX + (channelWidth - barWidth) / 2.0;

            // Fixture-Kanal-Rolle ermitteln
            String roleTag = getChannelRoleTag(ch + 1);
            boolean isAssigned = (roleTag != null && !roleTag.isBlank());

            // 1. Wert-Text oben (0..255)
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 9));
            if (val > 0) {
                gc.setFill(isAssigned ? MaterialTheme.COLOR_PRIMARY : Color.web("#00E5FF"));
            } else {
                gc.setFill(MaterialTheme.COLOR_TEXT_MED);
            }
            gc.fillText(String.valueOf(val), colCenterX, 14);

            // 2. Track Background
            gc.setFill(isAssigned ? Color.web("#282828") : MaterialTheme.COLOR_SURFACE_2DP);
            gc.fillRect(barX, trackTop, barWidth, trackH);

            // 3. Pegelbalken
            if (val > 0 && trackH > 0) {
                double barH = (val / 255.0) * trackH;
                Color barColor = isAssigned ? MaterialTheme.COLOR_PRIMARY : Color.web("#00E5FF");
                if (ch == hoveredChannel) {
                    barColor = Color.web("#00FF9A");
                }
                gc.setFill(barColor);
                gc.fillRect(barX, trackBottom - barH, barWidth, barH);
            }

            // 4. Kanal-Rolle oder Nummer unten
            gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 8));
            if (isAssigned) {
                gc.setFill(MaterialTheme.COLOR_PRIMARY);
                gc.fillText(roleTag, colCenterX, h - 14);
            }

            gc.setFont(Font.font("Segoe UI", 8));
            gc.setFill(MaterialTheme.COLOR_TEXT_DISABLED);
            gc.fillText(String.valueOf(ch + 1), colCenterX, h - 4);

            // Hover-Rahmen
            if (ch == hoveredChannel) {
                gc.setStroke(MaterialTheme.COLOR_PRIMARY);
                gc.setLineWidth(1.0);
                gc.strokeRect(screenX + 1, trackTop - 2, channelWidth - 2, trackH + 4);
            }
        }
    }

    public int getSelectedUniverse() {
        return selectedUniverse;
    }

    public void setSelectedUniverse(int universe) {
        this.selectedUniverse = universe;
        if (!cbUniverse.getItems().contains(universe)) {
            cbUniverse.getItems().add(universe);
        }
        cbUniverse.setValue(universe);
        render();
    }

    public void setAvailableUniverses(Collection<Integer> universes) {
        if (universes == null || universes.isEmpty()) {
            universes = List.of(0);
        }
        List<Integer> sorted = new ArrayList<>(new TreeSet<>(universes));
        if (!sorted.equals(new ArrayList<>(cbUniverse.getItems()))) {
            Integer cur = cbUniverse.getValue();
            cbUniverse.getItems().setAll(sorted);
            if (cur != null && sorted.contains(cur)) {
                cbUniverse.setValue(cur);
            } else {
                cbUniverse.setValue(sorted.get(0));
                selectedUniverse = sorted.get(0);
            }
        }
    }

    public void updateLocalizedTexts() {
        lblTitle.setText(de.exit.sound2artnet.util.I18n.get("visualizer.title"));
        lblBereich.setText(de.exit.sound2artnet.util.I18n.get("visualizer.range"));
        render();
    }

    private String getChannelRoleTag(int dmxAddr) {
        for (FixturePatch patch : currentPatches) {
            if (!patch.isEnabled() || patch.getProfile() == null || patch.getUniverse() != selectedUniverse) continue;
            int start = patch.getStartAddress();
            int end = patch.getEndAddress();
            if (dmxAddr >= start && dmxAddr <= end) {
                int offset = dmxAddr - start;
                for (ChannelMapping cm : patch.getProfile().getChannels()) {
                    if (cm.getOffset() == offset) {
                        return switch (cm.getFunction()) {
                            case PAN -> "PAN";
                            case PAN_FINE -> "PAN.F";
                            case TILT -> "TILT";
                            case TILT_FINE -> "TLT.F";
                            case PAN_TILT_SPEED -> "SPD";
                            case DIMMER -> "DIM";
                            case STROBE -> "STRB";
                            case RED -> "R";
                            case GREEN -> "G";
                            case BLUE -> "B";
                            case CYAN -> "C";
                            case MAGENTA -> "M";
                            case YELLOW -> "Y";
                            case WHITE -> "W";
                            case AMBER -> "A";
                            case UV -> "UV";
                            case COLOR_WHEEL -> "COL";
                            case GOBO_WHEEL -> "GOBO";
                            case PRISM -> "PRIS";
                            case FOCUS -> "FOC";
                            default -> "";
                        };
                    }
                }
            }
        }
        return "";
    }

    private static class CanvasPane extends Pane {
        private final Canvas canvas;
        private final ChannelVisualizer visualizer;

        public CanvasPane(Canvas canvas, ChannelVisualizer visualizer) {
            this.canvas = canvas;
            this.visualizer = visualizer;
            getChildren().add(canvas);
            canvas.setManaged(false);

            setMinHeight(60);
            setPrefHeight(150);
            setMaxHeight(Double.MAX_VALUE);
            setMinWidth(100);
            setMaxWidth(Double.MAX_VALUE);
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            double w = Math.floor(getWidth());
            double h = Math.floor(getHeight());
            if (w > 0 && h > 0) {
                canvas.relocate(0, 0);
                boolean sizeChanged = false;
                if (Math.abs(canvas.getWidth() - w) > 0.5) {
                    canvas.setWidth(w);
                    sizeChanged = true;
                }
                if (Math.abs(canvas.getHeight() - h) > 0.5) {
                    canvas.setHeight(h);
                    sizeChanged = true;
                }
                if (sizeChanged) {
                    visualizer.handleResize(w, h);
                }
            }
        }
    }
}

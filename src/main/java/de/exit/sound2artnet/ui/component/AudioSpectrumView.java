package de.exit.sound2artnet.ui.component;

import de.exit.sound2artnet.ui.MaterialTheme;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Echtzeit-Visualisierung des 8-Band Audiospektrums, VU-Pegelanzeige
 * und Beat-Indikator im Material Design 2 Dark Theme.
 */
public class AudioSpectrumView extends Pane {
    private static final String[] BAND_NAMES = {
        "SUB", "BASS", "LOW", "MID", "H-MID", "PRES", "TREB", "BRIL"
    };

    private final Canvas canvas;
    private double[] bands = new double[8];
    private double[] peaks = new double[8];
    private double rmsLevel = 0.0;
    private double peakLevel = 0.0;
    private boolean isBeat = false;
    private double beatFlash = 0.0;

    public AudioSpectrumView() {
        this.canvas = new Canvas(320, 88);
        this.canvas.setManaged(false);
        getChildren().add(canvas);

        setMinHeight(55);
        setPrefHeight(88);
        setMaxHeight(Double.MAX_VALUE);

        setMinWidth(180);
        setPrefWidth(320);
        setMaxWidth(Double.MAX_VALUE);

        render();
    }

    public void updateData(double[] bands, double[] peaks, double rms, double peak, boolean beat) {
        if (bands != null) {
            System.arraycopy(bands, 0, this.bands, 0, Math.min(bands.length, 8));
        }
        if (peaks != null) {
            System.arraycopy(peaks, 0, this.peaks, 0, Math.min(peaks.length, 8));
        }
        this.rmsLevel = rms;
        this.peakLevel = peak;
        this.isBeat = beat;

        if (beat) {
            this.beatFlash = 1.0;
        } else {
            this.beatFlash = Math.max(0.0, this.beatFlash - 0.12);
        }

        render();
    }

    public void updateData(double[] bands, double[] peaks, double rms, double peak,
                           boolean kick, boolean snare, boolean hat) {
        updateData(bands, peaks, rms, peak, kick);
    }

    public void updateData(double[] bands, double[] peaks, double rms, double peak,
                           boolean beat, boolean kick, boolean snare, boolean hat) {
        updateData(bands, peaks, rms, peak, beat);
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
                render();
            }
        }
    }

    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 20 || h <= 15) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        double marginLeft = 28.0; // Platz für VU-Meter
        double marginRight = 4.0;
        double marginTop = 6.0;
        double marginBottom = 14.0;

        double plotW = w - marginLeft - marginRight;
        double plotH = h - marginTop - marginBottom;

        // 1. Hintergrund
        gc.setFill(Color.web("#141414"));
        gc.fillRoundRect(0, marginTop, w, plotH, 3, 3);
        gc.setStroke(Color.rgb(255, 255, 255, 0.08));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(0, marginTop, w, plotH, 3, 3);

        // 2. VU-Meter ganz links
        double vuW = 14.0;
        double vuX = 6.0;
        double vuH = plotH - 4.0;
        double vuY = marginTop + 2.0;

        gc.setFill(Color.web("#1A1A1A"));
        gc.fillRect(vuX, vuY, vuW, vuH);

        double rmsH = Math.min(1.0, rmsLevel * 2.5) * vuH;
        LinearGradient vuGrad = new LinearGradient(
            0, vuY + vuH, 0, vuY, false, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#03DAC6")),
            new Stop(0.7, Color.web("#00E676")),
            new Stop(1.0, Color.web("#CF6679"))
        );
        gc.setFill(vuGrad);
        gc.fillRect(vuX, vuY + vuH - rmsH, vuW, rmsH);

        // VU Peak Marker
        double peakY = vuY + vuH - (Math.min(1.0, peakLevel * 2.5) * vuH);
        gc.setFill(Color.web("#FFFFFF"));
        gc.fillRect(vuX, Math.max(vuY, peakY), vuW, 2);

        // VU Beschriftung
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
        gc.setFill(MaterialTheme.COLOR_TEXT_DISABLED);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("VU", vuX + (vuW / 2.0), h - 2);

        // 3. Reiner, prominenter BEAT-Indikator oben links
        double indX = marginLeft + 6;
        double indY = marginTop + 4;

        if (beatFlash > 0.05) {
            // Strahlender Beat-Glow-Ring
            gc.setStroke(Color.color(1.0, 0.20, 0.25, 0.40 * beatFlash));
            gc.setLineWidth(2.0);
            gc.strokeOval(indX - 1.5, indY - 1.5, 10, 10);
        }

        Color beatCol = beatFlash > 0.05
            ? Color.color(1.0, 0.22, 0.28, 0.95 * beatFlash)
            : Color.color(0.3, 0.3, 0.3, 0.4);
        gc.setFill(beatCol);
        gc.fillOval(indX, indY, 7, 7);

        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(beatFlash > 0.05 ? MaterialTheme.COLOR_TEXT_HIGH : MaterialTheme.COLOR_TEXT_MED);
        gc.fillText("BEAT", indX + 11, indY + 7);

        // 4. Die 8 Frequenz-Balken
        double barsAreaX = marginLeft + 4.0;
        double barsAreaW = plotW - 8.0;
        double barGap = 4.0;
        double barW = Math.max(4.0, (barsAreaW - (barGap * 7)) / 8.0);
        double barMaxH = plotH - 16.0;
        double barBaseY = marginTop + plotH - 2.0;

        LinearGradient barGrad = new LinearGradient(
            0, barBaseY, 0, barBaseY - barMaxH, false, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#03DAC6")),
            new Stop(0.8, Color.web("#00E5FF")),
            new Stop(1.0, Color.web("#FFFFFF"))
        );

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 8));

        for (int i = 0; i < 8; i++) {
            double bx = barsAreaX + (i * (barW + barGap));
            double bVal = Math.min(1.0, Math.max(0.0, bands[i]));
            double bH = Math.max(2.0, bVal * barMaxH);

            // Balken
            gc.setFill(barGrad);
            gc.fillRoundRect(bx, barBaseY - bH, barW, bH, 2, 2);

            // Peak Hold Strich
            double pVal = Math.min(1.0, Math.max(0.0, peaks[i]));
            double pY = barBaseY - (pVal * barMaxH);
            gc.setFill(Color.web("#FFFFFF"));
            gc.fillRect(bx, Math.max(marginTop + 2, pY - 1), barW, 2);

            // Beschriftung unter dem Balken
            gc.setFill(MaterialTheme.COLOR_TEXT_DISABLED);
            gc.fillText(BAND_NAMES[i], bx + (barW / 2.0), h - 2);
        }
    }
}

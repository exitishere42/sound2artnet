package de.exit.sound2artnet.ui;

import de.exit.sound2artnet.artnet.ArtNetSender;
import de.exit.sound2artnet.audio.AudioCaptureService;
import de.exit.sound2artnet.audio.AudioDeviceInfo;
import de.exit.sound2artnet.audio.BeatDetector;
import de.exit.sound2artnet.config.AppConfig;
import de.exit.sound2artnet.config.ArtNetPreset;
import de.exit.sound2artnet.config.ConfigManager;
import de.exit.sound2artnet.engine.ColorEngine;
import de.exit.sound2artnet.engine.MovementPattern;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.fixture.FixtureProfile;
import de.exit.sound2artnet.ui.component.*;
import de.exit.sound2artnet.ui.icon.LucideIcon;
import de.exit.sound2artnet.util.I18n;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hauptansicht der sound2artnet Anwendung im Material Design 2 Dark Theme.
 * Ästhetik und Haptik identisch zu artnet2dmx.
 */
public class MainWindow extends StackPane {
    private static final Logger LOGGER = Logger.getLogger(MainWindow.class.getName());

    private final VBox contentBox = new VBox();
    private final StackPane overlayPane = new StackPane();

    private final AppConfig config;
    private final AudioCaptureService audioService = new AudioCaptureService();
    private final ArtNetSender artNetSender = new ArtNetSender();
    private final Sound2LightEngine showEngine;
    private final PauseTransition saveDebounce = new PauseTransition(Duration.millis(150));

    // Top Bar
    private LucideIcon chipIcon;
    private Label chipLabel;

    // Metriken
    private Label lblInTitle;
    private Label lblAudioLevel;
    private Label lblPeakHdr;
    private Label lblAudioPeak;
    private Label lblBpmHdr;
    private Label lblBpm;
    private Label lblTierHdr;
    private Label lblSpeedTier;
    private Label lblOutTitle;
    private Label lblPps;
    private Label lblPaketeHdr;
    private Label lblTotalPackets;
    private MetricHistoryChart chartPps;
    private AudioSpectrumView spectrumView;

    // Schnell-Steuerung
    private Label lblBoxAudio;
    private HelpBadge helpAudio;
    private ComboBox<AudioDeviceInfo> cbAudioDevice;
    private MaterialButton btnScanAudio;
    private Label lblBoxIp;
    private HelpBadge helpIp;
    private TextField txtTargetIp;
    private Label lblBoxUni;
    private HelpBadge helpUni;
    private Spinner<Integer> spUniverse;
    private Label lblBoxFps;
    private HelpBadge helpFps;
    private Spinner<Integer> spFps;
    private Label lblBoxGain;
    private HelpBadge helpGain;
    private Slider slGain;
    private CheckBox chkAgc;
    private Label lblBoxAction;
    private HelpBadge helpAction;
    private MaterialButton btnAction;

    // Tabs
    private Tab tabPatches;
    private Tab tabEngine;
    private Tab tabPresets;

    // Presets Table & Controls
    private final ObservableList<ArtNetPreset> presetList = FXCollections.observableArrayList();
    private TableView<ArtNetPreset> tablePresets;
    private TableColumn<ArtNetPreset, String> colPresetStatus;
    private TableColumn<ArtNetPreset, String> colPresetName;
    private TableColumn<ArtNetPreset, String> colPresetIp;
    private TableColumn<ArtNetPreset, String> colPresetUniverse;
    private TableColumn<ArtNetPreset, String> colPresetFps;
    private TableColumn<ArtNetPreset, String> colPresetFixtures;
    private TableColumn<ArtNetPreset, String> colPresetModified;
    private Label lblActivePresetBanner;
    private MaterialButton btnNewPreset;
    private MaterialButton btnLoadPreset;
    private MaterialButton btnUpdatePreset;
    private MaterialButton btnRenamePreset;
    private MaterialButton btnDeletePreset;

    // Fixture & Patching Table
    private final ObservableList<FixturePatch> patchList = FXCollections.observableArrayList();
    private TableView<FixturePatch> tablePatches;
    private TableColumn<FixturePatch, Boolean> colActive;
    private TableColumn<FixturePatch, String> colName;
    private TableColumn<FixturePatch, String> colProfile;
    private TableColumn<FixturePatch, String> colDmx;
    private TableColumn<FixturePatch, String> colLimits;
    private TableColumn<FixturePatch, String> colPhase;
    private MaterialButton btnAdd;
    private MaterialButton btnEdit;
    private MaterialButton btnDelete;
    private MaterialButton btnImportQlc;

    // Engine Controls
    private Label lblActive;
    private CheckBox chkMovementEnabled;
    private CheckBox chkLightEnabled;
    private CheckBox chkStrobeEnabled;
    private Label lblDetectMode;
    private ComboBox<BeatDetector.DetectionMode> cbDetectionMode;
    private Label lblBeatSens;
    private Slider slBeatSensitivity;
    private Label lblPattern;
    private ComboBox<MovementPattern> cbPattern;
    private Label lblSpeed;
    private Slider slSpeed;
    private Label lblAmp;
    private Slider slAmplitude;
    private Label lblDimmer;
    private ComboBox<Sound2LightEngine.DimmerMode> cbDimmerMode;
    private HBox alwaysOnBox;
    private Label lblAlwaysOnTitle;
    private Slider slAlwaysOnIntensity;
    private Label lblAlwaysOnVal;
    private Label lblPal;
    private ComboBox<ColorEngine.Palette> cbPalette;

    // Visualizer & Status
    private ChannelVisualizer visualizer;
    private Label statusLabel;

    private boolean isRunning = false;
    private long lastChartUpdate = 0;

    private record ControlBox(VBox box, Label titleLabel, HelpBadge helpBadge) {}

    public MainWindow() {
        this.config = ConfigManager.loadConfig();
        this.showEngine = new Sound2LightEngine(audioService, artNetSender);

        // Standard-Patches aus Config in die Engine laden
        if (config.getFixtures() != null) {
            patchList.addAll(config.getFixtures());
            showEngine.setPatchedFixtures(new ArrayList<>(config.getFixtures()));
        }

        // Profile / Presets laden
        config.ensureDefaultPreset();
        if (config.getPresets() != null) {
            presetList.addAll(config.getPresets());
        }

        showEngine.setMovementEnabled(config.isMovementEnabled());
        showEngine.setLightEnabled(config.isLightEnabled());
        showEngine.setStrobeEnabled(config.isStrobeEnabled());
        showEngine.setMovementPattern(config.getMovementPattern());
        showEngine.setDimmerMode(config.getDimmerMode());
        showEngine.setAlwaysOnIntensity(config.getAlwaysOnIntensity());
        showEngine.getColorEngine().setPalette(config.getColorPalette());
        showEngine.getMovementGenerator().setCurrentSpeed(config.getMovementSpeed());
        showEngine.getMovementGenerator().setBaseAmplitude(config.getMovementSize());
        audioService.getAnalyzer().setManualGain(config.getGain());
        audioService.getAnalyzer().setAgcEnabled(config.isAgc());
        audioService.getAnalyzer().getBeatDetector().setDetectionMode(config.getDetectionMode());
        audioService.getAnalyzer().getBeatDetector().setSensitivity(config.getBeatSensitivity());

        I18n.setLanguage(config.getLanguage());
        I18n.addListener(lang -> Platform.runLater(this::updateAllLocalizedTexts));

        contentBox.setStyle("-fx-background-color: " + MaterialTheme.HEX_BG + ";");
        contentBox.setSpacing(0);
        contentBox.setPadding(new Insets(0, 0, 4, 0));

        buildTopAppBar();

        Region metricsNode = buildMetricsCard();
        Region controlsNode = buildControlsCard();
        Region engineCard = buildEngineAndFixtureCard();
        Region visualizerNode = buildChannelsCard();

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        VBox wrapMetrics = wrapSplitItem(metricsNode, new Insets(8, 16, 4, 16), 80, 220);
        VBox wrapControls = wrapSplitItem(controlsNode, new Insets(4, 16, 4, 16), 68, 140);
        VBox wrapEngine = wrapSplitItem(engineCard, new Insets(4, 16, 4, 16), 180, Double.MAX_VALUE);
        VBox wrapVisualizer = wrapSplitItem(visualizerNode, new Insets(4, 16, 6, 16), 130, Double.MAX_VALUE);

        splitPane.getItems().addAll(wrapMetrics, wrapControls, wrapEngine, wrapVisualizer);
        SplitPane.setResizableWithParent(wrapMetrics, false);
        SplitPane.setResizableWithParent(wrapControls, false);
        SplitPane.setResizableWithParent(wrapEngine, true);
        SplitPane.setResizableWithParent(wrapVisualizer, true);

        Platform.runLater(() -> splitPane.setDividerPositions(0.18, 0.32, 0.68));

        contentBox.getChildren().add(splitPane);
        buildStatusBar();

        // In-App Modal Overlay Layer
        overlayPane.setVisible(false);
        overlayPane.setManaged(false);
        overlayPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.65);");
        overlayPane.setAlignment(Pos.CENTER);
        overlayPane.setOnMouseClicked(e -> {
            if (e.getTarget() == overlayPane) {
                hideOverlay();
            }
        });

        getChildren().addAll(contentBox, overlayPane);

        scanAudioDevices();
        updateAllLocalizedTexts();
    }

    public void showOverlay(Node modalContent) {
        overlayPane.getChildren().setAll(modalContent);
        overlayPane.setVisible(true);
        overlayPane.setManaged(true);
        modalContent.setOpacity(0);
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(160), modalContent);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    public void hideOverlay() {
        if (!overlayPane.getChildren().isEmpty()) {
            Node modalContent = overlayPane.getChildren().get(0);
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(120), modalContent);
            ft.setFromValue(1);
            ft.setToValue(0);
            ft.setOnFinished(e -> {
                overlayPane.getChildren().clear();
                overlayPane.setVisible(false);
                overlayPane.setManaged(false);
            });
            ft.play();
        } else {
            overlayPane.setVisible(false);
            overlayPane.setManaged(false);
        }
    }

    public void autoSaveConfig() {
        saveDebounce.setOnFinished(e -> saveStateToConfig());
        saveDebounce.playFromStart();
    }

    private VBox wrapSplitItem(Region content, Insets insets, double contentMinH, double contentMaxH) {
        VBox wrap = new VBox(content);
        wrap.setPadding(insets);
        double totalMinH = contentMinH + insets.getTop() + insets.getBottom();
        double totalMaxH = (contentMaxH == Double.MAX_VALUE) ? Double.MAX_VALUE : (contentMaxH + insets.getTop() + insets.getBottom());
        wrap.setMinHeight(totalMinH);
        wrap.setMaxHeight(totalMaxH);
        VBox.setVgrow(content, Priority.ALWAYS);
        content.setMinHeight(contentMinH);
        content.setMaxHeight(Double.MAX_VALUE);
        content.setMaxWidth(Double.MAX_VALUE);
        return wrap;
    }

    private void buildTopAppBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_2DP + 
                     "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + 
                     "; -fx-border-width: 0 0 1px 0;");

        // Klickbarer Logo- & Versionsbereich für Info & Updates
        HBox logoBox = new HBox(8);
        logoBox.setAlignment(Pos.CENTER_LEFT);
        logoBox.setCursor(Cursor.HAND);
        logoBox.setPadding(new Insets(3, 8, 3, 6));
        logoBox.setStyle("-fx-background-radius: 6px; -fx-background-color: transparent;");
        logoBox.setOnMouseEntered(e -> logoBox.setStyle("-fx-background-radius: 6px; -fx-background-color: " + MaterialTheme.HEX_SURFACE_4DP + ";"));
        logoBox.setOnMouseExited(e -> logoBox.setStyle("-fx-background-radius: 6px; -fx-background-color: transparent;"));

        LucideIcon iconLogo = new LucideIcon("music", 20, MaterialTheme.COLOR_PRIMARY);

        Label title = new Label("sound2artnet");
        title.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        title.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 15));

        Label versionBadge = new Label("v" + de.exit.sound2artnet.service.UpdateService.CURRENT_VERSION);
        versionBadge.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_4DP + 
                             "; -fx-text-fill: " + MaterialTheme.HEX_TEXT_MED + 
                             "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2px 6px; -fx-background-radius: 8px;");

        logoBox.getChildren().addAll(iconLogo, title, versionBadge);
        logoBox.setOnMouseClicked(e -> de.exit.sound2artnet.ui.AboutUpdateDialog.show(MainWindow.this));
        MaterialTooltip.install(logoBox, () -> "sound2artnet", () -> I18n.get("tooltip.logo"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Status Chip
        HBox chip = new HBox(6);
        chip.setAlignment(Pos.CENTER);
        chip.setPadding(new Insets(4, 10, 4, 10));
        chip.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_4DP + "; -fx-background-radius: 12px;");

        chipIcon = new LucideIcon("circle", 10, MaterialTheme.COLOR_TEXT_MED);
        chipLabel = new Label(I18n.get("status.ready"));
        chipLabel.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        chipLabel.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 11));
        chip.getChildren().addAll(chipIcon, chipLabel);

        bar.getChildren().addAll(logoBox, spacer, chip);
        contentBox.getChildren().add(bar);
    }

    private Region buildMetricsCard() {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(8, 12, 8, 12));
        card.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_1DP + 
                     "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + 
                     "; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px;");
        card.setMinHeight(75);
        card.setPrefHeight(105);
        card.setMaxHeight(Double.MAX_VALUE);

        // 1. Audio Eingang
        HBox audioSection = new HBox(10);
        audioSection.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(audioSection, Priority.ALWAYS);

        VBox audioLabels = new VBox(6);
        audioLabels.setAlignment(Pos.CENTER_LEFT);
        audioLabels.setMinWidth(110);

        VBox colRms = new VBox(1);
        HBox hdrIn = new HBox(4);
        hdrIn.setAlignment(Pos.CENTER_LEFT);
        LucideIcon iconMic = new LucideIcon("mic", 12, MaterialTheme.COLOR_PRIMARY);
        lblInTitle = new Label(I18n.get("metric.audio_rms"));
        lblInTitle.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblInTitle.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 10));
        hdrIn.getChildren().addAll(iconMic, lblInTitle);

        lblAudioLevel = new Label("0.0 %");
        lblAudioLevel.setStyle("-fx-text-fill: " + MaterialTheme.HEX_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 15px;");
        colRms.getChildren().addAll(hdrIn, lblAudioLevel);

        VBox colPeak = new VBox(1);
        lblPeakHdr = new Label(I18n.get("metric.peak"));
        lblPeakHdr.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblPeakHdr.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 10));

        lblAudioPeak = new Label("0.0 %");
        lblAudioPeak.setStyle("-fx-text-fill: " + MaterialTheme.HEX_TEXT_HIGH + "; -fx-font-weight: bold; -fx-font-size: 14px;");
        colPeak.getChildren().addAll(lblPeakHdr, lblAudioPeak);

        audioLabels.getChildren().addAll(colRms, colPeak);

        VBox tempoLabels = new VBox(6);
        tempoLabels.setAlignment(Pos.CENTER_LEFT);
        tempoLabels.setMinWidth(85);

        VBox colBpm = new VBox(1);
        lblBpmHdr = new Label(I18n.get("metric.tempo"));
        lblBpmHdr.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblBpmHdr.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 10));

        lblBpm = new Label("-- BPM");
        lblBpm.setStyle("-fx-text-fill: " + MaterialTheme.HEX_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 15px;");
        colBpm.getChildren().addAll(lblBpmHdr, lblBpm);

        VBox colTier = new VBox(1);
        lblTierHdr = new Label(I18n.get("metric.tier"));
        lblTierHdr.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblTierHdr.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 10));

        lblSpeedTier = new Label(showEngine.getCurrentSpeedTier().getDisplayName());
        lblSpeedTier.setStyle("-fx-text-fill: " + MaterialTheme.HEX_TEXT_HIGH + "; -fx-font-weight: bold; -fx-font-size: 13px;");
        colTier.getChildren().addAll(lblTierHdr, lblSpeedTier);

        tempoLabels.getChildren().addAll(colBpm, colTier);

        spectrumView = new AudioSpectrumView();
        HBox.setHgrow(spectrumView, Priority.ALWAYS);

        audioSection.getChildren().addAll(audioLabels, tempoLabels, spectrumView);

        // Trenner
        Region divider = new Region();
        divider.setMinWidth(1);
        divider.setMaxWidth(1);
        divider.setStyle("-fx-background-color: " + MaterialTheme.HEX_DIVIDER + ";");
        HBox.setMargin(divider, new Insets(2, 4, 2, 4));
        VBox.setVgrow(divider, Priority.ALWAYS);

        // 2. Art-Net Ausgang
        HBox outputSection = new HBox(10);
        outputSection.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(outputSection, Priority.ALWAYS);

        VBox outputLabels = new VBox(6);
        outputLabels.setAlignment(Pos.CENTER_LEFT);
        outputLabels.setMinWidth(110);

        VBox colOutRate = new VBox(1);
        HBox hdrOut = new HBox(4);
        hdrOut.setAlignment(Pos.CENTER_LEFT);
        LucideIcon iconOut = new LucideIcon("trending-up", 12, MaterialTheme.COLOR_ACCENT_GREEN);
        lblOutTitle = new Label(I18n.get("metric.artnet_out"));
        lblOutTitle.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblOutTitle.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 10));
        hdrOut.getChildren().addAll(iconOut, lblOutTitle);

        lblPps = new Label("0.0 pkt/s");
        lblPps.setStyle("-fx-text-fill: " + MaterialTheme.HEX_ACCENT_GREEN + "; -fx-font-weight: bold; -fx-font-size: 15px;");
        colOutRate.getChildren().addAll(hdrOut, lblPps);

        VBox colPakete = new VBox(1);
        lblPaketeHdr = new Label(I18n.get("metric.sent"));
        lblPaketeHdr.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblPaketeHdr.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 10));

        lblTotalPackets = new Label("0");
        lblTotalPackets.setStyle("-fx-text-fill: " + MaterialTheme.HEX_TEXT_HIGH + "; -fx-font-weight: bold; -fx-font-size: 14px;");
        colPakete.getChildren().addAll(lblPaketeHdr, lblTotalPackets);

        outputLabels.getChildren().addAll(colOutRate, colPakete);

        chartPps = new MetricHistoryChart(I18n.get("metric.chart_title"), MaterialTheme.COLOR_ACCENT_GREEN, 45.0);
        HBox.setHgrow(chartPps, Priority.ALWAYS);

        outputSection.getChildren().addAll(outputLabels, chartPps);

        card.getChildren().addAll(audioSection, divider, outputSection);
        return card;
    }

    private Region buildControlsCard() {
        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(8, 12, 8, 12));
        card.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_1DP + 
                     "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + 
                     "; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px;");
        card.setMinHeight(65);
        card.setPrefHeight(78);
        card.setMaxHeight(140);
        card.setFillHeight(true);

        // 1. Audio-Eingang
        ControlBox boxAudioInfo = createControlBox("ctrl.audio_source", "ctrl.audio_source.tt_title", "ctrl.audio_source.tt_desc");
        lblBoxAudio = boxAudioInfo.titleLabel();
        helpAudio = boxAudioInfo.helpBadge();
        VBox boxAudio = boxAudioInfo.box();
        HBox.setHgrow(boxAudio, Priority.ALWAYS);

        HBox rowAudio = new HBox(6);
        rowAudio.setAlignment(Pos.CENTER_LEFT);
        cbAudioDevice = new ComboBox<>();
        cbAudioDevice.setMaxWidth(Double.MAX_VALUE);
        cbAudioDevice.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                config.setAudioDevice(n.name());
                autoSaveConfig();
            }
        });
        HBox.setHgrow(cbAudioDevice, Priority.ALWAYS);

        btnScanAudio = new MaterialButton(I18n.get("btn.scan"), "refresh-cw", 
            MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_TEXT_HIGH, 11, 8, 4, 11, true, this::scanAudioDevices);
        rowAudio.getChildren().addAll(cbAudioDevice, btnScanAudio);
        boxAudio.getChildren().add(rowAudio);

        // 2. Art-Net Ziel-IP
        ControlBox boxIpInfo = createControlBox("ctrl.target_ip", "ctrl.target_ip.tt_title", "ctrl.target_ip.tt_desc");
        lblBoxIp = boxIpInfo.titleLabel();
        helpIp = boxIpInfo.helpBadge();
        VBox boxIp = boxIpInfo.box();
        boxIp.setMinWidth(125);
        txtTargetIp = new TextField(config.getTargetIp() != null ? config.getTargetIp() : "127.0.0.1");
        txtTargetIp.setMaxWidth(Double.MAX_VALUE);
        txtTargetIp.textProperty().addListener((obs, o, n) -> autoSaveConfig());
        boxIp.getChildren().add(txtTargetIp);

        // 3. Universum
        ControlBox boxUniInfo = createControlBox("ctrl.universe", "ctrl.universe.tt_title", "ctrl.universe.tt_desc");
        lblBoxUni = boxUniInfo.titleLabel();
        helpUni = boxUniInfo.helpBadge();
        VBox boxUni = boxUniInfo.box();
        boxUni.setMinWidth(95);
        spUniverse = new Spinner<>(0, 15, config.getUniverse());
        spUniverse.setEditable(true);
        spUniverse.setMaxWidth(Double.MAX_VALUE);
        spUniverse.valueProperty().addListener((obs, o, n) -> autoSaveConfig());
        boxUni.getChildren().add(spUniverse);

        // 4. Ziel-FPS
        ControlBox boxFpsInfo = createControlBox("ctrl.fps", "ctrl.fps.tt_title", "ctrl.fps.tt_desc");
        lblBoxFps = boxFpsInfo.titleLabel();
        helpFps = boxFpsInfo.helpBadge();
        VBox boxFps = boxFpsInfo.box();
        boxFps.setMinWidth(85);
        spFps = new Spinner<>(10, 44, config.getFps());
        spFps.setEditable(true);
        spFps.setMaxWidth(Double.MAX_VALUE);
        spFps.valueProperty().addListener((obs, o, n) -> autoSaveConfig());
        boxFps.getChildren().add(spFps);

        // 5. Gain / AGC
        ControlBox boxGainInfo = createControlBox("ctrl.gain_agc", "ctrl.gain_agc.tt_title", "ctrl.gain_agc.tt_desc");
        lblBoxGain = boxGainInfo.titleLabel();
        helpGain = boxGainInfo.helpBadge();
        VBox boxGain = boxGainInfo.box();
        boxGain.setMinWidth(130);
        HBox rowGain = new HBox(6);
        rowGain.setAlignment(Pos.CENTER_LEFT);
        slGain = new Slider(0.2, 4.0, config.getGain());
        slGain.valueProperty().addListener((obs, o, n) -> {
            audioService.getAnalyzer().setManualGain(n.doubleValue());
            autoSaveConfig();
        });
        HBox.setHgrow(slGain, Priority.ALWAYS);

        chkAgc = new CheckBox("AGC");
        chkAgc.setSelected(config.isAgc());
        chkAgc.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        chkAgc.setFont(Font.font("Segoe UI", 10));
        chkAgc.setOnAction(e -> {
            audioService.getAnalyzer().setAgcEnabled(chkAgc.isSelected());
            autoSaveConfig();
        });

        rowGain.getChildren().addAll(slGain, chkAgc);
        boxGain.getChildren().add(rowGain);

        // 6. Start / Stop Action Box
        ControlBox boxActionInfo = createControlBox("ctrl.action", "ctrl.action.tt_title", "ctrl.action.tt_desc");
        lblBoxAction = boxActionInfo.titleLabel();
        helpAction = boxActionInfo.helpBadge();
        VBox boxAction = boxActionInfo.box();
        boxAction.setMinWidth(110);
        btnAction = new MaterialButton(I18n.get("btn.start"), "play", 
            MaterialTheme.COLOR_PRIMARY, MaterialTheme.COLOR_ON_PRIMARY, 15, 16, 4, 12, true, this::toggleService);
        btnAction.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(btnAction, Priority.ALWAYS);
        boxAction.getChildren().add(btnAction);

        card.getChildren().addAll(boxAudio, boxIp, boxUni, boxFps, boxGain, boxAction);
        return card;
    }

    private Region buildEngineAndFixtureCard() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_1DP + 
                         "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + 
                         "; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px;");

        // Tab 1: Fixtures & Patches
        tabPatches = new Tab(I18n.get("tab.fixtures"));
        tabPatches.setClosable(false);
        tabPatches.setContent(buildPatchView());

        // Tab 2: Sound-to-Light & Movement Engine
        tabEngine = new Tab(I18n.get("tab.engine"));
        tabEngine.setClosable(false);
        tabEngine.setContent(buildEngineControlView());

        // Tab 3: Art-Net Profile / Presets
        tabPresets = new Tab(I18n.get("tab.presets"));
        tabPresets.setClosable(false);
        tabPresets.setContent(buildPresetsView());

        tabPane.getTabs().addAll(tabPatches, tabEngine, tabPresets);
        return tabPane;
    }

    @SuppressWarnings("unchecked")
    private Node buildPatchView() {
        VBox root = new VBox(8);
        root.setPadding(new Insets(10));

        tablePatches = new TableView<>(patchList);
        tablePatches.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tablePatches, Priority.ALWAYS);

        colActive = new TableColumn<>(I18n.get("patch.col.active"));
        colActive.setCellValueFactory(data -> new SimpleBooleanProperty(data.getValue().isEnabled()));
        colActive.setCellFactory(col -> new CheckBoxTableCell<>(idx -> {
            var item = tablePatches.getItems().get(idx);
            SimpleBooleanProperty prop = new SimpleBooleanProperty(item.isEnabled());
            prop.addListener((obs, o, n) -> {
                item.setEnabled(n);
                saveStateToConfig();
            });
            return prop;
        }));
        colActive.setMaxWidth(60);
        colActive.setStyle("-fx-alignment: CENTER;");

        colName = new TableColumn<>(I18n.get("patch.col.name"));
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colName.setMinWidth(140);

        colProfile = new TableColumn<>(I18n.get("patch.col.profile"));
        colProfile.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProfile() != null ? data.getValue().getProfile().getName() : ""));
        colProfile.setMinWidth(150);

        colDmx = new TableColumn<>(I18n.get("patch.col.dmx"));
        colDmx.setCellValueFactory(data -> new SimpleStringProperty("DMX " + data.getValue().getStartAddress() + " - " + data.getValue().getEndAddress()));
        colDmx.setMaxWidth(120);
        colDmx.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        colLimits = new TableColumn<>(I18n.get("patch.col.limits"));
        colLimits.setCellValueFactory(data -> {
            FixturePatch p = data.getValue();
            String inv = (p.isInvertPan() ? "Pan-Inv " : "") + (p.isInvertTilt() ? "Tilt-Inv" : "");
            return new SimpleStringProperty(inv.isBlank() ? I18n.get("patch.limits.default") : inv.trim());
        });
        colLimits.setMaxWidth(140);
        colLimits.setStyle("-fx-alignment: CENTER;");

        colPhase = new TableColumn<>(I18n.get("patch.col.phase"));
        colPhase.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.0f°", data.getValue().getPhaseOffset() * 180.0 / Math.PI)));
        colPhase.setMaxWidth(100);
        colPhase.setStyle("-fx-alignment: CENTER;");

        tablePatches.getColumns().addAll(colActive, colName, colProfile, colDmx, colLimits, colPhase);

        // Action Buttons Row
        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        btnAdd = new MaterialButton(I18n.get("btn.new_fixture"), "plus", 
            MaterialTheme.COLOR_PRIMARY, MaterialTheme.COLOR_ON_PRIMARY, 12, 10, 4, 11, true, this::openNewFixtureDialog);

        btnEdit = new MaterialButton(I18n.get("btn.edit"), "edit-3", 
            MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_TEXT_HIGH, 12, 10, 4, 11, false, this::openEditFixtureDialog);

        btnDelete = new MaterialButton(I18n.get("btn.delete"), "trash-2", 
            MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_ERROR, 12, 10, 4, 11, false, this::deleteSelectedFixture);

        btnImportQlc = new MaterialButton(I18n.get("btn.qlc_import"), "folder-open", 
            Color.web("#00E5FF"), Color.web("#000000"), 12, 10, 4, 11, true, this::importQlcFixture);

        btnRow.getChildren().addAll(btnAdd, btnEdit, btnDelete, btnImportQlc);
        root.getChildren().addAll(tablePatches, btnRow);
        return root;
    }

    private Node buildEngineControlView() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(14));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);

        // 0. Aktive Steuerung (Bewegung / Licht / Strobo einzeln ein-/ausschaltbar)
        lblActive = new Label(I18n.get("engine.active_control"));
        lblActive.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblActive.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        HBox activeBox = new HBox(20);
        activeBox.setAlignment(Pos.CENTER_LEFT);
        chkMovementEnabled = new CheckBox(I18n.get("engine.movement"));
        chkMovementEnabled.setSelected(showEngine.isMovementEnabled());
        chkMovementEnabled.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);

        chkLightEnabled = new CheckBox(I18n.get("engine.light"));
        chkLightEnabled.setSelected(showEngine.isLightEnabled());
        chkLightEnabled.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);

        chkStrobeEnabled = new CheckBox(I18n.get("engine.strobe"));
        chkStrobeEnabled.setSelected(showEngine.isStrobeEnabled());
        chkStrobeEnabled.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        activeBox.getChildren().addAll(chkMovementEnabled, chkLightEnabled, chkStrobeEnabled);

        // 1. Erkennungs-Modus (Level-Detect / Beat-Detect)
        lblDetectMode = new Label(I18n.get("engine.detection_mode"));
        lblDetectMode.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblDetectMode.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        cbDetectionMode = new ComboBox<>(FXCollections.observableArrayList(BeatDetector.DetectionMode.values()));
        cbDetectionMode.setValue(audioService.getAnalyzer().getBeatDetector().getDetectionMode());
        cbDetectionMode.setMaxWidth(Double.MAX_VALUE);
        cbDetectionMode.setOnAction(e -> {
            audioService.getAnalyzer().getBeatDetector().setDetectionMode(cbDetectionMode.getValue());
            autoSaveConfig();
        });

        // 2. Beat-Empfindlichkeit (Sensitivity)
        lblBeatSens = new Label(I18n.get("engine.beat_sensitivity"));
        lblBeatSens.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblBeatSens.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        HBox beatSensBox = new HBox(8);
        beatSensBox.setAlignment(Pos.CENTER_LEFT);
        slBeatSensitivity = new Slider(0.2, 2.0, audioService.getAnalyzer().getBeatDetector().getSensitivity());
        HBox.setHgrow(slBeatSensitivity, Priority.ALWAYS);
        Label lblBeatSensVal = new Label(String.format("%.0f%%", slBeatSensitivity.getValue() * 100));
        lblBeatSensVal.setMinWidth(40);
        lblBeatSensVal.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        slBeatSensitivity.valueProperty().addListener((obs, o, n) -> {
            audioService.getAnalyzer().getBeatDetector().setSensitivity(n.doubleValue());
            lblBeatSensVal.setText(String.format("%.0f%%", n.doubleValue() * 100));
            autoSaveConfig();
        });
        beatSensBox.getChildren().addAll(slBeatSensitivity, lblBeatSensVal);

        // 3. Bewegungsmuster
        lblPattern = new Label(I18n.get("engine.movement_pattern"));
        lblPattern.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblPattern.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        cbPattern = new ComboBox<>(FXCollections.observableArrayList(MovementPattern.values()));
        cbPattern.setValue(showEngine.getMovementPattern());
        cbPattern.setMaxWidth(Double.MAX_VALUE);
        cbPattern.setOnAction(e -> {
            showEngine.setMovementPattern(cbPattern.getValue());
            autoSaveConfig();
        });

        // 4. Geschwindigkeit
        lblSpeed = new Label(I18n.get("engine.speed"));
        lblSpeed.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblSpeed.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        HBox speedBox = new HBox(8);
        speedBox.setAlignment(Pos.CENTER_LEFT);
        slSpeed = new Slider(0.2, 3.0, showEngine.getMovementGenerator().getCurrentSpeed());
        HBox.setHgrow(slSpeed, Priority.ALWAYS);
        Label lblSpeedVal = new Label(String.format("%.1fx", slSpeed.getValue()));
        lblSpeedVal.setMinWidth(40);
        lblSpeedVal.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        slSpeed.valueProperty().addListener((obs, o, n) -> {
            showEngine.getMovementGenerator().setCurrentSpeed(n.doubleValue());
            lblSpeedVal.setText(String.format("%.1fx", n.doubleValue()));
            autoSaveConfig();
        });
        speedBox.getChildren().addAll(slSpeed, lblSpeedVal);

        // 5. Auslenkung / Weite
        lblAmp = new Label(I18n.get("engine.range"));
        lblAmp.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblAmp.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        HBox ampBox = new HBox(8);
        ampBox.setAlignment(Pos.CENTER_LEFT);
        slAmplitude = new Slider(0.1, 1.0, showEngine.getMovementGenerator().getBaseAmplitude());
        HBox.setHgrow(slAmplitude, Priority.ALWAYS);
        Label lblAmpVal = new Label(String.format("%.0f%%", slAmplitude.getValue() * 100));
        lblAmpVal.setMinWidth(40);
        lblAmpVal.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        slAmplitude.valueProperty().addListener((obs, o, n) -> {
            showEngine.getMovementGenerator().setBaseAmplitude(n.doubleValue());
            lblAmpVal.setText(String.format("%.0f%%", n.doubleValue() * 100));
            autoSaveConfig();
        });
        ampBox.getChildren().addAll(slAmplitude, lblAmpVal);

        // 6. Dimmer-Modus & Always-On Intensitäts-Slider
        lblDimmer = new Label(I18n.get("engine.dimmer_response"));
        lblDimmer.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblDimmer.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        VBox dimmerBox = new VBox(6);
        dimmerBox.setAlignment(Pos.CENTER_LEFT);

        cbDimmerMode = new ComboBox<>(FXCollections.observableArrayList(Sound2LightEngine.DimmerMode.values()));
        cbDimmerMode.setValue(showEngine.getDimmerMode());
        cbDimmerMode.setMaxWidth(Double.MAX_VALUE);

        alwaysOnBox = new HBox(8);
        alwaysOnBox.setAlignment(Pos.CENTER_LEFT);
        alwaysOnBox.setPadding(new Insets(2, 0, 0, 0));

        lblAlwaysOnTitle = new Label(I18n.get("engine.intensity") + ":");
        lblAlwaysOnTitle.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblAlwaysOnTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        lblAlwaysOnTitle.setMinWidth(65);

        slAlwaysOnIntensity = new Slider(0.0, 1.0, config.getAlwaysOnIntensity());
        HBox.setHgrow(slAlwaysOnIntensity, Priority.ALWAYS);
        lblAlwaysOnVal = new Label(String.format("%.0f%%", slAlwaysOnIntensity.getValue() * 100));
        lblAlwaysOnVal.setMinWidth(40);
        lblAlwaysOnVal.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);

        slAlwaysOnIntensity.valueProperty().addListener((obs, o, n) -> {
            showEngine.setAlwaysOnIntensity(n.doubleValue());
            lblAlwaysOnVal.setText(String.format("%.0f%%", n.doubleValue() * 100));
            autoSaveConfig();
        });

        alwaysOnBox.getChildren().addAll(lblAlwaysOnTitle, slAlwaysOnIntensity, lblAlwaysOnVal);

        Runnable updateAlwaysOnVisibility = () -> {
            boolean isAlwaysOn = cbDimmerMode.getValue() == Sound2LightEngine.DimmerMode.ALWAYS_ON;
            boolean lgt = chkLightEnabled.isSelected();
            alwaysOnBox.setVisible(isAlwaysOn);
            alwaysOnBox.setManaged(isAlwaysOn);
            slAlwaysOnIntensity.setDisable(!lgt);
        };

        cbDimmerMode.setOnAction(e -> {
            showEngine.setDimmerMode(cbDimmerMode.getValue());
            updateAlwaysOnVisibility.run();
            autoSaveConfig();
        });

        dimmerBox.getChildren().addAll(cbDimmerMode, alwaysOnBox);
        updateAlwaysOnVisibility.run();

        // 7. Farbpalette
        lblPal = new Label(I18n.get("engine.color_palette"));
        lblPal.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblPal.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        cbPalette = new ComboBox<>(FXCollections.observableArrayList(ColorEngine.Palette.values()));
        cbPalette.setValue(showEngine.getColorEngine().getCurrentPalette());
        cbPalette.setMaxWidth(Double.MAX_VALUE);
        cbPalette.setOnAction(e -> {
            showEngine.getColorEngine().setPalette(cbPalette.getValue());
            autoSaveConfig();
        });

        // Abhängige Steuerelemente bei Deaktivierung ausgrauen
        Runnable updateDisabledStates = () -> {
            boolean mov = chkMovementEnabled.isSelected();
            boolean lgt = chkLightEnabled.isSelected();
            cbPattern.setDisable(!mov);
            slSpeed.setDisable(!mov);
            slAmplitude.setDisable(!mov);
            cbDimmerMode.setDisable(!lgt);
            slAlwaysOnIntensity.setDisable(!lgt);
            cbPalette.setDisable(!lgt);
            chkStrobeEnabled.setDisable(!lgt);
            updateAlwaysOnVisibility.run();
        };
        updateDisabledStates.run();

        chkMovementEnabled.setOnAction(e -> {
            showEngine.setMovementEnabled(chkMovementEnabled.isSelected());
            updateDisabledStates.run();
            autoSaveConfig();
        });
        chkLightEnabled.setOnAction(e -> {
            showEngine.setLightEnabled(chkLightEnabled.isSelected());
            updateDisabledStates.run();
            autoSaveConfig();
        });
        chkStrobeEnabled.setOnAction(e -> {
            showEngine.setStrobeEnabled(chkStrobeEnabled.isSelected());
            autoSaveConfig();
        });

        grid.add(lblActive, 0, 0);
        grid.add(activeBox, 1, 0);
        grid.add(lblDetectMode, 0, 1);
        grid.add(cbDetectionMode, 1, 1);
        grid.add(lblBeatSens, 0, 2);
        grid.add(beatSensBox, 1, 2);
        grid.add(lblPattern, 0, 3);
        grid.add(cbPattern, 1, 3);
        grid.add(lblSpeed, 0, 4);
        grid.add(speedBox, 1, 4);
        grid.add(lblAmp, 0, 5);
        grid.add(ampBox, 1, 5);
        grid.add(lblDimmer, 0, 6);
        grid.add(dimmerBox, 1, 6);
        grid.add(lblPal, 0, 7);
        grid.add(cbPalette, 1, 7);

        ColumnConstraints cc0 = new ColumnConstraints(160);
        ColumnConstraints cc1 = new ColumnConstraints(300, 400, Double.MAX_VALUE);
        cc1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(cc0, cc1);

        root.getChildren().add(grid);
        return root;
    }

    @SuppressWarnings("unchecked")
    private Node buildPresetsView() {
        VBox root = new VBox(8);
        root.setPadding(new Insets(10));

        // 1. Info-Banner über das aktuell aktive Profil
        HBox banner = new HBox(10);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(8, 12, 8, 12));
        banner.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_2DP + 
                        "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + 
                        "; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px;");

        LucideIcon bannerIcon = new LucideIcon("bookmark", 18, MaterialTheme.COLOR_PRIMARY);

        lblActivePresetBanner = new Label();
        lblActivePresetBanner.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 12));
        lblActivePresetBanner.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        updateActivePresetBanner();

        Region bannerSpacer = new Region();
        HBox.setHgrow(bannerSpacer, Priority.ALWAYS);

        banner.getChildren().addAll(bannerIcon, lblActivePresetBanner, bannerSpacer);

        // 2. Presets Tabelle
        tablePresets = new TableView<>(presetList);
        tablePresets.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tablePresets, Priority.ALWAYS);

        colPresetStatus = new TableColumn<>(I18n.get("preset.col.status"));
        colPresetStatus.setCellValueFactory(data -> {
            boolean isActive = data.getValue().getId().equals(config.getActivePresetId());
            return new SimpleStringProperty(isActive ? I18n.get("preset.active_badge") : "");
        });
        colPresetStatus.setMaxWidth(80);
        colPresetStatus.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");
        colPresetStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(item);
                    setStyle("-fx-alignment: CENTER; -fx-font-weight: bold; -fx-text-fill: " + MaterialTheme.HEX_PRIMARY + ";");
                }
            }
        });

        colPresetName = new TableColumn<>(I18n.get("preset.col.name"));
        colPresetName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colPresetName.setMinWidth(140);

        colPresetIp = new TableColumn<>(I18n.get("preset.col.ip"));
        colPresetIp.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTargetIp()));
        colPresetIp.setMinWidth(110);
        colPresetIp.setStyle("-fx-alignment: CENTER;");

        colPresetUniverse = new TableColumn<>(I18n.get("preset.col.universe"));
        colPresetUniverse.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getUniverse())));
        colPresetUniverse.setMaxWidth(90);
        colPresetUniverse.setStyle("-fx-alignment: CENTER;");

        colPresetFps = new TableColumn<>(I18n.get("preset.col.fps"));
        colPresetFps.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFps() + " FPS"));
        colPresetFps.setMaxWidth(80);
        colPresetFps.setStyle("-fx-alignment: CENTER;");

        colPresetFixtures = new TableColumn<>(I18n.get("preset.col.fixtures"));
        colPresetFixtures.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getFixtures() != null ? data.getValue().getFixtures().size() : 0)));
        colPresetFixtures.setMaxWidth(90);
        colPresetFixtures.setStyle("-fx-alignment: CENTER;");

        colPresetModified = new TableColumn<>(I18n.get("preset.col.modified"));
        colPresetModified.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLastModified() != null ? data.getValue().getLastModified() : ""));
        colPresetModified.setMinWidth(130);
        colPresetModified.setStyle("-fx-alignment: CENTER;");

        tablePresets.getColumns().addAll(colPresetStatus, colPresetName, colPresetIp, colPresetUniverse, colPresetFps, colPresetFixtures, colPresetModified);

        // Doppelklick aktiviert Preset
        tablePresets.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                ArtNetPreset selected = tablePresets.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    loadPreset(selected);
                }
            }
        });

        // 3. Action Buttons Row
        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        btnNewPreset = new MaterialButton(I18n.get("preset.btn.new"), "plus",
                MaterialTheme.COLOR_PRIMARY, MaterialTheme.COLOR_ON_PRIMARY, 12, 10, 4, 11, true, this::openSavePresetDialog);

        btnLoadPreset = new MaterialButton(I18n.get("preset.btn.load"), "play",
                Color.web("#00E5FF"), Color.web("#000000"), 12, 10, 4, 11, true, () -> {
            ArtNetPreset sel = tablePresets.getSelectionModel().getSelectedItem();
            if (sel != null) {
                loadPreset(sel);
            }
        });

        btnUpdatePreset = new MaterialButton(I18n.get("preset.btn.update"), "save",
                MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_TEXT_HIGH, 12, 10, 4, 11, false, this::updateSelectedPresetWithCurrentValues);

        btnRenamePreset = new MaterialButton(I18n.get("preset.btn.rename"), "edit-3",
                MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_TEXT_HIGH, 12, 10, 4, 11, false, this::openRenamePresetDialog);

        btnDeletePreset = new MaterialButton(I18n.get("preset.btn.delete"), "trash-2",
                MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_ERROR, 12, 10, 4, 11, false, this::deleteSelectedPreset);

        btnRow.getChildren().addAll(btnNewPreset, btnLoadPreset, btnUpdatePreset, btnRenamePreset, btnDeletePreset);

        root.getChildren().addAll(banner, tablePresets, btnRow);
        return root;
    }

    private void updateActivePresetBanner() {
        if (lblActivePresetBanner == null) return;
        String activeId = config.getActivePresetId();
        ArtNetPreset active = null;
        for (ArtNetPreset p : presetList) {
            if (p.getId().equals(activeId)) {
                active = p;
                break;
            }
        }
        if (active != null) {
            lblActivePresetBanner.setText(String.format(I18n.get("preset.active_banner"), active.getName()) +
                    "  •  " + active.getTargetIp() + " (Uni " + active.getUniverse() + ", " + active.getFps() + " FPS, " +
                    (active.getFixtures() != null ? active.getFixtures().size() : 0) + " Fixtures)");
        } else {
            lblActivePresetBanner.setText(I18n.get("preset.custom"));
        }
    }

    private void loadPreset(ArtNetPreset preset) {
        if (preset == null) return;

        txtTargetIp.setText(preset.getTargetIp());
        spUniverse.getValueFactory().setValue(preset.getUniverse());
        spFps.getValueFactory().setValue(preset.getFps());

        patchList.setAll(preset.copyFixtures());
        showEngine.setPatchedFixtures(new ArrayList<>(patchList));

        if (preset.getMovementPattern() != null) {
            cbPattern.setValue(preset.getMovementPattern());
            showEngine.setMovementPattern(preset.getMovementPattern());
        }
        if (preset.getMovementSpeed() > 0) {
            slSpeed.setValue(preset.getMovementSpeed());
            showEngine.getMovementGenerator().setCurrentSpeed(preset.getMovementSpeed());
        }
        if (preset.getMovementSize() > 0) {
            slAmplitude.setValue(preset.getMovementSize());
            showEngine.getMovementGenerator().setBaseAmplitude(preset.getMovementSize());
        }
        if (preset.getDimmerMode() != null) {
            cbDimmerMode.setValue(preset.getDimmerMode());
            showEngine.setDimmerMode(preset.getDimmerMode());
        }
        if (slAlwaysOnIntensity != null) {
            slAlwaysOnIntensity.setValue(preset.getAlwaysOnIntensity());
            showEngine.setAlwaysOnIntensity(preset.getAlwaysOnIntensity());
        }
        if (preset.getColorPalette() != null) {
            cbPalette.setValue(preset.getColorPalette());
            showEngine.getColorEngine().setPalette(preset.getColorPalette());
        }
        if (chkMovementEnabled != null) {
            chkMovementEnabled.setSelected(preset.isMovementEnabled());
            showEngine.setMovementEnabled(preset.isMovementEnabled());
        }
        if (chkLightEnabled != null) {
            chkLightEnabled.setSelected(preset.isLightEnabled());
            showEngine.setLightEnabled(preset.isLightEnabled());
        }
        if (chkStrobeEnabled != null) {
            chkStrobeEnabled.setSelected(preset.isStrobeEnabled());
            showEngine.setStrobeEnabled(preset.isStrobeEnabled());
        }

        if (isRunning) {
            try {
                artNetSender.updateTarget(preset.getTargetIp(), preset.getUniverse());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Fehler beim Live-Aktualisieren der Art-Net Zieladresse: " + e.getMessage(), e);
            }
        }

        config.setActivePresetId(preset.getId());
        saveStateToConfig();

        tablePatches.refresh();
        tablePresets.refresh();
        updateActivePresetBanner();

        statusLabel.setText(String.format(I18n.get("preset.status.loaded"), preset.getName()));
    }

    private void updateSelectedPresetWithCurrentValues() {
        ArtNetPreset sel = tablePresets.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        sel.setTargetIp(txtTargetIp.getText().trim());
        sel.setUniverse(spUniverse.getValue());
        sel.setFps(spFps.getValue());
        sel.setFixtures(new ArrayList<>(patchList.stream().map(FixturePatch::copy).toList()));
        sel.setMovementPattern(cbPattern.getValue());
        sel.setMovementSpeed(slSpeed.getValue());
        sel.setMovementSize(slAmplitude.getValue());
        sel.setDimmerMode(cbDimmerMode.getValue());
        sel.setAlwaysOnIntensity(slAlwaysOnIntensity != null ? slAlwaysOnIntensity.getValue() : 1.0);
        sel.setColorPalette(cbPalette.getValue());
        sel.setMovementEnabled(chkMovementEnabled != null && chkMovementEnabled.isSelected());
        sel.setLightEnabled(chkLightEnabled != null && chkLightEnabled.isSelected());
        sel.setStrobeEnabled(chkStrobeEnabled != null && chkStrobeEnabled.isSelected());
        sel.touch();

        config.setActivePresetId(sel.getId());
        saveStateToConfig();

        tablePresets.refresh();
        updateActivePresetBanner();
        statusLabel.setText(String.format(I18n.get("preset.status.updated"), sel.getName()));
    }

    private void openSavePresetDialog() {
        VBox card = new VBox(14);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_1DP + 
                     "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + 
                     "; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;" +
                     "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.75), 24, 0, 0, 8);");
        card.setPrefWidth(420);
        card.setMaxWidth(420);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        LucideIcon iconHdr = new LucideIcon("bookmark", 20, MaterialTheme.COLOR_PRIMARY);
        Label lblTitle = new Label(I18n.get("preset.dialog.save_title"));
        lblTitle.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 14));
        lblTitle.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox btnClose = createModalCloseButton(this::hideOverlay);
        header.getChildren().addAll(iconHdr, lblTitle, sp, btnClose);

        Label lblName = new Label(I18n.get("preset.dialog.name_label"));
        lblName.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblName.setFont(Font.font(MaterialTheme.FONT_FAMILY, 12));

        TextField txtName = new TextField("Setup " + (presetList.size() + 1));
        txtName.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_2DP + "; -fx-text-fill: " + MaterialTheme.HEX_TEXT_HIGH + "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + "; -fx-border-radius: 4px; -fx-padding: 6 10;");

        Label lblDesc = new Label(I18n.get("preset.dialog.desc_label"));
        lblDesc.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblDesc.setFont(Font.font(MaterialTheme.FONT_FAMILY, 12));

        TextField txtDesc = new TextField();
        txtDesc.setPromptText("Optional...");
        txtDesc.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_2DP + "; -fx-text-fill: " + MaterialTheme.HEX_TEXT_HIGH + "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + "; -fx-border-radius: 4px; -fx-padding: 6 10;");

        VBox previewBox = new VBox(6);
        previewBox.setPadding(new Insets(10));
        previewBox.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_2DP + "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + "; -fx-border-radius: 4px;");

        Label lblPrevTitle = new Label(I18n.get("preset.dialog.preview_title"));
        lblPrevTitle.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 11));
        lblPrevTitle.setTextFill(MaterialTheme.COLOR_TEXT_MED);

        Label lblPrevValues = new Label("• " + I18n.get("ctrl.target_ip") + ": " + txtTargetIp.getText().trim() + "\n" +
                                        "• " + I18n.get("ctrl.universe") + ": " + spUniverse.getValue() + "\n" +
                                        "• " + I18n.get("ctrl.fps") + ": " + spFps.getValue() + " FPS\n" +
                                        "• " + I18n.get("tab.fixtures") + ": " + patchList.size() + " Fixtures");
        lblPrevValues.setFont(Font.font(MaterialTheme.FONT_FAMILY, 11));
        lblPrevValues.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        previewBox.getChildren().addAll(lblPrevTitle, lblPrevValues);

        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        MaterialButton btnCancel = new MaterialButton(I18n.get("btn.cancel"), "x",
                MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_TEXT_HIGH, 11, 10, 4, 11, false, this::hideOverlay);

        MaterialButton btnSave = new MaterialButton(I18n.get("btn.save"), "save",
                MaterialTheme.COLOR_PRIMARY, MaterialTheme.COLOR_ON_PRIMARY, 11, 10, 4, 11, true, () -> {
            String name = txtName.getText().trim();
            if (name.isEmpty()) name = "Preset " + (presetList.size() + 1);

            ArtNetPreset newPreset = new ArtNetPreset(
                    null,
                    name,
                    txtDesc.getText().trim(),
                    txtTargetIp.getText().trim(),
                    spUniverse.getValue(),
                    spFps.getValue(),
                    new ArrayList<>(patchList.stream().map(FixturePatch::copy).toList()),
                    cbPattern.getValue(),
                    slSpeed.getValue(),
                    slAmplitude.getValue(),
                    cbDimmerMode.getValue(),
                    slAlwaysOnIntensity != null ? slAlwaysOnIntensity.getValue() : 1.0,
                    cbPalette.getValue(),
                    chkMovementEnabled != null && chkMovementEnabled.isSelected(),
                    chkLightEnabled != null && chkLightEnabled.isSelected(),
                    chkStrobeEnabled != null && chkStrobeEnabled.isSelected(),
                    null
            );

            presetList.add(newPreset);
            config.setActivePresetId(newPreset.getId());
            saveStateToConfig();

            tablePresets.getSelectionModel().select(newPreset);
            tablePresets.refresh();
            updateActivePresetBanner();
            hideOverlay();
            statusLabel.setText(String.format(I18n.get("preset.status.saved"), newPreset.getName()));
        });

        btnRow.getChildren().addAll(btnCancel, btnSave);

        card.getChildren().addAll(header, lblName, txtName, lblDesc, txtDesc, previewBox, btnRow);
        showOverlay(card);
    }

    private void openRenamePresetDialog() {
        ArtNetPreset sel = tablePresets.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        VBox card = new VBox(14);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_1DP + 
                     "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + 
                     "; -fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;" +
                     "-fx-effect: dropshadow(three-pass-box, rgba(0, 0, 0, 0.75), 24, 0, 0, 8);");
        card.setPrefWidth(380);
        card.setMaxWidth(380);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        LucideIcon iconHdr = new LucideIcon("edit-3", 18, MaterialTheme.COLOR_PRIMARY);
        Label lblTitle = new Label(I18n.get("preset.dialog.rename_title"));
        lblTitle.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 14));
        lblTitle.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox btnClose = createModalCloseButton(this::hideOverlay);
        header.getChildren().addAll(iconHdr, lblTitle, sp, btnClose);

        Label lblName = new Label(I18n.get("preset.dialog.name_label"));
        lblName.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblName.setFont(Font.font(MaterialTheme.FONT_FAMILY, 12));

        TextField txtName = new TextField(sel.getName());
        txtName.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_2DP + "; -fx-text-fill: " + MaterialTheme.HEX_TEXT_HIGH + "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + "; -fx-border-radius: 4px; -fx-padding: 6 10;");

        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        MaterialButton btnCancel = new MaterialButton(I18n.get("btn.cancel"), "x",
                MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_TEXT_HIGH, 11, 10, 4, 11, false, this::hideOverlay);

        MaterialButton btnSave = new MaterialButton(I18n.get("btn.save"), "save",
                MaterialTheme.COLOR_PRIMARY, MaterialTheme.COLOR_ON_PRIMARY, 11, 10, 4, 11, true, () -> {
            String name = txtName.getText().trim();
            if (!name.isEmpty()) {
                sel.setName(name);
                sel.touch();
                saveStateToConfig();
                tablePresets.refresh();
                updateActivePresetBanner();
                statusLabel.setText(String.format(I18n.get("preset.status.renamed"), name));
            }
            hideOverlay();
        });

        btnRow.getChildren().addAll(btnCancel, btnSave);
        card.getChildren().addAll(header, lblName, txtName, btnRow);
        showOverlay(card);
    }

    private void deleteSelectedPreset() {
        ArtNetPreset sel = tablePresets.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (presetList.size() <= 1) {
            return;
        }
        presetList.remove(sel);
        config.getPresets().remove(sel);
        if (sel.getId().equals(config.getActivePresetId())) {
            config.setActivePresetId(presetList.isEmpty() ? null : presetList.get(0).getId());
        }
        saveStateToConfig();
        tablePresets.refresh();
        updateActivePresetBanner();
        statusLabel.setText(String.format(I18n.get("preset.status.deleted"), sel.getName()));
    }

    private HBox createModalCloseButton(Runnable onClose) {
        HBox btnCloseTop = new HBox();
        btnCloseTop.setAlignment(Pos.CENTER);
        btnCloseTop.setPadding(new Insets(4, 6, 4, 6));
        btnCloseTop.setCursor(Cursor.HAND);
        btnCloseTop.setStyle("-fx-background-radius: 4px; -fx-background-color: transparent;");
        btnCloseTop.setOnMouseEntered(e -> btnCloseTop.setStyle("-fx-background-radius: 4px; -fx-background-color: " + MaterialTheme.HEX_SURFACE_4DP + ";"));
        btnCloseTop.setOnMouseExited(e -> btnCloseTop.setStyle("-fx-background-radius: 4px; -fx-background-color: transparent;"));

        LucideIcon iconClose = new LucideIcon("x", 16, MaterialTheme.COLOR_TEXT_MED);
        btnCloseTop.getChildren().add(iconClose);
        btnCloseTop.setOnMouseClicked(e -> onClose.run());
        return btnCloseTop;
    }

    private Region buildChannelsCard() {
        visualizer = new ChannelVisualizer();
        visualizer.setMinHeight(120);
        visualizer.setMaxHeight(Double.MAX_VALUE);
        return visualizer;
    }

    private void buildStatusBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 16, 6, 16));

        statusLabel = new Label(I18n.get("statusbar.ready"));
        statusLabel.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        statusLabel.setFont(Font.font("Segoe UI", 11));

        bar.getChildren().add(statusLabel);
        contentBox.getChildren().add(bar);
    }

    private ControlBox createControlBox(String titleKey, String tooltipTitleKey, String tooltipTextKey) {
        VBox box = new VBox(5);
        box.setPadding(new Insets(6, 10, 8, 10));
        box.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_2DP + 
                     "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + 
                     "; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px;");

        HBox hdr = new HBox();
        hdr.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(I18n.get(titleKey));
        lbl.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HelpBadge help = new HelpBadge(() -> I18n.get(tooltipTitleKey), () -> I18n.get(tooltipTextKey));
        hdr.getChildren().addAll(lbl, spacer, help);

        box.getChildren().add(hdr);
        return new ControlBox(box, lbl, help);
    }

    private void updateAllLocalizedTexts() {
        // 1. Top Bar
        if (chipLabel != null) {
            if (!isRunning) {
                chipLabel.setText(I18n.get("status.ready"));
                btnAction.setText(I18n.get("btn.start"));
            } else {
                chipLabel.setText(I18n.get("status.active"));
                btnAction.setText(I18n.get("btn.stop"));
            }
        }

        // 2. Metriken
        if (lblInTitle != null) lblInTitle.setText(I18n.get("metric.audio_rms"));
        if (lblPeakHdr != null) lblPeakHdr.setText(I18n.get("metric.peak"));
        if (lblBpmHdr != null) lblBpmHdr.setText(I18n.get("metric.tempo"));
        if (lblTierHdr != null) lblTierHdr.setText(I18n.get("metric.tier"));
        if (lblSpeedTier != null) lblSpeedTier.setText(showEngine.getCurrentSpeedTier().getDisplayName());
        if (lblOutTitle != null) lblOutTitle.setText(I18n.get("metric.artnet_out"));
        if (lblPaketeHdr != null) lblPaketeHdr.setText(I18n.get("metric.sent"));
        if (chartPps != null) chartPps.setTitle(I18n.get("metric.chart_title"));

        // 3. Schnell-Steuerung
        if (lblBoxAudio != null) lblBoxAudio.setText(I18n.get("ctrl.audio_source"));
        if (btnScanAudio != null) btnScanAudio.setText(I18n.get("btn.scan"));
        if (lblBoxIp != null) lblBoxIp.setText(I18n.get("ctrl.target_ip"));
        if (lblBoxUni != null) lblBoxUni.setText(I18n.get("ctrl.universe"));
        if (lblBoxFps != null) lblBoxFps.setText(I18n.get("ctrl.fps"));
        if (lblBoxGain != null) lblBoxGain.setText(I18n.get("ctrl.gain_agc"));
        if (lblBoxAction != null) lblBoxAction.setText(I18n.get("ctrl.action"));

        // 4. Tabs
        if (tabPatches != null) tabPatches.setText(I18n.get("tab.fixtures"));
        if (tabEngine != null) tabEngine.setText(I18n.get("tab.engine"));
        if (tabPresets != null) tabPresets.setText(I18n.get("tab.presets"));

        // 5. Fixture Patch Tabelle
        if (colActive != null) colActive.setText(I18n.get("patch.col.active"));
        if (colName != null) colName.setText(I18n.get("patch.col.name"));
        if (colProfile != null) colProfile.setText(I18n.get("patch.col.profile"));
        if (colDmx != null) colDmx.setText(I18n.get("patch.col.dmx"));
        if (colLimits != null) colLimits.setText(I18n.get("patch.col.limits"));
        if (colPhase != null) colPhase.setText(I18n.get("patch.col.phase"));

        if (btnAdd != null) btnAdd.setText(I18n.get("btn.new_fixture"));
        if (btnEdit != null) btnEdit.setText(I18n.get("btn.edit"));
        if (btnDelete != null) btnDelete.setText(I18n.get("btn.delete"));
        if (btnImportQlc != null) btnImportQlc.setText(I18n.get("btn.qlc_import"));

        if (tablePatches != null) tablePatches.refresh();

        // 6. Engine Controls
        if (lblActive != null) lblActive.setText(I18n.get("engine.active_control"));
        if (chkMovementEnabled != null) chkMovementEnabled.setText(I18n.get("engine.movement"));
        if (chkLightEnabled != null) chkLightEnabled.setText(I18n.get("engine.light"));
        if (chkStrobeEnabled != null) chkStrobeEnabled.setText(I18n.get("engine.strobe"));

        if (lblDetectMode != null) lblDetectMode.setText(I18n.get("engine.detection_mode"));
        if (lblBeatSens != null) lblBeatSens.setText(I18n.get("engine.beat_sensitivity"));
        if (lblPattern != null) lblPattern.setText(I18n.get("engine.movement_pattern"));
        if (lblSpeed != null) lblSpeed.setText(I18n.get("engine.speed"));
        if (lblAmp != null) lblAmp.setText(I18n.get("engine.range"));
        if (lblDimmer != null) lblDimmer.setText(I18n.get("engine.dimmer_response"));
        if (lblPal != null) lblPal.setText(I18n.get("engine.color_palette"));

        // ComboBox Listen & Auswahlen auffrischen
        if (cbDetectionMode != null) {
            var val = cbDetectionMode.getValue();
            cbDetectionMode.setItems(FXCollections.observableArrayList(BeatDetector.DetectionMode.values()));
            cbDetectionMode.setValue(val);
        }
        if (cbPattern != null) {
            var val = cbPattern.getValue();
            cbPattern.setItems(FXCollections.observableArrayList(MovementPattern.values()));
            cbPattern.setValue(val);
        }
        if (cbDimmerMode != null) {
            var val = cbDimmerMode.getValue();
            cbDimmerMode.setItems(FXCollections.observableArrayList(Sound2LightEngine.DimmerMode.values()));
            cbDimmerMode.setValue(val);
        }
        if (cbPalette != null) {
            var val = cbPalette.getValue();
            cbPalette.setItems(FXCollections.observableArrayList(ColorEngine.Palette.values()));
            cbPalette.setValue(val);
        }
        if (lblAlwaysOnTitle != null) {
            lblAlwaysOnTitle.setText(I18n.get("engine.intensity") + ":");
        }

        // 7. Visualizer
        if (visualizer != null) visualizer.updateLocalizedTexts();

        // 8. Presets Tabelle & Buttons
        if (colPresetStatus != null) colPresetStatus.setText(I18n.get("preset.col.status"));
        if (colPresetName != null) colPresetName.setText(I18n.get("preset.col.name"));
        if (colPresetIp != null) colPresetIp.setText(I18n.get("preset.col.ip"));
        if (colPresetUniverse != null) colPresetUniverse.setText(I18n.get("preset.col.universe"));
        if (colPresetFps != null) colPresetFps.setText(I18n.get("preset.col.fps"));
        if (colPresetFixtures != null) colPresetFixtures.setText(I18n.get("preset.col.fixtures"));
        if (colPresetModified != null) colPresetModified.setText(I18n.get("preset.col.modified"));

        if (btnNewPreset != null) btnNewPreset.setText(I18n.get("preset.btn.new"));
        if (btnLoadPreset != null) btnLoadPreset.setText(I18n.get("preset.btn.load"));
        if (btnUpdatePreset != null) btnUpdatePreset.setText(I18n.get("preset.btn.update"));
        if (btnRenamePreset != null) btnRenamePreset.setText(I18n.get("preset.btn.rename"));
        if (btnDeletePreset != null) btnDeletePreset.setText(I18n.get("preset.btn.delete"));

        updateActivePresetBanner();
        if (tablePresets != null) tablePresets.refresh();

        // 9. Status Bar
        if (statusLabel != null) {
            if (isRunning) {
                String ip = txtTargetIp != null ? txtTargetIp.getText().trim() : "127.0.0.1";
                int universe = spUniverse != null ? spUniverse.getValue() : 0;
                statusLabel.setText(I18n.get("statusbar.active", ip, universe, audioService.getCurrentDeviceName()));
            } else {
                statusLabel.setText(I18n.get("statusbar.ready"));
            }
        }
    }

    public void tick() {
        if (isRunning) {
            showEngine.tick();
            byte[] dmx = showEngine.getCurrentDmxFrame();
            visualizer.updateChannels(dmx, patchList);

            var analyzer = audioService.getAnalyzer();
            spectrumView.updateData(
                analyzer.getBandLevels(),
                analyzer.getBandPeaks(),
                analyzer.getRmsLevel(),
                analyzer.getPeakLevel(),
                showEngine.isLastTickBeat()
            );

            lblAudioLevel.setText(String.format("%.1f %%", analyzer.getRmsLevel() * 100.0));
            lblAudioPeak.setText(String.format("%.1f %%", analyzer.getPeakLevel() * 100.0));

            double bpm = showEngine.getCurrentBpm();
            var tier = showEngine.getCurrentSpeedTier();
            lblBpm.setText(bpm >= 40.0 ? String.format("%.0f BPM", bpm) : "-- BPM");
            lblSpeedTier.setText(tier.getDisplayName());
            String tierHex = switch (tier) {
                case IDLE -> MaterialTheme.HEX_TEXT_MED;
                case SLOW -> "#4FC3F7";
                case MEDIUM -> MaterialTheme.HEX_ACCENT_GREEN;
                case FAST -> "#FFB74D";
                case RAVE -> "#FF4081";
            };
            lblSpeedTier.setStyle("-fx-text-fill: " + tierHex + "; -fx-font-weight: bold; -fx-font-size: 13px;");

            lblPps.setText(String.format("%.1f pkt/s", artNetSender.getPacketsPerSecond()));
            lblTotalPackets.setText(String.valueOf(artNetSender.getTotalPacketsSent()));

            long now = System.currentTimeMillis();
            if (now - lastChartUpdate >= 1000) {
                chartPps.addSample(artNetSender.getPacketsPerSecond());
                lastChartUpdate = now;
            }
        }
    }

    public synchronized void toggleService() {
        if (!isRunning) {
            startService();
        } else {
            stopService();
        }
    }

    public synchronized void startService() {
        try {
            String ip = txtTargetIp.getText().trim();
            int universe = spUniverse.getValue();
            artNetSender.start(ip, universe);

            AudioDeviceInfo dev = cbAudioDevice.getValue();
            audioService.start(dev);

            isRunning = true;
            btnAction.updateColors(MaterialTheme.COLOR_ERROR, MaterialTheme.COLOR_ON_ERROR, "square", I18n.get("btn.stop"));
            chipIcon.setIcon("circle", MaterialTheme.COLOR_PRIMARY);
            chipLabel.setText(I18n.get("status.active"));
            chipLabel.setTextFill(MaterialTheme.COLOR_PRIMARY);
            statusLabel.setText(I18n.get("statusbar.active", ip, universe, audioService.getCurrentDeviceName()));

            saveStateToConfig();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Starten von sound2artnet: " + e.getMessage(), e);
            statusLabel.setText(I18n.get("statusbar.error_start", e.getMessage()));
            stopService();
        }
    }

    public synchronized void stopService() {
        isRunning = false;
        audioService.stop();
        artNetSender.stop();

        btnAction.updateColors(MaterialTheme.COLOR_PRIMARY, MaterialTheme.COLOR_ON_PRIMARY, "play", I18n.get("btn.start"));
        chipIcon.setIcon("circle", MaterialTheme.COLOR_TEXT_MED);
        chipLabel.setText(I18n.get("status.ready"));
        chipLabel.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        statusLabel.setText(I18n.get("statusbar.stopped"));

        saveStateToConfig();
    }

    private void scanAudioDevices() {
        List<AudioDeviceInfo> devices = AudioCaptureService.listInputDevices();
        cbAudioDevice.getItems().clear();
        cbAudioDevice.getItems().addAll(devices);
        if (!devices.isEmpty()) {
            AudioDeviceInfo targetDev = devices.get(0);
            if (config.getAudioDevice() != null && !config.getAudioDevice().isBlank()) {
                for (AudioDeviceInfo dev : devices) {
                    if (dev.name().equalsIgnoreCase(config.getAudioDevice())) {
                        targetDev = dev;
                        break;
                    }
                }
            }
            cbAudioDevice.setValue(targetDev);
        }
    }

    private void openNewFixtureDialog() {
        int nextAddr = 1;
        for (FixturePatch p : patchList) {
            nextAddr = Math.max(nextAddr, p.getEndAddress() + 1);
        }
        FixtureEditorDialog dlg = new FixtureEditorDialog((Stage) getScene().getWindow(), null, nextAddr, newPatch -> {
            patchList.add(newPatch);
            showEngine.setPatchedFixtures(new ArrayList<>(patchList));
            saveStateToConfig();
        });
        dlg.show();
    }

    private void openEditFixtureDialog() {
        FixturePatch sel = tablePatches.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        FixtureEditorDialog dlg = new FixtureEditorDialog((Stage) getScene().getWindow(), sel, updatedPatch -> {
            tablePatches.refresh();
            showEngine.setPatchedFixtures(new ArrayList<>(patchList));
            saveStateToConfig();
        });
        dlg.show();
    }

    private void deleteSelectedFixture() {
        FixturePatch sel = tablePatches.getSelectionModel().getSelectedItem();
        if (sel != null) {
            patchList.remove(sel);
            showEngine.setPatchedFixtures(new ArrayList<>(patchList));
            saveStateToConfig();
        }
    }

    private void importQlcFixture() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.get("editor.qlc_chooser_title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("QLC+ Fixture Definition (*.qxf)", "*.qxf"));

        File qlcDir = new File(System.getProperty("user.home"), "Documents\\QLC+ Saves\\qxf");
        if (!qlcDir.exists()) {
            qlcDir = new File(System.getProperty("user.home"), "Documents");
        }
        if (qlcDir.exists()) {
            chooser.setInitialDirectory(qlcDir);
        }

        Stage stage = (Stage) getScene().getWindow();
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            try {
                var def = de.exit.sound2artnet.fixture.qlc.QlcFixtureParser.parse(file);
                int nextAddr = 1;
                for (FixturePatch p : patchList) {
                    nextAddr = Math.max(nextAddr, p.getEndAddress() + 1);
                }
                var dlg = new de.exit.sound2artnet.fixture.qlc.QlcImportDialog(stage, def, nextAddr, newPatch -> {
                    patchList.add(newPatch);
                    showEngine.setPatchedFixtures(new ArrayList<>(patchList));
                    saveStateToConfig();
                    statusLabel.setText(I18n.get("statusbar.qlc_imported", newPatch.getName(), newPatch.getStartAddress()));
                });
                dlg.show();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Fehler beim Parsen der QLC+ Datei: " + e.getMessage(), e);
                statusLabel.setText(I18n.get("statusbar.qlc_error", e.getMessage()));
            }
        }
    }

    private void saveStateToConfig() {
        if (cbAudioDevice != null && cbAudioDevice.getValue() != null) {
            config.setAudioDevice(cbAudioDevice.getValue().name());
        }
        config.setTargetIp(txtTargetIp.getText().trim());
        config.setUniverse(spUniverse.getValue());
        config.setFps(spFps.getValue());
        config.setGain(slGain.getValue());
        config.setAgc(chkAgc.isSelected());
        if (cbDetectionMode != null) {
            config.setDetectionMode(cbDetectionMode.getValue());
        }
        if (slBeatSensitivity != null) {
            config.setBeatSensitivity(slBeatSensitivity.getValue());
        }
        if (chkMovementEnabled != null) {
            config.setMovementEnabled(chkMovementEnabled.isSelected());
        }
        if (chkLightEnabled != null) {
            config.setLightEnabled(chkLightEnabled.isSelected());
        }
        if (chkStrobeEnabled != null) {
            config.setStrobeEnabled(chkStrobeEnabled.isSelected());
        }
        config.setMovementPattern(cbPattern.getValue());
        config.setMovementSpeed(slSpeed.getValue());
        config.setMovementSize(slAmplitude.getValue());
        config.setDimmerMode(cbDimmerMode.getValue());
        if (slAlwaysOnIntensity != null) {
            config.setAlwaysOnIntensity(slAlwaysOnIntensity.getValue());
        }
        config.setColorPalette(cbPalette.getValue());
        config.setFixtures(new ArrayList<>(patchList));
        config.setPresets(new ArrayList<>(presetList));
        ConfigManager.saveConfig(config);
    }

    public AppConfig getConfig() {
        return config;
    }
}

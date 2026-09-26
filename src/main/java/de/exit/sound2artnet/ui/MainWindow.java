package de.exit.sound2artnet.ui;

import de.exit.sound2artnet.artnet.ArtNetSender;
import de.exit.sound2artnet.audio.AudioCaptureService;
import de.exit.sound2artnet.audio.AudioDeviceInfo;
import de.exit.sound2artnet.audio.BeatDetector;
import de.exit.sound2artnet.config.AppConfig;
import de.exit.sound2artnet.config.ConfigManager;
import de.exit.sound2artnet.engine.ColorEngine;
import de.exit.sound2artnet.engine.MovementPattern;
import de.exit.sound2artnet.engine.Sound2LightEngine;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.fixture.FixtureProfile;
import de.exit.sound2artnet.ui.component.*;
import de.exit.sound2artnet.ui.icon.LucideIcon;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
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
public class MainWindow extends VBox {
    private static final Logger LOGGER = Logger.getLogger(MainWindow.class.getName());

    private final AppConfig config;
    private final AudioCaptureService audioService = new AudioCaptureService();
    private final ArtNetSender artNetSender = new ArtNetSender();
    private final Sound2LightEngine showEngine;
    private final PauseTransition saveDebounce = new PauseTransition(Duration.millis(150));

    // Top Bar
    private LucideIcon chipIcon;
    private Label chipLabel;

    // Metriken
    private Label lblAudioLevel;
    private Label lblAudioPeak;
    private Label lblBpm;
    private Label lblSpeedTier;
    private Label lblPps;
    private Label lblTotalPackets;
    private MetricHistoryChart chartPps;
    private AudioSpectrumView spectrumView;

    // Schnell-Steuerung
    private ComboBox<AudioDeviceInfo> cbAudioDevice;
    private TextField txtTargetIp;
    private Spinner<Integer> spUniverse;
    private Spinner<Integer> spFps;
    private Slider slGain;
    private CheckBox chkAgc;
    private MaterialButton btnAction;

    // Fixture & Patching Table
    private final ObservableList<FixturePatch> patchList = FXCollections.observableArrayList();
    private TableView<FixturePatch> tablePatches;

    // Engine Controls
    private CheckBox chkMovementEnabled;
    private CheckBox chkLightEnabled;
    private CheckBox chkStrobeEnabled;
    private ComboBox<BeatDetector.DetectionMode> cbDetectionMode;
    private Slider slBeatSensitivity;
    private ComboBox<MovementPattern> cbPattern;
    private Slider slSpeed;
    private Slider slAmplitude;
    private ComboBox<Sound2LightEngine.DimmerMode> cbDimmerMode;
    private ComboBox<ColorEngine.Palette> cbPalette;

    // Visualizer & Status
    private ChannelVisualizer visualizer;
    private Label statusLabel;

    private boolean isRunning = false;
    private long lastChartUpdate = 0;

    public MainWindow() {
        this.config = ConfigManager.loadConfig();
        this.showEngine = new Sound2LightEngine(audioService, artNetSender);

        // Standard-Patches aus Config in die Engine laden
        if (config.getFixtures() != null) {
            patchList.addAll(config.getFixtures());
            showEngine.setPatchedFixtures(new ArrayList<>(config.getFixtures()));
        }

        showEngine.setMovementEnabled(config.isMovementEnabled());
        showEngine.setLightEnabled(config.isLightEnabled());
        showEngine.setStrobeEnabled(config.isStrobeEnabled());
        showEngine.setMovementPattern(config.getMovementPattern());
        showEngine.setDimmerMode(config.getDimmerMode());
        showEngine.getColorEngine().setPalette(config.getColorPalette());
        showEngine.getMovementGenerator().setCurrentSpeed(config.getMovementSpeed());
        showEngine.getMovementGenerator().setBaseAmplitude(config.getMovementSize());
        audioService.getAnalyzer().setManualGain(config.getGain());
        audioService.getAnalyzer().setAgcEnabled(config.isAgc());
        audioService.getAnalyzer().getBeatDetector().setDetectionMode(config.getDetectionMode());
        audioService.getAnalyzer().getBeatDetector().setSensitivity(config.getBeatSensitivity());

        setStyle("-fx-background-color: " + MaterialTheme.HEX_BG + ";");
        setSpacing(0);
        setPadding(new Insets(0, 0, 4, 0));

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

        getChildren().add(splitPane);
        buildStatusBar();

        scanAudioDevices();
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

        LucideIcon iconLogo = new LucideIcon("music", 20, MaterialTheme.COLOR_PRIMARY);

        Label title = new Label("sound2artnet");
        title.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        title.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 15));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Status Chip
        HBox chip = new HBox(6);
        chip.setAlignment(Pos.CENTER);
        chip.setPadding(new Insets(4, 10, 4, 10));
        chip.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_4DP + "; -fx-background-radius: 12px;");

        chipIcon = new LucideIcon("circle", 10, MaterialTheme.COLOR_TEXT_MED);
        chipLabel = new Label("BEREIT");
        chipLabel.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        chipLabel.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 11));
        chip.getChildren().addAll(chipIcon, chipLabel);

        bar.getChildren().addAll(iconLogo, title, spacer, chip);
        getChildren().add(bar);
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
        Label lblInTitle = new Label("AUDIO RMS");
        lblInTitle.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblInTitle.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 10));
        hdrIn.getChildren().addAll(iconMic, lblInTitle);

        lblAudioLevel = new Label("0.0 %");
        lblAudioLevel.setStyle("-fx-text-fill: " + MaterialTheme.HEX_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 15px;");
        colRms.getChildren().addAll(hdrIn, lblAudioLevel);

        VBox colPeak = new VBox(1);
        Label lblPeakHdr = new Label("PEAK");
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
        Label lblBpmHdr = new Label("TEMPO");
        lblBpmHdr.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblBpmHdr.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 10));

        lblBpm = new Label("-- BPM");
        lblBpm.setStyle("-fx-text-fill: " + MaterialTheme.HEX_PRIMARY + "; -fx-font-weight: bold; -fx-font-size: 15px;");
        colBpm.getChildren().addAll(lblBpmHdr, lblBpm);

        VBox colTier = new VBox(1);
        Label lblTierHdr = new Label("STUFE");
        lblTierHdr.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblTierHdr.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 10));

        lblSpeedTier = new Label("Ruhe");
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
        Label lblOutTitle = new Label("ART-NET OUT");
        lblOutTitle.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblOutTitle.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 10));
        hdrOut.getChildren().addAll(iconOut, lblOutTitle);

        lblPps = new Label("0.0 pkt/s");
        lblPps.setStyle("-fx-text-fill: " + MaterialTheme.HEX_ACCENT_GREEN + "; -fx-font-weight: bold; -fx-font-size: 15px;");
        colOutRate.getChildren().addAll(hdrOut, lblPps);

        VBox colPakete = new VBox(1);
        Label lblPaketeHdr = new Label("GESENDET");
        lblPaketeHdr.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblPaketeHdr.setFont(Font.font(MaterialTheme.FONT_FAMILY, FontWeight.BOLD, 10));

        lblTotalPackets = new Label("0");
        lblTotalPackets.setStyle("-fx-text-fill: " + MaterialTheme.HEX_TEXT_HIGH + "; -fx-font-weight: bold; -fx-font-size: 14px;");
        colPakete.getChildren().addAll(lblPaketeHdr, lblTotalPackets);

        outputLabels.getChildren().addAll(colOutRate, colPakete);

        chartPps = new MetricHistoryChart("ART-NET (PKT/S)", MaterialTheme.COLOR_ACCENT_GREEN, 45.0);
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
        VBox boxAudio = createControlBox("AUDIO QUELLE", "Eingangs-Audioquelle",
            "Wählt das Aufnahmegerät:\n\n• Mikrofon / Line-In\n• Virtuelles Audiokabel (CABLE Output)\n• Stereo-Mixer für Desktop-Sound");
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

        MaterialButton btnScanAudio = new MaterialButton("Scan", "refresh-cw", 
            MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_TEXT_HIGH, 11, 8, 4, 11, true, this::scanAudioDevices);
        rowAudio.getChildren().addAll(cbAudioDevice, btnScanAudio);
        boxAudio.getChildren().add(rowAudio);

        // 2. Art-Net Ziel-IP
        VBox boxIp = createControlBox("ZIEL-IP", "Art-Net Empfänger IP-Adresse",
            "Zieladresse der Art-Net Pakete:\n\n• 127.0.0.1: Lokaler Empfang (QLC+, Resolume, GrandMA onPC)\n• 255.255.255.255: Netzwerk-Broadcast\n• Spezifische IP: z. B. 192.168.1.50");
        boxIp.setMinWidth(125);
        txtTargetIp = new TextField(config.getTargetIp() != null ? config.getTargetIp() : "127.0.0.1");
        txtTargetIp.setMaxWidth(Double.MAX_VALUE);
        txtTargetIp.textProperty().addListener((obs, o, n) -> autoSaveConfig());
        boxIp.getChildren().add(txtTargetIp);

        // 3. Universum
        VBox boxUni = createControlBox("UNIVERSUM", "Art-Net DMX Universum",
            "Das Art-Net SubUni/Universum (0 - 15):\n\n• 0: Erstes Standard-Universum\n• Muss mit der Zielsoftware / dem DMX-Node übereinstimmen");
        boxUni.setMinWidth(95);
        spUniverse = new Spinner<>(0, 15, config.getUniverse());
        spUniverse.setEditable(true);
        spUniverse.setMaxWidth(Double.MAX_VALUE);
        spUniverse.valueProperty().addListener((obs, o, n) -> autoSaveConfig());
        boxUni.getChildren().add(spUniverse);

        // 4. Ziel-FPS
        VBox boxFps = createControlBox("FPS", "Art-Net Bildrate (Hz)",
            "Sendefrequenz der Art-Net Pakete:\n\n• 40 Hz: Empfohlener Standard für flüssige Moving-Head-Fahrten\n• 20 - 44 Hz: Konform zu DMX512-Timing");
        boxFps.setMinWidth(85);
        spFps = new Spinner<>(10, 44, config.getFps());
        spFps.setEditable(true);
        spFps.setMaxWidth(Double.MAX_VALUE);
        spFps.valueProperty().addListener((obs, o, n) -> autoSaveConfig());
        boxFps.getChildren().add(spFps);

        // 5. Gain / AGC
        VBox boxGain = createControlBox("GAIN / AGC", "Audio-Verstärkung & Normalisierung",
            "Empfindlichkeitsregelung:\n\n• Gain-Regler: Manuelle Vorverstärkung (0.5x bis 5x)\n• AGC: Auto-Gain-Control passt Pegel automatisch an leise/laute Musik an");
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
        VBox boxAction = createControlBox("AKTION", "Sound2ArtNet Aktivierung",
            "Startet oder stoppt die Audio-Erfassung und das Art-Net Senden.");
        boxAction.setMinWidth(110);
        btnAction = new MaterialButton("Start", "play", 
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
        Tab tabPatches = new Tab("Moving Heads & Fixture Patch");
        tabPatches.setClosable(false);
        tabPatches.setContent(buildPatchView());

        // Tab 2: Sound-to-Light & Movement Engine
        Tab tabEngine = new Tab("Sound-to-Light & Bewegungssteuerung");
        tabEngine.setClosable(false);
        tabEngine.setContent(buildEngineControlView());

        tabPane.getTabs().addAll(tabPatches, tabEngine);
        return tabPane;
    }

    @SuppressWarnings("unchecked")
    private Node buildPatchView() {
        VBox root = new VBox(8);
        root.setPadding(new Insets(10));

        tablePatches = new TableView<>(patchList);
        tablePatches.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tablePatches, Priority.ALWAYS);

        TableColumn<FixturePatch, Boolean> colActive = new TableColumn<>("Aktiv");
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

        TableColumn<FixturePatch, String> colName = new TableColumn<>("Name des Scheinwerfers");
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colName.setMinWidth(140);

        TableColumn<FixturePatch, String> colProfile = new TableColumn<>("Profil / Modell");
        colProfile.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProfile() != null ? data.getValue().getProfile().getName() : ""));
        colProfile.setMinWidth(150);

        TableColumn<FixturePatch, String> colDmx = new TableColumn<>("DMX Adressen");
        colDmx.setCellValueFactory(data -> new SimpleStringProperty("DMX " + data.getValue().getStartAddress() + " - " + data.getValue().getEndAddress()));
        colDmx.setMaxWidth(120);
        colDmx.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        TableColumn<FixturePatch, String> colLimits = new TableColumn<>("Pan/Tilt Invert & Limits");
        colLimits.setCellValueFactory(data -> {
            FixturePatch p = data.getValue();
            String inv = (p.isInvertPan() ? "Pan-Inv " : "") + (p.isInvertTilt() ? "Tilt-Inv" : "");
            return new SimpleStringProperty(inv.isBlank() ? "Standard" : inv.trim());
        });
        colLimits.setMaxWidth(140);
        colLimits.setStyle("-fx-alignment: CENTER;");

        TableColumn<FixturePatch, String> colPhase = new TableColumn<>("Phasenversatz");
        colPhase.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.0f°", data.getValue().getPhaseOffset() * 180.0 / Math.PI)));
        colPhase.setMaxWidth(100);
        colPhase.setStyle("-fx-alignment: CENTER;");

        tablePatches.getColumns().addAll(colActive, colName, colProfile, colDmx, colLimits, colPhase);

        // Action Buttons Row
        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        MaterialButton btnAdd = new MaterialButton("+ Neues Fixture", "plus", 
            MaterialTheme.COLOR_PRIMARY, MaterialTheme.COLOR_ON_PRIMARY, 12, 10, 4, 11, true, this::openNewFixtureDialog);

        MaterialButton btnEdit = new MaterialButton("Bearbeiten", "edit-3", 
            MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_TEXT_HIGH, 12, 10, 4, 11, false, this::openEditFixtureDialog);

        MaterialButton btnDelete = new MaterialButton("Löschen", "trash-2", 
            MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_ERROR, 12, 10, 4, 11, false, this::deleteSelectedFixture);

        MaterialButton btnImportQlc = new MaterialButton("QLC+ Import", "folder-open", 
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
        Label lblActive = new Label("AKTIVE STEUERUNG:");
        lblActive.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblActive.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        HBox activeBox = new HBox(20);
        activeBox.setAlignment(Pos.CENTER_LEFT);
        chkMovementEnabled = new CheckBox("Bewegung");
        chkMovementEnabled.setSelected(showEngine.isMovementEnabled());
        chkMovementEnabled.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);

        chkLightEnabled = new CheckBox("Licht");
        chkLightEnabled.setSelected(showEngine.isLightEnabled());
        chkLightEnabled.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);

        chkStrobeEnabled = new CheckBox("Strobo");
        chkStrobeEnabled.setSelected(showEngine.isStrobeEnabled());
        chkStrobeEnabled.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        activeBox.getChildren().addAll(chkMovementEnabled, chkLightEnabled, chkStrobeEnabled);

        // 1. Erkennungs-Modus (Level-Detect / Beat-Detect)
        Label lblDetectMode = new Label("ERKENNUNGS-MODUS:");
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
        Label lblBeatSens = new Label("BEAT-EMPFINDLICHKEIT:");
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
        Label lblPattern = new Label("BEWEGUNGSMUSTER:");
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
        Label lblSpeed = new Label("GESCHWINDIGKEIT:");
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
        Label lblAmp = new Label("AUSLENKUNG / WEITE:");
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

        // 6. Dimmer-Modus
        Label lblDimmer = new Label("DIMMER-REAKTION:");
        lblDimmer.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblDimmer.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        cbDimmerMode = new ComboBox<>(FXCollections.observableArrayList(Sound2LightEngine.DimmerMode.values()));
        cbDimmerMode.setValue(showEngine.getDimmerMode());
        cbDimmerMode.setMaxWidth(Double.MAX_VALUE);
        cbDimmerMode.setOnAction(e -> {
            showEngine.setDimmerMode(cbDimmerMode.getValue());
            autoSaveConfig();
        });

        // 7. Farbpalette
        Label lblPal = new Label("FARBPALETTE:");
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
            cbPalette.setDisable(!lgt);
            chkStrobeEnabled.setDisable(!lgt);
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
        grid.add(cbDimmerMode, 1, 6);
        grid.add(lblPal, 0, 7);
        grid.add(cbPalette, 1, 7);

        ColumnConstraints cc0 = new ColumnConstraints(160);
        ColumnConstraints cc1 = new ColumnConstraints(300, 400, Double.MAX_VALUE);
        cc1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(cc0, cc1);

        root.getChildren().add(grid);
        return root;
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

        statusLabel = new Label("Bereit. Klicken Sie auf Start um Audio-Erfassung und Art-Net zu aktivieren.");
        statusLabel.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        statusLabel.setFont(Font.font("Segoe UI", 11));

        bar.getChildren().add(statusLabel);
        getChildren().add(bar);
    }

    private VBox createControlBox(String title, String tooltipTitle, String tooltipText) {
        VBox box = new VBox(5);
        box.setPadding(new Insets(6, 10, 8, 10));
        box.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_2DP + 
                     "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + 
                     "; -fx-border-width: 1px; -fx-background-radius: 4px; -fx-border-radius: 4px;");

        HBox hdr = new HBox();
        hdr.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(title);
        lbl.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HelpBadge help = new HelpBadge(tooltipTitle, tooltipText);
        hdr.getChildren().addAll(lbl, spacer, help);

        box.getChildren().add(hdr);
        return box;
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
            btnAction.updateColors(MaterialTheme.COLOR_ERROR, MaterialTheme.COLOR_ON_ERROR, "square", "Stop");
            chipIcon.setIcon("circle", MaterialTheme.COLOR_PRIMARY);
            chipLabel.setText("AKTIV");
            chipLabel.setTextFill(MaterialTheme.COLOR_PRIMARY);
            statusLabel.setText("Aktiv: Sende Art-Net an " + ip + " (Univ: " + universe + ") | Audio: " + audioService.getCurrentDeviceName());

            saveStateToConfig();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Starten von sound2artnet: " + e.getMessage(), e);
            statusLabel.setText("Fehler beim Starten: " + e.getMessage());
            stopService();
        }
    }

    public synchronized void stopService() {
        isRunning = false;
        audioService.stop();
        artNetSender.stop();

        btnAction.updateColors(MaterialTheme.COLOR_PRIMARY, MaterialTheme.COLOR_ON_PRIMARY, "play", "Start");
        chipIcon.setIcon("circle", MaterialTheme.COLOR_TEXT_MED);
        chipLabel.setText("BEREIT");
        chipLabel.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        statusLabel.setText("Gestoppt. Klicken Sie auf Start um die Übertragung fortzusetzen.");

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
        chooser.setTitle("QLC+ Fixture Definition (*.qxf) auswählen");
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
                    statusLabel.setText("QLC+ Fixture importiert: " + newPatch.getName() + " an DMX " + newPatch.getStartAddress());
                });
                dlg.show();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Fehler beim Parsen der QLC+ Datei: " + e.getMessage(), e);
                statusLabel.setText("Fehler beim Importieren: " + e.getMessage());
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
        config.setColorPalette(cbPalette.getValue());
        config.setFixtures(new ArrayList<>(patchList));
        ConfigManager.saveConfig(config);
    }

    public AppConfig getConfig() {
        return config;
    }
}

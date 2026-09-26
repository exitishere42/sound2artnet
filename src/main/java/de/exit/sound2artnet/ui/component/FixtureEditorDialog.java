package de.exit.sound2artnet.ui.component;

import de.exit.sound2artnet.fixture.ChannelFunction;
import de.exit.sound2artnet.fixture.ChannelMapping;
import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.fixture.FixtureProfile;
import de.exit.sound2artnet.ui.MaterialTheme;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interaktiver Dialog zum Erstellen und Bearbeiten von Fixture-Profilen und Kanalbelegungen.
 */
public class FixtureEditorDialog extends Stage {
    private final FixturePatch patch;
    private final boolean isEditMode;
    private final Consumer<FixturePatch> onSave;

    private TextField txtName;
    private Spinner<Integer> spQuantity;
    private Spinner<Integer> spStartAddr;
    private ComboBox<FixtureProfile> cbPresets;
    private CheckBox chkInvertPan;
    private CheckBox chkInvertTilt;
    private Slider slPanMin;
    private Slider slPanMax;
    private Slider slTiltMin;
    private Slider slTiltMax;
    private Slider slPhase;

    private final ObservableList<ChannelMapping> channelData = FXCollections.observableArrayList();
    private TableView<ChannelMapping> tableChannels;

    public FixtureEditorDialog(Stage owner, FixturePatch existingPatch, Consumer<FixturePatch> onSave) {
        this(owner, existingPatch, 1, onSave);
    }

    public FixtureEditorDialog(Stage owner, FixturePatch existingPatch, int suggestedStartAddr, Consumer<FixturePatch> onSave) {
        this.isEditMode = (existingPatch != null);
        int startAddr = Math.max(1, Math.min(512, suggestedStartAddr));
        this.patch = isEditMode ? existingPatch : new FixturePatch("Neuer Moving Head", startAddr, FixtureLibrary.createGeneric9chSpot());
        this.onSave = onSave;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(isEditMode ? "Fixture bearbeiten: " + existingPatch.getName() : "Neues Fixture patchen");
        setResizable(true);

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: " + MaterialTheme.HEX_BG + ";");

        buildHeader(root);
        buildPresetBar(root);
        buildChannelTable(root);
        buildLimitsSection(root);
        buildButtonBar(root);

        loadPatchData();

        Scene scene = new Scene(root, 650, 680);
        try {
            scene.getStylesheets().add(getClass().getResource("/styles/material-dark.css").toExternalForm());
        } catch (Exception ignored) {}

        setScene(scene);
    }

    private void buildHeader(VBox root) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox boxName = new VBox(4);
        HBox.setHgrow(boxName, Priority.ALWAYS);
        Label lblName = new Label("NAME DES GERAETS");
        lblName.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        txtName = new TextField(patch.getName());
        boxName.getChildren().addAll(lblName, txtName);

        row.getChildren().add(boxName);

        if (!isEditMode) {
            VBox boxQty = new VBox(4);
            boxQty.setMinWidth(90);
            boxQty.setMaxWidth(100);
            Label lblQty = new Label("ANZAHL");
            lblQty.setTextFill(MaterialTheme.COLOR_TEXT_MED);
            lblQty.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            spQuantity = new Spinner<>(1, 32, 1);
            spQuantity.setEditable(true);
            spQuantity.setMaxWidth(Double.MAX_VALUE);
            boxQty.getChildren().addAll(lblQty, spQuantity);
            row.getChildren().add(boxQty);
        }

        VBox boxAddr = new VBox(4);
        boxAddr.setMinWidth(110);
        Label lblAddr = new Label("DMX START");
        lblAddr.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblAddr.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        spStartAddr = new Spinner<>(1, 512, patch.getStartAddress());
        spStartAddr.setEditable(true);
        boxAddr.getChildren().addAll(lblAddr, spStartAddr);

        row.getChildren().add(boxAddr);
        root.getChildren().add(row);
    }

    private void buildPresetBar(VBox root) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 10, 6, 10));
        row.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_1DP + "; -fx-background-radius: 4px; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + "; -fx-border-radius: 4px;");

        Label lbl = new Label("PROFIL-VORLAGE:");
        lbl.setTextFill(MaterialTheme.COLOR_PRIMARY);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        cbPresets = new ComboBox<>();
        cbPresets.getItems().addAll(FixtureLibrary.getDefaultProfiles());
        cbPresets.setPromptText("Vorlage wählen...");
        HBox.setHgrow(cbPresets, Priority.ALWAYS);

        MaterialButton btnApply = new MaterialButton("Laden", "refresh-cw", MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_TEXT_HIGH, 11, 8, 4, 11, false, () -> {
            FixtureProfile sel = cbPresets.getValue();
            if (sel != null) {
                channelData.clear();
                for (ChannelMapping cm : sel.getChannels()) {
                    channelData.add(new ChannelMapping(cm.getOffset(), cm.getFunction(), cm.getDefaultValue()));
                }
            }
        });

        MaterialButton btnImportQlc = new MaterialButton("QLC+ Import", "folder-open", 
            Color.web("#00E5FF"), Color.web("#000000"), 11, 8, 4, 11, false, this::importQlcFile);

        MaterialButton btnAddCh = new MaterialButton("+ Kanal", "plus", MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_TEXT_HIGH, 11, 8, 4, 11, false, () -> {
            int newOff = channelData.size();
            channelData.add(new ChannelMapping(newOff, ChannelFunction.UNUSED, 0));
        });

        MaterialButton btnDelCh = new MaterialButton("- Kanal", "trash-2", MaterialTheme.COLOR_SURFACE_4DP, MaterialTheme.COLOR_ERROR, 11, 8, 4, 11, false, () -> {
            if (!channelData.isEmpty()) {
                channelData.remove(channelData.size() - 1);
            }
        });

        row.getChildren().addAll(lbl, cbPresets, btnApply, btnImportQlc, btnAddCh, btnDelCh);
        root.getChildren().add(row);
    }

    private void importQlcFile() {
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

        File file = chooser.showOpenDialog(this);
        if (file != null) {
            try {
                var def = de.exit.sound2artnet.fixture.qlc.QlcFixtureParser.parse(file);
                txtName.setText(def.getManufacturer() + " " + def.getModel());
                var prof = def.toFixtureProfile(null);
                channelData.clear();
                for (ChannelMapping cm : prof.getChannels()) {
                    channelData.add(new ChannelMapping(cm.getOffset(), cm.getFunction(), cm.getDefaultValue()));
                }
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler beim Laden der QLC+ Datei: " + e.getMessage(), ButtonType.OK);
                alert.initOwner(this);
                alert.showAndWait();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void buildChannelTable(VBox root) {
        VBox box = new VBox(6);
        VBox.setVgrow(box, Priority.ALWAYS);

        Label lbl = new Label("KANALBELEGUNG");
        lbl.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        tableChannels = new TableView<>(channelData);
        tableChannels.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tableChannels, Priority.ALWAYS);

        TableColumn<ChannelMapping, Number> colOff = new TableColumn<>("Offset");
        colOff.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getOffset()));
        colOff.setMinWidth(60);
        colOff.setMaxWidth(80);
        colOff.setStyle("-fx-alignment: CENTER;");

        TableColumn<ChannelMapping, String> colDmx = new TableColumn<>("DMX Adr");
        colDmx.setCellValueFactory(data -> {
            int start = spStartAddr.getValue();
            return new SimpleStringProperty("Ch " + (start + data.getValue().getOffset()));
        });
        colDmx.setMinWidth(75);
        colDmx.setMaxWidth(90);
        colDmx.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        TableColumn<ChannelMapping, ChannelFunction> colFunc = new TableColumn<>("Funktion");
        colFunc.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getFunction()));
        colFunc.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<ChannelFunction> cb = new ComboBox<>(FXCollections.observableArrayList(ChannelFunction.values()));

            {
                cb.setMaxWidth(Double.MAX_VALUE);
                cb.setOnAction(e -> {
                    ChannelMapping item = getTableView().getItems().get(getIndex());
                    item.setFunction(cb.getValue());
                    if ((cb.getValue() == ChannelFunction.PAN || cb.getValue() == ChannelFunction.TILT) && item.getDefaultValue() == 0) {
                        item.setDefaultValue(128);
                        getTableView().refresh();
                    }
                });
            }

            @Override
            protected void updateItem(ChannelFunction item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    cb.setValue(item);
                    setGraphic(cb);
                }
            }
        });
        colFunc.setMinWidth(200);

        TableColumn<ChannelMapping, Number> colDef = new TableColumn<>("Standard-Wert");
        colDef.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getDefaultValue()));
        colDef.setCellFactory(col -> new TableCell<>() {
            private final Spinner<Integer> sp = new Spinner<>(0, 255, 0);

            {
                sp.setEditable(true);
                sp.setMaxWidth(Double.MAX_VALUE);
                sp.valueProperty().addListener((obs, oldV, newV) -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        getTableView().getItems().get(getIndex()).setDefaultValue(newV);
                    }
                });
            }

            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    sp.getValueFactory().setValue(item.intValue());
                    setGraphic(sp);
                }
            }
        });
        colDef.setMinWidth(110);
        colDef.setMaxWidth(130);

        tableChannels.getColumns().addAll(colOff, colDmx, colFunc, colDef);
        box.getChildren().addAll(lbl, tableChannels);
        root.getChildren().add(box);
    }

    private void buildLimitsSection(VBox root) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_1DP + "; -fx-background-radius: 4px; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + "; -fx-border-radius: 4px;");

        Label lbl = new Label("MOVING HEAD SCHUTZGRENZEN & BEWEGUNGSPARAMETER");
        lbl.setTextFill(MaterialTheme.COLOR_PRIMARY);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        HBox rowChecks = new HBox(20);
        rowChecks.setAlignment(Pos.CENTER_LEFT);
        chkInvertPan = new CheckBox("Pan invertieren");
        chkInvertTilt = new CheckBox("Tilt invertieren");
        rowChecks.getChildren().addAll(chkInvertPan, chkInvertTilt);

        // Pan Limits Slider
        HBox rowPan = new HBox(8);
        rowPan.setAlignment(Pos.CENTER_LEFT);
        Label lblPanRange = new Label("Pan Limit:");
        lblPanRange.setMinWidth(80);
        lblPanRange.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblPanRange.setFont(Font.font("Segoe UI", 11));
        slPanMin = new Slider(0, 255, patch.getPanMin());
        slPanMax = new Slider(0, 255, patch.getPanMax());
        HBox.setHgrow(slPanMin, Priority.ALWAYS);
        HBox.setHgrow(slPanMax, Priority.ALWAYS);
        rowPan.getChildren().addAll(lblPanRange, new Label("Min"), slPanMin, new Label("Max"), slPanMax);

        // Tilt Limits Slider
        HBox rowTilt = new HBox(8);
        rowTilt.setAlignment(Pos.CENTER_LEFT);
        Label lblTiltRange = new Label("Tilt Limit:");
        lblTiltRange.setMinWidth(80);
        lblTiltRange.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblTiltRange.setFont(Font.font("Segoe UI", 11));
        slTiltMin = new Slider(0, 255, patch.getTiltMin());
        slTiltMax = new Slider(0, 255, patch.getTiltMax());
        HBox.setHgrow(slTiltMin, Priority.ALWAYS);
        HBox.setHgrow(slTiltMax, Priority.ALWAYS);
        rowTilt.getChildren().addAll(lblTiltRange, new Label("Min"), slTiltMin, new Label("Max"), slTiltMax);

        // Phasenversatz
        HBox rowPhase = new HBox(8);
        rowPhase.setAlignment(Pos.CENTER_LEFT);
        Label lblPhase = new Label("Phasenversatz:");
        lblPhase.setMinWidth(80);
        lblPhase.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblPhase.setFont(Font.font("Segoe UI", 11));
        slPhase = new Slider(0, 360, (patch.getPhaseOffset() * 180.0 / Math.PI));
        HBox.setHgrow(slPhase, Priority.ALWAYS);
        Label lblPhaseVal = new Label(String.format("%.0f°", slPhase.getValue()));
        lblPhaseVal.setMinWidth(40);
        lblPhaseVal.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        slPhase.valueProperty().addListener((obs, o, n) -> lblPhaseVal.setText(String.format("%.0f°", n.doubleValue())));
        rowPhase.getChildren().addAll(lblPhase, slPhase, lblPhaseVal);

        box.getChildren().addAll(lbl, rowChecks, rowPan, rowTilt, rowPhase);
        root.getChildren().add(box);
    }

    private void buildButtonBar(VBox root) {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_RIGHT);

        MaterialButton btnCancel = new MaterialButton("Abbrechen", null, MaterialTheme.COLOR_SURFACE_2DP, MaterialTheme.COLOR_TEXT_MED, 12, 12, 6, 12, false, this::close);
        MaterialButton btnSave = new MaterialButton("Speichern", "play", MaterialTheme.COLOR_PRIMARY, MaterialTheme.COLOR_ON_PRIMARY, 12, 16, 6, 12, true, this::handleSave);

        bar.getChildren().addAll(btnCancel, btnSave);
        root.getChildren().add(bar);
    }

    private void loadPatchData() {
        txtName.setText(patch.getName());
        spStartAddr.getValueFactory().setValue(patch.getStartAddress());
        chkInvertPan.setSelected(patch.isInvertPan());
        chkInvertTilt.setSelected(patch.isInvertTilt());
        slPanMin.setValue(patch.getPanMin());
        slPanMax.setValue(patch.getPanMax());
        slTiltMin.setValue(patch.getTiltMin());
        slTiltMax.setValue(patch.getTiltMax());
        slPhase.setValue(patch.getPhaseOffset() * 180.0 / Math.PI);

        channelData.clear();
        if (patch.getProfile() != null && patch.getProfile().getChannels() != null) {
            for (ChannelMapping cm : patch.getProfile().getChannels()) {
                channelData.add(new ChannelMapping(cm.getOffset(), cm.getFunction(), cm.getDefaultValue()));
            }
        }
    }

    private void handleSave() {
        String baseName = txtName.getText().trim().isEmpty() ? "Moving Head" : txtName.getText().trim();
        int baseStartAddr = readSpinnerValue(spStartAddr, 1);
        int quantity = isEditMode ? 1 : readSpinnerValue(spQuantity, 1);

        List<ChannelMapping> updatedChannels = new ArrayList<>(channelData);
        for (int i = 0; i < updatedChannels.size(); i++) {
            updatedChannels.get(i).setOffset(i);
        }

        FixtureProfile prof = new FixtureProfile(
            patch.getProfile() != null ? patch.getProfile().getId() : "custom",
            baseName + " Profil",
            updatedChannels.size(),
            updatedChannels
        );

        int chCount = Math.max(1, updatedChannels.size());
        for (int i = 0; i < quantity; i++) {
            int addr = baseStartAddr + (i * chCount);
            if (addr > 512) {
                break;
            }
            String instanceName = (quantity > 1) ? (baseName + " " + (i + 1)) : baseName;
            FixturePatch target = (i == 0) ? patch : new FixturePatch(instanceName, addr, prof.copy());
            target.setName(instanceName);
            target.setStartAddress(addr);
            target.setInvertPan(chkInvertPan.isSelected());
            target.setInvertTilt(chkInvertTilt.isSelected());
            target.setPanMin((int) Math.round(slPanMin.getValue()));
            target.setPanMax((int) Math.round(slPanMax.getValue()));
            target.setTiltMin((int) Math.round(slTiltMin.getValue()));
            target.setTiltMax((int) Math.round(slTiltMax.getValue()));
            target.setPhaseOffset(slPhase.getValue() * Math.PI / 180.0);
            target.setProfile(prof.copy());

            if (onSave != null) {
                onSave.accept(target);
            }
        }
        close();
    }

    private static int readSpinnerValue(Spinner<Integer> spinner, int fallback) {
        if (spinner == null) return fallback;
        try {
            String text = spinner.getEditor().getText().trim();
            if (!text.isEmpty()) {
                int val = Integer.parseInt(text);
                SpinnerValueFactory<Integer> vf = spinner.getValueFactory();
                if (vf instanceof SpinnerValueFactory.IntegerSpinnerValueFactory ivf) {
                    val = Math.max(ivf.getMin(), Math.min(ivf.getMax(), val));
                }
                vf.setValue(val);
                return val;
            }
        } catch (NumberFormatException ignored) {}
        return spinner.getValue() != null ? spinner.getValue() : fallback;
    }
}

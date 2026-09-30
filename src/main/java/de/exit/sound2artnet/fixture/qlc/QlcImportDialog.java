package de.exit.sound2artnet.fixture.qlc;

import de.exit.sound2artnet.fixture.ChannelFunction;
import de.exit.sound2artnet.fixture.ChannelMapping;
import de.exit.sound2artnet.fixture.FixturePatch;
import de.exit.sound2artnet.fixture.FixtureProfile;
import de.exit.sound2artnet.ui.MaterialTheme;
import de.exit.sound2artnet.ui.component.MaterialButton;
import de.exit.sound2artnet.util.I18n;
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
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Vorschau- und Importdialog für QLC+ Gerätedefinitionen (*.qxf).
 */
public class QlcImportDialog extends Stage {
    private final QlcFixtureDefinition definition;
    private final Consumer<FixturePatch> onImport;

    private TextField txtName;
    private Spinner<Integer> spQuantity;
    private Spinner<Integer> spUniverse;
    private Spinner<Integer> spStartAddr;
    private ComboBox<QlcFixtureDefinition.QlcMode> cbMode;
    private TableView<ChannelPreviewRow> tableChannels;
    private final ObservableList<ChannelPreviewRow> previewRows = FXCollections.observableArrayList();

    public static class ChannelPreviewRow {
        private final int index;
        private final String originalName;
        private ChannelFunction function;
        private int defaultValue;

        public ChannelPreviewRow(int index, String originalName, ChannelFunction function, int defaultValue) {
            this.index = index;
            this.originalName = originalName;
            this.function = function;
            this.defaultValue = defaultValue;
        }

        public int getIndex() { return index; }
        public String getOriginalName() { return originalName; }
        public ChannelFunction getFunction() { return function; }
        public void setFunction(ChannelFunction function) { this.function = function; }
        public int getDefaultValue() { return defaultValue; }
        public void setDefaultValue(int defaultValue) { this.defaultValue = defaultValue; }
    }

    public QlcImportDialog(Stage owner, QlcFixtureDefinition definition, int suggestedStartAddr, Consumer<FixturePatch> onImport) {
        this(owner, definition, 0, suggestedStartAddr, onImport);
    }

    public QlcImportDialog(Stage owner, QlcFixtureDefinition definition, int suggestedUniverse, int suggestedStartAddr, Consumer<FixturePatch> onImport) {
        this.definition = definition;
        this.onImport = onImport;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(I18n.get("qlc.title", definition.getManufacturer(), definition.getModel()));

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: " + MaterialTheme.HEX_BG + ";");

        buildHeader(root, suggestedUniverse, suggestedStartAddr);
        buildModeSelector(root);
        buildChannelTable(root);
        buildButtonBar(root);

        if (!definition.getModes().isEmpty()) {
            cbMode.setValue(definition.getModes().get(0));
            loadModeChannels(definition.getModes().get(0));
        }

        Scene scene = new Scene(root, 620, 600);
        try {
            scene.getStylesheets().add(getClass().getResource("/styles/material-dark.css").toExternalForm());
        } catch (Exception ignored) {}

        setScene(scene);
    }

    private void buildHeader(VBox root, int suggestedUniverse, int suggestedStartAddr) {
        VBox boxMeta = new VBox(4);
        boxMeta.setPadding(new Insets(8, 12, 8, 12));
        boxMeta.setStyle("-fx-background-color: " + MaterialTheme.HEX_SURFACE_1DP + "; -fx-border-color: " + MaterialTheme.HEX_DIVIDER + "; -fx-border-width: 1px; -fx-background-radius: 4px;");

        Label lblTitle = new Label(I18n.get("qlc.detected"));
        lblTitle.setTextFill(MaterialTheme.COLOR_PRIMARY);
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        Label lblModel = new Label(definition.getManufacturer() + " " + definition.getModel() + " (" + definition.getType() + ")");
        lblModel.setTextFill(MaterialTheme.COLOR_TEXT_HIGH);
        lblModel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        String panTiltText = (definition.getPanMax() > 0)
                ? String.format("Max Pan: %d° | Max Tilt: %d°", definition.getPanMax(), definition.getTiltMax())
                : (definition.getTiltMax() > 0 ? String.format("Max Tilt: %d° (%s)", definition.getTiltMax(), I18n.get("qlc.no_pan")) : I18n.get("qlc.no_pan"));
        Label lblPhysical = new Label(panTiltText);
        lblPhysical.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblPhysical.setFont(Font.font("Segoe UI", 11));

        boxMeta.getChildren().addAll(lblTitle, lblModel, lblPhysical);

        HBox rowInputs = new HBox(12);
        rowInputs.setAlignment(Pos.CENTER_LEFT);

        VBox boxName = new VBox(4);
        HBox.setHgrow(boxName, Priority.ALWAYS);
        Label lblName = new Label(I18n.get("qlc.name"));
        lblName.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        txtName = new TextField(definition.getManufacturer() + " " + definition.getModel());
        boxName.getChildren().addAll(lblName, txtName);

        VBox boxQty = new VBox(4);
        boxQty.setMinWidth(90);
        boxQty.setMaxWidth(100);
        Label lblQty = new Label(I18n.get("qlc.quantity"));
        lblQty.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblQty.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        spQuantity = new Spinner<>(1, 32, 1);
        spQuantity.setEditable(true);
        spQuantity.setMaxWidth(Double.MAX_VALUE);
        boxQty.getChildren().addAll(lblQty, spQuantity);

        VBox boxUni = new VBox(4);
        boxUni.setMinWidth(80);
        boxUni.setMaxWidth(90);
        Label lblUni = new Label(I18n.get("qlc.universe"));
        lblUni.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblUni.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        spUniverse = new Spinner<>(0, 15, Math.max(0, Math.min(15, suggestedUniverse)));
        spUniverse.setEditable(true);
        boxUni.getChildren().addAll(lblUni, spUniverse);

        VBox boxDmx = new VBox(4);
        boxDmx.setMinWidth(110);
        Label lblDmx = new Label(I18n.get("qlc.start_addr"));
        lblDmx.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lblDmx.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        spStartAddr = new Spinner<>(1, 512, Math.max(1, Math.min(512, suggestedStartAddr)));
        spStartAddr.setEditable(true);
        boxDmx.getChildren().addAll(lblDmx, spStartAddr);

        rowInputs.getChildren().addAll(boxName, boxQty, boxUni, boxDmx);
        root.getChildren().addAll(boxMeta, rowInputs);
    }

    private void buildModeSelector(VBox root) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(I18n.get("qlc.mode"));
        lbl.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        cbMode = new ComboBox<>(FXCollections.observableArrayList(definition.getModes()));
        cbMode.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cbMode, Priority.ALWAYS);
        cbMode.setOnAction(e -> {
            QlcFixtureDefinition.QlcMode m = cbMode.getValue();
            if (m != null) {
                loadModeChannels(m);
            }
        });

        row.getChildren().addAll(lbl, cbMode);
        root.getChildren().add(row);
    }

    @SuppressWarnings("unchecked")
    private void buildChannelTable(VBox root) {
        VBox box = new VBox(4);
        VBox.setVgrow(box, Priority.ALWAYS);

        Label lbl = new Label(I18n.get("qlc.mapping_preview"));
        lbl.setTextFill(MaterialTheme.COLOR_TEXT_MED);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        tableChannels = new TableView<>(previewRows);
        tableChannels.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tableChannels, Priority.ALWAYS);

        TableColumn<ChannelPreviewRow, Number> colNum = new TableColumn<>(I18n.get("qlc.col_channel"));
        colNum.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getIndex() + 1));
        colNum.setMaxWidth(60);
        colNum.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        TableColumn<ChannelPreviewRow, String> colOrig = new TableColumn<>(I18n.get("qlc.col_qlc_name"));
        colOrig.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getOriginalName()));
        colOrig.setMinWidth(150);

        TableColumn<ChannelPreviewRow, ChannelFunction> colFunc = new TableColumn<>(I18n.get("qlc.col_function"));
        colFunc.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getFunction()));
        colFunc.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<ChannelFunction> cb = new ComboBox<>(FXCollections.observableArrayList(ChannelFunction.values()));
            {
                cb.setMaxWidth(Double.MAX_VALUE);
                cb.setOnAction(e -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        ChannelPreviewRow row = getTableView().getItems().get(getIndex());
                        row.setFunction(cb.getValue());
                        if ((cb.getValue() == ChannelFunction.PAN || cb.getValue() == ChannelFunction.TILT) && row.getDefaultValue() == 0) {
                            row.setDefaultValue(128);
                            getTableView().refresh();
                        }
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
        colFunc.setMinWidth(180);

        TableColumn<ChannelPreviewRow, Number> colDef = new TableColumn<>(I18n.get("qlc.col_default"));
        colDef.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getDefaultValue()));
        colDef.setMaxWidth(70);
        colDef.setStyle("-fx-alignment: CENTER;");

        tableChannels.getColumns().addAll(colNum, colOrig, colFunc, colDef);
        box.getChildren().addAll(lbl, tableChannels);
        root.getChildren().add(box);
    }

    private void buildButtonBar(VBox root) {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_RIGHT);

        MaterialButton btnCancel = new MaterialButton(I18n.get("btn.cancel"), null, MaterialTheme.COLOR_SURFACE_2DP, MaterialTheme.COLOR_TEXT_MED, 12, 12, 6, 12, false, this::close);
        MaterialButton btnImport = new MaterialButton(I18n.get("btn.import_patch"), "plus", MaterialTheme.COLOR_PRIMARY, MaterialTheme.COLOR_ON_PRIMARY, 12, 16, 6, 12, true, this::handleImport);

        bar.getChildren().addAll(btnCancel, btnImport);
        root.getChildren().add(bar);
    }

    private void loadModeChannels(QlcFixtureDefinition.QlcMode mode) {
        previewRows.clear();
        for (int i = 0; i < mode.getChannelNames().size(); i++) {
            String chName = mode.getChannelNames().get(i);
            QlcFixtureDefinition.QlcChannel ch = definition.getChannels().get(chName);
            ChannelFunction func = (ch != null) ? ch.getResolvedFunction() : ChannelFunction.UNUSED;
            int defVal = (ch != null) ? ch.getDefaultValue() : 0;
            previewRows.add(new ChannelPreviewRow(i, chName, func, defVal));
        }
    }

    private void handleImport() {
        String baseName = txtName.getText().trim().isEmpty() ? definition.getModel() : txtName.getText().trim();
        int baseStartAddr = spStartAddr.getValue();
        int baseUniverse = spUniverse.getValue();
        int quantity = spQuantity.getValue();

        List<ChannelMapping> mappings = new ArrayList<>();
        for (ChannelPreviewRow row : previewRows) {
            mappings.add(new ChannelMapping(row.getIndex(), row.getFunction(), row.getDefaultValue()));
        }

        FixtureProfile prof = new FixtureProfile(
            definition.getManufacturer().toLowerCase() + "-" + definition.getModel().toLowerCase().replace(" ", "-"),
            baseName + I18n.get("editor.profile_suffix"),
            mappings.size(),
            mappings
        );

        int chCount = Math.max(1, mappings.size());
        int currentUni = baseUniverse;
        int currentAddr = baseStartAddr;

        for (int i = 0; i < quantity; i++) {
            if (currentAddr + chCount - 1 > 512) {
                currentUni++;
                currentAddr = 1;
                if (currentUni > 15) break;
            }
            String instanceName = (quantity > 1) ? (baseName + " " + (i + 1)) : baseName;
            FixturePatch patch = new FixturePatch(instanceName, currentAddr, prof.copy());
            patch.setUniverse(currentUni);

            if (definition.getPanMax() > 0) {
                patch.setPanMax(255);
            } else {
                patch.setPanMax(0);
                patch.setPanMin(0);
                patch.setInvertPan(false);
            }
            if (definition.getTiltMax() > 0) {
                patch.setTiltMax(255);
            } else {
                patch.setTiltMax(0);
                patch.setTiltMin(0);
                patch.setInvertTilt(false);
            }

            if (onImport != null) {
                onImport.accept(patch);
            }
            currentAddr += chCount;
        }

        close();
    }
}

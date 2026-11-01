package de.exit.sound2artnet;

import de.exit.sound2artnet.fixture.FixtureLibrary;
import de.exit.sound2artnet.fixture.FixturePatch;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class FixtureTableMultiSelectionTest {

    @BeforeAll
    static void initJFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testMultipleSelectionAndBatchDeletion() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                ObservableList<FixturePatch> patchList = FXCollections.observableArrayList();
                FixturePatch f1 = new FixturePatch("Spot 1", 0, 1, FixtureLibrary.createGeneric9chSpot());
                FixturePatch f2 = new FixturePatch("Wash 1", 0, 10, FixtureLibrary.createGeneric9chWash());
                FixturePatch f3 = new FixturePatch("Spot 2", 0, 19, FixtureLibrary.createGeneric9chSpot());
                patchList.addAll(f1, f2, f3);

                TableView<FixturePatch> table = new TableView<>(patchList);
                table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

                assertEquals(SelectionMode.MULTIPLE, table.getSelectionModel().getSelectionMode());

                // Select f1 and f3 (indices 0 and 2) simulating Ctrl + Click
                table.getSelectionModel().selectIndices(0, 2);

                assertEquals(2, table.getSelectionModel().getSelectedItems().size());
                assertTrue(table.getSelectionModel().getSelectedItems().contains(f1));
                assertTrue(table.getSelectionModel().getSelectedItems().contains(f3));
                assertFalse(table.getSelectionModel().getSelectedItems().contains(f2));

                // Perform batch delete
                var selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
                patchList.removeAll(selected);
                table.getSelectionModel().clearSelection();

                assertEquals(1, patchList.size());
                assertEquals("Wash 1", patchList.get(0).getName());
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testBatchToggleEnable() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                ObservableList<FixturePatch> patchList = FXCollections.observableArrayList();
                FixturePatch f1 = new FixturePatch("Spot 1", 0, 1, FixtureLibrary.createGeneric9chSpot());
                FixturePatch f2 = new FixturePatch("Wash 1", 0, 10, FixtureLibrary.createGeneric9chWash());
                f1.setEnabled(false);
                f2.setEnabled(true);
                patchList.addAll(f1, f2);

                TableView<FixturePatch> table = new TableView<>(patchList);
                table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                table.getSelectionModel().selectAll();

                var selected = table.getSelectionModel().getSelectedItems();
                assertEquals(2, selected.size());

                // If any is disabled, enable all
                boolean anyDisabled = selected.stream().anyMatch(f -> !f.isEnabled());
                assertTrue(anyDisabled);
                for (FixturePatch p : selected) {
                    p.setEnabled(anyDisabled);
                }

                assertTrue(f1.isEnabled());
                assertTrue(f2.isEnabled());

                // If none disabled, disable all
                anyDisabled = selected.stream().anyMatch(f -> !f.isEnabled());
                assertFalse(anyDisabled);
                for (FixturePatch p : selected) {
                    p.setEnabled(anyDisabled);
                }

                assertFalse(f1.isEnabled());
                assertFalse(f2.isEnabled());
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}

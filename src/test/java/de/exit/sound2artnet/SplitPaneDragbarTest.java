package de.exit.sound2artnet;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class SplitPaneDragbarTest {

    @BeforeAll
    static void initJFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // Bereits initialisiert
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testSplitPaneDividerHasGrabberWithStyle() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean grabberFound = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                SplitPane splitPane = new SplitPane();
                splitPane.setOrientation(Orientation.VERTICAL);
                splitPane.getItems().addAll(new Region(), new Region());

                Scene scene = new Scene(splitPane, 400, 400);
                scene.getStylesheets().add(getClass().getResource("/styles/material-dark.css").toExternalForm());

                splitPane.applyCss();
                splitPane.layout();

                for (Node divider : splitPane.lookupAll(".split-pane-divider")) {
                    if (divider instanceof Parent p) {
                        for (Node child : p.getChildrenUnmodifiable()) {
                            if (child.getStyleClass().contains("vertical-grabber") || child.getStyleClass().contains("grabber")) {
                                grabberFound.set(true);
                            }
                        }
                    }
                }
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(grabberFound.get(), "vertical-grabber / grabber must be present inside SplitPaneDivider");
    }

    @Test
    void testMainWindowDragbarRestrictsDraggingOutsideGrabber() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean testPassed = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                de.exit.sound2artnet.ui.MainWindow window = new de.exit.sound2artnet.ui.MainWindow();
                Scene scene = new Scene(window, 800, 600);
                scene.getStylesheets().add(getClass().getResource("/styles/material-dark.css").toExternalForm());

                window.applyCss();
                window.layout();

                Platform.runLater(() -> {
                    try {
                        // Finde das SplitPane in MainWindow
                        SplitPane sp = null;
                        for (Node n : window.lookupAll(".split-pane")) {
                            if (n instanceof SplitPane s) {
                                sp = s;
                                break;
                            }
                        }
                        assertNotNull(sp, "SplitPane muss in MainWindow vorhanden sein");
                        sp.applyCss();
                        sp.layout();

                        var dividers = sp.lookupAll(".split-pane-divider");
                        assertFalse(dividers.isEmpty(), "SplitPaneDivider müssen vorhanden sein");

                        for (Node dividerNode : dividers) {
                            AtomicBoolean handlerInvoked = new AtomicBoolean(false);
                            javafx.event.EventHandler<MouseEvent> testHandler = e -> handlerInvoked.set(true);
                            dividerNode.addEventHandler(MouseEvent.MOUSE_PRESSED, testHandler);

                            // Test 1: Simulierter Klick weit links von der Mitte (x=20) -> muss blockiert (consumed) werden
                            handlerInvoked.set(false);
                            MouseEvent eventOutside = new MouseEvent(
                                    MouseEvent.MOUSE_PRESSED,
                                    20, 2, 20, 2,
                                    MouseButton.PRIMARY, 1,
                                    false, false, false, false,
                                    true, false, false, false, false, false, null
                            );
                            javafx.event.Event.fireEvent(dividerNode, eventOutside);
                            assertFalse(handlerInvoked.get(),
                                    "EventHandler darf bei Klick außerhalb der Dragbar nicht aufgerufen werden!");

                            // Test 2: Simulierter Klick in der Mitte (x=400) -> darf nicht blockiert werden
                            handlerInvoked.set(false);
                            MouseEvent eventInside = new MouseEvent(
                                    MouseEvent.MOUSE_PRESSED,
                                    400, 2, 400, 2,
                                    MouseButton.PRIMARY, 1,
                                    false, false, false, false,
                                    true, false, false, false, false, false, null
                            );
                            javafx.event.Event.fireEvent(dividerNode, eventInside);
                            assertTrue(handlerInvoked.get(),
                                    "EventHandler muss bei Klick auf der Dragbar aufgerufen werden!");

                            dividerNode.removeEventHandler(MouseEvent.MOUSE_PRESSED, testHandler);
                        }
                        testPassed.set(true);
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (Exception e) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(testPassed.get());
    }
}

package de.exit.sound2artnet.fixture.qlc;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parst QLC+ Fixture Definition XML-Dateien (*.qxf).
 */
public final class QlcFixtureParser {
    private static final Logger LOGGER = Logger.getLogger(QlcFixtureParser.class.getName());

    private QlcFixtureParser() {}

    public static QlcFixtureDefinition parse(File file) throws Exception {
        try (InputStream in = new FileInputStream(file)) {
            return parse(in);
        }
    }

    public static QlcFixtureDefinition parse(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Externe DTD Validierung deaktivieren, da QLC+ Dateien <!DOCTYPE FixtureDefinition> ohne URL enthalten
        try {
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/validation", false);
        } catch (Exception ignored) {}

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

        Document doc = builder.parse(inputStream);
        doc.getDocumentElement().normalize();

        QlcFixtureDefinition def = new QlcFixtureDefinition();

        // 1. Metadaten (Manufacturer, Model, Type)
        def.setManufacturer(getTagText(doc.getDocumentElement(), "Manufacturer", "Unbekannt"));
        def.setModel(getTagText(doc.getDocumentElement(), "Model", "QLC+ Fixture"));
        def.setType(getTagText(doc.getDocumentElement(), "Type", "Moving Head"));

        // 2. Physical Focus (PanMax, TiltMax)
        NodeList focusList = doc.getElementsByTagName("Focus");
        if (focusList.getLength() > 0) {
            Element focusEl = (Element) focusList.item(0);
            if (focusEl.hasAttribute("PanMax")) {
                try { def.setPanMax(Integer.parseInt(focusEl.getAttribute("PanMax"))); } catch (Exception ignored) {}
            }
            if (focusEl.hasAttribute("TiltMax")) {
                try { def.setTiltMax(Integer.parseInt(focusEl.getAttribute("TiltMax"))); } catch (Exception ignored) {}
            }
        }

        // 3. Kanäle erfassen: <Channel Name="..." Preset="..." Default="...">
        NodeList channelNodes = doc.getElementsByTagName("Channel");
        for (int i = 0; i < channelNodes.getLength(); i++) {
            Node n = channelNodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element chEl = (Element) n;

            // Prüfen, ob es eine Kanaldefinition (unter Root) oder eine Kanalreferenz (unter Mode) ist
            if (chEl.getParentNode() != doc.getDocumentElement()) {
                continue; // Mode-Unterelement wird später separat ausgewertet
            }

            String chName = chEl.getAttribute("Name");
            if (chName == null || chName.isBlank()) continue;

            String preset = chEl.getAttribute("Preset");
            int defVal = 0;
            if (chEl.hasAttribute("Default")) {
                try { defVal = Integer.parseInt(chEl.getAttribute("Default")); } catch (Exception ignored) {}
            }

            String group = "";
            int byteIndex = 0;
            NodeList groupNodes = chEl.getElementsByTagName("Group");
            if (groupNodes.getLength() > 0) {
                Element gEl = (Element) groupNodes.item(0);
                group = gEl.getTextContent().trim();
                if (gEl.hasAttribute("Byte")) {
                    try { byteIndex = Integer.parseInt(gEl.getAttribute("Byte")); } catch (Exception ignored) {}
                }
            }

            QlcFixtureDefinition.QlcChannel channel = new QlcFixtureDefinition.QlcChannel(chName, preset, group, byteIndex, defVal);
            def.getChannels().put(chName, channel);
        }

        // 4. Modi erfassen: <Mode Name="...">
        NodeList modeNodes = doc.getElementsByTagName("Mode");
        for (int m = 0; m < modeNodes.getLength(); m++) {
            Node mn = modeNodes.item(m);
            if (mn.getNodeType() != Node.ELEMENT_NODE) continue;
            Element modeEl = (Element) mn;

            String modeName = modeEl.getAttribute("Name");
            if (modeName == null || modeName.isBlank()) {
                modeName = "Modus " + (m + 1);
            }

            QlcFixtureDefinition.QlcMode mode = new QlcFixtureDefinition.QlcMode(modeName);

            NodeList modeChannelNodes = modeEl.getElementsByTagName("Channel");
            // Kanäle nach ihrer Number sortiert hinzufügen
            String[] sortedChannels = new String[modeChannelNodes.getLength()];
            for (int c = 0; c < modeChannelNodes.getLength(); c++) {
                Element mcEl = (Element) modeChannelNodes.item(c);
                int num = c;
                if (mcEl.hasAttribute("Number")) {
                    try { num = Integer.parseInt(mcEl.getAttribute("Number")); } catch (Exception ignored) {}
                }
                String referencedName = mcEl.getTextContent().trim();
                if (num >= 0 && num < sortedChannels.length) {
                    sortedChannels[num] = referencedName;
                }
            }

            for (String ch : sortedChannels) {
                if (ch != null && !ch.isBlank()) {
                    mode.getChannelNames().add(ch);
                }
            }

            def.getModes().add(mode);
        }

        LOGGER.info("QLC+ Fixture importiert: " + def);
        return def;
    }

    private static String getTagText(Element root, String tagName, String fallback) {
        NodeList nl = root.getElementsByTagName(tagName);
        if (nl.getLength() > 0) {
            String txt = nl.item(0).getTextContent();
            if (txt != null && !txt.isBlank()) {
                return txt.trim();
            }
        }
        return fallback;
    }
}

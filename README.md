# sound2artnet

**sound2artnet** ist eine Echtzeit-Sound-to-Light-Steuerung und ein Moving-Head-Effektgenerator für Art-Net (DMX512) auf Basis von **Java 21 LTS** und **JavaFX 21**.

Die Anwendung analysiert das laufende Musiksignal in Echtzeit, erkennt Kicks sowie das aktuelle Tempo (BPM) und übersetzt die Musik vollautomatisch in synchrone Bewegungen, Farbwechsel, Dimmer-Pulse und Stroboskop-Effekte für Moving Heads und DMX-Scheinwerfer.

Optisch und im Aufbau besitzt **sound2artnet** exakt das gleiche dunkle Material-Design-Interface wie **artnet2dmx** und fügt sich damit nahtlos als passendes Gegenstück in das bestehende Lichtsteuerungs-Setup ein.

---

## Funktionsübersicht

### 1. Echtzeit-Audioanalyse & Beat-/BPM-Erkennung
- **Direkte Systemaudio-Erfassung**: Nimmt unter Windows über `PC-Sound` (WASAPI Loopback) direkt den ausgegebenen Systemsound auf – ohne virtuelle Audiokabel oder zusätzliche Treiber. Alternativ können beliebige Mikrofone und Audio-Interfaces gewählt werden.
- **8-Band-Spektrumanalysator**: Echtzeit-FFT-Analyse (1024 Samples, Hanning-Fenster) aufgeteilt in 8 Frequenzbänder (`SUB`, `BASS`, `LOW`, `MID`, `H-MID`, `PRES`, `TREB`, `BRIL`) inklusive Peak-Hold-Anzeige.
- **Präzise Kick- & Beat-Erkennung**: Mehrstufige Transienten- und Subbass-Analyse für zuverlässige Kick-Drum-Erkennung auch bei stark komprimierten Tracks.
- **Einstellbare Beat-Empfindlichkeit**: Über einen eigenen Regler (`20 %` bis `200 %`) lässt sich die Auslöseschwelle jederzeit live an leise Passagen oder harte Club-Tracks anpassen.
- **Automatische Pegel-Normalisierung (AGC)** & manueller **Gain-Regler**.
- **Live-BPM-Detektor**: Ermittelt fortlaufend das Tempo des laufenden Songs in BPM und ordnet es automatisch einer Geschwindigkeitsstufe (`Pause`, `Langsam`, `Mittel`, `Schnell`, `Rave`) zu.

### 2. Dynamische Licht- & Bewegungs-Engine
- **Auto-BPM-Modus**: Passt Bewegungsmuster, Geschwindigkeit, Auslenkung und Farbwechsel automatisch an die erkannte BPM-Stufe an – von ruhigen, fließenden Fahrten bei langsamen Liedern (~90 BPM) bis hin zu schnellen, energiegeladenen Mustern ab 120+ BPM.
- **Wählbare Bewegungsmuster**: `Auto-BPM`, `Kreis`, `Acht`, `Ballyhoo`, `Welle`, `Pan-Sweep`, `Tilt-Swing` und `Beat-Bounce`.
- **Ruheposition bei deaktivierter Bewegung**: Wird die Bewegung ausgeschaltet, fahren alle Moving Heads automatisch in ihre neutrale Standardposition (`DMX 128`, gerade nach unten).
- **Zuschaltbarer Strobo-Effekt**: Über den Toggle-Schalter `Strobo` in den Einstellungen werden in schnellen Tempo-Stufen (`Schnell` und `Rave`) bei markanten Beats automatisch kurze Stroboskop-Bursts ausgelöst – wahlweise über den Hardware-Strobe-Kanal des Geräts oder als Software-Shutter über Dimmer/RGB.
- **Farbpaletten & Dimmer-Modi**: Verschiedene Farbpaletten (`Club Neon`, `Cyberpunk`, `Fire & Ice`, `Regenbogen`, `Material Teal`) sowie wählbare Dimmer-Reaktionen (`Beat-Puls`, `Audio-Pegel`, `Dauer-An`).

### 3. Fixture-Management & QLC+ Import
- **QLC+ Import (`*.qxf`)**: Direkter Import von QLC+ Gerätedefinitionen mit automatischer Erkennung der DMX-Modi und Kanalfunktionen sowie Mehrfach-Patching für mehrere baugleiche Geräte.
- **Integrierte Profil-Bibliothek**: Mitgelieferte Vorlagen für 9-/11-Kanal Spot-Moving-Heads, 9-/14-Kanal Wash-Moving-Heads sowie 4-/7-Kanal RGBW-PAR-Scheinwerfer.
- **Fixture-Editor**: Freie Konfiguration aller Kanäle (`PAN`, `PAN_FINE`, `TILT`, `TILT_FINE`, `PAN_TILT_SPEED`, `DIMMER`, `STROBE`, `RED`, `GREEN`, `BLUE`, `WHITE`, `AMBER`, `UV`, `COLOR_WHEEL`, `GOBO_WHEEL`, `PRISM`, `FOCUS`, `CONSTANT`, `UNUSED`).
- **Schutzgrenzen & Phasenversatz**: Einstellbare Pan/Tilt-Limits (`Min`/`Max`), Pan/Tilt-Invertierung sowie Phasenversatz (`0°–360°`) für symmetrische oder versetzte Gruppenbewegungen.

### 4. Art-Net 4 Ausgang & DMX512 Live-Visualizer
- **Art-Net 4 Sender**: Sendet standardkonforme `ArtDMX`-Pakete (UDP Port `6454`) an eine frei wählbare Ziel-IP (Unicast, Broadcast oder `127.0.0.1`) und ein einstellbares Universum (`0–15`) mit konfigurierbarer Bildrate (`10–44 FPS`).
- **Live-Metriken**: Anzeige von Audio-Pegel, Peak, Tempo (BPM), aktiver Geschwindigkeitsstufe, Paketen pro Sekunde (inkl. Echtzeit-Verlaufsdiagramm) und Gesamtzahl gesendeter Pakete.
- **512-Kanal DMX-Visualizer**: Live-Balkenanzeige aller 512 DMX-Kanäle mit direkter Beschriftung der zugewiesenen Funktion (`PAN`, `TILT`, `DIM`, `STRB`, `R`, `G`, `B` usw.) und Schnellwahl-Buttons für Kanalbereiche.
- **Automatische Speicherung**: Alle Einstellungen und gepatchten Geräte werden automatisch in der Datei `config.json` gespeichert.

---

## Bauen & Starten

### Voraussetzungen
- **Java 21 LTS** oder neuer
- **Apache Maven 3.8+**

### Anwendung starten (GUI)
Unter Linux (AppImage):
```bash
chmod +x sound2artnet-x86_64.AppImage
./sound2artnet-x86_64.AppImage
```

Per Startskript unter Windows:
```cmd
run.bat
```
Oder direkt über Maven:
```bash
mvn exec:java
```

### AppImage neu bauen (Linux / WSL)
```bash
mvn clean package -DskipTests
./build-appimage.sh
```

### Kompilieren & Tests ausführen
```bash
mvn clean test
```

### Ausführbare Fat-JAR erstellen
```bash
mvn clean package
```
Die fertige All-in-One-JAR befindet sich anschließend unter `target/sound2artnet-1.0.0-all.jar`.

### Headless-CLI-Modus (ohne GUI)
```bash
java -jar target/sound2artnet-1.0.0-all.jar --cli --ip 192.168.200.232 --universe 0 --fps 40
```

---

## Autor

Programmiert von **exitishere42** ([github.com/exitishere42](https://github.com/exitishere42)).

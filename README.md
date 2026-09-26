# sound2artnet

**sound2artnet** ist ein professionelles, soundreaktives Art-Net-DMX512-Steuerungsprogramm und Moving-Head-Effektgenerator in **Java 21 LTS** und **JavaFX 21**. 

Die Benutzeroberfläche folgt der Designphilosophie von **artnet2dmx** (Material Design 2 Dark Theme, strikte Lucide-Vektor-Icons, keine System-Emojis).

---

## 🌟 Highlights & Funktionen

- **Echtzeit-Audioanalyse (Sound-to-Light)**:
  - Direkte Audio-Erfassung über Java Sound API (`javax.sound.sampled`) ohne native C-Bibliotheken oder DLLs.
  - Radix-2 Cooley-Tukey FFT (1024 Samples) mit Hanning-Fensterfunktion.
  - 8-Band Spektrumanalysator (`SUB`, `BASS`, `LOW`, `MID`, `H-MID`, `PRES`, `TREB`, `BRIL`) mit Peak-Hold und logarithmischer Skalierung.
  - Dynamischer Subband Beat- & Transienten-Detektor für präzise Kick-Drum-Erkennung.
  - Integrierte AGC (Auto-Gain Control) zur automatischen Pegel-Normalisierung bei leisen und lauten Audioquellen.

- **Intelligente Moving-Head-Steuerung (Semantisches Mapping)**:
  - **Lösung des Kanalbelegungsproblems**: Trennung von musikalischer Bewegungslogik und herstellerspezifischen DMX-Kanälen.
  - Vordefinierte Profile:
    - *Generic 9-Kanal Spot Moving Head* (Pan, Pan Fine, Tilt, Tilt Fine, Color Wheel, Shutter/Strobe, Dimmer, Gobo, Speed)
    - *Generic 11-Kanal Spot Moving Head* (+ Prism, Focus)
    - *Generic 9-Kanal Wash RGBW Moving Head*
    - *Generic 14-Kanal Wash RGBW Moving Head*
    - *Generic 4-Kanal & 7-Kanal RGBW PARs*
  - **Interaktiver Profil-Editor**: Beliebige Fixtures erstellen oder bearbeiten, freie Zuweisung semantischer Rollen (`PAN`, `TILT`, `DIMMER`, `STROBE`, `RED`, `GREEN`, `BLUE`, `WHITE`, `COLOR_WHEEL`, `GOBO_WHEEL`, `CONSTANT`, `UNUSED`).
  - **Schutzgrenzen & Safety Limits**:
    - Pan Invert & Tilt Invert (Spiegelung)
    - Pan Min/Max & Tilt Min/Max (Schutz vor Anstrahlen von Decken/Wänden/Augen)
    - Einstellbarer Phasenversatz für symmetrische Fahrten oder Wellenbewegungen zwischen mehreren Moving Heads.

- **Musikalische Bewegungs- & Effekt-Engine**:
  - **Mathematische Bewegungsmuster**: *Kreis (Circle)*, *Acht (Figure 8)*, *Ballyhoo / Chaos*, *Welle (Wave)*, *Pan-Sweep*, *Tilt-Swing*, *Beat-Bounce*.
  - **Sound-Reaktivität**: Lautstärke moduliert die Weite (Amplitude) der Fahrt; leise Musik = dezente kleine Fahrten, Drop = raumfüllende Dynamik.
  - **Beat-Aktionen**: Kick-Drums lösen Positionssprünge, Dimmer-Flashs oder Farbwechsel aus.
  - **Farbpaletten**: *Club Neon*, *Cyberpunk*, *Fire & Ice*, *Regenbogen*, *Material Teal*.
  - **Stroboskop-Automatik**: Extreme Höhenspitzen (Snare/Hi-Hats) triggern automatisch kurze Strobe-Flashs.

- **Art-Net 4 Sender-Engine**:
  - Sendet standardkonforme Art-Net 4 `OpDmx` UDP-Pakete (Port 6454).
  - Ziel-IP frei einstellbar: `127.0.0.1` (lokaler Empfang für QLC+, Resolume, GrandMA onPC etc.), Broadcast `255.255.255.255` oder spezifische Adapter-IP.
  - Universum frei wählbar (0 bis 15).
  - Bildrate einstellbar (10 bis 44 FPS, empfohlen 40 Hz).

- **512-Kanal DMX Live-Visualizer**:
  - 32 sichtbare Kanäle im Viewport mit flüssiger Scrollbar und Bereichs-Sprungtasten (1-32, 33-64 ...).
  - Direkte Anzeige der zugewiesenen Rolle (`PAN`, `TILT`, `DIM`, `STRB`, `R`, `G`, `B`) am Kanal!

---

## 🚀 Bauen & Starten

### Voraussetzungen
- Java 21 LTS oder neuer
- Apache Maven 3.8+

### Kompilieren und Tests ausführen
```bash
mvn clean test
```

### Anwendung starten (GUI)
Unter Windows:
```cmd
run.bat
```
oder via Maven:
```bash
mvn exec:java
```

### Headless CLI Modus (für Server / Raspberry Pi)
```bash
java -jar target/sound2artnet-1.0.0-all.jar --cli --ip 127.0.0.1 --universe 0 --fps 40
```

### All-in-One Fat JAR erstellen
```bash
mvn clean package
# Die fertige ausführbare JAR liegt unter:
# target/sound2artnet-1.0.0-all.jar
```

---

## 🎨 UI-Design

Die Benutzeroberfläche setzt das **Material Design 2 Dark Theme** mit strikter 4-stufiger Elevation (`#121212`, `#1E1E1E`, `#232323`, `#272727`), `#03DAC6` Primary Teal und gestochen scharfen Canvas-Vektor-Icons aus dem **Lucide Icon Set** (lucide.dev) ohne System-Emojis um.

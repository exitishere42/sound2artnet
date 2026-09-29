<div align="center">

<img src="sound2artnet.png" alt="sound2artnet Logo" width="128" height="128" />

```text
                                           _ ____             _              _   
                  ___  ___  _   _ _ __   __| |___ \ __ _ _ __| |_ _ __   ___| |_ 
                 / __|/ _ \| | | | '_ \ / _` | __) / _` | '__| __| '_ \ / _ \ __|
                 \__ \ (_) | |_| | | | | (_| |/ __/ (_| | |  | |_| | | |  __/ |_ 
                 |___/\___/ \__,_|_| |_|\__,_|_____\__,_|_|   \__|_| |_|\___|\__|
                                                                                 
```

**Real-time Sound-to-Light Controller and Moving Head Effect Generator for Art-Net (DMX512) built with Java 21 LTS & JavaFX 21**

</div>

---

## About the Project

**sound2artnet** analyzes live audio in real time, detects kicks as well as the current tempo (BPM), and automatically translates the music into synchronized movements, color changes, dimmer pulses, and strobe effects for Moving Heads and DMX fixtures.

Visually and structurally, **sound2artnet** features the exact same dark Material Design interface as **artnet2dmx**, making it a seamless companion in your lighting control setup.

```text
+--------------------+      +-------------------------+      +--------------------------+
|    AUDIO INPUT     |      |   DSP & BEAT ANALYSIS   |      |    SHOW & DMX ENGINE     |
|                    |      |                         |      |                          |
|  * System Audio    | ===> |  * 1024-Sample FFT      | ===> |  * Auto-BPM Speed Tiers  |
|    (WASAPI)        |      |  * 8-Band Spectrum      |      |  * Pan/Tilt Generator    |
|  * Microphone /    |      |  * Kick / Beat Detector |      |  * Palettes & Dimmer     |
|    Line-In         |      |  * Live BPM Detection   |      |  * Auto-Strobe (Fast)    |
+--------------------+      +-------------------------+      +------------+-------------+
                                                                          |
                                                                          | Art-Net 4 (UDP 6454)
                                                                          v
                            +-----------------------------------------------------------+
                            |                 DMX512 OUTPUT / RECEIVERS                 |
                            |                                                           |
                            |  [ Moving Heads ]   [ PAR Spots ]    [ artnet2dmx / QLC+ ]|
                            +-----------------------------------------------------------+
```

---

## Features

### 1. Real-Time Audio Analysis & Beat / BPM Detection
- **Direct System Audio Capture**: Captures desktop system audio directly on Windows (WASAPI Loopback) and Linux (PipeWire / PulseAudio) via `PC-Sound` without requiring virtual audio cables or extra drivers. Any microphone or audio interface can also be selected.
- **8-Band Spectrum Analyzer**: Real-time FFT analysis (1024 samples, Hanning window) split into 8 frequency bands (`SUB`, `BASS`, `LOW`, `MID`, `H-MID`, `PRES`, `TREB`, `BRIL`) with peak-hold indicators.
- **Accurate Kick & Beat Detection (`Hybrid`, `Bass Only`, `Manual`)**: Multi-stage sub-bass and transient onset analysis for reliable kick drum detection, plus a dedicated **Manual / MIDI** mode for 100% manual beat and tap-tempo control.
- **Adjustable Beat Sensitivity**: Dedicated sensitivity slider (`20%` to `200%`) to fine-tune trigger thresholds on the fly for quiet passages or heavy club tracks.
- **Automatic Gain Control (AGC)** & manual **Gain Slider**.
- **Live BPM Detector & Tap Tempo**: Continuously calculates the tempo of the playing track (or manual MIDI taps) in BPM and automatically maps it to an effect speed tier.

### 2. Dedicated MIDI Tab & Manual Beat Control
- **MIDI Input Device Support**: Connect any USB MIDI controller, drum pad, or keyboard directly in the **`MIDI`** tab with instant hot-plug rescan.
- **MIDI Learn & Key Binding**: Click **`MIDI Learn`** and press any MIDI key, pad, or pedal (`Note On` or `Control Change`) to bind it as your manual beat trigger, or reset to **`Any Key`** so every key on the controller triggers a beat.
- **Tap Tempo & Live Beat Pad**: Tapping your bound MIDI key (or clicking the oversized interactive **`BEAT`** button in the UI) immediately fires a synchronized light pulse (`Beat Pulse`), advances color/movement phases, and computes the exact live BPM and speed tier (`Slow`, `Medium`, `Fast`, `Rave`) from your tap intervals.
- **Live MIDI Monitor**: Real-time display of incoming MIDI messages (`Note On` / `CC`, note name, channel, and velocity).

### 3. Dynamic Light & Movement Engine (`Auto-BPM`)

```text
+----------+---------------+------------------+--------------------+------------------+
|  TIER    |  BPM RANGE    |  MOVEMENT        |  MOVEMENT SPEED    |  STROBE EFFECT   |
+----------+---------------+------------------+--------------------+------------------+
|  Idle    |   < 40 BPM    |  Gentle Wave     |  0.25x (Calm)      |  Off             |
|  Slow    |  40 -  98 BPM |  Wave / Sweep    |  0.45x - 0.70x     |  Off             |
|  Medium  |  98 - 116 BPM |  Circle / Eight  |  0.90x - 1.15x     |  Off             |
|  Fast    | 116 - 138 BPM |  Eight / Bounce  |  1.35x - 1.75x     |  Active on Drops |
|  Rave    |   >= 138 BPM  |  Bounce / Chaos  |  1.85x - 2.40x     |  Active (Intense)|
+----------+---------------+------------------+--------------------+------------------+
```

- **Movement Patterns**: `Auto-BPM`, `Circle`, `Eight`, `Ballyhoo`, `Wave`, `Pan-Sweep`, `Tilt-Swing`, and `Beat-Bounce`.
- **Default Center Position When Movement Is Disabled**: Turning off movement automatically returns all Moving Heads to their neutral center position (`DMX 128`, pointing straight down).
- **Toggleable Strobe Effect**: Dedicated `Strobo` toggle in the settings triggers short strobe bursts on strong beats during fast tempo tiers (`Fast` and `Rave`) — using either the fixture's hardware strobe channel or a software shutter across dimmer/RGB channels.
- **Color Palettes & Dimmer Modes**: Multiple color palettes (`Club Neon`, `Cyberpunk`, `Fire & Ice`, `Rainbow`, `Material Teal`) and selectable dimmer responses (`Beat Pulse`, `Audio Level`, `Always On`) with dedicated intensity/max-level sliders.

### 4. Fixture Management & QLC+ Import
- **QLC+ Import (`*.qxf`)**: Direct import of QLC+ fixture definitions with automatic detection of DMX modes, pixel/segment master consolidation, and multi-fixture patching.
- **Built-in Profile Library**: Ready-to-use templates for 9-/11-channel Spot Moving Heads, 9-/14-channel Wash Moving Heads, and 4-/7-channel RGBW PAR cans.
- **Fixture Editor**: Full customization of all channels (`PAN`, `PAN_FINE`, `TILT`, `TILT_FINE`, `PAN_TILT_SPEED`, `DIMMER`, `STROBE`, `RED`, `GREEN`, `BLUE`, `CYAN`, `MAGENTA`, `YELLOW`, `WHITE`, `AMBER`, `UV`, `COLOR_WHEEL`, `GOBO_WHEEL`, `PRISM`, `FOCUS`, `CONSTANT`, `UNUSED`).
- **Safety Limits & Phase Offset**: Configurable Pan/Tilt limits (`Min`/`Max`), Pan/Tilt inversion, and phase offset (`0°–360°`) for symmetrical or wave-like group movements.

### 5. Art-Net 4 Output & 512-Channel DMX Live Visualizer
- **Art-Net 4 Sender**: Broadcasts standard-compliant `ArtDMX` packets (UDP port `6454`) to any target IP (unicast, broadcast, or `127.0.0.1`) and universe (`0–15`) at a configurable frame rate (`10–44 FPS`).
- **Live Metrics**: Real-time readouts for audio RMS, peak, tempo (BPM), active speed tier, packets per second (including a live history chart), and total packets sent.
- **512-Channel DMX Visualizer**: Live bar graph of all 512 DMX channels with semantic role labels (`PAN`, `TILT`, `DIM`, `STRB`, `R`, `G`, `B`, `C`, `M`, `Y`, etc.) and quick-jump range buttons.
- **Multi-Profile & Venue Presets**: Dedicated `Profile` tab for saving and switching multiple venue configurations (Target IP, Universe, FPS, Fixture Patches, and Engine Settings). Double-click or click `Laden` to activate instantly with live Art-Net target switching without restarting the stream.
- **In-App Update & Global Settings Dialog**: Click on the title logo badge (`v1.5.4`) to inspect release notes, check for updates, or switch global settings (e.g., German/English language, autostart).
- **Automatic Persistence**: All settings, MIDI bindings, profiles, and patched fixtures are automatically saved to `config.json`.

---

## Installation & Quick Start (GitHub Release)

### 1. Automatic Installer (Recommended)
Download [`install.sh`](https://github.com/exitishere42/sound2artnet/releases/latest/download/install.sh) (Linux) or [`install.bat`](https://github.com/exitishere42/sound2artnet/releases/latest/download/install.bat) (Windows) from the [**Releases**](https://github.com/exitishere42/sound2artnet/releases/latest) page:
- Automatically installs the application into `~/sound2artnet` (e.g., `/home/regie/sound2artnet` on Linux or `%USERPROFILE%\sound2artnet` on Windows).
- Checks if **Java 21+** is already installed — skips installation if present, or automatically installs the latest OpenJDK 21 LTS if missing.
- Creates a **Desktop Entry / Start Menu App** with the **sound2artnet** logo so you can launch it directly from your application dash.

**Linux One-Line Install:**
```bash
curl -fsSL https://github.com/exitishere42/sound2artnet/releases/latest/download/install.sh | bash
```

**Windows Install:**
Download and run `install.bat` from the [latest release](https://github.com/exitishere42/sound2artnet/releases/latest).

### 2. Standalone Linux AppImage
Download [`sound2artnet-x86_64.AppImage`](https://github.com/exitishere42/sound2artnet/releases/latest/download/sound2artnet-x86_64.AppImage) from the [latest release](https://github.com/exitishere42/sound2artnet/releases/latest):
```bash
chmod +x sound2artnet-x86_64.AppImage
./sound2artnet-x86_64.AppImage
```

### 3. Uninstallation (`uninstall.sh` / `uninstall.bat`)
To completely remove **sound2artnet** (including `~/sound2artnet`, icons, and the Desktop/Start Menu entry):

**Linux One-Line Uninstall:**
```bash
curl -fsSL https://github.com/exitishere42/sound2artnet/releases/latest/download/uninstall.sh | bash
```

**Windows Uninstall:**
Download and run [`uninstall.bat`](https://github.com/exitishere42/sound2artnet/releases/latest/download/uninstall.bat) from the [latest release](https://github.com/exitishere42/sound2artnet/releases/latest) (or run `%USERPROFILE%\sound2artnet\uninstall.bat`).

---

## Build from Source

### Requirements
- **Java 21 LTS** or newer
- **Apache Maven 3.8+**

### Run Application (Development)
On Windows (batch launcher):
```cmd
run.bat
```

Or directly via Maven:
```bash
mvn exec:java
```

### Rebuild AppImage (Linux / WSL)
```bash
mvn clean package -DskipTests
./build-appimage.sh
```

### Compile & Run Tests
```bash
mvn clean test
```

### Build Executable Fat JAR
```bash
mvn clean package
```
The standalone fat JAR will be created at `target/sound2artnet-1.5.4-all.jar`.

### Headless CLI Mode (No GUI)
```bash
java -jar target/sound2artnet-1.5.4-all.jar --cli --ip 192.168.200.232 --universe 0 --fps 40
```

---

## Author

```text
+-------------------------------------------------------------------+
|  Programmed by:     exitishere42                                  |
|  GitHub:            https://github.com/exitishere42               |
|  Repository:        https://github.com/exitishere42/sound2artnet  |
+-------------------------------------------------------------------+
```

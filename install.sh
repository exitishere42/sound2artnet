#!/usr/bin/env bash
# ==============================================================================
# sound2artnet - Automatischer Linux Installe
# Lädt sound2artnet nach ~/sound2artnet (z. B. /home/regie/sound2artnet),
# prüft/installiert Java 21+ und erstellt den Desktop-Eintrag mit Logo im Dash.
# ==============================================================================

set -e

REPO="exitishere42/sound2artnet"
INSTALL_DIR="${INSTALL_DIR:-$HOME/sound2artnet}"
APP_NAME="sound2artnet"
JAR_NAME="sound2artnet-1.4.2-all.jar"
DETECTED_JAR=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" 2>/dev/null | grep -o '"name": *"sound2artnet-[^"]*-all\.jar"' | head -n 1 | cut -d '"' -f 4 || true)
if [ -n "$DETECTED_JAR" ]; then
    JAR_NAME="$DETECTED_JAR"
fi
APPIMAGE_NAME="sound2artnet-x86_64.AppImage"
RELEASE_BASE_URL="https://github.com/$REPO/releases/latest/download"
RAW_BASE_URL="https://raw.githubusercontent.com/$REPO/main"

echo "=================================================================="
echo "  sound2artnet - Linux Installer"
echo "  Zielordner: $INSTALL_DIR"
echo "=================================================================="

# ------------------------------------------------------------------------------
# 1. Prüfen ob Java 21+ installiert ist (wenn ja -> skip, wenn nein -> installieren)
# ------------------------------------------------------------------------------
check_java_version() {
    if command -v java &> /dev/null; then
        local ve
        ver=$(java -version 2>&1 | head -n 1 | sed -E 's/.*"([0-9]+).*/\1/')
        if [[ "$ver" =~ ^[0-9]+$ ]] && [ "$ver" -ge 21 ]; then
            return 0
        fi
    fi
    return 1
}

if check_java_version; then
    JAVA_VER_STR=$(java -version 2>&1 | head -n 1)
    echo "[OK] Passendes Java bereits installiert ($JAVA_VER_STR) -> überspringe Java-Installation."
else
    echo "[*] Java 21+ wurde nicht gefunden. Installiere neuestes OpenJDK..."
    if command -v apt-get &> /dev/null; then
        sudo apt-get update -y
        if apt-cache show openjdk-21-jre &> /dev/null; then
            sudo apt-get install -y openjdk-21-jre
        else
            echo "[*] Füge Eclipse Adoptium Repository für OpenJDK 21 hinzu..."
            sudo apt-get install -y wget apt-transport-https gpg
            wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
            echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list > /dev/null
            sudo apt-get update -y
            sudo apt-get install -y temurin-21-jre
        fi
    elif command -v dnf &> /dev/null; then
        sudo dnf install -y java-21-openjdk
    elif command -v pacman &> /dev/null; then
        sudo pacman -Sy --noconfirm jre-openjdk
    elif command -v zypper &> /dev/null; then
        sudo zypper install -y java-21-openjdk
    else
        echo "[!] Konnte Paketmanager nicht erkennen. Bitte OpenJDK 21+ manuell installieren."
        exit 1
    fi
    echo "[OK] Java 21+ erfolgreich installiert."
fi

# ------------------------------------------------------------------------------
# 2. Projektdateien in Zielordner (z. B. /home/regie/sound2artnet) herunterladen
# ------------------------------------------------------------------------------
echo "[*] Erstelle Programmordner: $INSTALL_DIR"
mkdir -p "$INSTALL_DIR"

download_file() {
    local url="$1"
    local dest="$2"
    if command -v curl &> /dev/null; then
        curl -fL --progress-bar -o "$dest" "$url"
    elif command -v wget &> /dev/null; then
        wget -q --show-progress -O "$dest" "$url"
    else
        echo "[!] Weder curl noch wget installiert."
        exit 1
    fi
}

echo "[*] Lade Programmdateien und Logo herunter..."
download_file "$RELEASE_BASE_URL/$JAR_NAME" "$INSTALL_DIR/$JAR_NAME"
download_file "$RELEASE_BASE_URL/$APPIMAGE_NAME" "$INSTALL_DIR/$APPIMAGE_NAME" || true
download_file "$RAW_BASE_URL/sound2artnet.png" "$INSTALL_DIR/sound2artnet.png"
download_file "$RAW_BASE_URL/sound2artnet.svg" "$INSTALL_DIR/sound2artnet.svg"
download_file "$RAW_BASE_URL/uninstall.sh" "$INSTALL_DIR/uninstall.sh" || true
chmod +x "$INSTALL_DIR/uninstall.sh" 2>/dev/null || true

if [ -f "$INSTALL_DIR/$APPIMAGE_NAME" ]; then
    chmod +x "$INSTALL_DIR/$APPIMAGE_NAME"
fi

# Startskript run.sh im Zielordner erzeugen
cat > "$INSTALL_DIR/run.sh" << EOF
#!/usr/bin/env bash
SCRIPT_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
cd "\$SCRIPT_DIR"
exec java -jar "\$SCRIPT_DIR/$JAR_NAME" "\$@"
EOF
chmod +x "$INSTALL_DIR/run.sh"

# ------------------------------------------------------------------------------
# 3. Desktop-Entry & Icon für das App-Dash / Anwendungsmenü einrichten
# ------------------------------------------------------------------------------
echo "[*] Richte Desktop-Entry und App-Logo für das Dash ein..."
ICON_DIR_PNG="$HOME/.local/share/icons/hicolor/256x256/apps"
ICON_DIR_SVG="$HOME/.local/share/icons/hicolor/scalable/apps"
APPS_DIR="$HOME/.local/share/applications"

mkdir -p "$ICON_DIR_PNG" "$ICON_DIR_SVG" "$APPS_DIR"

cp "$INSTALL_DIR/sound2artnet.png" "$ICON_DIR_PNG/sound2artnet.png"
cp "$INSTALL_DIR/sound2artnet.svg" "$ICON_DIR_SVG/sound2artnet.svg"

DESKTOP_FILE="$APPS_DIR/sound2artnet.desktop"
cat > "$DESKTOP_FILE" << EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=sound2artnet
Comment=Sound-to-Light & Moving Head Art-Net Controlle
Exec=$INSTALL_DIR/run.sh
Icon=$INSTALL_DIR/sound2artnet.png
Path=$INSTALL_DIR
Terminal=false
Categories=AudioVideo;Audio;
StartupNotify=true
StartupWMClass=de.exit.sound2artnet.Sound2ArtNetApp
EOF
chmod +x "$DESKTOP_FILE"
cp "$DESKTOP_FILE" "$INSTALL_DIR/sound2artnet.desktop"

if command -v update-desktop-database &> /dev/null; then
    update-desktop-database "$APPS_DIR" &> /dev/null || true
fi
if command -v gtk-update-icon-cache &> /dev/null; then
    gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" &> /dev/null || true
fi

echo "=================================================================="
echo "  [OK] Installation abgeschlossen!"
echo "  Ordner:        $INSTALL_DIR"
echo "  Startbefehl:   $INSTALL_DIR/run.sh"
echo "  AppImage:      $INSTALL_DIR/$APPIMAGE_NAME"
echo "  Dash-Eintrag:  sound2artnet ist jetzt direkt im App-Menü verfügbar."
echo "=================================================================="

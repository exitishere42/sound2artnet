#!/usr/bin/env bash
# ==============================================================================
# sound2artnet - Linux Deinstallations-Skript (Uninstaller)
# Entfernt den Programmordner (~/sound2artnet), das App-Logo und den Desktop-Entry.
# ==============================================================================

set -e

INSTALL_DIR="${INSTALL_DIR:-$HOME/sound2artnet}"
DESKTOP_FILE="$HOME/.local/share/applications/sound2artnet.desktop"
ICON_PNG="$HOME/.local/share/icons/hicolor/256x256/apps/sound2artnet.png"
ICON_SVG="$HOME/.local/share/icons/hicolor/scalable/apps/sound2artnet.svg"

echo "=================================================================="
echo "  sound2artnet - Linux Uninstaller"
echo "=================================================================="

# 1. Laufende Instanzen beenden (falls aktiv)
if pgrep -f "sound2artnet.*\.jar|sound2artnet.*\.AppImage" &> /dev/null; then
    echo "[*] Beende laufende sound2artnet-Instanz..."
    pkill -f "sound2artnet.*\.jar|sound2artnet.*\.AppImage" || true
    sleep 1
fi

# 2. Desktop-Entry und Icons entfernen
if [ -f "$DESKTOP_FILE" ]; then
    echo "[*] Entferne Desktop-Entry: $DESKTOP_FILE"
    rm -f "$DESKTOP_FILE"
fi

rm -f "$HOME/Desktop/sound2artnet.desktop" "$HOME/Schreibtisch/sound2artnet.desktop" 2>/dev/null || true

if [ -f "$ICON_PNG" ] || [ -f "$ICON_SVG" ]; then
    echo "[*] Entferne App-Icons..."
    rm -f "$ICON_PNG" "$ICON_SVG"
fi

if command -v update-desktop-database &> /dev/null; then
    update-desktop-database "$HOME/.local/share/applications" &> /dev/null || true
fi
if command -v gtk-update-icon-cache &> /dev/null; then
    gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" &> /dev/null || true
fi

# 3. Programmordner löschen
if [ -d "$INSTALL_DIR" ]; then
    echo "[*] Lösche Programmordner: $INSTALL_DIR"
    rm -rf "$INSTALL_DIR"
else
    echo "[i] Programmordner $INSTALL_DIR existiert bereits nicht mehr."
fi

echo "=================================================================="
echo "  [OK] sound2artnet wurde vollständig deinstalliert!"
echo "=================================================================="

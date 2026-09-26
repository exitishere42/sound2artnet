#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="/tmp/sound2artnet-appimage"
JAR_SRC="$PROJECT_DIR/target/sound2artnet-1.0.0-all.jar"

if [ ! -f "$JAR_SRC" ]; then
    echo "[!] Fat JAR nicht gefunden unter $JAR_SRC. Bitte zuerst 'mvn package' ausführen."
    exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/AppDir/usr/lib/sound2artnet"
mkdir -p "$BUILD_DIR/AppDir/usr/share/icons/hicolor/256x256/apps"
mkdir -p "$BUILD_DIR/AppDir/usr/share/applications"

cp "$JAR_SRC" "$BUILD_DIR/AppDir/usr/lib/sound2artnet/sound2artnet-1.0.0-all.jar"
cp "$PROJECT_DIR/sound2artnet.png" "$BUILD_DIR/AppDir/sound2artnet.png"
cp "$PROJECT_DIR/sound2artnet.png" "$BUILD_DIR/AppDir/.DirIcon"
cp "$PROJECT_DIR/sound2artnet.png" "$BUILD_DIR/AppDir/usr/share/icons/hicolor/256x256/apps/sound2artnet.png"
cp "$PROJECT_DIR/sound2artnet.svg" "$BUILD_DIR/AppDir/sound2artnet.svg"

cat > "$BUILD_DIR/AppDir/sound2artnet.desktop" << 'EOF'
[Desktop Entry]
Type=Application
Name=sound2artnet
Comment=Sound-to-Light & Moving Head Art-Net Controlle
Exec=AppRun
Icon=sound2artnet
Categories=AudioVideo;Audio;
Terminal=false
StartupNotify=true
EOF
cp "$BUILD_DIR/AppDir/sound2artnet.desktop" "$BUILD_DIR/AppDir/usr/share/applications/sound2artnet.desktop"

cat > "$BUILD_DIR/AppDir/AppRun" << 'EOF'
#!/usr/bin/env bash
HERE="$(dirname "$(readlink -f "${0}")")"
JAR_FILE="$HERE/usr/lib/sound2artnet/sound2artnet-1.0.0-all.jar"
if ! command -v java &> /dev/null; then
    echo "[!] Fehler: Java 21+ (java) wurde nicht gefunden. Bitte openjdk-21-jre installieren." >&2
    exit 1
fi
exec java -jar "$JAR_FILE" "$@"
EOF
chmod +x "$BUILD_DIR/AppDir/AppRun"

curl -fsSL -o "$BUILD_DIR/runtime-x86_64" "https://github.com/AppImage/type2-runtime/releases/download/continuous/runtime-x86_64"
mksquashfs "$BUILD_DIR/AppDir" "$BUILD_DIR/app.squashfs" -root-owned -noappend -comp gzip > /dev/null
cat "$BUILD_DIR/runtime-x86_64" "$BUILD_DIR/app.squashfs" > "$PROJECT_DIR/sound2artnet-x86_64.AppImage"
chmod +x "$PROJECT_DIR/sound2artnet-x86_64.AppImage"
echo "[OK] AppImage erfolgreich erstellt: $PROJECT_DIR/sound2artnet-x86_64.AppImage"

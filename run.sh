#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=================================================================="
echo "  Starte sound2artnet (JavaFX 21 LTS)"
echo "=================================================================="

if [ -f "target/sound2artnet-1.0.0-all.jar" ]; then
    java -jar target/sound2artnet-1.0.0-all.jar "$@"
else
    echo "Fat JAR nicht gefunden. Starte via Maven exec:java..."
    mvn exec:java -Dexec.args="$*"
fi

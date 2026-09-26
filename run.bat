@echo off
setlocal
cd /d "%~dp0"

echo ==================================================================
echo   Starte sound2artnet (JavaFX 21 LTS)
echo ==================================================================

if exist "target\sound2artnet-1.0.0-all.jar" (
    java -jar target\sound2artnet-1.0.0-all.jar %*
) else (
    echo Fat JAR nicht gefunden. Starte via Maven exec:java...
    call mvn exec:java -Dexec.args="%*"
)

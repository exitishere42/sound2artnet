@echo off
setlocal
cd /d "%~dp0"

echo ==================================================================
echo   Starte sound2artnet (JavaFX 21 LTS)
echo ==================================================================

set "FOUND_JAR="
for %%f in (target\sound2artnet-*-all.jar) do (
    set "FOUND_JAR=%%f"
)

if defined FOUND_JAR (
    java -jar "%FOUND_JAR%" %*
) else (
    echo Fat JAR nicht gefunden. Starte via Maven exec:java...
    call mvn exec:java -Dexec.args="%*"
)

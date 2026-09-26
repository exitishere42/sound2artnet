@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul 2>&1

set "INSTALL_DIR=%USERPROFILE%\sound2artnet"
set "SM_LNK=%APPDATA%\Microsoft\Windows\Start Menu\Programs\sound2artnet.lnk"

echo ==================================================================
echo   sound2artnet - Windows Uninstaller
echo ==================================================================

:: 1. Startmenue- und Desktop-Verknuepfungen loeschen
if exist "%SM_LNK%" (
    echo [*] Entferne Startmenue-Eintrag...
    del /f /q "%SM_LNK%"
)

for /f "usebackq tokens=*" %%d in (`powershell -NoProfile -Command "[Environment]::GetFolderPath('Desktop')"`) do (
    if exist "%%d\sound2artnet.lnk" (
        echo [*] Entferne Desktop-Verknuepfung...
        del /f /q "%%d\sound2artnet.lnk"
    )
)

:: 2. Programmordner %USERPROFILE%\sound2artnet loeschen
if exist "%INSTALL_DIR%" (
    echo [*] Loesche Programmordner: %INSTALL_DIR%
    :: Falls das Skript direkt aus %INSTALL_DIR% gestartet wurde, kurz nach %TEMP% wechseln
    cd /d "%TEMP%"
    rmdir /s /q "%INSTALL_DIR%"
) else (
    echo [i] Programmordner %INSTALL_DIR% wurde bereits entfernt.
)

echo ==================================================================
echo   [OK] sound2artnet wurde vollstaendig deinstalliert!
echo ==================================================================
pause

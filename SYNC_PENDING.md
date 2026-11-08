# SYNC PENDING: Native MarkdownView für AboutUpdateDialog

- **Quelle**: `jdmx v1.3.0` (06.11.2026)
- **Komponente**: Update-System / Dialoge (`MarkdownView.java`, `AboutUpdateDialog.java`)
- **Grund**: GitHub Release Notes wurden bisher als roher Text in einer unformatierten `TextArea` angezeigt. Neu implementiert ist ein 100% nativer JavaFX `MarkdownView` (Überschriften `#`/`##`, Bullet-Listen, Code-Blöcke, Inline-Code und anklickbare Hyperlinks im Studio/Material Dark Theme ohne WebKit-Abhängigkeiten).

### Checkliste für den Folge-Agenten:
1. `MarkdownView.java` in `ui/component/` übernehmen (Package anpassen).
2. `AboutUpdateDialog.java` anpassen: `txtNotes` (TextArea) durch `MarkdownView` ersetzen.
3. Unit-Tests ausführen (`mvn clean test`).
4. Build & Release durchführen.
5. Diese `SYNC_PENDING.md` löschen.

# BlockBlastBot 4.1 – One Upload

## Auf GitHub musst du nur 2 Dateien hochladen

```text
.github/
└── workflows/
    └── build-apk.yml

make_project.sh
```

Du musst **keine** `app/`, `src/`, `java/`, `res/` usw. auf GitHub anlegen.

### Build

1. Repository auf GitHub erstellen.
2. `make_project.sh` hochladen.
3. Den Ordner `.github/workflows` anlegen und darin `build-apk.yml` hochladen.
4. **Actions → Build BlockBlastBot 4.1 → Run workflow**.
5. Nach erfolgreichem Build beim Workflow unter **Artifacts** `BlockBlastBot-4.1-debug` herunterladen.

Die Action erzeugt beim Build automatisch das komplette Android-Projekt im Ordner `generated/`.

**Hinweis:** Diese 4.1-Version ist weiterhin die Logging-/Diagnoseversion. Die vollständige Vision → Board-Erkennung → Solver → Drag-Ausführung ist noch nicht fertig verdrahtet.

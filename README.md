# HTML Ordner

Eine kleine Android-App, in der du HTML-Dateien (z. B. Spiele) speicherst und mit einem Tipp wieder öffnest.

## Installation

1. Auf dem Handy den Tab **Releases** dieses Repos öffnen.
2. Die neueste `app-debug.apk` herunterladen.
3. Die Datei öffnen und installieren. Falls Android fragt, die Installation aus dieser Quelle einmal erlauben.

## Benutzung

- **HTML-Datei hinzufügen:** Button antippen und Datei(en) auswählen. Sie werden in der App gespeichert.
- **Öffnen:** Eintrag antippen. Die Datei läuft im Vollbild, „Zurück“ bringt dich zur Liste.
- **Löschen:** Eintrag lange drücken.
- **Aus dem Dateimanager:** Eine `.html`-Datei kann mit „Öffnen mit → HTML Ordner“ direkt übernommen werden.

## APK selbst bauen

Bei jedem Push auf `main` baut GitHub Actions die APK neu (Workflow `APK bauen`) und veröffentlicht sie unter Releases.

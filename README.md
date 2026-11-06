# MC Music — YouTube Music in Minecraft (Fabric 26.3)

Ein Musik-Client für **Minecraft Java 26.3 „Wilderness Bound"**, der wie eine moderne
Desktop-App aussieht: keine Pixel-Menüs, sondern ein hochauflösendes, abgerundetes
Interface mit Suche, Playlists, Favoriten und einem Player unten am Fenster — im Stil von
YouTube Music.

Musik wird **über Simple Voice Chat** ausgegeben. Das heißt: du hörst sie, und **alle
anderen auf dem Server, die Simple Voice Chat haben, hören sie auch** — ohne zusätzliche
Mod, ohne Download, ohne Client-Mod bei den Zuhörern.

```
/mcmusic                 öffnet das GUI   (oder Taste M)
/mcmusic play <url|id>   spielt einen YouTube-Music-Link direkt ab
/mcmusic stop            stoppt die Wiedergabe
```

![Design-Konzept](docs/design-concept.png)

*Design-Konzept des GUIs (Mockup, keine Spielaufnahme) — genau diese Anordnung setzt
`MusicScreen` um: Sidebar mit Playlists, Suchleiste oben, Karten-Grid, Player-Leiste unten.*

---

## Inhalte

* **Modernes GUI** – eigene UI-Bibliothek mit runden Ecken, Blur-Hintergrund, Hover-Animationen,
  Scroll-Listen, Slidern und einer selbst gebackenen, kantengeglätteten Schrift (kein Vanilla-Pixel-Font).
* **Suche** über eine Piped- oder Invidious-Instanz (kostenlos, selbst hostbar, kein API-Key).
* **Favoriten** und **Playlists** – lokal gespeichert in `config/mcmusic-library.json`.
* **Queue, Shuffle, Repeat, Seek, Lautstärke** – Lautstärke taucht in Simple Voice Chat als
  eigene Kategorie „Musikplayer" auf, jeder Spieler kann sie also für sich regeln.
* **Server-Streaming** – das Audio wird serverseitig dekodiert (48 kHz mono PCM) und als
  eigener SVC-Audiokanal an alle Verbundenen geschickt.
* **Zwei Themes** (Dark/Light) und reduzierte Animationen als Option.

---

## Voraussetzungen

| Was | Version |
|---|---|
| Minecraft Java Edition | **26.3** (Fabric unterstützt 26.3 seit September 2026) |
| Fabric Loader | ≥ 0.19.3 |
| Fabric API | 0.160.5+26.3 oder neuer |
| Java | **25** (26.3 verlangt Java 25) |
| Simple Voice Chat | ≥ 2.6.0 (Server **und** Client) |

Zuhörer brauchen **nur** Simple Voice Chat. Wer das GUI und die Suche nutzen will, braucht
MC Music auf dem Client.

---

## Installation

1. **Client:** `mcmusic-1.0.0.jar` nach `.minecraft/mods/` legen (zusammen mit Fabric API und
   Simple Voice Chat).
2. **Server:** dieselbe Jar nach `mods/` legen (zusammen mit Fabric API und Simple Voice Chat).
   Nur dann kann Musik an alle gestreamt werden.
3. Spiel starten, `M` drücken oder `/mcmusic` eingeben.

### Woher kommt das Audio?

MC Music kann drei Quellen nutzen (einstellbar in `config/mcmusic.json` → `audioBackend`):

| Backend | Was passiert | Wann sinnvoll |
|---|---|---|
| `ytdlp` | Server ruft `yt-dlp` auf und pipet das Ergebnis durch `ffmpeg` zu 48 kHz WAV | **Echte YouTube-Music-Links** |
| `http` | Provider liefert eine direkte Audio-URL (mp3/wav) | eigene Quellen, kleine Setups |
| `local` | Dateien aus `mcmusic-library/<videoId>.mp3` | DJ-Sets, keine Internetabhängigkeit |
| `auto` (Default) | lokale Datei → `yt-dlp` → direkte URL | alles |

Für echtes YouTube Music ist **`yt-dlp` + `ffmpeg` auf dem Server** nötig, weil YouTube
kein MP3/WAV ausliefert, sondern AAC/Opus — das muss einmal umkodiert werden. Beides sind
freie Kommandozeilen-Tools (`pip install yt-dlp`, `apt install ffmpeg`) und werden nur
ausgeführt, wenn `allowExternalTools: true` gesetzt ist. Für `local`/`http` braucht man sie nicht.

> **Rechtliches:** MC Music lädt keine Musik herunter und hostet nichts. Es dekodiert einen
> Stream, den du bzw. dein Server abruft — genau wie ein Browser. YouTube-Nutzungsbedingungen
> können das dennoch einschränken; betreibe das nur auf Servern, auf denen du das darfst,
> und nutze am besten eigene/kreativlizenzierte Musik oder eine eigene Piped-/Invidious-Instanz.

---

## Bedienung

| Aktion | Wo |
|---|---|
| GUI öffnen | Taste `M` oder `/mcmusic` |
| Suchen | Suchfeld oben, `Enter` |
| Abspielen | Song-Zeile oder Karte anklicken |
| Pause / Weiter | Play-Button unten oder `Leertaste` |
| Nächster / Vorheriger | Buttons unten |
| Favorit | Herz-Symbol in der Zeile oder neben dem Player |
| Zu Playlist hinzufügen | Plus-Symbol erscheint beim Hovern einer Zeile |
| Playlist anlegen | Bibliothek → „New playlist" |
| Provider/Theme ändern | Zahnrad unten links |
| Schließen | `Esc` oder X |

---

## Konfiguration

`config/mcmusic.json` (Client und Server nutzen dieselbe Datei):

```json
{
  "providerType": "piped",
  "pipedUrl": "https://pipedapi.kavin.rocks",
  "invidiousUrl": "https://inv.nadeko.net",
  "audioBackend": "auto",
  "allowExternalTools": true,
  "ytDlpPath": "yt-dlp",
  "ffmpegPath": "ffmpeg",
  "libraryFolder": "mcmusic-library",
  "volume": 0.65,
  "maxTrackSeconds": 900,
  "everyoneCanControl": true,
  "darkTheme": true
}
```

* `providerType` – `piped` oder `invidious`. Öffentliche Instanzen wechseln häufig; wenn die
  Suche fehlschlägt, trage im GUI unter Einstellungen → Instance eine funktionierende URL ein.
* `volume` – 0…1, wird zusätzlich zum SVC-Regler angewandt.
* `everyoneCanControl` – dürfen alle Spieler pausieren/springen oder nur wer gestartet hat.

Favoriten, Playlists und Verlauf liegen in `config/mcmusic-library.json`.

---

## Bauen

```bash
# Gradle 9.4+ und JDK 25 nötig
gradle build           # -> build/libs/mcmusic-1.0.0.jar
# oder einmalig einen Wrapper erzeugen:
gradle wrapper && ./gradlew build
```

In IntelliJ IDEA: Projekt öffnen, Gradle-Sync laufen lassen, `runClient` / `runServer`.

Die UI-Texturen (Font-Atlas, Icons, Ecken-Masken, Mod-Icon) sind **generiert**:

```bash
pip install pillow
python3 tools/gen_ui_assets.py
```

Damit werden `textures/gui/font.png`, `icons.png`, `corner*.png`, `shadow.png`,
`assets/mcmusic/icon.png` und die Metrik-Klasse `FontMetrics.java` neu erzeugt.

---

## Projektstruktur

```
src/main/java/de/chaostobimc/mcmusic/
├── MCMusic.java                  Server-Entrypoint, Payloads, Lifecycle
├── api/Track.java                Song-Datensatz + Netzwerk-Codec
├── api/LocalPlaylist.java
├── config/MusicConfig.java       GSON-Config (Client + Server)
├── provider/                     Piped- und Invidious-Anbindung (JSON/HTTP)
├── audio/                        PCM-Pipeline: Quellen, Decoder, Queue
├── voicechat/                    Simple-Voice-Chat-Integration
├── server/MusicServer.java       Wiedergabe-Zustand, Broadcast, Rechte
├── net/                          Payloads + Server-Handler
└── client/
    ├── MCMusicClient.java        Keybind, /mcmusic, Receiver
    ├── ClientMusic.java          Zustand, Queue, Bibliothek, Persistenz
    └── ui/                       Theme, Font, Zeichen-Primitive, MusicScreen
```

**Wie das Audio ankommt:** `MusicServer` erzeugt eine `PlaybackSession`. Die dekodiert den
Stream in 48-kHz-mono-PCM und schiebt alle 20 ms 960 Samples in einen
`StaticAudioChannel` von Simple Voice Chat, dessen Targets alle verbundenen Spieler sind.
Die Clients brauchen dafür nichts weiter zu tun — SVC spielt den Kanal ab, Lautstärke regelt
jeder über die Kategorie „Musikplayer".

---

## Bekannte Grenzen / ehrliche Hinweise

* **Es gibt keine offizielle YouTube-Music-API für Wiedergabe.** Suche und Metadaten laufen
  über Piped/Invidious; das eigentliche Audio kommt entweder von `yt-dlp`/`ffmpeg` (auf dem
  Server) oder aus einer direkten Quelle. Ohne diese Hilfsmittel bleibt nur die lokale
  Bibliothek.
* **Andere hören nur mit, wenn der Server die Mod hat** — simples Voice Chat allein überträgt
  kein fremdes Audio. Auf fremden Servern ohne die Mod funktioniert das GUI, aber nicht die
  Wiedergabe (die Mod sagt dir das im GUI).
* Dieses Repository wurde in einer Umgebung ohne Zugriff auf die Fabric-/Mojang-Maven-Repos
  erstellt. Der Code ist gegen die offiziellen 26.2/26.3-API-Signaturen (Mojang-Mappings,
  Fabric-API-Branch 26.3, SVC-API 2.6) geschrieben und syntaktisch geprüft, aber hier nicht
  kompiliert worden. Falls beim ersten `gradle build` etwas klemmt: die Meldungen sind
  API-Details, die sich meist in einer Zeile beheben lassen.
* Piped/Invidious-Instanzen sind oft überlastet. Das GUI zeigt Fehler an und du kannst die
  Instanz in den Einstellungen wechseln.

## Lizenz

MIT — siehe [LICENSE](LICENSE). Die gebackene Schrift basiert auf DejaVu Sans
(Bitstream Vera Lizenz, frei weiterverwendbar); die Icons sind generiert.

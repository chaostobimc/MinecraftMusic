# Minecraft Music

YouTube Music direkt in Minecraft: eigene Desktop-Oberfläche, Suche, Playlists,
Favoriten – und Wiedergabe über **Simple Voice Chat**, sodass alle Spieler in der
Nähe mithören können, ohne die Mod zu installieren.

![Minecraft 26.3](https://img.shields.io/badge/Minecraft-26.3-informational)
![Fabric](https://img.shields.io/badge/Loader-Fabric%200.20.1-success)
![Java 25](https://img.shields.io/badge/Java-25-orange)

---

## Was es kann

| Bereich | Funktion |
| --- | --- |
| **Interface** | Eigene Desktop-Optik (dunkles Theme, echte Roboto-/Material-Fonts, gerundete Flächen, weiche Schatten) – kein Vanilla-Look |
| **Suche** | YouTube-Music-Suche mit Debounce, Ergebnisliste mit Cover, Dauer, Favoriten-Stern |
| **Wiedergabe** | Queue, Shuffle, Repeat (aus/alle/ein), Seek über die Fortschrittsleiste, Lautstärke |
| **Bibliothek** | Favoriten, eigene Playlists anlegen/öffnen/löschen, „Zuletzt gehört“ |
| **Cover** | Thumbnails werden asynchron geladen und als dynamische Texturen eingebunden |
| **Voice Chat** | Der eigene Stream läuft über einen SVC-*locational*-Kanal am Spieler – andere hören mit, räumlich begrenzt (Standard 64 Blöcke) |
| **Ohne SVC** | Volle lokale Wiedergabe über ein eigenes OpenAL-Gerät; am Server ist keine Änderung nötig |

## Installation

1. **Fabric Loader ≥ 0.20.1** für **Minecraft 26.3** installieren.
2. **Fabric API** (0.165.0+26.3 oder neuer) in `mods/`.
3. **Simple Voice Chat** (2.7.x für 26.3) installieren – auf Client **und** Server,
   wenn andere mithören sollen. Ohne SVC funktioniert alles außer dem Broadcast.
4. `minecraftmusic-1.0.0.jar` in `mods/`.

LavaPlayer, youtube-source und der Opus-Encoder (Concentus) sind bereits in der
Jar enthalten – es muss nichts zusätzlich installiert werden.

## Bedienung

- **`M`** (frei belegbar) oder **`/mcmusic`** öffnet das Fenster.
- **`Leertaste`** pausiert / setzt fort (solange die Suche nicht fokussiert ist).
- **`Esc`** löst zuerst den Fokus der Suche und schließt erst danach das Fenster.
- Beim Tippen im Suchfeld gehen Tasten an das Feld; `Enter` sucht und gibt den
  Fokus wieder frei, damit `Leertaste` erneut pausiert.
- `/mcmusic stop` beendet die Wiedergabe, `/mcmusic broadcast` schaltet den
  Voice-Chat-Broadcast um (wirkt sofort, auch für einen laufenden Titel).

Unten in der Player-Leiste: Cover, Titel, Shuffle · Zurück · Play/Pause · Weiter ·
Repeat, Fortschrittsbalken (Klick = Springen), Lautstärke, Broadcast-Schalter und
Favoriten-Herz.

## Wie der Ton zu anderen kommt

```
Client                                  Server
──────                                  ──────
LavaPlayer dekodiert 48 kHz mono
   │
PlaybackPump: Volume + Opus (20 ms)
   ├──► eigenes OpenAL-Gerät  (man selbst hört lokal)
   └──► MusicFramePayload ───────────► SvcStreamHandler
                                          │  LocationalAudioChannel
                                          ▼
                                    alle SVC-Spieler in Reichweite
```

Der Server schreibt die fertig encodierten Frames direkt in einen
SVC-Locational-Channel; Zuhörer brauchen nur Simple Voice Chat. Der Kanal filtert
den Sender heraus, damit sich lokale Wiedergabe und Voice-Chat nicht überlagern.
Die Musik hat eine eigene SVC-Lautstärkekategorie (*Minecraft Music*).

Die eigene Wiedergabe läuft über ein **eigenes OpenAL-Gerät mit eigenem Context**
auf einem eigenen Thread – Minecrafts AL-Context ist thread-lokal und gehört dem
Render-Thread, ein Zugriff von außen würde den Sound zerstören.

## Bauen

```bash
./gradlew build
```

Ergebnis: `build/libs/minecraftmusic-1.0.0.jar`.

Voraussetzungen: JDK 25. Beim ersten Lauf lädt Loom die Minecraft-Artefakte und
die Audio-Bibliotheken.

Der Build prüft automatisch (`checkEmbeddedBytecode`), dass die eingebetteten
Bibliotheken keinen Bytecode neuer als Java 21 enthalten – sonst bricht der
Mod-Load auf Java-21-Runtimes.

> **Hinweis:** Der Code in diesem Repository wurde vollständig gegen echte
> Minecraft-Klassen, die Fabric API 0.153, LavaPlayer 2.2.7, youtube-source v2,
> die Simple-Voice-Chat-API 2.7.0 und Concentus 1.0.1 typgeprüft und kompiliert.
> Ein vollständiger `gradlew build` (mit Remapping) muss auf einem Rechner mit
> Netzwerkzugang erfolgen.

## Projektstruktur

```
src/main/java/…        Gemeinsamer Code: Payloads, Server-Stream-Management,
                       SVC-Bridge, Opus-Encoding, Config
src/client/java/…      Client: Screen/GUI, Player, Playback-Pump, Thumbnails,
                       Command, Keybind, lokale Audioausgabe
src/main/resources/    fabric.mod.json, Fonts (Roboto, Material Icons),
                       gerenderte 9-Slice-Texturen, Sprache
```

## Danksagung / Lizenzen

- **LavaPlayer** (lavalink-devs) – Apache-2.0
- **youtube-source** (lavalink-devs) – MIT
- **Concentus** – BSD-3-Clause
- **Roboto** – Apache-2.0 · **Material Icons** – Apache-2.0
- **Simple Voice Chat API** (henkelmax) – siehe SVC-Repository

Der Quellcode dieser Mod steht unter der MIT-Lizenz.

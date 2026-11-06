package com.chaostobimc.minecraftmusic.gui;

import com.chaostobimc.minecraftmusic.audio.AudioEngine;
import com.chaostobimc.minecraftmusic.audio.ClientNetworkingHolder;
import com.chaostobimc.minecraftmusic.audio.MusicPlayer;
import com.chaostobimc.minecraftmusic.audio.Track;
import com.chaostobimc.minecraftmusic.config.ConfigManager;
import com.chaostobimc.minecraftmusic.config.ModConfig;
import com.chaostobimc.minecraftmusic.gui.widgets.HitList;
import com.chaostobimc.minecraftmusic.thumbnails.ThumbnailCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Das YouTube-Music-Fenster.
 * <p>
 * Aufbau wie in der Desktop-App: linke Navigation, Kopfzeile mit Suche,
 * scrollbarer Inhalt, unten die Player-Leiste. Alles vektorbasiert
 * gerundet und mit Roboto-Text, damit es nicht nach Vanilla-Minecraft aussieht.
 */
public class McMusicScreen extends Screen {

    private final HitList hits = new HitList();

    private View view = View.HOME;
    private EditBox searchBox;
    /**
     * Suchfeld hat den Tastaturfokus. Bewusst eigener Zustand: das Feld ist im
     * Header auf jeder Ansicht sichtbar, soll aber nur nach einem Klick tippen.
     */
    private boolean searchFocused;

    private final List<Track> results = new ArrayList<>();
    private String statusText = "";
    private String statusText2 = "";
    private String lastQuery = "";
    private int maxScroll;
    private static boolean recentListenerAdded;
    private boolean loading;
    private long lastSearchAt;

    private double scroll;
    private double scrollTarget;
    private long lastFrameTime = System.currentTimeMillis();

    private String openPlaylist;

    // Player-Leiste

    public McMusicScreen() {
        super(Component.literal("YouTube Music"));
    }

    // ==================================================================
    //  Lifecycle
    // ==================================================================

    @Override
    protected void init() {
        super.init();
        hits.clear();

        searchBox = new EditBox(font, 0, 0, 10, 10, Component.literal("Suche"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(120);
        searchBox.setVisible(false);
        addRenderableWidget(searchBox);

        if (!recentListenerAdded) {
            recentListenerAdded = true;
            MusicPlayer.get().addTrackListener(track -> {
                // Queue-Historie für "Zuletzt gehört"
                ModConfig config = ConfigManager.get();
                config.recentlyPlayed.removeIf(t -> t.id.equals(track.id));
                config.recentlyPlayed.add(0, track.toStored());
                while (config.recentlyPlayed.size() > 30) {
                    config.recentlyPlayed.remove(config.recentlyPlayed.size() - 1);
                }
                ConfigManager.save();
            });
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Kein Vanilla-Hintergrund: wir zeichnen unseren eigenen Verlauf.
     * Ohne dieses Override wuerden Panorama/Dirt-Textur und Blur darueberliegen.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        drawBackground(g);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void resize(int width, int height) {
        // Screen.resize() ruft bereits repositionElements() -> rebuildWidgets() -> init().
        String search = searchBox == null ? "" : searchBox.getValue();
        super.resize(width, height);
        if (searchBox != null) {
            searchBox.setValue(search);
        }
    }

    // ==================================================================
    //  Rendern
    // ==================================================================

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        hits.clear();

        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (now - lastFrameTime) / 1000F);
        lastFrameTime = now;

        // sanftes Scrollen
        scroll += (scrollTarget - scroll) * Math.min(1F, dt * 18F);
        if (Math.abs(scrollTarget - scroll) < 0.4) {
            scroll = scrollTarget;
        }

        UiContext ui = new UiContext(g, font, mouseX, mouseY, delta);

        int x = 0;
        int y = 0;
        int w = width;
        int h = height;

        drawSidebar(ui, x, y, Theme.SIDEBAR_W, h - Theme.PLAYER_H);
        drawHeader(ui, Theme.SIDEBAR_W, 0, w - Theme.SIDEBAR_W, Theme.HEADER_H);
        drawContent(ui, Theme.SIDEBAR_W, Theme.HEADER_H,
                w - Theme.SIDEBAR_W, h - Theme.PLAYER_H - Theme.HEADER_H);
        drawPlayerBar(ui, 0, h - Theme.PLAYER_H, w, Theme.PLAYER_H);

        // Tooltip
        var hovered = hits.hovered(mouseX, mouseY);
        if (hovered != null && hovered.tooltip != null) {
            g.setTooltipForNextFrame(font, Component.literal(hovered.tooltip), mouseX, mouseY);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void drawBackground(GuiGraphicsExtractor g) {
        int h = height;
        // vertikaler Verlauf in Baendern
        int bands = Math.max(1, h / 2);
        for (int i = 0; i < bands; i++) {
            float t = i / (float) bands;
            int color = lerpColor(Ui.BG_TOP, Ui.BG_BOTTOM, t);
            int y0 = h * i / bands;
            int y1 = h * (i + 1) / bands;
            g.fill(0, y0, width, y1, color);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    // ------------------------------------------------------------------
    //  Sidebar
    // ------------------------------------------------------------------

    private void drawSidebar(UiContext ui, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ui.graphics;
        g.fill(x, y, x + w, y + h, Ui.SIDEBAR);
        g.fill(x + w - 1, y, x + w, y + h, Ui.BORDER);

        // Logo
        int logoY = y + 16;
        Ui.icon16(g, font, Icons.MUSIC_NOTE, x + 16, logoY + 1, Ui.ACCENT);
        Ui.text13Bold(g, font, "Music", x + 38, logoY, Ui.TEXT);
        Ui.text11(g, font, "in Minecraft", x + 38, logoY + 13, Ui.TEXT_MUTED);

        int navY = logoY + 40;
        for (View v : new View[]{View.HOME, View.EXPLORE, View.SEARCH}) {
            navY = drawNavItem(ui, v, x, navY, w);
        }

        navY += 8;
        Ui.text11Med(g, font, "MEDIATHEK", x + 16, navY, Ui.TEXT_MUTED);
        navY += 16;

        for (View v : new View[]{View.PLAYLISTS, View.FAVORITES}) {
            navY = drawNavItem(ui, v, x, navY, w);
        }

        // Playlists
        navY += 10;
        ModConfig config = ConfigManager.get();
        for (ModConfig.StoredPlaylist playlist : config.playlists) {
            if (navY > y + h - 46) {
                break;
            }
            int itemY = navY;
            boolean hovered = ui.hovered(x + 8, itemY, w - 16, Theme.NAV_ITEM_H);
            if (hovered) {
                Ui.roundRect(g, x + 8, itemY, w - 16, Theme.NAV_ITEM_H, Theme.R_SMALL, Ui.SURFACE_HOVER);
            }
            Ui.iconCentered(g, font, Icons.QUEUE, x + 23, itemY + Theme.NAV_ITEM_H / 2, Ui.ICON_9, Ui.TEXT_DIM);
            Ui.text11(g, font, Ui.trim11(font, playlist.name, w - 52), x + 38, itemY + 7, Ui.TEXT_DIM);
            hits.add(x + 8, itemY, w - 16, Theme.NAV_ITEM_H, () -> {
                openPlaylist = playlist.name;
                view = View.PLAYLISTS;
                scrollTarget = 0;
                scroll = 0;
            });
            navY += Theme.NAV_ITEM_H;
        }

        // Einstellungen unten
        int settingsY = y + h - 34;
        boolean hovered = ui.hovered(x + 8, settingsY, w - 16, Theme.NAV_ITEM_H);
        if (hovered) {
            Ui.roundRect(g, x + 8, settingsY, w - 16, Theme.NAV_ITEM_H, Theme.R_SMALL, Ui.SURFACE_HOVER);
        }
        Ui.iconCentered(g, font, Icons.SETTINGS, x + 23, settingsY + Theme.NAV_ITEM_H / 2, Ui.ICON_9, Ui.TEXT_DIM);
        Ui.text11(g, font, "Einstellungen", x + 38, settingsY + 7, Ui.TEXT_DIM);
        hits.add(x + 8, settingsY, w - 16, Theme.NAV_ITEM_H, () -> {
            view = View.SETTINGS;
            scrollTarget = 0;
            scroll = 0;
        });
    }

    private int drawNavItem(UiContext ui, View v, int x, int y, int w) {
        GuiGraphicsExtractor g = ui.graphics;
        boolean selected = view == v;
        boolean hovered = ui.hovered(x + 8, y, w - 16, Theme.NAV_ITEM_H);

        if (selected) {
            Ui.roundRect(g, x + 8, y, w - 16, Theme.NAV_ITEM_H, Theme.R_SMALL, Ui.SURFACE_HI);
        } else if (hovered) {
            Ui.roundRect(g, x + 8, y, w - 16, Theme.NAV_ITEM_H, Theme.R_SMALL, Ui.SURFACE_HOVER);
        }
        int color = selected ? Ui.TEXT : Ui.TEXT_DIM;
        Ui.iconCentered(g, font, v.icon, x + 23, y + Theme.NAV_ITEM_H / 2, Ui.ICON_9,
                selected ? Ui.ACCENT : Ui.TEXT_MUTED);
        Ui.text11Med(g, font, v.label, x + 38, y + 7, color);

        hits.add(x + 8, y, w - 16, Theme.NAV_ITEM_H, () -> {
            view = v;
            scrollTarget = 0;
            scroll = 0;
            openPlaylist = null;
            if (v == View.SEARCH) {
                focusSearch(true);
            } else {
                focusSearch(false);
            }
        });
        return y + Theme.NAV_ITEM_H;
    }

    // ------------------------------------------------------------------
    //  Header / Suche
    // ------------------------------------------------------------------

    private void drawHeader(UiContext ui, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ui.graphics;
        g.fill(x, y, x + w, y + h, 0xCC121214);
        g.fill(x, y + h - 1, x + w, y + h, Ui.BORDER);

        int searchW = Math.min(420, w - 200);
        int searchX = x + 20;
        int searchY = y + (h - 28) / 2;
        boolean hovered = ui.hovered(searchX, searchY, searchW, 28);

        Ui.roundRect(g, searchX, searchY, searchW, 28, Theme.R_PILL,
                hovered ? Ui.SURFACE_HI : Ui.SURFACE);
        Ui.iconCentered(g, font, Icons.SEARCH, searchX + 16, searchY + 14, Ui.ICON_12, Ui.TEXT_MUTED);

        String value = searchBox.getValue();
        if (value.isEmpty() && !searchFocused) {
            Ui.text11(g, font, "Titel, Künstler oder Album suchen", searchX + 30, searchY + 9, Ui.TEXT_MUTED);
        }
        searchBox.setX(searchX + 30);
        searchBox.setY(searchY + 9);
        searchBox.setWidth(searchW - 44);
        searchBox.setHeight(12);
        searchBox.setVisible(true);
        searchBox.setTextColor(Ui.TEXT);
        // Fokus nur bei echter Zustandsaenderung setzen - jedes Frame wuerde
        // focusedTime neu starten (Cursor blinkt nicht) und Preedit-Events spammen.
        if (searchBox.isFocused() != searchFocused) {
            searchBox.setFocused(searchFocused);
            if (!searchFocused) {
                clearFocus();
            }
        }

        hits.add(searchX, searchY, searchW, 28, () -> focusSearch(true));

        // Suche ausloesen
        if (searchFocused) {
            String query = searchBox.getValue().trim();
            if (!query.isEmpty() && now() - lastSearchAt > 450) {
                if (!query.equals(lastQuery)) {
                    lastQuery = query;
                    lastSearchAt = now();
                    performSearch(query);
                    view = View.SEARCH;
                }
            }
        }
    }

    /** Setzt oder loescht den Tastaturfokus des Suchfelds. */
    private void focusSearch(boolean focused) {
        if (searchBox == null) {
            return;
        }
        searchFocused = focused;
        searchBox.setFocused(focused);
        if (focused) {
            // Erst in die Suchansicht wechseln, sonst zeigt der Inhalt nichts an.
            view = View.SEARCH;
            focusSearchField();
        } else {
            clearFocus();
        }
    }

    private void focusSearchField() {
        if (getFocused() != searchBox) {
            setFocused(searchBox);
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private void performSearch(String query) {
        loading = true;
        statusText = "";
        AudioEngine.get().search(query, 40, result -> Minecraft.getInstance().execute(() -> {
            loading = false;
            results.clear();
            if (result.isError()) {
                statusText = result.error();
            } else {
                results.addAll(result.tracks());
                if (results.isEmpty()) {
                    statusText = "Keine Ergebnisse für \"" + query + "\"";
                }
            }
            scrollTarget = 0;
            scroll = 0;
        }));
    }

    // ------------------------------------------------------------------
    //  Inhalt
    // ------------------------------------------------------------------

    private void drawContent(UiContext ui, int x, int y, int w, int h) {
        switch (view) {
            case HOME -> drawHome(ui, x, y, w, h);
            case SEARCH -> drawSearch(ui, x, y, w, h);
            case FAVORITES -> drawFavorites(ui, x, y, w, h);
            case PLAYLISTS -> drawPlaylists(ui, x, y, w, h);
            case EXPLORE -> drawExplore(ui, x, y, w, h);
            case SETTINGS -> drawSettings(ui, x, y, w, h);
            default -> drawHome(ui, x, y, w, h);
        }
    }

    // ---- Home ----

    private void drawHome(UiContext ui, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ui.graphics;
        int pad = 22;
        int contentX = x + pad;
        int contentW = w - pad * 2;

        ui.pushClip(x, y, w, h);
        int cy = y + (int) -scroll + 12;

        // Hero-Karte
        int heroH = 132;
        if (cy + heroH > y && cy < y + h) {
            Ui.roundRect(g, contentX, cy, contentW, heroH, Theme.R_PANEL, 0xFF2A1B22);
            g.fillGradient(contentX + 1, cy + 1, contentX + contentW - 1, cy + heroH - 1, 0x33FF4D57, 0x00000000);

            Ui.text11Med(g, font, "DEIN MIX", contentX + 24, cy + 22, Ui.ACCENT);
            Ui.text26Bold(g, font, "Musik, die zu", contentX + 24, cy + 38, Ui.TEXT);
            Ui.text26Bold(g, font, "Minecraft passt", contentX + 24, cy + 64, Ui.TEXT);
            Ui.text11(g, font, "Suche nach Songs und starte deinen eigenen Stream.", contentX + 24, cy + 94, Ui.TEXT_DIM);

            Ui.roundRect(g, contentX + 24, cy + 108, 104, 18, Theme.R_PILL, Ui.ACCENT);
            Ui.text11Med(g, font, "Jetzt entdecken", contentX + 40, cy + 113, 0xFFFFFFFF);
            hits.add(contentX + 24, cy + 108, 104, 18, () -> {
                view = View.SEARCH;
                searchBox.setFocused(true);
            });

            // Deko-EQ
            for (int i = 0; i < 7; i++) {
                int barH = 12 + (i * 9) % 40;
                int bx = contentX + contentW - 40 - i * 14;
                int by = cy + heroH - 20 - barH;
                Ui.roundRect(g, bx, by, 6, barH, 3, 0x55FF4D57);
            }
        }
        cy += heroH + 26;

        // Zuletzt gehoert
        ModConfig config = ConfigManager.get();
        if (!config.recentlyPlayed.isEmpty()) {
            cy = sectionTitle(ui, "Zuletzt gehört", contentX, cy, contentW);
            List<Track> recent = new ArrayList<>();
            for (var stored : config.recentlyPlayed) {
                recent.add(Track.fromStored(stored));
            }
            cy = cardRow(ui, recent, contentX, cy, contentW);
            cy += 26;
        }

        // Favoriten
        if (!config.favorites.isEmpty()) {
            cy = sectionTitle(ui, "Deine Favoriten", contentX, cy, contentW);
            List<Track> favs = new ArrayList<>();
            for (var stored : config.favorites) {
                favs.add(Track.fromStored(stored));
            }
            cy = cardRow(ui, favs, contentX, cy, contentW);
            cy += 26;
        }

        // Beliebte Suchvorschlaege
        cy = sectionTitle(ui, "Stimmungen", contentX, cy, contentW);
        String[] moods = {"Chill", "Focus", "Epic", "Lo-Fi", "Synthwave", "C418"};
        int chipX = contentX;
        int chipY = cy;
        for (String mood : moods) {
            int chipW = Ui.width11Med(font, mood) + 26;
            if (chipX + chipW > contentX + contentW) {
                chipX = contentX;
                chipY += 28;
            }
            boolean hovered = ui.hovered(chipX, chipY, chipW, 22);
            Ui.roundRect(g, chipX, chipY, chipW, 22, Theme.R_PILL,
                    hovered ? Ui.SURFACE_HI : Ui.SURFACE);
            Ui.text11Med(g, font, mood, chipX + 13, chipY + 7, Ui.TEXT_DIM);
            hits.add(chipX, chipY, chipW, 22, () -> {
                searchBox.setValue(mood + " music");
                lastQuery = "";
                searchBox.setFocused(true);
                view = View.SEARCH;
                performSearch(mood + " music");
            });
            chipX += chipW + 8;
        }
        cy = chipY + 40;

        updateScrollLimit(cy - (y - (int) scroll), h);
        ui.popClip();
    }

    private int sectionTitle(UiContext ui, String title, int x, int y, int w) {
        Ui.text18Bold(ui.graphics, font, title, x, y, Ui.TEXT);
        return y + 26;
    }

    /** Karten-Reihe (wie YouTube-Music-Shelves). */
    private int cardRow(UiContext ui, List<Track> tracks, int x, int y, int width) {
        int cardW = Theme.CARD_W;
        int gap = 12;
        int perRow = Math.max(1, (width + gap) / (cardW + gap));
        int count = Math.min(tracks.size(), perRow * 2);
        int cx = x;
        int cy = y;
        for (int i = 0; i < count; i++) {
            if (i > 0 && i % perRow == 0) {
                cx = x;
                cy += Theme.CARD_H + gap;
            }
            Track track = tracks.get(i);
            drawTrackCard(ui, track, cx, cy, cardW, Theme.CARD_H);
            cx += cardW + gap;
        }
        int rows = (count + perRow - 1) / perRow;
        return y + rows * (Theme.CARD_H + gap);
    }

    private void drawTrackCard(UiContext ui, Track track, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ui.graphics;
        boolean hovered = ui.hovered(x, y, w, h);
        int artH = w;

        // Cover
        Identifier cover = ThumbnailCache.get().get(track.artworkUrl);
        if (cover != null) {
            Ui.image(g, cover, x, y, w, artH);
            // Abdunkeln unten fuer die Abspielwolke
            if (hovered) {
                g.fillGradient(x, y + artH - 40, x + w, y + artH, 0x00000000, 0x88000000);
            }
        } else {
            Ui.roundRect(g, x, y, w, artH, Theme.R_CARD, Ui.SURFACE);
            Ui.iconCentered(g, font, Icons.MUSIC_NOTE, x + w / 2, y + artH / 2, Ui.ICON_22, Ui.TEXT_MUTED);
        }

        if (hovered) {
            int btn = 32;
            int bx = x + w - btn - 8;
            int by = y + artH - btn - 8;
            g.fill(bx, by, bx + btn, by + btn, 0xFF000000);
            Ui.iconCentered(g, font, Icons.PLAY, bx + btn / 2, by + btn / 2, Ui.ICON_16, 0xFFFFFFFF);
        }

        Ui.text11Med(g, font, Ui.trim11Med(font, track.title, w), x, y + artH + 8, Ui.TEXT);
        Ui.text11(g, font, Ui.trim11(font, track.artist, w), x, y + artH + 21, Ui.TEXT_MUTED);

        hits.add(x, y, w, h, () -> playSingle(track), track.title + " - " + track.artist);
    }

    // ---- Suche ----

    private void drawSearch(UiContext ui, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ui.graphics;
        int pad = 22;
        int contentX = x + pad;
        int contentW = w - pad * 2;

        ui.pushClip(x, y, w, h);
        int cy = y - (int) scroll + 12;

        if (loading) {
            Ui.text11(g, font, "Suche läuft …", contentX, cy, Ui.TEXT_DIM);
            ui.popClip();
            return;
        }
        if (!statusText.isEmpty()) {
            Ui.text11(g, font, statusText, contentX, cy, Ui.TEXT_DIM);
            ui.popClip();
            return;
        }
        if (results.isEmpty()) {
            Ui.text18Bold(g, font, "Was willst du hören?", contentX, cy + 10, Ui.TEXT);
            Ui.text11(g, font, "Tippe oben in die Suche. Ergebnisse erscheinen sofort.", contentX, cy + 40, Ui.TEXT_DIM);
            ui.popClip();
            return;
        }

        Ui.text18Bold(g, font, "Suchergebnisse", contentX, cy, Ui.TEXT);
        cy += 26;
        int rowH = Theme.ROW_H + 6;
        for (int i = 0; i < results.size(); i++) {
            Track track = results.get(i);
            if (cy + rowH > y && cy < y + h) {
                drawTrackRow(ui, track, contentX, cy, contentW, i + 1);
            }
            cy += rowH;
        }
        updateScrollLimit(cy - (y - (int) scroll), h);
        ui.popClip();
    }

    /** Listenzeile mit Nummer, Cover, Titel, Kuenstler, Dauer und Favoriten-Stern. */
    private void drawTrackRow(UiContext ui, Track track, int x, int y, int w, int index) {
        GuiGraphicsExtractor g = ui.graphics;
        int h = Theme.ROW_H;
        boolean hovered = ui.hovered(x, y, w, h);
        boolean current = MusicPlayer.get().current() != null && MusicPlayer.get().current().id.equals(track.id);

        if (hovered || current) {
            Ui.roundRect(g, x, y, w, h, Theme.R_SMALL, hovered ? Ui.SURFACE_HOVER : Ui.SURFACE);
        }

        // Nummer / Play
        if (hovered) {
            Ui.icon12(g, font, Icons.PLAY, x + 10, y + 14, Ui.TEXT);
        } else {
            Ui.text11(g, font, String.valueOf(index), x + 13, y + 15,
                    current ? Ui.ACCENT : Ui.TEXT_MUTED);
        }

        // Cover
        int artSize = 30;
        int artX = x + 32;
        int artY = y + (h - artSize) / 2;
        Identifier cover = ThumbnailCache.get().get(track.artworkUrl);
        if (cover != null) {
            Ui.image(g, cover, artX, artY, artSize, artSize);
        } else {
            Ui.roundRect(g, artX, artY, artSize, artSize, 3, Ui.SURFACE);
            Ui.icon9(g, font, Icons.MUSIC_NOTE, artX + 10, artY + 11, Ui.TEXT_MUTED);
        }

        int textX = artX + artSize + 12;
        int textW = w - (textX - x) - 120;
        Ui.text11Med(g, font, Ui.trim11Med(font, track.title, textW), textX, y + 9,
                current ? Ui.ACCENT : Ui.TEXT);
        Ui.text11(g, font, Ui.trim11(font, track.artist, textW), textX, y + 23, Ui.TEXT_MUTED);

        // Dauer
        String duration = track.durationText();
        Ui.text11(g, font, duration, x + w - 96, y + 15, Ui.TEXT_MUTED);

        // Favorit
        boolean fav = ConfigManager.get().isFavorite(track.id);
        int favX = x + w - 30;
        if (fav || hovered) {
            Ui.icon9(g, font, fav ? Icons.FAVORITE : Icons.FAVORITE_BORDER,
                    favX + 6, y + 16, fav ? Ui.ACCENT : Ui.TEXT_MUTED);
            hits.add(favX, y + 8, 20, 20, () -> {
                boolean added = ConfigManager.get().toggleFavorite(track.toStored());
                ConfigManager.save();
                statusText2 = added ? "Zu Favoriten hinzugefuegt" : "Aus Favoriten entfernt";
            }, fav ? "Favorit entfernen" : "Zu Favoriten");
        }

        hits.add(x, y, w, h, () -> playSingle(track), "Abspielen");
    }

    // ---- Favoriten ----

    private void drawFavorites(UiContext ui, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ui.graphics;
        int pad = 22;
        int contentX = x + pad;
        int contentW = w - pad * 2;
        ModConfig config = ConfigManager.get();

        ui.pushClip(x, y, w, h);
        int cy = y - (int) scroll + 12;

        Ui.icon22(g, font, Icons.FAVORITE, contentX, cy, Ui.ACCENT);
        Ui.text26Bold(g, font, "Favoriten", contentX, cy + 28, Ui.TEXT);
        Ui.text11(g, font, config.favorites.size() + " Titel", contentX, cy + 60, Ui.TEXT_MUTED);
        cy += 84;

        if (config.favorites.isEmpty()) {
            Ui.text11(g, font, "Noch keine Favoriten. Klicke bei einem Song auf das Herz.", contentX, cy, Ui.TEXT_DIM);
            ui.popClip();
            return;
        }

        if (!statusText2.isEmpty()) {
            Ui.text11(g, font, statusText2, contentX, cy, Ui.GREEN);
            cy += 16;
        }

        // Play-All
        int btnW = 120;
        Ui.roundRect(g, contentX, cy, btnW, 26, Theme.R_PILL, Ui.ACCENT);
        Ui.icon12(g, font, Icons.PLAY, contentX + 14, cy + 7, 0xFFFFFFFF);
        Ui.text11Med(g, font, "Alle abspielen", contentX + 34, cy + 8, 0xFFFFFFFF);
        hits.add(contentX, cy, btnW, 26, () -> {
            List<Track> tracks = new ArrayList<>();
            for (var stored : config.favorites) {
                tracks.add(Track.fromStored(stored));
            }
            MusicPlayer.get().playQueue(tracks, 0);
        });
        cy += 44;

        int index = 1;
        for (var stored : config.favorites) {
            Track track = Track.fromStored(stored);
            if (cy + Theme.ROW_H + 6 > y && cy < y + h) {
                drawTrackRow(ui, track, contentX, cy, contentW, index);
            }
            cy += Theme.ROW_H + 6;
            index++;
        }
        updateScrollLimit(cy - (y - (int) scroll), h);
        ui.popClip();
    }

    // ---- Playlists ----

    private void drawPlaylists(UiContext ui, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ui.graphics;
        int pad = 22;
        int contentX = x + pad;
        int contentW = w - pad * 2;
        ModConfig config = ConfigManager.get();

        ui.pushClip(x, y, w, h);
        int cy = y - (int) scroll + 12;

        if (openPlaylist == null) {
            Ui.icon22(g, font, Icons.PLAYLIST, contentX, cy, Ui.ACCENT);
            Ui.text26Bold(g, font, "Playlists", contentX, cy + 28, Ui.TEXT);
            cy += 70;

            // neue Playlist
            int newW = 150;
            Ui.roundRect(g, contentX, cy, newW, 26, Theme.R_PILL, Ui.SURFACE_HI);
            Ui.icon12(g, font, Icons.ADD, contentX + 12, cy + 7, Ui.TEXT);
            Ui.text11Med(g, font, "Neue Playlist", contentX + 32, cy + 8, Ui.TEXT);
            hits.add(contentX, cy, newW, 26, this::createPlaylist);
            cy += 42;

            for (ModConfig.StoredPlaylist playlist : config.playlists) {
                int rowH = 54;
                boolean hovered = ui.hovered(contentX, cy, contentW, rowH);
                if (hovered) {
                    Ui.roundRect(g, contentX, cy, contentW, rowH, Theme.R_SMALL, Ui.SURFACE_HOVER);
                }
                Ui.roundRect(g, contentX + 6, cy + 8, 38, 38, Theme.R_SMALL, Ui.SURFACE);
                Ui.icon16(g, font, Icons.QUEUE, contentX + 17, cy + 19, Ui.ACCENT);

                Ui.text11Med(g, font, Ui.trim11Med(font, playlist.name, contentW - 130),
                        contentX + 54, cy + 12, Ui.TEXT);
                Ui.text11(g, font, playlist.tracks.size() + " Titel", contentX + 54, cy + 28, Ui.TEXT_MUTED);

                Ui.icon9(g, font, Icons.MORE, contentX + contentW - 24, cy + 22, Ui.TEXT_MUTED);
                hits.add(contentX, cy, contentW - 26, rowH, () -> {
                    openPlaylist = playlist.name;
                    scrollTarget = 0;
                    scroll = 0;
                });
                hits.add(contentX + contentW - 30, cy + 12, 24, 28, () -> {
                    config.playlists.remove(playlist);
                    ConfigManager.save();
                }, "Playlist loeschen");
                cy += rowH;
            }
        } else {
            ModConfig.StoredPlaylist playlist = null;
            for (ModConfig.StoredPlaylist p : config.playlists) {
                if (p.name.equals(openPlaylist)) {
                    playlist = p;
                    break;
                }
            }
            if (playlist == null) {
                openPlaylist = null;
                ui.popClip();
                return;
            }

            // Zurueck
            Ui.icon12(g, font, Icons.BACK, contentX, cy, Ui.TEXT_DIM);
            hits.add(contentX - 4, cy - 4, 24, 24, () -> {
                openPlaylist = null;
                scrollTarget = 0;
                scroll = 0;
            });
            cy += 24;

            Ui.text26Bold(g, font, playlist.name, contentX, cy, Ui.TEXT);
            cy += 36;
            Ui.text11(g, font, playlist.tracks.size() + " Titel", contentX, cy, Ui.TEXT_MUTED);
            cy += 24;

            if (playlist.tracks.isEmpty()) {
                Ui.text11(g, font, "Noch keine Titel. Nutze das + bei einem Song.", contentX, cy, Ui.TEXT_DIM);
            } else {
                int playW = 100;
                Ui.roundRect(g, contentX, cy, playW, 26, Theme.R_PILL, Ui.ACCENT);
                Ui.icon12(g, font, Icons.PLAY, contentX + 12, cy + 7, 0xFFFFFFFF);
                Ui.text11Med(g, font, "Abspielen", contentX + 32, cy + 8, 0xFFFFFFFF);
                final ModConfig.StoredPlaylist target = playlist;
                hits.add(contentX, cy, playW, 26, () -> {
                    List<Track> tracks = new ArrayList<>();
                    for (var stored : target.tracks) {
                        tracks.add(Track.fromStored(stored));
                    }
                    MusicPlayer.get().playQueue(tracks, 0);
                });
                cy += 44;

                int index = 1;
                for (var stored : playlist.tracks) {
                    Track track = Track.fromStored(stored);
                    if (cy + Theme.ROW_H + 6 > y && cy < y + h) {
                        drawTrackRow(ui, track, contentX, cy, contentW, index);
                    }
                    cy += Theme.ROW_H + 6;
                    index++;
                }
            }
        }
        updateScrollLimit(cy - (y - (int) scroll), h);
        ui.popClip();
    }

    private void createPlaylist() {
        ModConfig config = ConfigManager.get();
        String name = "Playlist " + (config.playlists.size() + 1);
        config.playlists.add(new ModConfig.StoredPlaylist(name));
        ConfigManager.save();
        openPlaylist = name;
    }

    // ---- Explore ----

    private void drawExplore(UiContext ui, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ui.graphics;
        int pad = 22;
        int contentX = x + pad;
        int contentW = w - pad * 2;

        ui.pushClip(x, y, w, h);
        int cy = y - (int) scroll + 12;

        Ui.icon22(g, font, Icons.EXPLORE, contentX, cy, Ui.ACCENT);
        Ui.text26Bold(g, font, "Entdecken", contentX, cy + 28, Ui.TEXT);
        cy += 74;

        String[][] genres = {
                {"Chill & Lo-Fi", "chill lofi music"},
                {"Epic Orchestral", "epic orchestral music"},
                {"Synthwave", "synthwave mix"},
                {"C418 & Minecraft", "C418 minecraft music"},
                {"Phonk", "phonk music"},
                {"Jazz Cafe", "jazz cafe music"},
                {"Focus Piano", "focus piano study music"},
                {"Night Drive", "night drive synthwave"}
        };
        int tileW = (contentW - 12) / 2;
        int tileH = 62;
        int tx = contentX;
        int ty = cy;
        for (int i = 0; i < genres.length; i++) {
            if (i > 0 && i % 2 == 0) {
                tx = contentX;
                ty += tileH + 12;
            }
            boolean hovered = ui.hovered(tx, ty, tileW, tileH);
            int tint = 0xFF000000 | (0x20 + (i * 37) % 0x50 << 16) | (0x1A + (i * 19) % 0x40 << 8) | 0x2A;
            Ui.roundRect(g, tx, ty, tileW, tileH, Theme.R_CARD, hovered ? Ui.SURFACE_HI : tint);
            Ui.text13Bold(g, font, genres[i][0], tx + 14, ty + 16, Ui.TEXT);
            Ui.text11(g, font, "Playlist starten", tx + 14, ty + 34, Ui.TEXT_MUTED);
            Ui.icon12(g, font, Icons.PLAY, tx + tileW - 30, ty + tileH - 28, Ui.ACCENT);
            final String query = genres[i][1];
            hits.add(tx, ty, tileW, tileH, () -> {
                searchBox.setValue(query);
                lastQuery = "";
                view = View.SEARCH;
                performSearch(query);
            });
            tx += tileW + 12;
        }
        cy = ty + tileH + 24;
        updateScrollLimit(cy - (y - (int) scroll), h);
        ui.popClip();
    }

    // ---- Settings ----

    private void drawSettings(UiContext ui, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ui.graphics;
        int pad = 22;
        int contentX = x + pad;
        int contentW = Math.min(520, w - pad * 2);
        ModConfig config = ConfigManager.get();

        ui.pushClip(x, y, w, h);
        int cy = y - (int) scroll + 12;

        Ui.icon22(g, font, Icons.SETTINGS, contentX, cy, Ui.ACCENT);
        Ui.text26Bold(g, font, "Einstellungen", contentX, cy + 28, Ui.TEXT);
        cy += 74;

        // SVC-Broadcast
        cy = settingToggle(ui, "Voice-Chat-Broadcast",
                "Musik über Simple Voice Chat für alle hörbar machen",
                contentX, cy, contentW, config.svcBroadcast,
                value -> MusicPlayer.get().setBroadcastEnabled(value));

        // Lautstaerke
        cy = settingSlider(ui, "Lautstärke", contentX, cy, contentW, config.volume, value -> {
            config.volume = value;
            ConfigManager.save();
        });

        // Reichweite
        cy = settingSliderRange(ui, "Reichweite (Blöcke)", contentX, cy, contentW,
                (config.broadcastDistance - 8F) / 120F, value -> {
                    config.broadcastDistance = Math.round(8F + value * 120F);
                    ConfigManager.save();
                }, Math.round(config.broadcastDistance) + " Blöcke");

        // Status
        cy += 8;
        Ui.roundRect(g, contentX, cy, contentW, 56, Theme.R_CARD, Ui.SURFACE);
        boolean svc = ClientNetworkingHolder.isSvcAvailable();
        Ui.icon12(g, font, svc ? Icons.CAST_CONNECTED : Icons.CAST,
                contentX + 14, cy + 14, svc ? Ui.GREEN : Ui.TEXT_MUTED);
        Ui.text11Med(g, font, svc ? "Simple Voice Chat verbunden" : "Lokale Wiedergabe",
                contentX + 36, cy + 12, Ui.TEXT);
        Ui.text11(g, font, svc
                        ? "Andere Spieler in Reichweite hören mit."
                        : "Ohne Simple Voice Chat hörst nur du die Musik.",
                contentX + 36, cy + 28, Ui.TEXT_MUTED);
        cy += 72;

        // Info
        Ui.text11(g, font, "Minecraft Music 1.0.0 - gebaut für Minecraft 26.3", contentX, cy, Ui.TEXT_MUTED);
        cy += 14;
        Ui.text11(g, font, "Audio: LavaPlayer + youtube-source | Stimme: Simple Voice Chat", contentX, cy, Ui.TEXT_MUTED);

        updateScrollLimit(cy - (y - (int) scroll) + 40, h);
        ui.popClip();
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private interface FloatSetter {
        void set(float value);
    }

    private int settingToggle(UiContext ui, String title, String description,
                              int x, int y, int w, boolean value, BoolSetter setter) {
        GuiGraphicsExtractor g = ui.graphics;
        int h = 52;
        Ui.roundRect(g, x, y, w, h, Theme.R_CARD, Ui.SURFACE);
        Ui.text11Med(g, font, title, x + 14, y + 11, Ui.TEXT);
        Ui.text11(g, font, description, x + 14, y + 27, Ui.TEXT_MUTED);

        int swW = 34;
        int swH = 18;
        int sx = x + w - swW - 14;
        int sy = y + (h - swH) / 2;
        Ui.roundRect(g, sx, sy, swW, swH, 9, value ? Ui.ACCENT : 0xFF3A3A42);
        int knob = 14;
        int kx = value ? sx + swW - knob - 2 : sx + 2;
        Ui.circle(g, kx, sy + 2, knob, 0xFFFFFFFF);

        hits.add(sx, sy, swW, swH, () -> setter.set(!value));
        hits.add(x, y, w, h, () -> setter.set(!value));
        return y + h + 10;
    }

    private int settingSlider(UiContext ui, String title, int x, int y, int w, float value, FloatSetter setter) {
        GuiGraphicsExtractor g = ui.graphics;
        int h = 46;
        Ui.roundRect(g, x, y, w, h, Theme.R_CARD, Ui.SURFACE);
        Ui.text11Med(g, font, title, x + 14, y + 9, Ui.TEXT);
        Ui.text11(g, font, Math.round(value * 100) + "%", x + w - 48, y + 9, Ui.TEXT_MUTED);

        int trackX = x + 14;
        int trackW = w - 28;
        int trackY = y + 30;
        Ui.roundRect(g, trackX, trackY, trackW, 4, 2, 0xFF35353D);
        Ui.roundRect(g, trackX, trackY, (int) (trackW * value), 4, 2, Ui.ACCENT);
        int knobX = trackX + (int) (trackW * value) - 5;
        g.fill(knobX, trackY - 4, knobX + 10, trackY + 8, Ui.ACCENT);
        hits.add(trackX - 4, trackY - 8, trackW + 8, 18, () -> {
            float newValue = Math.max(0F, Math.min(1F, (ui.mouseX - trackX) / (float) trackW));
            setter.set(newValue);
        });
        return y + h + 10;
    }

    private int settingSliderRange(UiContext ui, String title, int x, int y, int w,
                                   float normalized, FloatSetter setter, String label) {
        GuiGraphicsExtractor g = ui.graphics;
        int h = 46;
        Ui.roundRect(g, x, y, w, h, Theme.R_CARD, Ui.SURFACE);
        Ui.text11Med(g, font, title, x + 14, y + 9, Ui.TEXT);
        Ui.text11(g, font, label, x + w - 70, y + 9, Ui.TEXT_MUTED);

        int trackX = x + 14;
        int trackW = w - 28;
        int trackY = y + 30;
        Ui.roundRect(g, trackX, trackY, trackW, 4, 2, 0xFF35353D);
        Ui.roundRect(g, trackX, trackY, (int) (trackW * normalized), 4, 2, Ui.ACCENT);
        int knobX = trackX + (int) (trackW * normalized) - 5;
        g.fill(knobX, trackY - 4, knobX + 10, trackY + 8, Ui.ACCENT);
        hits.add(trackX - 4, trackY - 8, trackW + 8, 18, () -> {
            float newValue = Math.max(0F, Math.min(1F, (ui.mouseX - trackX) / (float) trackW));
            setter.set(newValue);
        });
        return y + h + 10;
    }

    // ------------------------------------------------------------------
    //  Player-Leiste
    // ------------------------------------------------------------------

    private void drawPlayerBar(UiContext ui, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ui.graphics;
        g.fill(x, y, x + w, y + h, Ui.BAR);
        g.fill(x, y, x + w, y + 1, Ui.BORDER_STRONG);

        MusicPlayer player = MusicPlayer.get();
        Track current = player.current();
        ModConfig config = ConfigManager.get();

        // Cover + Titel
        int artSize = 44;
        int artX = x + 14;
        int artY = y + (h - artSize) / 2 - 2;
        if (current != null) {
            Identifier cover = ThumbnailCache.get().get(current.artworkUrl);
            if (cover != null) {
                Ui.image(g, cover, artX, artY, artSize, artSize);
            } else {
                Ui.roundRect(g, artX, artY, artSize, artSize, Theme.R_SMALL, Ui.SURFACE);
                Ui.iconCentered(g, font, Icons.MUSIC_NOTE, artX + 26, artY + 26, Ui.ICON_16, Ui.TEXT_MUTED);
            }
            Ui.text11Med(g, font, Ui.trim11Med(font, current.title, 160), artX + artSize + 12, y + 18, Ui.TEXT);
            Ui.text11(g, font, Ui.trim11(font, current.artist, 160), artX + artSize + 12, y + 32, Ui.TEXT_MUTED);
        } else {
            Ui.roundRect(g, artX, artY, artSize, artSize, Theme.R_SMALL, Ui.SURFACE);
            Ui.iconCentered(g, font, Icons.MUSIC_NOTE, artX + 26, artY + 26, Ui.ICON_16, Ui.TEXT_MUTED);
            Ui.text11Med(g, font, "Nichts wird abgespielt", artX + artSize + 12, y + 18, Ui.TEXT_DIM);
            Ui.text11(g, font, "Wähle einen Song aus", artX + artSize + 12, y + 32, Ui.TEXT_MUTED);
        }

        // Steuerung mittig
        int centerX = x + w / 2;
        int buttonY = y + 16;

        // Shuffle
        int shuffleCx = centerX - 72;
        Ui.iconCentered(g, font, Icons.SHUFFLE, shuffleCx, buttonY + 4, Ui.ICON_12,
                config.shuffle ? Ui.ACCENT : Ui.TEXT_MUTED);
        hits.add(shuffleCx - 11, buttonY - 7, 22, 22, player::toggleShuffle, "Shuffle");

        // Prev
        int prevCx = centerX - 44;
        Ui.iconCentered(g, font, Icons.PREV, prevCx, buttonY + 4, Ui.ICON_16, Ui.TEXT_DIM);
        hits.add(prevCx - 12, buttonY - 8, 24, 24, player::previous, "Zurück");

        // Play/Pause
        boolean playing = player.isPlaying();
        Ui.circle(g, centerX - 15, buttonY - 5, 30, Ui.TEXT);
        Ui.iconCentered(g, font, playing ? Icons.PAUSE : Icons.PLAY, centerX, buttonY + 9, Ui.ICON_16, 0xFF0E0E10);
        hits.add(centerX - 16, buttonY - 6, 32, 32, () -> {
            if (player.current() == null) {
                return;
            }
            if (player.isPaused()) {
                player.resume();
            } else if (!playing) {
                player.resume();
            } else {
                player.pause();
            }
        }, playing ? "Pause" : "Abspielen");

        // Next
        int nextCx = centerX + 44;
        Ui.iconCentered(g, font, Icons.NEXT, nextCx, buttonY + 4, Ui.ICON_16, Ui.TEXT_DIM);
        hits.add(nextCx - 12, buttonY - 8, 24, 24, player::next, "Weiter");

        // Repeat
        String repeatIcon = config.repeatMode == 2 ? Icons.REPEAT_ONE : Icons.REPEAT;
        int repeatCx = centerX + 72;
        Ui.iconCentered(g, font, repeatIcon, repeatCx, buttonY + 4, Ui.ICON_12,
                config.repeatMode != 0 ? Ui.ACCENT : Ui.TEXT_MUTED);
        hits.add(repeatCx - 11, buttonY - 7, 22, 22, player::cycleRepeat, "Wiederholen");

        // Fortschritt
        int trackX = centerX - 90;
        int trackW = 180;
        int trackY = y + 46;
        int progressW = current == null ? 0 : (int) (trackW * Math.min(1F,
                current.durationMs <= 0 ? 0F : player.positionMs() / (float) current.durationMs));
        Ui.roundRect(g, trackX, trackY, trackW, 3, 2, 0xFF35353D);
        if (progressW > 0) {
            Ui.roundRect(g, trackX, trackY, progressW, 3, 2, Ui.ACCENT);
        }
        String pos = formatTime(player.positionMs());
        String total = current == null ? "0:00" : current.durationText();
        Ui.text11(g, font, pos, trackX - 34, trackY - 4, Ui.TEXT_MUTED);
        Ui.text11(g, font, total, trackX + trackW + 8, trackY - 4, Ui.TEXT_MUTED);

        // Klick auf Fortschrittsbalken = Springen
        if (current != null && current.durationMs > 0) {
            hits.add(trackX, trackY - 6, trackW, 14, () -> {
                float ratio = Math.max(0F, Math.min(1F, (ui.mouseX - trackX) / (float) trackW));
                player.seek((long) (ratio * current.durationMs));
            });
        }

        // Rechts: Favorit, Lautstaerke, SVC
        int right = x + w - 16;

        // Broadcast-Schalter
        boolean svc = ClientNetworkingHolder.isSvcAvailable();
        int castX = right - 26;
        Ui.icon12(g, font, svc && config.svcBroadcast ? Icons.CAST_CONNECTED : Icons.CAST,
                castX, y + 18, svc && config.svcBroadcast ? Ui.ACCENT : Ui.TEXT_MUTED);
        hits.add(castX - 4, y + 14, 24, 24, () -> {
            config.svcBroadcast = !config.svcBroadcast;
            ConfigManager.save();
            if (player.current() != null) {
                if (config.svcBroadcast) {
                    com.chaostobimc.minecraftmusic.audio.ClientNetworkingHolder.sendControl(
                            com.chaostobimc.minecraftmusic.net.MusicControlPayload.start(
                                    config.volume, config.broadcastDistance,
                                    player.current().title, player.current().artist));
                } else {
                    com.chaostobimc.minecraftmusic.audio.ClientNetworkingHolder.sendControl(
                            com.chaostobimc.minecraftmusic.net.MusicControlPayload.stop());
                }
            }
        }, "Voice-Chat-Broadcast (an/aus)");
        right -= 34;

        // Lautstaerke
        int volW = 70;
        int volX = right - volW;
        int volY = y + 30;
        Ui.iconCentered(g, font, config.volume <= 0.01F ? Icons.VOLUME_OFF : Icons.VOLUME_UP,
                volX + 2, volY + 1, Ui.ICON_9, Ui.TEXT_MUTED);
        Ui.roundRect(g, volX + 12, volY, volW - 12, 3, 2, 0xFF35353D);
        Ui.roundRect(g, volX + 12, volY, (int) ((volW - 12) * config.volume), 3, 2, Ui.TEXT_DIM);
        hits.add(volX + 8, volY - 8, volW, 18, () -> {
            float value = Math.max(0F, Math.min(1F, (ui.mouseX - (volX + 12)) / (float) (volW - 12)));
            player.setVolume(value);
        }, "Lautstärke");
        right -= volW + 14;

        // Favorit des aktuellen Songs
        if (current != null) {
            boolean fav = config.isFavorite(current.id);
            Ui.iconCentered(g, font, fav ? Icons.FAVORITE : Icons.FAVORITE_BORDER,
                    right - 8, y + 26, Ui.ICON_12, fav ? Ui.ACCENT : Ui.TEXT_MUTED);
            hits.add(right - 20, y + 14, 24, 24, () -> {
                config.toggleFavorite(current.toStored());
                ConfigManager.save();
            }, "Favorit");
        }
    }

    private static String formatTime(long ms) {
        long total = Math.max(0, ms) / 1000L;
        return String.format("%d:%02d", total / 60, total % 60);
    }

    // ------------------------------------------------------------------
    //  Scroll
    // ------------------------------------------------------------------

    /** contentHeight = komplette Hoehe des Inhalts; viewHeight = sichtbarer Bereich. */
    private void updateScrollLimit(int contentHeight, int viewHeight) {
        maxScroll = Math.max(0, contentHeight - viewHeight);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - scrollY * 42));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Suche zuerst: Textfeld braucht Fokus (auch beim allerersten Klick)
        if (searchBox != null && searchBox.visible && searchBox.isMouseOver(event.x(), event.y())) {
            boolean wasFocused = searchFocused;
            focusSearch(true);
            // Der erste Klick fokussiert nur; erst danach Cursor setzen.
            return wasFocused ? searchBox.mouseClicked(event, doubleClick) : true;
        }
        if (searchFocused) {
            focusSearch(false);
        }
        if (hits.click(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBox != null && searchFocused) {
            if (event.isConfirmation()) {
                String query = searchBox.getValue().trim();
                if (!query.isEmpty()) {
                    lastQuery = "";
                    performSearch(query);
                    view = View.SEARCH;
                }
                // Enter gibt den Fokus frei, damit Space wieder Pause/Play macht.
                focusSearch(false);
                return true;
            }
            // Escape loest zuerst nur den Fokus, damit man nicht versehentlich
            // das ganze GUI schliesst, waehrend man tippt.
            if (event.isEscape()) {
                focusSearch(false);
                return true;
            }
            if (searchBox.keyPressed(event)) {
                return true;
            }
        }
        if (event.isEscape()) {
            onClose();
            return true;
        }
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE) {
            // Space ist Wiedergabe/Pause - beim Tippen im Suchfeld aber ein Leerzeichen.
            if (searchFocused) {
                searchBox.charTyped(new CharacterEvent(' '));
            } else {
                MusicPlayer.get().togglePause();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchBox != null && searchFocused) {
            return searchBox.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        Minecraft.getInstance().gui.setScreen(null);
    }

    private void playSingle(Track track) {
        MusicPlayer.get().playNow(track);
    }
}

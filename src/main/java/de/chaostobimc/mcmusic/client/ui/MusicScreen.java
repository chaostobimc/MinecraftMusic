package de.chaostobimc.mcmusic.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.api.LocalPlaylist;
import de.chaostobimc.mcmusic.api.Track;
import de.chaostobimc.mcmusic.client.ClientMusic;
import de.chaostobimc.mcmusic.config.MusicConfig;
import de.chaostobimc.mcmusic.client.NowPlaying;
import de.chaostobimc.mcmusic.voicechat.VoicechatSupport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The MC Music window: a flat, rounded, high resolution music client that follows the
 * YouTube Music layout - sidebar, search, home feed, library, playlists and a docked
 * player bar.
 */
public final class MusicScreen extends Screen {

    private enum View {
        HOME, SEARCH, LIBRARY, FAVORITES, PLAYLIST
    }

    // layout
    private int winX;
    private int winY;
    private int winW;
    private int winH;
    private int sideX;
    private int sideY;
    private int sideW;
    private int sideH;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;
    private int topY;
    private int playerY;
    private int playerH;
    private boolean compact;

    // state
    private View view = View.HOME;
    private float scroll;
    private float scrollTarget;
    private float maxScroll;
    private int selectedPlaylist = -1;
    private boolean settingsOpen;
    private boolean creatingPlaylist;
    private String createBuffer = "";
    private String query = "";
    private int caret;
    private boolean searchFocused;
    private boolean draggingProgress;
    private boolean draggingVolume;
    private float pendingSeek = -1f;
    private float pendingVolume = -1f;
    private String settingsEditing;
    private String settingsBuffer = "";
    private int frameCount;
    private long lastFrameTime = System.currentTimeMillis();

    private final List<Hit> hits = new ArrayList<>();
    private final ClientMusic music = ClientMusic.get();

    public MusicScreen() {
        super(Component.literal("MC Music"));
    }

    // ------------------------------------------------------------------
    // layout
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        layout();
        music.load();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        layout();
    }

    private void layout() {
        int margin = Math.max(6, Math.min(14, width / 90));
        winX = margin;
        winY = margin;
        winW = width - margin * 2;
        winH = height - margin * 2;

        compact = winW < 560;
        sideW = compact ? 54 : Math.max(150, Math.min(190, winW / 6));
        playerH = winH < 260 ? 54 : 66;
        int topH = 50;

        sideX = winX + 8;
        sideY = winY + 8;
        sideH = winH - 16 - playerH - 8;
        contentX = sideX + sideW + 10;
        contentW = winX + winW - 10 - contentX;
        topY = winY + 10;
        contentY = winY + topH;
        contentH = winH - topH - playerH - 8;
        playerY = winY + winH - playerH - 8;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------
    // render
    // ------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level != null) {
            graphics.blurBeforeThisStratum();
        }
        graphics.fill(0, 0, width, height, 0xB8000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        frameCount++;
        long now = System.currentTimeMillis();
        float delta = Math.min(0.1f, (now - lastFrameTime) / 1000f);
        lastFrameTime = now;

        hits.clear();
        Theme theme = Theme.get();

        // window
        Ui.shadow(graphics, winX, winY, winW, winH, 18, theme.shadow);
        Ui.rect(graphics, winX, winY, winW, winH, 16, theme.window);
        Ui.outline(graphics, winX, winY, winW, winH, 16, theme.divider);

        drawSidebar(graphics, theme, mouseX, mouseY);
        drawTopBar(graphics, theme, mouseX, mouseY);
        drawContent(graphics, theme, mouseX, mouseY, delta);
        drawPlayerBar(graphics, theme, mouseX, mouseY);

        if (settingsOpen) {
            drawSettings(graphics, theme, mouseX, mouseY);
        }
        if (creatingPlaylist) {
            drawCreatePlaylist(graphics, theme, mouseX, mouseY);
        }

        String notice = music.notice();
        if (!notice.isEmpty()) {
            drawNotice(graphics, theme, notice);
        }
    }

    // ------------------------------------------------------------------
    // sidebar
    // ------------------------------------------------------------------

    private void drawSidebar(GuiGraphicsExtractor graphics, Theme theme, int mouseX, int mouseY) {
        int x = sideX;
        int y = sideY;

        // logo
        Ui.rect(graphics, x, y + 2, 30, 30, 9, theme.accent);
        Ui.icon(graphics, x + 7, y + 9, 16, Ui.ICON_NOTE, 0xFFFFFFFF);
        if (!compact) {
            UiFont.draw(graphics, "Music", x + 40, y + 26, 19, theme.text, 1);
        }

        int itemY = y + 52;
        itemY = navItem(graphics, theme, x, itemY, Ui.ICON_HOME, "Home", view == View.HOME, mouseX, mouseY, Action.NAV_HOME);
        itemY = navItem(graphics, theme, x, itemY, Ui.ICON_SEARCH, "Search", view == View.SEARCH, mouseX, mouseY, Action.NAV_SEARCH);
        itemY = navItem(graphics, theme, x, itemY, Ui.ICON_LIBRARY, "Library", view == View.LIBRARY, mouseX, mouseY, Action.NAV_LIBRARY);
        navItem(graphics, theme, x, itemY, Ui.ICON_HEART, "Favourites", view == View.FAVORITES, mouseX, mouseY, Action.NAV_FAVORITES);

        if (!compact) {
            int labelY = itemY + 34;
            if (labelY < sideY + sideH - 60) {
                UiFont.draw(graphics, "PLAYLISTS", x + 12, labelY, 10, theme.textMuted, 1);
                int playlistY = labelY + 10;
                int index = 0;
                for (LocalPlaylist playlist : music.playlists) {
                    if (playlistY > sideY + sideH - 70) {
                        break;
                    }
                    boolean active = view == View.PLAYLIST && selectedPlaylist == music.playlists.indexOf(playlist);
                    boolean hover = Ui.inside(mouseX, mouseY, x + 6, playlistY, sideW - 6, 24);
                    if (active) {
                        Ui.rect(graphics, x + 6, playlistY, sideW - 10, 24, 7, theme.surfaceActive);
                    } else if (hover) {
                        Ui.rect(graphics, x + 6, playlistY, sideW - 10, 24, 7, theme.surfaceHover);
                    }
                    Ui.rect(graphics, x + 10, playlistY + 7, 10, 10, 3, playlist.color);
                    UiFont.draw(graphics, UiFont.ellipsize(playlist.name, 12, sideW - 48), x + 26, playlistY + 16, 12,
                            active ? theme.text : theme.textSecondary, active ? 1 : 0);
                    hits.add(new Hit(x + 6, playlistY, sideW - 10, 24, Action.OPEN_PLAYLIST, index));
                    playlistY += 26;
                    index++;
                }
                if (music.playlists.isEmpty()) {
                    UiFont.draw(graphics, "No playlists yet", x + 12, playlistY + 10, 11, theme.textMuted, 0);
                }
            }
        }

        // bottom: settings
        int settingsY = sideY + sideH - 30;
        boolean hover = Ui.inside(mouseX, mouseY, x + 6, settingsY, sideW - 10, 26);
        if (hover) {
            Ui.rect(graphics, x + 6, settingsY, sideW - 10, 26, 7, theme.surfaceHover);
        }
        Ui.iconCentered(graphics, x + 20, settingsY + 13, 15, Ui.ICON_SETTINGS, theme.textSecondary);
        if (!compact) {
            UiFont.draw(graphics, "Settings", x + 36, settingsY + 18, 12, theme.textSecondary, 0);
        }
        hits.add(new Hit(x + 6, settingsY, sideW - 10, 26, Action.OPEN_SETTINGS, null));

        // close
        int closeY = settingsY - 32;
        boolean closeHover = Ui.inside(mouseX, mouseY, x + 6, closeY, sideW - 10, 26);
        if (closeHover) {
            Ui.rect(graphics, x + 6, closeY, sideW - 10, 26, 7, theme.surfaceHover);
        }
        Ui.iconCentered(graphics, x + 20, closeY + 13, 14, Ui.ICON_CLOSE, theme.textSecondary);
        if (!compact) {
            UiFont.draw(graphics, "Close", x + 36, closeY + 18, 12, theme.textSecondary, 0);
        }
        hits.add(new Hit(x + 6, closeY, sideW - 10, 26, Action.CLOSE, null));

        // separator
        graphics.fill(contentX - 5, winY + 12, contentX - 4, winY + winH - 12, theme.divider);
    }

    private int navItem(GuiGraphicsExtractor graphics, Theme theme, int x, int y, int icon, String label,
                        boolean active, int mouseX, int mouseY, Action action) {
        boolean hover = Ui.inside(mouseX, mouseY, x + 6, y, sideW - 10, 28);
        if (active) {
            Ui.rect(graphics, x + 6, y, sideW - 10, 28, 8, theme.surfaceActive);
        } else if (hover) {
            Ui.rect(graphics, x + 6, y, sideW - 10, 28, 8, theme.surfaceHover);
        }
        int colour = active ? theme.text : theme.textSecondary;
        Ui.iconCentered(graphics, x + 22, y + 14, 16, icon, colour);
        if (!compact) {
            UiFont.draw(graphics, label, x + 40, y + 19, 13, colour, active ? 1 : 0);
        }
        hits.add(new Hit(x + 6, y, sideW - 10, 28, action, null));
        return y + 30;
    }

    // ------------------------------------------------------------------
    // top bar
    // ------------------------------------------------------------------

    private void drawTopBar(GuiGraphicsExtractor graphics, Theme theme, int mouseX, int mouseY) {
        int searchW = Math.min(Math.max(160, contentW - 150), 420);
        int searchH = 32;
        int searchY = topY + 2;
        int searchX = contentX;

        boolean hover = Ui.inside(mouseX, mouseY, searchX, searchY, searchW, searchH);
        Ui.rect(graphics, searchX, searchY, searchW, searchH, searchH / 2, hover || searchFocused ? theme.surfaceHover : theme.search);
        Ui.icon(graphics, searchX + 11, searchY + 9, 14, Ui.ICON_SEARCH, theme.textSecondary);

        String shown = query.isEmpty() && !searchFocused ? "Search songs, artists, playlists" : query;
        int textColour = query.isEmpty() && !searchFocused ? theme.textMuted : theme.text;
        String visible = UiFont.ellipsize(shown, 13, searchW - 52);
        UiFont.draw(graphics, visible, searchX + 32, searchY + 21, 13, textColour, 0);
        if (searchFocused && (frameCount / 20) % 2 == 0) {
            float caretX = searchX + 32 + UiFont.width(query.substring(0, Math.min(caret, query.length())), 13);
            graphics.fill((int) caretX, searchY + 9, (int) caretX + 1, searchY + 24, theme.accent);
        }
        hits.add(new Hit(searchX, searchY, searchW, searchH, Action.FOCUS_SEARCH, null));

        // status chip
        int chipRight = winX + winW - 46;
        int chipW = 92;
        int chipX = chipRight - chipW;
        boolean live = music.state.hasTrack();
        boolean voice = VoicechatSupport.canStreamToOthers();
        int chipColor = live ? theme.surface : theme.surfaceHover;
        Ui.rect(graphics, chipX, searchY + 2, chipW, 28, 14, chipColor);
        Ui.icon(graphics, chipX + 10, searchY + 9, 14, Ui.ICON_WIFI, voice ? theme.accent : theme.textMuted);
        String chipText = voice ? (live ? "Live" : "Ready") : "Local";
        UiFont.draw(graphics, chipText, chipX + 30, searchY + 21, 11, voice ? theme.text : theme.textMuted, 0);

        // close button
        int closeX = winX + winW - 38;
        boolean closeHover = Ui.inside(mouseX, mouseY, closeX, searchY + 2, 28, 28);
        Ui.rect(graphics, closeX, searchY + 2, 28, 28, 9, closeHover ? theme.surfaceHover : theme.surface);
        Ui.iconCentered(graphics, closeX + 14, searchY + 16, 13, Ui.ICON_CLOSE, theme.textSecondary);
        hits.add(new Hit(closeX, searchY + 2, 28, 28, Action.CLOSE, null));
    }

    // ------------------------------------------------------------------
    // content
    // ------------------------------------------------------------------

    private void drawContent(GuiGraphicsExtractor graphics, Theme theme, int mouseX, int mouseY, float delta) {
        // smooth scrolling
        if (Math.abs(scrollTarget - scroll) > 0.5f) {
            float speed = MusicConfig.get().reduceMotion ? 1f : Math.min(1f, delta * 14f);
            scroll += (scrollTarget - scroll) * speed;
        } else {
            scroll = scrollTarget;
        }

        graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        int cursorX = contentX;
        int cursorY = contentY - (int) scroll;

        switch (view) {
            case HOME -> cursorY = drawHome(graphics, theme, cursorX, cursorY, mouseX, mouseY);
            case SEARCH -> cursorY = drawSearch(graphics, theme, cursorX, cursorY, mouseX, mouseY);
            case LIBRARY -> cursorY = drawLibrary(graphics, theme, cursorX, cursorY, mouseX, mouseY);
            case FAVORITES -> cursorY = drawFavorites(graphics, theme, cursorX, cursorY, mouseX, mouseY);
            case PLAYLIST -> cursorY = drawPlaylist(graphics, theme, cursorX, cursorY, mouseX, mouseY);
            default -> {
            }
        }
        graphics.disableScissor();

        maxScroll = Math.max(0, (cursorY + (int) scroll) - (contentY + contentH) + 16);
        if (scrollTarget > maxScroll) {
            scrollTarget = maxScroll;
        }

        // scrollbar
        if (maxScroll > 1) {
            int barX = contentX + contentW - 3;
            int trackH = contentH;
            float ratio = Math.min(1f, (float) contentH / (contentH + maxScroll));
            int thumbH = Math.max(24, (int) (trackH * ratio));
            int thumbY = contentY + (int) ((trackH - thumbH) * (maxScroll <= 0 ? 0 : scroll / maxScroll));
            Ui.rect(graphics, barX, contentY, 3, trackH, 2, Theme.alpha(theme.textMuted, 0.12f));
            Ui.rect(graphics, barX, thumbY, 3, thumbH, 2, Theme.alpha(theme.textSecondary, 0.55f));
        }
    }

    private int drawHome(GuiGraphicsExtractor graphics, Theme theme, int x, int y, int mouseX, int mouseY) {
        String greeting = greeting();
        UiFont.draw(graphics, greeting, x + 2, y + 28, 26, theme.text, 1);
        UiFont.draw(graphics, "Your music, streamed through Simple Voice Chat", x + 2, y + 50, 12, theme.textSecondary, 0);
        y += 74;

        List<Track> picks = new ArrayList<>(music.recentlyPlayed);
        if (picks.isEmpty()) {
            picks.addAll(music.favorites);
        }
        if (!picks.isEmpty()) {
            y = sectionLabel(graphics, theme, x, y, "QUICK PICKS");
            int cards = Math.min(4, picks.size());
            int gap = 10;
            int cardW = (contentW - gap * (cards - 1)) / cards;
            for (int i = 0; i < cards; i++) {
                Track track = picks.get(i);
                int cardX = x + i * (cardW + gap);
                drawCard(graphics, theme, cardX, y, cardW, track, mouseX, mouseY, i);
            }
            y += cardW + 34;
        }

        List<Track> recent = music.recentlyPlayed;
        if (!recent.isEmpty()) {
            y = sectionLabel(graphics, theme, x, y, "RECENTLY PLAYED");
            int rows = Math.min(8, recent.size());
            for (int i = 0; i < rows; i++) {
                y = drawRow(graphics, theme, x, y, recent.get(i), i, mouseX, mouseY);
            }
        } else {
            Ui.rect(graphics, x, y, contentW, 92, 12, theme.surface);
            Ui.icon(graphics, x + 18, y + 30, 30, Ui.ICON_NOTE, Theme.alpha(theme.textSecondary, 0.5f));
            UiFont.draw(graphics, "Nothing here yet", x + 60, y + 40, 15, theme.text, 0);
            UiFont.draw(graphics, "Search for a song or open a playlist to get started.",
                    x + 60, y + 58, 12, theme.textSecondary, 0);
            y += 104;
        }

        if (!music.favorites.isEmpty()) {
            y = sectionLabel(graphics, theme, x, y, "FAVOURITES");
            int rows = Math.min(5, music.favorites.size());
            for (int i = 0; i < rows; i++) {
                y = drawRow(graphics, theme, x, y, music.favorites.get(i), i, mouseX, mouseY);
            }
        }
        return y;
    }

    private int drawSearch(GuiGraphicsExtractor graphics, Theme theme, int x, int y, int mouseX, int mouseY) {
        UiFont.draw(graphics, query.isEmpty() ? "Search" : "\"" + UiFont.ellipsize(query, 22, contentW - 120) + "\"", x + 2, y + 30, 24, theme.text, 1);
        y += 52;

        if (music.searching) {
            drawSpinner(graphics, theme, x + contentW / 2, y + 60);
            UiFont.drawCentered(graphics, "Searching YouTube Music\u2026", x + contentW / 2f, y + 96, 12, theme.textSecondary, 0);
            return y + 140;
        }
        if (!music.searchError.isEmpty() && music.results.isEmpty()) {
            Ui.rect(graphics, x, y, contentW, 96, 12, theme.surface);
            Ui.icon(graphics, x + 18, y + 30, 26, Ui.ICON_GLOBE, Theme.alpha(theme.textSecondary, 0.6f));
            UiFont.draw(graphics, "Search failed", x + 58, y + 38, 14, theme.text, 0);
            String message = UiFont.ellipsize(music.searchError, 11, contentW - 90);
            UiFont.draw(graphics, message, x + 58, y + 58, 11, theme.textSecondary, 0);
            UiFont.draw(graphics, "Check the provider instance in the settings.", x + 18, y + 80, 11, theme.textMuted, 0);
            return y + 112;
        }
        if (music.results.isEmpty()) {
            Ui.rect(graphics, x, y, contentW, 88, 12, theme.surface);
            Ui.icon(graphics, x + 18, y + 28, 28, Ui.ICON_SEARCH, Theme.alpha(theme.textSecondary, 0.5f));
            UiFont.draw(graphics, "Find your music", x + 58, y + 34, 14, theme.text, 0);
            UiFont.draw(graphics, "Type into the search bar above and press enter.", x + 58, y + 54, 12, theme.textSecondary, 0);
            return y + 104;
        }

        y = sectionLabel(graphics, theme, x, y, "SONGS");
        int index = 0;
        for (Track track : music.results) {
            int rowY = y;
            y = drawRow(graphics, theme, x, y, track, index, mouseX, mouseY);
            if (rowY > contentY + contentH + (int) scroll) {
                break;
            }
            index++;
        }
        return y;
    }

    private int drawLibrary(GuiGraphicsExtractor graphics, Theme theme, int x, int y, int mouseX, int mouseY) {
        UiFont.draw(graphics, "Library", x + 2, y + 30, 24, theme.text, 1);
        y += 54;

        y = sectionLabel(graphics, theme, x, y, "PLAYLISTS");
        int gap = 10;
        int perRow = Math.max(2, Math.min(4, contentW / 150));
        int cardW = (contentW - gap * (perRow - 1)) / perRow;
        int cardH = cardW / 2 + 34;
        int index = 0;
        int column = 0;
        int rowStartY = y;
        for (LocalPlaylist playlist : music.playlists) {
            int cardX = x + column * (cardW + gap);
            drawPlaylistCard(graphics, theme, cardX, rowStartY, cardW, cardH, playlist, index, mouseX, mouseY);
            index++;
            column++;
            if (column >= perRow) {
                column = 0;
                rowStartY += cardH + gap;
            }
        }
        // "new playlist" card
        int cardX = x + column * (cardW + gap);
        boolean hover = Ui.inside(mouseX, mouseY, cardX, rowStartY, cardW, cardH);
        Ui.rect(graphics, cardX, rowStartY, cardW, cardH, 12, hover ? theme.surfaceHover : theme.surface);
        Ui.outline(graphics, cardX, rowStartY, cardW, cardH, 12, theme.divider);
        Ui.iconCentered(graphics, cardX + cardW / 2, rowStartY + cardH / 2 - 8, 22, Ui.ICON_PLUS, theme.textSecondary);
        UiFont.drawCentered(graphics, "New playlist", cardX + cardW / 2f, rowStartY + cardH / 2 + 16, 12, theme.textSecondary, 0);
        hits.add(new Hit(cardX, rowStartY, cardW, cardH, Action.NEW_PLAYLIST, null));

        if (column == perRow - 1) {
            rowStartY += cardH + gap;
            column = 0;
        } else if (music.playlists.isEmpty()) {
            rowStartY += cardH + gap;
        }
        y = rowStartY + cardH + 16;

        if (!music.favorites.isEmpty()) {
            y = sectionLabel(graphics, theme, x, y, "FAVOURITES");
            int rows = Math.min(6, music.favorites.size());
            for (int i = 0; i < rows; i++) {
                y = drawRow(graphics, theme, x, y, music.favorites.get(i), i, mouseX, mouseY);
            }
        }
        return y;
    }

    private int drawFavorites(GuiGraphicsExtractor graphics, Theme theme, int x, int y, int mouseX, int mouseY) {
        UiFont.draw(graphics, "Favourites", x + 2, y + 30, 24, theme.text, 1);
        y += 52;
        UiFont.draw(graphics, music.favorites.size() + " songs \u00b7 " + totalTime(music.favorites),
                x + 2, y, 12, theme.textSecondary, 0);
        y += 12;

        int playW = 96;
        boolean hover = Ui.inside(mouseX, mouseY, x, y, playW, 30);
        Ui.rect(graphics, x, y, playW, 30, 15, hover ? theme.accentHover : theme.accent);
        Ui.icon(graphics, x + 16, y + 9, 12, Ui.ICON_PLAY, theme.accentText);
        UiFont.draw(graphics, "Play all", x + 34, y + 20, 12, theme.accentText, 1);
        hits.add(new Hit(x, y, playW, 30, Action.PLAY_FAVORITES, null));
        y += 44;

        if (music.favorites.isEmpty()) {
            UiFont.draw(graphics, "Songs you like will show up here.", x + 2, y + 20, 12, theme.textMuted, 0);
            return y + 40;
        }
        int index = 0;
        for (Track track : music.favorites) {
            y = drawRow(graphics, theme, x, y, track, index, mouseX, mouseY);
            index++;
        }
        return y;
    }

    private int drawPlaylist(GuiGraphicsExtractor graphics, Theme theme, int x, int y, int mouseX, int mouseY) {
        LocalPlaylist playlist = selectedPlaylist();
        if (playlist == null) {
            view = View.LIBRARY;
            return y;
        }
        int coverSize = Math.min(120, contentW / 3);
        Ui.gradientRect(graphics, x + 2, y + 6, coverSize, coverSize, 12, playlist.color,
                Theme.mix(playlist.color, 0xFF000000, 0.55f));
        Ui.iconCentered(graphics, x + 2 + coverSize / 2, y + 6 + coverSize / 2, coverSize / 3, Ui.ICON_NOTE, 0xDDFFFFFF);

        int textX = x + coverSize + 18;
        UiFont.draw(graphics, "PLAYLIST", textX, y + 20, 10, theme.textMuted, 1);
        UiFont.draw(graphics, UiFont.ellipsize(playlist.name, 24, contentW - coverSize - 40), textX, y + 48, 24, theme.text, 1);
        UiFont.draw(graphics, playlist.size() + " songs \u00b7 " + totalTime(playlist.tracks), textX, y + 70, 12, theme.textSecondary, 0);

        int buttonY = y + 84;
        int playW = 92;
        boolean hover = Ui.inside(mouseX, mouseY, textX, buttonY, playW, 30);
        Ui.rect(graphics, textX, buttonY, playW, 30, 15, hover ? theme.accentHover : theme.accent);
        Ui.icon(graphics, textX + 15, buttonY + 9, 12, Ui.ICON_PLAY, theme.accentText);
        UiFont.draw(graphics, "Play all", textX + 33, buttonY + 20, 12, theme.accentText, 1);
        hits.add(new Hit(textX, buttonY, playW, 30, Action.PLAY_PLAYLIST, playlist.id));

        int deleteW = 84;
        boolean deleteHover = Ui.inside(mouseX, mouseY, textX + playW + 8, buttonY, deleteW, 30);
        Ui.rect(graphics, textX + playW + 8, buttonY, deleteW, 30, 15, deleteHover ? theme.surfaceHover : theme.surface);
        UiFont.draw(graphics, "Delete", textX + playW + 26, buttonY + 20, 12, theme.textSecondary, 0);
        hits.add(new Hit(textX + playW + 8, buttonY, deleteW, 30, Action.DELETE_PLAYLIST, playlist.id));

        y += Math.max(coverSize + 24, 128);

        if (playlist.tracks.isEmpty()) {
            UiFont.draw(graphics, "This playlist is empty. Hover a song and use the plus button.", x + 2, y + 16, 12, theme.textMuted, 0);
            return y + 40;
        }
        int index = 0;
        for (Track track : playlist.tracks) {
            y = drawRow(graphics, theme, x, y, track, index, mouseX, mouseY);
            index++;
        }
        return y;
    }

    private void drawCard(GuiGraphicsExtractor graphics, Theme theme, int x, int y, int width, Track track,
                          int mouseX, int mouseY, int index) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, width, width + 30);
        if (hover) {
            Ui.rect(graphics, x - 4, y - 4, width + 8, width + 42, 14, Theme.alpha(theme.surfaceHover, 0.75f));
        }
        Identifier cover = CoverCache.get(track.id(), track.thumbnailUrl());
        int coverSize = width;
        if (cover != null) {
            Ui.image(graphics, cover, x, y, coverSize, 10, theme.window, 0xFFFFFFFF);
        } else {
            Ui.cover(graphics, track.id(), x, y, coverSize, 10, theme.window);
        }
        UiFont.draw(graphics, UiFont.ellipsize(track.title(), 12, width), x, y + coverSize + 16, 12, theme.text, 1);
        UiFont.draw(graphics, UiFont.ellipsize(track.artist(), 11, width), x, y + coverSize + 30, 11, theme.textSecondary, 0);

        if (hover) {
            int buttonSize = 30;
            int buttonX = x + coverSize - buttonSize - 8;
            int buttonY = y + coverSize - buttonSize - 8;
            Ui.rect(graphics, buttonX, buttonY, buttonSize, buttonSize, buttonSize / 2, theme.accent);
            Ui.iconCentered(graphics, buttonX + buttonSize / 2, buttonY + buttonSize / 2, 13, Ui.ICON_PLAY, theme.accentText);
        }
        hits.add(new Hit(x, y, width, width + 32, Action.PLAY_TRACK, track));
    }

    private void drawPlaylistCard(GuiGraphicsExtractor graphics, Theme theme, int x, int y, int width, int height,
                                  LocalPlaylist playlist, int index, int mouseX, int mouseY) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, width, height);
        Ui.gradientRect(graphics, x, y, width, height, 12, Theme.mix(playlist.color, 0xFFFFFFFF, hover ? 0.12f : 0.0f),
                Theme.mix(playlist.color, 0xFF000000, 0.55f));
        Ui.icon(graphics, x + 12, y + 12, 16, Ui.ICON_LIST, 0xCCFFFFFF);
        UiFont.draw(graphics, UiFont.ellipsize(playlist.name, 14, width - 24), x + 12, y + height - 26, 14, 0xFFFFFFFF, 1);
        UiFont.draw(graphics, playlist.size() + " songs", x + 12, y + height - 10, 11, 0xCCFFFFFF, 0);
        hits.add(new Hit(x, y, width, height, Action.OPEN_PLAYLIST, index));
    }

    /** One song row; returns the y position of the next row. */
    private int drawRow(GuiGraphicsExtractor graphics, Theme theme, int x, int y, Track track, int index,
                        int mouseX, int mouseY) {
        int rowH = 46;
        boolean hover = Ui.inside(mouseX, mouseY, x, y, contentW, rowH);
        if (hover) {
            Ui.rect(graphics, x, y, contentW, rowH, 8, theme.surfaceHover);
        }

        int coverSize = 36;
        int coverX = x + 5;
        int coverY = y + (rowH - coverSize) / 2;
        Identifier cover = CoverCache.get(track.id(), track.thumbnailUrl());
        if (cover != null) {
            Ui.image(graphics, cover, coverX, coverY, coverSize, 7, hover ? theme.surfaceHover : theme.window, 0xFFFFFFFF);
        } else {
            Ui.cover(graphics, track.id(), coverX, coverY, coverSize, 7, hover ? theme.surfaceHover : theme.window);
        }

        int textX = coverX + coverSize + 12;
        boolean playing = track.id().equals(music.state.trackId()) && music.state.hasTrack();
        int titleColour = playing ? theme.accent : theme.text;
        int reserved = 150;
        UiFont.draw(graphics, UiFont.ellipsize(track.title(), 13, contentW - (textX - x) - reserved), textX, y + 20,
                13, titleColour, playing ? 1 : 0);
        UiFont.draw(graphics, UiFont.ellipsize(track.artist(), 11, contentW - (textX - x) - reserved), textX, y + 34,
                11, theme.textSecondary, 0);

        int right = x + contentW - 14;
        UiFont.drawRight(graphics, track.durationText(), right, y + 27, 11, theme.textSecondary, 0);
        hits.add(new Hit(x, y, contentW - 160, rowH, Action.PLAY_TRACK, track));

        // hover actions
        if (hover) {
            int buttonSize = 26;
            int likeX = right - 56;
            boolean favourite = music.isFavorite(track.id());
            Ui.iconCentered(graphics, likeX + buttonSize / 2, y + rowH / 2, 15,
                    favourite ? Ui.ICON_HEART_FILLED : Ui.ICON_HEART, favourite ? theme.accent : theme.textSecondary);
            hits.add(new Hit(likeX, y + 10, buttonSize, buttonSize, Action.TOGGLE_FAVORITE, track));

            int addX = likeX + buttonSize + 2;
            Ui.iconCentered(graphics, addX + buttonSize / 2, y + rowH / 2, 15, Ui.ICON_PLUS, theme.textSecondary);
            hits.add(new Hit(addX, y + 10, buttonSize, buttonSize, Action.ADD_TO_PLAYLIST, track));

            int playX = addX + buttonSize + 2;
            Ui.iconCentered(graphics, playX + buttonSize / 2, y + rowH / 2, 14, Ui.ICON_PLAY, theme.text);
            hits.add(new Hit(playX, y + 10, buttonSize, buttonSize, Action.PLAY_TRACK, track));
        }

        return y + rowH + 2;
    }

    private int sectionLabel(GuiGraphicsExtractor graphics, Theme theme, int x, int y, String label) {
        UiFont.draw(graphics, label, x + 2, y + 12, 10, theme.textMuted, 1);
        return y + 22;
    }

    // ------------------------------------------------------------------
    // player bar
    // ------------------------------------------------------------------

    private void drawPlayerBar(GuiGraphicsExtractor graphics, Theme theme, int mouseX, int mouseY) {
        Ui.rect(graphics, winX + 8, playerY, winW - 16, playerH, 12, theme.playerBar);
        Ui.outline(graphics, winX + 8, playerY, winW - 16, playerH, 12, theme.divider);

        NowPlaying state = music.state;
        int x = winX + 20;
        int coverSize = playerH - 20;
        int coverY = playerY + 10;

        Track current = state.hasTrack() ? findTrack(state.trackId()) : null;
        Identifier cover = current == null ? null : CoverCache.get(current.id(), current.thumbnailUrl());
        if (cover != null) {
            Ui.image(graphics, cover, x, coverY, coverSize, 8, theme.playerBar, 0xFFFFFFFF);
        } else {
            Ui.cover(graphics, state.hasTrack() ? state.trackId() : "mcmusic", x, coverY, coverSize, 8, theme.playerBar);
        }

        int textX = x + coverSize + 12;
        int maxTextW = compact ? 90 : Math.max(120, winW / 4);
        if (state.hasTrack()) {
            UiFont.draw(graphics, UiFont.ellipsize(state.title(), 13, maxTextW), textX, playerY + playerH / 2 - 3, 13, theme.text, 1);
            String subtitle = state.artist() + (state.loading() ? "  \u00b7  buffering\u2026" : "");
            UiFont.draw(graphics, UiFont.ellipsize(subtitle, 11, maxTextW), textX, playerY + playerH / 2 + 12, 11, theme.textSecondary, 0);
        } else {
            UiFont.draw(graphics, "Nothing playing", textX, playerY + playerH / 2 - 3, 13, theme.text, 1);
            UiFont.draw(graphics, "Pick a song to start", textX, playerY + playerH / 2 + 12, 11, theme.textSecondary, 0);
        }

        // like button
        int likeX = textX + maxTextW + 8;
        if (state.hasTrack() && likeX + 20 < winX + winW * 0.55) {
            boolean favourite = music.isFavorite(state.trackId());
            Ui.iconCentered(graphics, likeX + 10, playerY + playerH / 2, 15,
                    favourite ? Ui.ICON_HEART_FILLED : Ui.ICON_HEART, favourite ? theme.accent : theme.textSecondary);
            hits.add(new Hit(likeX, playerY + 10, 22, playerH - 20, Action.TOGGLE_FAVORITE_CURRENT, null));
        }

        // transport
        int centreX = compact ? winX + winW - 60 : winX + winW / 2 + 40;
        int transportY = playerY + playerH / 2 - 14;
        int buttonGap = 34;
        int prevX = centreX - buttonGap;
        int nextX = centreX + buttonGap;

        Ui.iconCentered(graphics, prevX, transportY + 14, 16, Ui.ICON_PREV, theme.textSecondary);
        hits.add(new Hit(prevX - 14, transportY, 28, 28, Action.PREVIOUS, null));

        boolean playing = state.hasTrack() && state.playing();
        Ui.rect(graphics, centreX - 17, transportY - 1, 34, 34, 17, theme.accent);
        Ui.iconCentered(graphics, centreX, transportY + 16, 15, playing ? Ui.ICON_PAUSE : Ui.ICON_PLAY, theme.accentText);
        hits.add(new Hit(centreX - 17, transportY - 1, 34, 34, Action.TOGGLE_PLAY, null));

        Ui.iconCentered(graphics, nextX, transportY + 14, 16, Ui.ICON_NEXT, theme.textSecondary);
        hits.add(new Hit(nextX - 14, transportY, 28, 28, Action.NEXT, null));

        if (!compact) {
            int shuffleX = prevX - 30;
            Ui.iconCentered(graphics, shuffleX, transportY + 14, 14, Ui.ICON_SHUFFLE,
                    music.shuffle ? theme.accent : Theme.alpha(theme.textSecondary, 0.7f));
            hits.add(new Hit(shuffleX - 12, transportY, 24, 28, Action.TOGGLE_SHUFFLE, null));

            int repeatX = nextX + 30;
            Ui.iconCentered(graphics, repeatX, transportY + 14, 14, Ui.ICON_REPEAT,
                    music.repeat ? theme.accent : Theme.alpha(theme.textSecondary, 0.7f));
            hits.add(new Hit(repeatX - 12, transportY, 24, 28, Action.TOGGLE_REPEAT, null));
        }

        // progress
        int progressLeft = compact ? winX + 16 : winX + 24;
        int progressRight = compact ? winX + winW - 16 : winX + winW - 24;
        int progressW = Math.max(60, progressRight - progressLeft);
        int progressY = playerY + playerH - 13;

        int duration = state.durationSeconds();
        float progress = 0f;
        int position = state.positionSeconds();
        if (duration > 0) {
            progress = Math.min(1f, (pendingSeek >= 0 ? pendingSeek : position) / (float) duration);
        }
        Ui.progressBar(graphics, progressLeft, progressY, progressW, progress,
                Theme.alpha(theme.textMuted, 0.28f), theme.accent, theme.text);

        if (state.hasTrack()) {
            String left = Track.formatPosition(pendingSeek >= 0 ? (int) (pendingSeek * duration) : position);
            String right = Track.formatTime(duration);
            UiFont.draw(graphics, left, progressLeft, progressY - 4, 9, theme.textMuted, 0);
            UiFont.drawRight(graphics, right, progressRight, progressY - 4, 9, theme.textMuted, 0);
        }
        hits.add(new Hit(progressLeft, progressY - 8, progressW, 16, Action.SEEK, null));

        // volume
        if (!compact) {
            int volumeW = 72;
            int volumeX = winX + winW - 32 - volumeW;
            int volumeY = playerY + 16;
            Ui.icon(graphics, volumeX - 20, volumeY - 3, 14, Ui.ICON_VOLUME, theme.textSecondary);
            float volume = pendingVolume >= 0 ? pendingVolume : (float) MusicConfig.get().volume;
            Ui.progressBar(graphics, volumeX, volumeY + 2, volumeW, volume, Theme.alpha(theme.textMuted, 0.28f),
                    theme.textSecondary, theme.text);
            hits.add(new Hit(volumeX, volumeY - 6, volumeW, 20, Action.VOLUME, null));
        }
    }

    // ------------------------------------------------------------------
    // overlays
    // ------------------------------------------------------------------

    private void drawSettings(GuiGraphicsExtractor graphics, Theme theme, int mouseX, int mouseY) {
        graphics.fill(winX, winY, winX + winW, winY + winH, 0x99000000);
        int w = Math.min(420, winW - 40);
        int h = Math.min(360, winH - 40);
        int x = winX + (winW - w) / 2;
        int y = winY + (winH - h) / 2;
        Ui.rect(graphics, x, y, w, h, 14, theme.surface);
        Ui.outline(graphics, x, y, w, h, 14, theme.divider);

        UiFont.draw(graphics, "Settings", x + 20, y + 32, 18, theme.text, 1);
        Ui.iconCentered(graphics, x + w - 28, y + 26, 13, Ui.ICON_CLOSE, theme.textSecondary);
        hits.add(new Hit(x + w - 42, y + 12, 28, 28, Action.CLOSE_SETTINGS, null));

        MusicConfig config = MusicConfig.get();
        int rowY = y + 58;

        UiFont.draw(graphics, "Provider", x + 20, rowY, 11, theme.textMuted, 1);
        String provider = config.isInvidious() ? "Invidious" : "Piped";
        Ui.rect(graphics, x + 20, rowY + 8, 110, 26, 8, theme.window);
        UiFont.draw(graphics, provider, x + 32, rowY + 26, 12, theme.text, 0);
        hits.add(new Hit(x + 20, rowY + 8, 110, 26, Action.TOGGLE_PROVIDER, null));
        rowY += 48;

        String url = config.isInvidious() ? config.invidiousUrl : config.pipedUrl;
        UiFont.draw(graphics, "Instance", x + 20, rowY, 11, theme.textMuted, 1);
        boolean editingUrl = "url".equals(settingsEditing);
        Ui.rect(graphics, x + 20, rowY + 8, w - 40, 26, 8, editingUrl ? theme.surfaceActive : theme.window);
        String shown = editingUrl ? settingsBuffer : url;
        if (editingUrl && (frameCount / 20) % 2 == 0) {
            shown = shown + "|";
        }
        UiFont.draw(graphics, UiFont.ellipsize(shown, 11, w - 60), x + 32, rowY + 26, 11, theme.text, 0);
        hits.add(new Hit(x + 20, rowY + 8, w - 40, 26, Action.EDIT_URL, null));
        rowY += 48;

        UiFont.draw(graphics, "Theme", x + 20, rowY, 11, theme.textMuted, 1);
        Ui.rect(graphics, x + 20, rowY + 8, 110, 26, 8, theme.window);
        UiFont.draw(graphics, config.darkTheme ? "Dark" : "Light", x + 32, rowY + 26, 12, theme.text, 0);
        hits.add(new Hit(x + 20, rowY + 8, 110, 26, Action.TOGGLE_THEME, null));
        rowY += 48;

        UiFont.draw(graphics, "Voice Chat", x + 20, rowY, 11, theme.textMuted, 1);
        boolean streaming = config.broadcastToVoicechat;
        String streamLabel = streaming
                ? (VoicechatSupport.isActive() ? "Streaming" : "No voice chat mod")
                : "Off (local only)";
        Ui.rect(graphics, x + 20, rowY + 8, 110, 26, 8, theme.window);
        UiFont.draw(graphics, streamLabel, x + 32, rowY + 26, 11, streaming ? theme.text : theme.textMuted, 0);
        hits.add(new Hit(x + 20, rowY + 8, 110, 26, Action.TOGGLE_BROADCAST, null));

        // volume slider
        UiFont.draw(graphics, "Volume", x + w - 160, rowY, 11, theme.textMuted, 1);
        float volume = pendingVolume >= 0 ? pendingVolume : (float) config.volume;
        Ui.progressBar(graphics, x + w - 160, rowY + 20, 130, volume, Theme.alpha(theme.textMuted, 0.28f),
                theme.accent, theme.text);
        hits.add(new Hit(x + w - 160, rowY + 12, 130, 20, Action.VOLUME, null));
        rowY += 52;

        String route = config.broadcastToVoicechat
                ? "Mixed into your Simple Voice Chat microphone stream"
                : "Local playback only - nobody else hears this";
        UiFont.draw(graphics, route, x + 20, rowY + 6, 11, theme.textSecondary, 0);
        String backend = "Backend: " + config.audioBackend + "  \u00b7  on this PC";
        UiFont.draw(graphics, backend, x + 20, rowY + 22, 11, theme.textMuted, 0);

        int closeW = 96;
        boolean hover = Ui.inside(mouseX, mouseY, x + w - closeW - 20, y + h - 44, closeW, 30);
        Ui.rect(graphics, x + w - closeW - 20, y + h - 44, closeW, 30, 15, hover ? theme.accentHover : theme.accent);
        UiFont.draw(graphics, "Done", x + w - closeW - 20 + 32, y + h - 24, 12, theme.accentText, 1);
        hits.add(new Hit(x + w - closeW - 20, y + h - 44, closeW, 30, Action.CLOSE_SETTINGS, null));
    }

    private void drawCreatePlaylist(GuiGraphicsExtractor graphics, Theme theme, int mouseX, int mouseY) {
        graphics.fill(winX, winY, winX + winW, winY + winH, 0x99000000);
        int w = Math.min(320, winW - 40);
        int h = 150;
        int x = winX + (winW - w) / 2;
        int y = winY + (winH - h) / 2;
        Ui.rect(graphics, x, y, w, h, 14, theme.surface);
        UiFont.draw(graphics, "New playlist", x + 20, y + 34, 16, theme.text, 1);
        Ui.rect(graphics, x + 20, y + 52, w - 40, 30, 8, theme.window);
        String shown = createBuffer.isEmpty() ? "Playlist name" : createBuffer;
        UiFont.draw(graphics, shown + ((frameCount / 20) % 2 == 0 ? "|" : ""), x + 32, y + 72, 12,
                createBuffer.isEmpty() ? theme.textMuted : theme.text, 0);

        int okW = 90;
        boolean hover = Ui.inside(mouseX, mouseY, x + w - okW - 20, y + h - 44, okW, 28);
        Ui.rect(graphics, x + w - okW - 20, y + h - 44, okW, 28, 14, hover ? theme.accentHover : theme.accent);
        UiFont.draw(graphics, "Create", x + w - okW - 20 + 24, y + h - 26, 12, theme.accentText, 1);
        hits.add(new Hit(x + w - okW - 20, y + h - 44, okW, 28, Action.CONFIRM_PLAYLIST, null));
    }

    private void drawNotice(GuiGraphicsExtractor graphics, Theme theme, String message) {
        int w = Math.min(contentW - 20, (int) UiFont.width(message, 12) + 60);
        int x = contentX + (contentW - w) / 2;
        int y = contentY + contentH - 46;
        Ui.rect(graphics, x, y, w, 34, 10, theme.surfaceActive);
        Ui.icon(graphics, x + 12, y + 10, 14, Ui.ICON_WIFI, theme.accent);
        UiFont.draw(graphics, UiFont.ellipsize(message, 12, w - 50), x + 34, y + 22, 12, theme.text, 0);
    }

    private void drawSpinner(GuiGraphicsExtractor graphics, Theme theme, int centreX, int centreY) {
        for (int i = 0; i < 10; i++) {
            double angle = (System.currentTimeMillis() % 1200) / 1200.0 * Math.PI * 2 + i * 0.32;
            int x = centreX + (int) (Math.cos(angle) * 16);
            int y = centreY + (int) (Math.sin(angle) * 16);
            int alpha = 40 + i * 20;
            Ui.rect(graphics, x - 2, y - 2, 4, 4, 2, Theme.alpha(theme.accent, alpha / 255f));
        }
    }

    // ------------------------------------------------------------------
    // input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!Ui.inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
                continue;
            }
            if (activate(hit, mouseX, mouseY, button)) {
                return true;
            }
        }

        if (settingsOpen || creatingPlaylist) {
            return true;
        }
        if (!Ui.inside(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            searchFocused = false;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean activate(Hit hit, double mouseX, double mouseY, int button) {
        if (button != 0 && hit.action != Action.SEEK && hit.action != Action.VOLUME) {
            return true;
        }
        switch (hit.action) {
            case NAV_HOME -> {
                view = View.HOME;
                scrollTarget = 0;
            }
            case NAV_SEARCH -> {
                view = View.SEARCH;
                searchFocused = true;
                scrollTarget = 0;
            }
            case NAV_LIBRARY -> {
                view = View.LIBRARY;
                scrollTarget = 0;
            }
            case NAV_FAVORITES -> {
                view = View.FAVORITES;
                scrollTarget = 0;
            }
            case OPEN_PLAYLIST -> {
                selectedPlaylist = (Integer) hit.payload;
                view = View.PLAYLIST;
                scrollTarget = 0;
            }
            case PLAY_TRACK -> {
                music.playQueue(new ArrayList<>(currentTracks()), currentTracks().indexOf(hit.payload));
            }
            case PLAY_FAVORITES -> music.playQueue(new ArrayList<>(music.favorites), 0);
            case PLAY_PLAYLIST -> {
                LocalPlaylist playlist = playlistById((String) hit.payload);
                if (playlist != null) {
                    music.playQueue(new ArrayList<>(playlist.tracks), 0);
                }
            }
            case DELETE_PLAYLIST -> {
                LocalPlaylist playlist = playlistById((String) hit.payload);
                if (playlist != null) {
                    music.deletePlaylist(playlist);
                    selectedPlaylist = -1;
                    view = View.LIBRARY;
                }
            }
            case TOGGLE_FAVORITE -> music.toggleFavorite((Track) hit.payload);
            case TOGGLE_FAVORITE_CURRENT -> music.toggleFavoriteCurrent();
            case ADD_TO_PLAYLIST -> {
                Track track = (Track) hit.payload;
                if (music.playlists.isEmpty()) {
                    music.createPlaylist("My playlist").add(track);
                    music.notify("Created \u201cMy playlist\u201d");
                    music.save();
                } else {
                    music.addToPlaylist(music.playlists.get(0), track);
                }
            }
            case TOGGLE_PLAY -> music.togglePlayPause();
            case NEXT -> music.next();
            case PREVIOUS -> music.previous();
            case TOGGLE_SHUFFLE -> {
                music.shuffle = !music.shuffle;
                music.save();
            }
            case TOGGLE_REPEAT -> {
                music.repeat = !music.repeat;
                music.save();
            }
            case SEEK -> {
                draggingProgress = true;
                updateSeek(mouseX);
            }
            case VOLUME -> {
                draggingVolume = true;
                updateVolume(mouseX);
            }
            case FOCUS_SEARCH -> {
                searchFocused = true;
                caret = query.length();
                if (view != View.SEARCH) {
                    view = View.SEARCH;
                }
            }
            case CLOSE -> onClose();
            case OPEN_SETTINGS -> settingsOpen = true;
            case CLOSE_SETTINGS -> {
                settingsOpen = false;
                settingsEditing = null;
                MusicConfig.get().save();
            }
            case TOGGLE_PROVIDER -> {
                MusicConfig config = MusicConfig.get();
                config.providerType = config.isInvidious() ? "piped" : "invidious";
                config.save();
            }
            case TOGGLE_THEME -> {
                MusicConfig config = MusicConfig.get();
                config.darkTheme = !config.darkTheme;
                config.save();
            }
            case TOGGLE_BROADCAST -> {
                MusicConfig config = MusicConfig.get();
                config.broadcastToVoicechat = !config.broadcastToVoicechat;
                config.save();
                if (config.broadcastToVoicechat && !VoicechatSupport.isActive()) {
                    music.notify("Simple Voice Chat is not running - music stays on this PC.");
                } else if (config.broadcastToVoicechat) {
                    music.notify("Music is now mixed into your voice chat stream.");
                } else {
                    music.notify("Local playback only - nobody else hears this anymore.");
                }
            }
            case EDIT_URL -> {
                MusicConfig config = MusicConfig.get();
                settingsEditing = "url";
                settingsBuffer = config.isInvidious() ? config.invidiousUrl : config.pipedUrl;
            }
            case NEW_PLAYLIST -> {
                creatingPlaylist = true;
                createBuffer = "";
            }
            case CONFIRM_PLAYLIST -> {
                if (!createBuffer.isBlank()) {
                    music.createPlaylist(createBuffer.trim());
                }
                creatingPlaylist = false;
                createBuffer = "";
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingProgress) {
            draggingProgress = false;
            applySeek();
        }
        if (draggingVolume) {
            draggingVolume = false;
            applyVolume();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingProgress) {
            updateSeek(event.x());
            return true;
        }
        if (draggingVolume) {
            updateVolume(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Ui.inside(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            scrollTarget -= (float) scrollY * 32f;
            if (scrollTarget < 0) {
                scrollTarget = 0;
            }
            if (scrollTarget > maxScroll) {
                scrollTarget = maxScroll;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (settingsOpen || creatingPlaylist) {
            if (event.isEscape()) {
                settingsOpen = false;
                creatingPlaylist = false;
                settingsEditing = null;
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                if ("url".equals(settingsEditing)) {
                    settingsBuffer = chop(settingsBuffer);
                    applySettingsUrl();
                } else if (creatingPlaylist) {
                    createBuffer = chop(createBuffer);
                }
                return true;
            }
            if (event.isConfirmation()) {
                if (creatingPlaylist) {
                    if (!createBuffer.isBlank()) {
                        music.createPlaylist(createBuffer.trim());
                    }
                    creatingPlaylist = false;
                    createBuffer = "";
                } else {
                    settingsOpen = false;
                    settingsEditing = null;
                    MusicConfig.get().save();
                }
                return true;
            }
            return true;
        }

        if (event.isEscape()) {
            if (searchFocused) {
                searchFocused = false;
                return true;
            }
            onClose();
            return true;
        }

        if (searchFocused) {
            switch (key) {
                case InputConstants.KEY_BACKSPACE -> {
                    if (caret > 0 && !query.isEmpty()) {
                        query = query.substring(0, caret - 1) + query.substring(Math.min(caret, query.length()));
                        caret--;
                    }
                    return true;
                }
                case InputConstants.KEY_DELETE -> {
                    if (caret < query.length()) {
                        query = query.substring(0, caret) + query.substring(caret + 1);
                    }
                    return true;
                }
                case InputConstants.KEY_LEFT -> {
                    caret = Math.max(0, caret - 1);
                    return true;
                }
                case InputConstants.KEY_RIGHT -> {
                    caret = Math.min(query.length(), caret + 1);
                    return true;
                }
                case InputConstants.KEY_HOME -> {
                    caret = 0;
                    return true;
                }
                case InputConstants.KEY_END -> {
                    caret = query.length();
                    return true;
                }
                default -> {
                }
            }
            if (event.isConfirmation()) {
                caret = query.length();
                view = View.SEARCH;
                scrollTarget = 0;
                music.search(query);
                return true;
            }
        }

        if (key == InputConstants.KEY_SPACE && !searchFocused) {
            music.togglePlayPause();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int codepoint = event.codepoint();
        if (codepoint < 32) {
            return false;
        }
        if ("url".equals(settingsEditing)) {
            settingsBuffer += (char) codepoint;
            applySettingsUrl();
            return true;
        }
        if (creatingPlaylist) {
            if (createBuffer.length() < 48) {
                createBuffer += (char) codepoint;
            }
            return true;
        }
        if (searchFocused) {
            if (query.length() < 90) {
                int position = Math.min(caret, query.length());
                query = query.substring(0, position) + (char) codepoint + query.substring(position);
                caret = position + 1;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        music.save();
        MusicConfig.get().save();
        minecraft.gui.setScreen(null);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void applySettingsUrl() {
        MusicConfig config = MusicConfig.get();
        if (config.isInvidious()) {
            config.invidiousUrl = settingsBuffer;
        } else {
            config.pipedUrl = settingsBuffer;
        }
    }

    private void updateSeek(double mouseX) {
        int progressLeft = compact ? winX + 16 : winX + 24;
        int progressRight = compact ? winX + winW - 16 : winX + winW - 24;
        int progressW = Math.max(60, progressRight - progressLeft);
        pendingSeek = (float) Math.max(0, Math.min(1, (mouseX - progressLeft) / progressW));
    }

    private void applySeek() {
        int duration = music.state.durationSeconds();
        if (pendingSeek >= 0 && duration > 0) {
            music.seek((int) (pendingSeek * duration));
        }
        pendingSeek = -1f;
    }

    private void updateVolume(double mouseX) {
        int volumeW = 72;
        int volumeX = winX + winW - 32 - volumeW;
        pendingVolume = (float) Math.max(0, Math.min(1, (mouseX - volumeX) / volumeW));
    }

    private void applyVolume() {
        if (pendingVolume >= 0) {
            music.setVolume(pendingVolume);
        }
        pendingVolume = -1f;
    }

    private List<Track> currentTracks() {
        return switch (view) {
            case SEARCH -> music.results;
            case FAVORITES -> music.favorites;
            case PLAYLIST -> {
                LocalPlaylist playlist = selectedPlaylist();
                yield playlist == null ? List.of() : playlist.tracks;
            }
            default -> music.recentlyPlayed;
        };
    }

    private Track findTrack(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (List<Track> list : List.of(music.queue, music.favorites, music.recentlyPlayed, music.results)) {
            for (Track track : list) {
                if (track.id().equals(id)) {
                    return track;
                }
            }
        }
        return null;
    }

    private LocalPlaylist selectedPlaylist() {
        if (selectedPlaylist < 0 || selectedPlaylist >= music.playlists.size()) {
            return null;
        }
        return music.playlists.get(selectedPlaylist);
    }

    private LocalPlaylist playlistById(String id) {
        for (LocalPlaylist playlist : music.playlists) {
            if (playlist.id.equals(id)) {
                return playlist;
            }
        }
        return null;
    }

    private static String chop(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, value.length() - 1);
    }

    private static String totalTime(List<Track> tracks) {
        int seconds = 0;
        for (Track track : tracks) {
            seconds += Math.max(0, track.durationSeconds());
        }
        int minutes = seconds / 60;
        if (minutes >= 60) {
            return String.format(Locale.ROOT, "%d h %d min", minutes / 60, minutes % 60);
        }
        return minutes + " min";
    }

    private static String greeting() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 5) {
            return "Good night";
        }
        if (hour < 11) {
            return "Good morning";
        }
        if (hour < 18) {
            return "Good afternoon";
        }
        return "Good evening";
    }

    private enum Action {
        NAV_HOME, NAV_SEARCH, NAV_LIBRARY, NAV_FAVORITES, OPEN_PLAYLIST, PLAY_TRACK, PLAY_FAVORITES,
        PLAY_PLAYLIST, DELETE_PLAYLIST, TOGGLE_FAVORITE, TOGGLE_FAVORITE_CURRENT, ADD_TO_PLAYLIST,
        TOGGLE_PLAY, NEXT, PREVIOUS, TOGGLE_SHUFFLE, TOGGLE_REPEAT, SEEK, VOLUME, FOCUS_SEARCH,
        CLOSE, OPEN_SETTINGS, CLOSE_SETTINGS, TOGGLE_PROVIDER, TOGGLE_THEME, TOGGLE_BROADCAST, EDIT_URL,
        NEW_PLAYLIST, CONFIRM_PLAYLIST
    }

    private record Hit(int x, int y, int w, int h, Action action, Object payload) {
    }
}

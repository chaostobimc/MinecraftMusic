package com.chaostobimc.minecraftmusic.gui;

/**
 * Die Bereiche der Seitenleiste.
 */
public enum View {
    HOME("Startseite", Icons.HOME),
    EXPLORE("Entdecken", Icons.EXPLORE),
    SEARCH("Suchen", Icons.SEARCH),
    PLAYLISTS("Playlists", Icons.PLAYLIST),
    FAVORITES("Favoriten", Icons.FAVORITE),
    SETTINGS("Einstellungen", Icons.SETTINGS);

    public final String label;
    public final String icon;

    View(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }
}

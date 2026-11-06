package com.chaostobimc.minecraftmusic.gui.widgets;


/**
 * Beschreibung eines klickbaren Bereichs. Wird vom Screen gesammelt
 * und in {@code mouseClicked} abgearbeitet - so bleibt der Code schlank
 * und wir brauchen keine Minecraft-Buttons im Desktop-Look.
 */
public final class UiButton {

    public final int x, y, w, h;
    public final Runnable action;
    public final String tooltip;

    public UiButton(int x, int y, int w, int h, Runnable action) {
        this(x, y, w, h, action, null);
    }

    public UiButton(int x, int y, int w, int h, Runnable action, String tooltip) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.action = action;
        this.tooltip = tooltip;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public boolean isHovered(int mouseX, int mouseY) {
        return contains(mouseX, mouseY);
    }
}

package com.chaostobimc.minecraftmusic.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Kleiner Zeichen-Kontext: kennt Mausposition, Hover, Clip und Scroll.
 * Dadurch bleibt der Screen-Code lesbar und Widgets koennen einfach
 * als Methoden umgesetzt werden.
 */
public final class UiContext {

    public final GuiGraphicsExtractor graphics;
    public final Font font;
    public final int mouseX;
    public final int mouseY;
    public final float delta;

    private int clipX0, clipY0, clipX1, clipY1;
    private boolean clipped;
    private int scrollOffset;

    public UiContext(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float delta) {
        this.graphics = graphics;
        this.font = font;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.delta = delta;
    }

    public boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public void pushClip(int x, int y, int w, int h) {
        clipX0 = x;
        clipY0 = y;
        clipX1 = x + w;
        clipY1 = y + h;
        clipped = true;
        graphics.enableScissor(x, y, x + w, y + h);
    }

    public void popClip() {
        if (clipped) {
            graphics.disableScissor();
            clipped = false;
        }
    }

    public boolean visible(int y, int h) {
        if (!clipped) {
            return true;
        }
        return y + h >= clipY0 && y <= clipY1;
    }

    public int clipTop() {
        return clipY0;
    }

    public int clipBottom() {
        return clipY1;
    }

    public int clipLeft() {
        return clipX0;
    }

    public int clipRight() {
        return clipX1;
    }

    public int scrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int offset) {
        this.scrollOffset = offset;
    }
}

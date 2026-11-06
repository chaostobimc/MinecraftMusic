package de.chaostobimc.mcmusic.client.ui;

import de.chaostobimc.mcmusic.MCMusic;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Drawing primitives for the flat, rounded, desktop style interface.
 *
 * <p>Rounded corners come from a small anti aliased mask texture ({@code corner.png}) that
 * is stretched to the requested radius and flipped for the other three corners, so every
 * panel stays razor sharp at any GUI scale.</p>
 */
public final class Ui {

    public static final Identifier CORNER = MCMusic.id("textures/gui/corner.png");
    public static final Identifier CORNER_INVERSE = MCMusic.id("textures/gui/corner_inv.png");
    public static final Identifier SHADOW = MCMusic.id("textures/gui/shadow.png");
    public static final Identifier ICONS = MCMusic.id("textures/gui/icons.png");

    public static final int ICON_SIZE = 64;
    public static final int ICON_COLUMNS = 6;

    // icon indices, matching tools/gen_ui_assets.py
    public static final int ICON_HOME = 0;
    public static final int ICON_SEARCH = 1;
    public static final int ICON_LIBRARY = 2;
    public static final int ICON_HEART = 3;
    public static final int ICON_HEART_FILLED = 4;
    public static final int ICON_PLAY = 5;
    public static final int ICON_PAUSE = 6;
    public static final int ICON_NEXT = 7;
    public static final int ICON_PREV = 8;
    public static final int ICON_SHUFFLE = 9;
    public static final int ICON_REPEAT = 10;
    public static final int ICON_SETTINGS = 11;
    public static final int ICON_CLOSE = 12;
    public static final int ICON_VOLUME = 13;
    public static final int ICON_VOLUME_MUTED = 14;
    public static final int ICON_PLUS = 15;
    public static final int ICON_NOTE = 16;
    public static final int ICON_CHECK = 17;
    public static final int ICON_CHEVRON_LEFT = 18;
    public static final int ICON_CHEVRON_RIGHT = 19;
    public static final int ICON_LIST = 20;
    public static final int ICON_LINK = 21;
    public static final int ICON_WIFI = 22;
    public static final int ICON_GLOBE = 23;

    private Ui() {
    }

    // ------------------------------------------------------------------
    // shapes
    // ------------------------------------------------------------------

    public static void fill(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
    }

    public static void gradient(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int top, int bottom) {
        graphics.fillGradient(x, y, x + width, y + height, top, bottom);
    }

    /** Filled rounded rectangle. */
    public static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + r, y + height - r, color);
        graphics.fill(x + width - r, y + r, x + width, y + height - r, color);
        corner(graphics, CORNER, x, y, r, color, false, false);
        corner(graphics, CORNER, x + width - r, y, r, color, true, false);
        corner(graphics, CORNER, x, y + height - r, r, color, false, true);
        corner(graphics, CORNER, x + width - r, y + height - r, r, color, true, true);
    }

    /** Vertical rounded gradient. */
    public static void gradientRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                    int radius, int top, int bottom) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            graphics.fillGradient(x, y, x + width, y + height, top, bottom);
            return;
        }
        graphics.fillGradient(x + r, y, x + width - r, y + height, top, bottom);
        graphics.fillGradient(x, y + r, x + r, y + height - r, top, bottom);
        graphics.fillGradient(x + width - r, y + r, x + width, y + height - r, top, bottom);
        corner(graphics, CORNER, x, y, r, top, false, false);
        corner(graphics, CORNER, x + width - r, y, r, top, true, false);
        corner(graphics, CORNER, x, y + height - r, r, bottom, false, true);
        corner(graphics, CORNER, x + width - r, y + height - r, r, bottom, true, true);
    }

    /** 1 px rounded outline. */
    public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        if (radius <= 0) {
            graphics.outline(x, y, width, height, color);
            return;
        }
        int r = Math.min(radius, Math.min(width, height) / 2);
        graphics.fill(x + r, y, x + width - r, y + 1, color);
        graphics.fill(x + r, y + height - 1, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + 1, y + height - r, color);
        graphics.fill(x + width - 1, y + r, x + width, y + height - r, color);
        // corner arcs approximated by the mask, drawn as a thin ring
        outlineCorner(graphics, x, y, r, color, false, false);
        outlineCorner(graphics, x + width - r, y, r, color, true, false);
        outlineCorner(graphics, x, y + height - r, r, color, false, true);
        outlineCorner(graphics, x + width - r, y + height - r, r, color, true, true);
    }

    private static void outlineCorner(GuiGraphicsExtractor graphics, int x, int y, int r, int color,
                                      boolean flipX, boolean flipY) {
        corner(graphics, CORNER, x, y, r, color, flipX, flipY);
        int inner = Math.max(1, r - 1);
        cornerMask(graphics, x + (flipX ? 1 : 0), y + (flipY ? 1 : 0), inner, color, flipX, flipY);
    }

    private static void corner(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int size, int color,
                               boolean flipX, boolean flipY) {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(flipX ? x + size : x, flipY ? y + size : y);
        pose.scale(flipX ? -1f : 1f, flipY ? -1f : 1f);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, size, size, 128, 128, color);
        pose.popMatrix();
    }

    private static void cornerMask(GuiGraphicsExtractor graphics, int x, int y, int size, int color,
                                   boolean flipX, boolean flipY) {
        corner(graphics, CORNER_INVERSE, x, y, size, color, flipX, flipY);
    }

    /** Soft drop shadow around a rect. */
    public static void shadow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int spread, int color) {
        int s = spread;
        var pose = graphics.pose();
        pose.pushMatrix();
        graphics.blit(RenderPipelines.GUI_TEXTURED, SHADOW, x - s, y - s, 0f, 0f, width + s * 2, height + s * 2, 128, 128, color);
        pose.popMatrix();
    }

    /** Picture with rounded corners; the corners are masked with the given background colour. */
    public static void image(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int size, int radius,
                             int background, int tint) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, size, size, size, size, tint);
        int r = Math.max(0, Math.min(radius, size / 2));
        if (r > 0) {
            corner(graphics, CORNER_INVERSE, x, y, r, background, false, false);
            corner(graphics, CORNER_INVERSE, x + size - r, y, r, background, true, false);
            corner(graphics, CORNER_INVERSE, x, y + size - r, r, background, false, true);
            corner(graphics, CORNER_INVERSE, x + size - r, y + size - r, r, background, true, true);
        }
    }

    /** Flat cover placeholder with a music note. */
    public static void cover(GuiGraphicsExtractor graphics, String seed, int x, int y, int size, int radius, int background) {
        int color = Theme.colorFor(seed == null ? "" : seed);
        gradientRect(graphics, x, y, size, size, radius, color, Theme.mix(color, 0xFF000000, 0.45f));
        int iconSize = Math.max(8, (int) (size * 0.42f));
        icon(graphics, x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize, ICON_NOTE, 0xCCFFFFFF);
        if (radius > 0) {
            int r = Math.min(radius, size / 2);
            corner(graphics, CORNER_INVERSE, x, y, r, background, false, false);
            corner(graphics, CORNER_INVERSE, x + size - r, y, r, background, true, false);
            corner(graphics, CORNER_INVERSE, x, y + size - r, r, background, false, true);
            corner(graphics, CORNER_INVERSE, x + size - r, y + size - r, r, background, true, true);
        }
    }

    // ------------------------------------------------------------------
    // icons
    // ------------------------------------------------------------------

    public static void icon(GuiGraphicsExtractor graphics, int x, int y, int size, int index, int color) {
        int column = index % ICON_COLUMNS;
        int row = index / ICON_COLUMNS;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y,
                column * ICON_SIZE, row * ICON_SIZE, size, size,
                ICON_COLUMNS * ICON_SIZE, (24 / ICON_COLUMNS) * ICON_SIZE, color);
    }

    public static void iconCentered(GuiGraphicsExtractor graphics, int centerX, int centerY, int size, int index, int color) {
        icon(graphics, centerX - size / 2, centerY - size / 2, size, index, color);
    }

    // ------------------------------------------------------------------
    // misc
    // ------------------------------------------------------------------

    public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** Horizontal progress bar. */
    public static void progressBar(GuiGraphicsExtractor graphics, int x, int y, int width, float progress,
                                   int trackColor, int fillColor, int knobColor) {
        int height = 4;
        rect(graphics, x, y, width, height, 2, trackColor);
        int filled = (int) (width * Math.max(0f, Math.min(1f, progress)));
        if (filled > 0) {
            rect(graphics, x, y, Math.max(2, filled), height, 2, fillColor);
        }
        int knobSize = 10;
        int knobX = x + Math.max(0, Math.min(width - knobSize, filled - knobSize / 2));
        rect(graphics, knobX, y - (knobSize - height) / 2, knobSize, knobSize, knobSize / 2, knobColor);
    }

    public static int sliderKnobPosition(int x, int width, float value) {
        return x + (int) (Math.max(0f, Math.min(1f, value)) * width);
    }
}

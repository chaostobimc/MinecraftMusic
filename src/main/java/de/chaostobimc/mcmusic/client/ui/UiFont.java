package de.chaostobimc.mcmusic.client.ui;

import de.chaostobimc.mcmusic.MCMusic;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders text from the generated high resolution glyph atlas.
 *
 * <p>This is what makes the interface look like a desktop application instead of a
 * pixel font menu: the glyphs are baked at 40 px with anti aliasing and drawn scaled,
 * with real kerning widths taken from {@link FontMetrics}.</p>
 */
public final class UiFont {

    private static final Identifier TEXTURE = MCMusic.id("textures/gui/font.png");
    private static final Map<Character, Integer> INDEX = new HashMap<>();

    static {
        int index = 0;
        for (char c = 32; c < 127; c++) {
            INDEX.put(c, index++);
        }
        for (char c = 160; c < 256; c++) {
            INDEX.put(c, index++);
        }
        for (int i = 0; i < FontMetrics.SPECIALS.length(); i++) {
            INDEX.put(FontMetrics.SPECIALS.charAt(i), index++);
        }
    }

    private UiFont() {
    }

    public static float scale(float size) {
        return size / (float) FontMetrics.GLYPH_SIZE;
    }

    public static float lineHeight(float size) {
        return FontMetrics.LINE_HEIGHT * scale(size);
    }

    public static float width(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        float scale = scale(size);
        float total = 0f;
        for (int i = 0; i < text.length(); i++) {
            total += advance(text.charAt(i)) * scale;
        }
        return total;
    }

    private static float advance(char c) {
        Integer index = INDEX.get(c);
        if (index == null || index >= FontMetrics.ADVANCE_Q4.length) {
            return FontMetrics.GLYPH_SIZE * 0.42f;
        }
        return FontMetrics.ADVANCE_Q4[index] / 4f;
    }

    /**
     * Draws text with the baseline at {@code y}.
     *
     * @param x        left edge of the text
     * @param y        baseline position
     * @param size     cap height in gui units
     * @param color    ARGB colour
     * @param weight   0 = regular, 1 = semibold (drawn twice with a sub pixel offset)
     */
    public static void draw(GuiGraphicsExtractor graphics, String text, float x, float y, float size, int color, int weight) {
        if (text == null || text.isEmpty()) {
            return;
        }
        var pose = graphics.pose();
        float scale = scale(size);
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);

        float pen = 0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Integer index = INDEX.get(c);
            if (index == null) {
                pen += FontMetrics.GLYPH_SIZE * 0.42f;
                continue;
            }
            if (c != ' ') {
                int column = index % FontMetrics.COLUMNS;
                int row = index / FontMetrics.COLUMNS;
                int px = (int) (pen + FontMetrics.ORIGIN_X);
                graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, px, FontMetrics.ORIGIN_Y,
                        column * FontMetrics.CELL_WIDTH, row * FontMetrics.CELL_HEIGHT,
                        FontMetrics.CELL_WIDTH, FontMetrics.CELL_HEIGHT,
                        FontMetrics.ATLAS_WIDTH, FontMetrics.ATLAS_HEIGHT, color);
                if (weight > 0) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, px + 1, FontMetrics.ORIGIN_Y,
                            column * FontMetrics.CELL_WIDTH, row * FontMetrics.CELL_HEIGHT,
                            FontMetrics.CELL_WIDTH, FontMetrics.CELL_HEIGHT,
                            FontMetrics.ATLAS_WIDTH, FontMetrics.ATLAS_HEIGHT, color);
                }
            }
            pen += advance(c);
        }
        pose.popMatrix();
    }

    public static void draw(GuiGraphicsExtractor graphics, String text, float x, float y, float size, int color) {
        draw(graphics, text, x, y, size, color, 0);
    }

    /** Draws text vertically centred inside the given rect. */
    public static void drawCentered(GuiGraphicsExtractor graphics, String text, float centerX, float centerY,
                                    float size, int color, int weight) {
        float width = width(text, size);
        draw(graphics, text, centerX - width / 2f, centerY + size * 0.36f, size, color, weight);
    }

    public static void drawRight(GuiGraphicsExtractor graphics, String text, float right, float y, float size,
                                 int color, int weight) {
        draw(graphics, text, right - width(text, size), y, size, color, weight);
    }

    /** Shortens text with an ellipsis so it never leaves {@code maxWidth}. */
    public static String ellipsize(String text, float size, float maxWidth) {
        if (text == null) {
            return "";
        }
        if (width(text, size) <= maxWidth) {
            return text;
        }
        String ellipsis = "\u2026";
        float ellipsisWidth = width(ellipsis, size);
        StringBuilder builder = new StringBuilder();
        float used = 0f;
        for (int i = 0; i < text.length(); i++) {
            float advance = advance(text.charAt(i)) * scale(size);
            if (used + advance + ellipsisWidth > maxWidth) {
                break;
            }
            builder.append(text.charAt(i));
            used += advance;
        }
        return builder + ellipsis;
    }
}

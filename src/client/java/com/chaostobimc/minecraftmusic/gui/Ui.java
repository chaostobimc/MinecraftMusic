package com.chaostobimc.minecraftmusic.gui;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Zeichen-Helfer fuer den Desktop-Look.
 * <p>
 * Wichtig: Alle Texte laufen ueber eigene Roboto-Fonts (echte TTF-Fonts,
 * mit FreeType gerastert und 3x Oversampling). Dadurch ist der Text
 * scharf und sieht nicht nach Minecraft-Pixelfont aus.
 */
public final class Ui {

    // ---- Farbpalette (YouTube Music Dark) ----
    public static final int BG_TOP = 0xFF1B1B1F;
    public static final int BG_BOTTOM = 0xFF0E0E10;
    public static final int SIDEBAR = 0xFF121214;
    public static final int SURFACE = 0xFF1F1F23;
    public static final int SURFACE_HI = 0xFF2A2A30;
    public static final int SURFACE_HOVER = 0xFF303038;
    public static final int BAR = 0xFF18181B;

    public static final int TEXT = 0xFFF2F2F5;
    public static final int TEXT_DIM = 0xFFA9A9B3;
    public static final int TEXT_MUTED = 0xFF6E6E78;

    public static final int ACCENT = 0xFFFF4D57;      // YouTube-Music-Rot
    public static final int ACCENT_DIM = 0xFFB93640;
    public static final int ACCENT_SOFT = 0x33FF4D57;

    public static final int GREEN = 0xFF3EDC81;
    public static final int BORDER = 0x22FFFFFF;
    public static final int BORDER_STRONG = 0x3AFFFFFF;

    // ---- Fonts ----
    private static final FontDescription F_REG = font("ui_9");
    private static final FontDescription F_MED = font("ui_9m");
    private static final FontDescription F_BOLD = font("ui_9b");
    private static final FontDescription F_11M = font("ui_11m");
    private static final FontDescription F_11R = font("ui_11r");
    private static final FontDescription F_13B = font("ui_13b");
    private static final FontDescription F_18B = font("ui_18b");
    private static final FontDescription F_26B = font("ui_26b");
    private static final FontDescription F_ICON9 = font("icons_9");
    private static final FontDescription F_ICON12 = font("icons_12");
    private static final FontDescription F_ICON16 = font("icons_16");
    private static final FontDescription F_ICON22 = font("icons_22");

    private static FontDescription font(String path) {
        return new FontDescription.Resource(Identifier.fromNamespaceAndPath(MinecraftMusic.MOD_ID, path));
    }

    private Ui() {
    }

    // ---- Text ----

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, plain(s, F_REG), x, y, color, false);
    }

    public static void textMed(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, plain(s, F_MED), x, y, color, false);
    }

    public static void textBold(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, plain(s, F_BOLD), x, y, color, false);
    }

    public static void text11(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, plain(s, F_11R), x, y, color, false);
    }

    public static void text11Med(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, plain(s, F_11M), x, y, color, false);
    }

    public static void text13Bold(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, plain(s, F_13B), x, y, color, false);
    }

    public static void text18Bold(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, plain(s, F_18B), x, y, color, false);
    }

    public static void text26Bold(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, plain(s, F_26B), x, y, color, false);
    }

    /** Icon-Groessen in Pixeln (entsprechen den size-Werten der Font-JSONs). */
    public static final int ICON_9 = 9;
    public static final int ICON_12 = 12;
    public static final int ICON_16 = 16;
    public static final int ICON_22 = 22;

    /**
     * Zeichnet ein Icon so, dass seine <b>Mitte</b> auf (centerX, centerY) liegt.
     * Material-Icons haben 1 em Advance, die Glyphe selbst ist aber nur ca.
     * 0,7 em gross - deshalb wird zentriert statt mit festen Offsets gerechnet.
     */
    public static void iconCentered(GuiGraphicsExtractor g, Font font, String glyph,
                                    int centerX, int centerY, int size, int color) {
        int advance = iconAdvance(font, glyph, size);
        int x = Math.round(centerX - advance / 2F);
        // Text wird mit der Oberkante der Zeile gezeichnet; 0.75 em ist die
        // ungefaehre Mitte der Icon-Glyphe in der Zeilenbox.
        int y = Math.round(centerY - size * 0.5F);
        g.text(font, plain(glyph, iconFont(size)), x, y, color, false);
    }

    /** Gemessene Advance-Breite eines Icons in Pixeln. */
    public static int iconAdvance(Font font, String glyph, int size) {
        return font.width(plain(glyph, iconFont(size)));
    }

    public static void icon9(GuiGraphicsExtractor g, Font font, String glyph, int x, int y, int color) {
        g.text(font, plain(glyph, F_ICON9), x, y, color, false);
    }

    private static FontDescription iconFont(int size) {
        return switch (size) {
            case 9 -> F_ICON9;
            case 12 -> F_ICON12;
            case 16 -> F_ICON16;
            default -> F_ICON22;
        };
    }

    public static void icon12(GuiGraphicsExtractor g, Font font, String glyph, int x, int y, int color) {
        g.text(font, plain(glyph, F_ICON12), x, y, color, false);
    }

    public static void icon16(GuiGraphicsExtractor g, Font font, String glyph, int x, int y, int color) {
        g.text(font, plain(glyph, F_ICON16), x, y, color, false);
    }

    public static void icon22(GuiGraphicsExtractor g, Font font, String glyph, int x, int y, int color) {
        g.text(font, plain(glyph, F_ICON22), x, y, color, false);
    }

    private static Component plain(String s, FontDescription description) {
        return Component.literal(s).withStyle(Style.EMPTY.withFont(description));
    }

    // ---- Messen ----

    public static int width11(Font font, String s) {
        return font.width(plain(s, F_11R));
    }

    public static int width11Med(Font font, String s) {
        return font.width(plain(s, F_11M));
    }

    public static int width13Bold(Font font, String s) {
        return font.width(plain(s, F_13B));
    }

    public static int width18Bold(Font font, String s) {
        return font.width(plain(s, F_18B));
    }

    public static int width26Bold(Font font, String s) {
        return font.width(plain(s, F_26B));
    }

    public static int widthMed(Font font, String s) {
        return font.width(plain(s, F_MED));
    }

    public static int widthIcon(Font font, String glyph) {
        return font.width(plain(glyph, F_ICON16));
    }

    /** Kuerzt Text, bis er in die Breite passt. */
    public static String trim(Font font, String s, int maxWidth, FontDescription description) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        int full = font.width(plain(s, description));
        if (full <= maxWidth) {
            return s;
        }
        String ellipsis = "…";
        int ellipsisWidth = font.width(plain(ellipsis, description));
        if (maxWidth <= ellipsisWidth) {
            return ellipsis;
        }
        // Breiten sind additiv: einmal den ganzen String messen und
        // zeichenweise die Breite aus dem Prefix bestimmen.
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            String candidate = s.substring(0, mid);
            if (font.width(plain(candidate, description)) + ellipsisWidth <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return s.substring(0, lo) + ellipsis;
    }

    public static String trim11(Font font, String s, int maxWidth) {
        return trim(font, s, maxWidth, F_11R);
    }

    public static String trim11Med(Font font, String s, int maxWidth) {
        return trim(font, s, maxWidth, F_11M);
    }

    public static String trim13Bold(Font font, String s, int maxWidth) {
        return trim(font, s, maxWidth, F_13B);
    }

    public static String trimMed(Font font, String s, int maxWidth) {
        return trim(font, s, maxWidth, F_MED);
    }

    // ---- Formen ----

    /** Gefüllte, weich gerundete Fläche (9-Slice aus einer vorgerenderten Ecke). */
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        int r = fitRadius(radius, w, h);
        int side = r * 2;          // Textur ist 2r x 2r gross
        int midW = w - side;
        int midH = h - side;
        Identifier tex = radiusTexture(r);

        // Mitte und Kantenstreifen füllen (die Ecken bleiben frei)
        if (midW > 0) {
            fill(g, x + r, y, x + r + midW, y + h, color);
        }
        if (midH > 0) {
            fill(g, x, y + r, x + r, y + r + midH, color);
            fill(g, x + w - r, y + r, x + w, y + r + midH, color);
        }

        // Ecken: je ein Viertel der Textur, damit halbtransparente Farben
        // nicht doppelt übereinanderliegen.
        blitCorner(g, tex, x, y, 0, 0, r, side, color);
        blitCorner(g, tex, x + w - r, y, r, 0, r, side, color);
        blitCorner(g, tex, x, y + h - r, 0, r, r, side, color);
        blitCorner(g, tex, x + w - r, y + h - r, r, r, r, side, color);
    }

    private static void blitCorner(GuiGraphicsExtractor g, Identifier tex, int x, int y,
                                   int u, int v, int r, int side, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, r, r, r, r, side, side, color);
    }

    /**
     * Waehlt die groesste vorhandene Ecken-Textur, die noch in die Flaeche passt.
     * Vorhanden sind r3, r4, r6, r8, r10, r12, r16, r20, r24.
     */
    private static int fitRadius(int radius, int w, int h) {
        int limit = Math.min(w, h) / 2;
        int best = 3;
        for (int candidate : new int[]{3, 4, 6, 8, 10, 12, 16, 20, 24}) {
            if (candidate <= radius && candidate <= limit) {
                best = candidate;
            }
        }
        return best;
    }

    public static void fill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        if (x1 > x0 && y1 > y0) {
            g.fill(x0, y0, x1, y1, color);
        }
    }

    /** 1px-Rahmen. */
    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        fill(g, x, y, x + w, y + 1, color);
        fill(g, x, y + h - 1, x + w, y + h, color);
        fill(g, x, y, x + 1, y + h, color);
        fill(g, x + w - 1, y, x + w, y + h, color);
    }

    /** Weicher Schatten hinter Karten/Panels. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex("shadow"), x - 16, y - 16, 0F, 0F,
                w + 32, h + 32, 128, 128, 0x66000000);
    }

    private static Identifier radiusTexture(int radius) {
        return Identifier.fromNamespaceAndPath(MinecraftMusic.MOD_ID, "textures/gui/r" + radius + ".png");
    }

    /** Textur-ID im GUI-Ordner. 26.x erwartet das .png-Suffix im Identifier. */
    public static Identifier tex(String file) {
        String path = file.endsWith(".png") ? file : file + ".png";
        return Identifier.fromNamespaceAndPath(MinecraftMusic.MOD_ID, "textures/gui/" + path);
    }

    /** Farbiger Kreis (fuer Play-Button, Toggle-Knopf, Avatare). */
    public static void circle(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex("circle"), x, y, 0F, 0F, size, size, 64, 64, color);
    }

    // ---- Transform ----

    public static Matrix3x2fStack pose(GuiGraphicsExtractor g) {
        return g.pose();
    }

    public static void scaled(GuiGraphicsExtractor g, float scale, int originX, int originY, Runnable body) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(scale, scale);
        pose.translate(-originX, -originY);
        body.run();
        pose.popMatrix();
    }

    public static void image(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0F, 0F, w, h, w, h, 0xFFFFFFFF);
    }

    public static void imageTinted(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0F, 0F, w, h, w, h, color);
    }

    /** Spalten-Scrollbalken. */
    public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int thumbY, int thumbH) {
        roundRect(g, x, y, 3, h, 3, 0x18FFFFFF);
        roundRect(g, x, thumbY, 3, Math.max(12, thumbH), 3, 0x66FFFFFF);
    }
}

package de.chaostobimc.mcmusic.client.ui;

import de.chaostobimc.mcmusic.config.MusicConfig;

/**
 * Colours and metrics of the interface. Two palettes: the YouTube Music dark theme and a
 * light variant, both tuned for a flat "desktop app" look.
 */
public final class Theme {

    public final int window;
    public final int sidebar;
    public final int surface;
    public final int surfaceHover;
    public final int surfaceActive;
    public final int playerBar;
    public final int text;
    public final int textSecondary;
    public final int textMuted;
    public final int accent;
    public final int accentHover;
    public final int accentText;
    public final int divider;
    public final int shadow;
    public final int search;

    private Theme(boolean dark) {
        if (dark) {
            window = 0xFF0B0B0D;
            sidebar = 0xFF0B0B0D;
            surface = 0xFF17171A;
            surfaceHover = 0xFF232328;
            surfaceActive = 0xFF2C2C33;
            playerBar = 0xFF17171A;
            text = 0xFFF7F7F9;
            textSecondary = 0xFFA9A9B3;
            textMuted = 0xFF6E6E79;
            accent = 0xFFFF0033;
            accentHover = 0xFFFF3355;
            accentText = 0xFFFFFFFF;
            divider = 0x22FFFFFF;
            shadow = 0x99000000;
            search = 0xFF1E1E23;
        } else {
            window = 0xFFF4F4F6;
            sidebar = 0xFFF4F4F6;
            surface = 0xFFFFFFFF;
            surfaceHover = 0xFFEDEDF1;
            surfaceActive = 0xFFE2E2E8;
            playerBar = 0xFFFFFFFF;
            text = 0xFF111114;
            textSecondary = 0xFF5A5A66;
            textMuted = 0xFF8B8B96;
            accent = 0xFFFF0033;
            accentHover = 0xFFE00029;
            accentText = 0xFFFFFFFF;
            divider = 0x18000000;
            shadow = 0x44000000;
            search = 0xFFE7E7EC;
        }
    }

    private static Theme current;

    public static Theme get() {
        boolean dark = MusicConfig.get().darkTheme;
        if (current == null || current.isDark() != dark) {
            current = new Theme(dark);
        }
        return current;
    }

    private boolean isDark() {
        return window == 0xFF0B0B0D;
    }

    /** A stable, pleasant colour for a song / playlist, used for generated cover art. */
    public static int colorFor(String seed) {
        int hash = 0;
        for (int i = 0; i < seed.length(); i++) {
            hash = hash * 31 + seed.charAt(i);
        }
        float hue = Math.abs(hash % 360) / 360f;
        return 0xFF000000 | hsvToRgb(hue, 0.62f, 0.72f);
    }

    public static int accentFor(int index) {
        int[] colours = {0xFF3D5AFE, 0xFF00BFA5, 0xFFD81B60, 0xFFFF6D00, 0xFF7C4DFF, 0xFF00897B, 0xFFC51162};
        return colours[Math.abs(index) % colours.length];
    }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    public static int alpha(int color, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * ((color >>> 24) & 0xFF));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int colorA, int colorB, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int a = (int) (((colorA >>> 24) & 0xFF) * (1 - clamped) + ((colorB >>> 24) & 0xFF) * clamped);
        int r = (int) (((colorA >> 16) & 0xFF) * (1 - clamped) + ((colorB >> 16) & 0xFF) * clamped);
        int g = (int) (((colorA >> 8) & 0xFF) * (1 - clamped) + ((colorB >> 8) & 0xFF) * clamped);
        int b = (int) ((colorA & 0xFF) * (1 - clamped) + (colorB & 0xFF) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

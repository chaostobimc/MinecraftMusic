package com.chaostobimc.minecraftmusic.gui.widgets;

import java.util.ArrayList;
import java.util.List;

/**
 * Sammelt alle klickbaren Bereiche eines Frames.
 * Der zuletzt hinzugefuegte (oberste) Bereich gewinnt.
 */
public final class HitList {

    private final List<UiButton> buttons = new ArrayList<>();

    public void clear() {
        buttons.clear();
    }

    public UiButton add(int x, int y, int w, int h, Runnable action) {
        UiButton button = new UiButton(x, y, w, h, action);
        buttons.add(button);
        return button;
    }

    public UiButton add(int x, int y, int w, int h, Runnable action, String tooltip) {
        UiButton button = new UiButton(x, y, w, h, action, tooltip);
        buttons.add(button);
        return button;
    }

    /** Liefert true, wenn ein Button geklickt wurde. */
    public boolean click(double mouseX, double mouseY) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            UiButton button = buttons.get(i);
            if (button.contains(mouseX, mouseY)) {
                button.action.run();
                return true;
            }
        }
        return false;
    }

    public UiButton hovered(int mouseX, int mouseY) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            UiButton button = buttons.get(i);
            if (button.contains(mouseX, mouseY) && button.tooltip != null) {
                return button;
            }
        }
        return null;
    }
}

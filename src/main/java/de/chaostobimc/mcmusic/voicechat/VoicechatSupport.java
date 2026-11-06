package de.chaostobimc.mcmusic.voicechat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Answers "can we stream through Simple Voice Chat?" without ever touching a Simple Voice
 * Chat class.
 *
 * <p>The voice chat API is a compile-only dependency: on a client without the mod those
 * classes simply do not exist. Every class that references them ({@link MCMusicVoicechatPlugin})
 * is therefore only ever loaded by Simple Voice Chat itself, while the rest of the mod talks
 * to this class.</p>
 */
public final class VoicechatSupport {

    private static volatile boolean active;

    private VoicechatSupport() {
    }

    /** Called by the voice chat plugin once Simple Voice Chat handed us its client API. */
    public static void markActive() {
        active = true;
    }

    /** True when the Simple Voice Chat mod is installed. */
    public static boolean isModLoaded() {
        return FabricLoader.getInstance().isModLoaded("voicechat");
    }

    /** True when Simple Voice Chat is installed and its client API is ready. */
    public static boolean isActive() {
        return active;
    }

    /** True when music can currently be heard by other players. */
    public static boolean canStreamToOthers() {
        return active && de.chaostobimc.mcmusic.config.MusicConfig.get().broadcastToVoicechat;
    }
}

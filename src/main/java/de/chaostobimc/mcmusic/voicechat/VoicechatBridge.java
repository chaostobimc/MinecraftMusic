package de.chaostobimc.mcmusic.voicechat;

import de.chaostobimc.mcmusic.MCMusic;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Isolates every Simple Voice Chat API call behind one class.
 *
 * <p>Nothing here is touched unless {@link #isAvailable()} is true, which keeps MC Music
 * loadable on a client that does not have Simple Voice Chat installed.</p>
 */
public final class VoicechatBridge {

    private static VoicechatApi api;
    private static boolean categoryRegistered;

    private VoicechatBridge() {
    }

    static void setApi(VoicechatApi voicechatApi) {
        api = voicechatApi;
    }

    /** True when the SVC mod is loaded and its API handed us a server instance. */
    public static boolean isAvailable() {
        return api instanceof VoicechatServerApi;
    }

    public static boolean isModLoaded() {
        return FabricLoader.getInstance().isModLoaded("voicechat");
    }

    public static VoicechatServerApi serverApi() {
        if (api instanceof VoicechatServerApi serverApi) {
            return serverApi;
        }
        throw new IllegalStateException("Simple Voice Chat server API is not available");
    }

    /** Registers the volume slider category once, so players can mix the music in SVC. */
    public static void ensureVolumeCategory() {
        if (categoryRegistered || !isAvailable()) {
            return;
        }
        try {
            VolumeCategory category = api.volumeCategoryBuilder()
                    .setId(PlaybackSession.VOLUME_CATEGORY)
                    .setName("Music player")
                    .setNameTranslationKey("mcmusic.voicechat.category")
                    .setDescription("Volume of music played through MC Music")
                    .setDescriptionTranslationKey("mcmusic.voicechat.category.desc")
                    .build();
            serverApi().registerVolumeCategory(category);
            categoryRegistered = true;
        } catch (Exception exception) {
            MCMusic.LOGGER.warn("Could not register the voice chat volume category", exception);
        }
    }
}

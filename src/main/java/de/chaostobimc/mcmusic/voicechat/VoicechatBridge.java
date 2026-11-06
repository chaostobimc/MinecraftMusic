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
                    .setIcon(noteIcon())
                    .setDescriptionTranslationKey("mcmusic.voicechat.category.desc")
                    .build();
            serverApi().registerVolumeCategory(category);
            categoryRegistered = true;
        } catch (Exception exception) {
            MCMusic.LOGGER.warn("Could not register the voice chat volume category", exception);
        }
    }

    /** A 16x16 musical note, used as the icon of the voice chat volume slider. */
    private static int[][] noteIcon() {
        int[][] icon = new int[16][16];
        int white = 0xFFFFFFFF;
        for (int y = 2; y <= 11; y++) {
            icon[y][9] = white;
            icon[y][10] = white;
        }
        for (int x = 11; x <= 13; x++) {
            icon[2][x] = white;
            icon[3][x] = white;
            icon[4][x] = white;
        }
        for (int y = 9; y <= 13; y++) {
            for (int x = 4; x <= 9; x++) {
                double dx = (x - 6.5) / 3.0;
                double dy = (y - 11.5) / 2.2;
                if (dx * dx + dy * dy <= 1.0) {
                    icon[y][x] = white;
                }
            }
        }
        return icon;
    }
}

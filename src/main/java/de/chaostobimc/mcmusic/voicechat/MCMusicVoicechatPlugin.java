package de.chaostobimc.mcmusic.voicechat;

import de.chaostobimc.mcmusic.MCMusic;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;

/**
 * Registered through the {@code voicechat} entrypoint in fabric.mod.json.
 */
public final class MCMusicVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return MCMusic.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatBridge.setApi(api);
        VoicechatBridge.ensureVolumeCategory();
        MCMusic.LOGGER.info("Hooked into Simple Voice Chat.");
    }
}

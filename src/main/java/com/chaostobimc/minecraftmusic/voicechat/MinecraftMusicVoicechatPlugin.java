package com.chaostobimc.minecraftmusic.voicechat;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import com.chaostobimc.minecraftmusic.server.VoicechatBridge;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

/**
 * Simple-Voice-Chat-Addon-Entrypoint.
 * Wird nur geladen, wenn Simple Voice Chat installiert ist (siehe fabric.mod.json).
 */
public class MinecraftMusicVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return MinecraftMusic.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi serverApi) {
            VoicechatBridge.get().setServerApi(serverApi);
        }
        MinecraftMusic.LOGGER.debug("SVC-Plugin initialisiert");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class,
                event -> VoicechatBridge.get().setServerApi(event.getVoicechat()));

        registration.registerEvent(VoicechatServerStoppedEvent.class,
                event -> VoicechatBridge.get().setServerApi(null));
    }
}

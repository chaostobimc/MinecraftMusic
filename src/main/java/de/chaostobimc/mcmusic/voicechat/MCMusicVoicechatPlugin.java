package de.chaostobimc.mcmusic.voicechat;

import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.client.playback.ClientPlayer;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;

/**
 * Hooks the music into Simple Voice Chat - completely client side.
 *
 * <p>Simple Voice Chat offers exactly one official way for a client mod to get audio to
 * other players without a server mod: {@link MergeClientSoundEvent}. It is fired on the
 * microphone thread for every captured frame and lets plugins add their own audio to it.
 * Whatever ends up in that frame is encoded and sent to the server like normal voice, so
 * every player in hearing range hears the song - and the server needs nothing but the
 * regular Simple Voice Chat mod.</p>
 */
public final class MCMusicVoicechatPlugin implements VoicechatPlugin {

    public static final String PLUGIN_ID = "mcmusic";

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi voicechatApi) {
        if (voicechatApi instanceof VoicechatClientApi) {
            VoicechatSupport.markActive();
            MCMusic.LOGGER.info("MC Music hooked into Simple Voice Chat - music will be mixed into your microphone stream");
        } else {
            MCMusic.LOGGER.warn("Simple Voice Chat handed us a server API; MC Music only streams on the client");
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MergeClientSoundEvent.class, event -> {
            short[] frame = ClientPlayer.get().nextVoiceFrame();
            if (frame != null) {
                event.mergeAudio(frame);
            }
        });
    }
}

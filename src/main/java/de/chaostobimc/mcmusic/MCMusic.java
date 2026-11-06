package de.chaostobimc.mcmusic;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants of MC Music.
 *
 * <p>MC Music is a <b>client only</b> mod. There is no server entrypoint and no server
 * side playback: the music is decoded on your PC and mixed into your own Simple Voice
 * Chat microphone stream, which any Simple Voice Chat server relays to the players around
 * you. The server never needs this mod.</p>
 */
public final class MCMusic {

    public static final String MOD_ID = "mcmusic";
    public static final Logger LOGGER = LoggerFactory.getLogger("MC Music");

    private MCMusic() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

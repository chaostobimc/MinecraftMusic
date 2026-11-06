package de.chaostobimc.mcmusic.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.client.ui.MusicScreen;
import de.chaostobimc.mcmusic.config.MusicConfig;
import de.chaostobimc.mcmusic.voicechat.VoicechatSupport;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Client entrypoint: key binding, the {@code /mcmusic} command, the music screen and the
 * tick hook that keeps the playback state in sync.
 *
 * <p>MC Music is a client only mod - there is no server initializer and no networking.</p>
 */
public final class MCMusicClient implements ClientModInitializer {

    private static KeyMapping openKey;
    /** Set by the key binding or the command, handled on the next client tick. */
    private static boolean openRequested;

    @Override
    public void onInitializeClient() {
        MCMusic.LOGGER.info("MC Music: client initialising");

        MusicConfig.load();
        ClientMusic.get().load();
        ClientMusic.get().syncVolume();
        MCMusic.LOGGER.info("MC Music: {} favourites, {} playlists, Simple Voice Chat active: {}",
                ClientMusic.get().favorites.size(), ClientMusic.get().playlists.size(), VoicechatSupport.isActive());

        KeyMapping.Category category = KeyMapping.Category.register(MCMusic.id("keys"));
        openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("mcmusic.key.open", InputConstants.KEY_M, category));
        MCMusic.LOGGER.info("MC Music: key binding '{}' registered for key M", openKey.getName());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("mcmusic")
                        .executes(context -> {
                            requestOpen();
                            return 1;
                        })
                        .then(ClientCommands.literal("play")
                                .then(ClientCommands.argument("song", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            ClientMusic.get().playFromUrl(StringArgumentType.getString(context, "song"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("stop")
                                .executes(context -> {
                                    ClientMusic.get().stop();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("pause")
                                .executes(context -> {
                                    ClientMusic.get().togglePlayPause();
                                    return 1;
                                }))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openRequested) {
                openRequested = false;
                openScreen();
            }
            while (openKey.consumeClick()) {
                requestOpen();
            }
            ClientMusic.get().tick();
        });

        MCMusic.LOGGER.info("MC Music ready - client only, streaming through Simple Voice Chat.");
    }

    private static void requestOpen() {
        MCMusic.LOGGER.info("MC Music: opening the music screen");
        openRequested = true;
    }

    private static void openScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.gui.screen();
        if (current instanceof MusicScreen) {
            return;
        }
        try {
            minecraft.gui.setScreen(new MusicScreen());
            MCMusic.LOGGER.info("MC Music: music screen opened (previous screen: {})",
                    current == null ? "none" : current.getClass().getSimpleName());
        } catch (Throwable throwable) {
            MCMusic.LOGGER.error("MC Music: could not open the music screen", throwable);
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("MC Music: " + throwable));
            }
        }
    }
}

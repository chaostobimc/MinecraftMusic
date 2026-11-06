package de.chaostobimc.mcmusic.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.client.ui.MusicScreen;
import de.chaostobimc.mcmusic.config.MusicConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Client entrypoint: key binding, the {@code /mcmusic} command, the music screen and the
 * tick hook that keeps the playback state in sync.
 *
 * <p>MC Music is a client only mod - there is no server initializer and no networking.</p>
 */
public final class MCMusicClient implements ClientModInitializer {

    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        MusicConfig.load();
        ClientMusic.get().load();
        ClientMusic.get().syncVolume();

        KeyMapping.Category category = KeyMapping.Category.register(MCMusic.id("keys"));
        openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("mcmusic.key.open", InputConstants.KEY_M, category));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("mcmusic")
                        .executes(context -> {
                            openScreen();
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
            while (openKey.consumeClick()) {
                openScreen();
            }
            ClientMusic.get().tick();
        });

        MCMusic.LOGGER.info("MC Music ready - client only, streaming through Simple Voice Chat.");
    }

    private static void openScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() == null) {
            minecraft.gui.setScreen(new MusicScreen());
        }
    }
}

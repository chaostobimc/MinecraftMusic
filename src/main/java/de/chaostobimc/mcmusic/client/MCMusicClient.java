package de.chaostobimc.mcmusic.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.client.ui.MusicScreen;
import de.chaostobimc.mcmusic.config.MusicConfig;
import de.chaostobimc.mcmusic.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client entrypoint: key binding, the {@code /mcmusic} command, the screen and the
 * receiver that keeps the UI in sync with the server.
 */
public final class MCMusicClient implements ClientModInitializer {

    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        MusicConfig.load();
        ClientMusic.get().load();

        KeyMapping.Category category = KeyMapping.Category.register(MCMusic.id("keys"));
        openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("mcmusic.key.open", com.mojang.blaze3d.platform.InputConstants.KEY_M, category));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.PlaybackState.TYPE,
                (payload, context) -> ClientMusic.get().onState(payload));

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
                                }))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    openScreen();
                } else {
                    client.player.sendSystemMessage(Component.literal("Close the current screen first."));
                }
            }
        });

        MCMusic.LOGGER.info("MC Music client ready.");
    }

    private static void openScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new MusicScreen());
    }
}

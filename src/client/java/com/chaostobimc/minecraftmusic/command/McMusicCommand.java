package com.chaostobimc.minecraftmusic.command;

import com.chaostobimc.minecraftmusic.audio.MusicPlayer;
import com.chaostobimc.minecraftmusic.gui.McMusicScreen;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * /mcmusic            -> GUI oeffnen
 * /mcmusic stop       -> Wiedergabe stoppen
 * /mcmusic broadcast  -> SVC-Broadcast an/aus
 */
public final class McMusicCommand {

    private McMusicCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("mcmusic")
                .executes(ctx -> {
                    Minecraft.getInstance().execute(() ->
                            Minecraft.getInstance().gui.setScreen(new McMusicScreen()));
                    return 1;
                })
                .then(ClientCommands.literal("open").executes(ctx -> {
                    Minecraft.getInstance().execute(() ->
                            Minecraft.getInstance().gui.setScreen(new McMusicScreen()));
                    return 1;
                }))
                .then(ClientCommands.literal("stop").executes(ctx -> {
                    MusicPlayer.get().stop();
                    ctx.getSource().sendFeedback(Component.literal("§cWiedergabe gestoppt"));
                    return 1;
                }))
                .then(ClientCommands.literal("broadcast").executes(ctx -> {
                    var config = com.chaostobimc.minecraftmusic.config.ConfigManager.get();
                    config.svcBroadcast = !config.svcBroadcast;
                    com.chaostobimc.minecraftmusic.config.ConfigManager.save();
                    ctx.getSource().sendFeedback(Component.literal(
                            "§cVoice-Chat-Broadcast: " + (config.svcBroadcast ? "§aAN" : "§cAUS")));
                    return 1;
                })));
    }
}

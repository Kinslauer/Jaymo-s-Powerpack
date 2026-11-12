package com.AdamNew.dynamicwaters.platform;

import com.AdamNew.dynamicwaters.network.Payload;
import java.nio.file.Path;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class Platform {
    private Platform() {
    }

    public static boolean isModLoaded(String modId) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
    }

    public static Path configDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
    }

    public static boolean isClient() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT;
    }

    public static String modVersion(String modId) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("absent");
    }

    public static boolean isOwnPlayer(ServerPlayer player) {
        return player.connection.connection.isMemoryConnection();
    }

    public static void sendToPlayer(ServerPlayer player, Payload payload) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }

    public static void sendToLevel(ServerLevel level, Payload payload) {
        for (ServerPlayer player : level.players()) sendToPlayer(player, payload);
    }
}

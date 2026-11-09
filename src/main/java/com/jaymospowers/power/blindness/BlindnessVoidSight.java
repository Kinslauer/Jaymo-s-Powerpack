package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Ascended ability: Void Sight.
 * Total blindness becomes true sight—see entities through walls, hear heartbeats, but the world stays dark.
 */
public final class BlindnessVoidSight {
    private static final Set<ServerPlayer> ACTIVE = new HashSet<>();

    private BlindnessVoidSight() {
    }

    public static void init() {
    }

    public static void key(ServerPlayer player, boolean down) {
        if (!down) return;
        if (ACTIVE.contains(player)) {
            deactivate(player);
        } else {
            activate(player);
        }
    }

    private static void activate(ServerPlayer player) {
        ACTIVE.add(player);
        BlindnessState state = BlindnessPower.state(player);
        BlindnessPower.set(player, state.withVoidSightActive(true));
        // Notify client to start void sight rendering
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                net.minecraft.network.chat.Component.translatable("message.jaymospowers.blindness.void_sight.on")
                        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.BOLD)
        ));
    }

    private static void deactivate(ServerPlayer player) {
        ACTIVE.remove(player);
        BlindnessState state = BlindnessPower.state(player);
        BlindnessPower.set(player, state.withVoidSightActive(false));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                net.minecraft.network.chat.Component.translatable("message.jaymospowers.blindness.void_sight.off")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)
        ));
    }

    public static void tick(ServerPlayer player) {
        if (ACTIVE.contains(player)) {
            // Void sight logic: reveal entities through walls within range
            // The client handles the actual rendering
        }
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.contains(player);
    }

    public static void forget(ServerPlayer player) {
        ACTIVE.remove(player);
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
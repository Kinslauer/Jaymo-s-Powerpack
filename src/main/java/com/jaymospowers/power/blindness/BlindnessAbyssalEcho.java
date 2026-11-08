package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.net.PowerPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Both ascended and corrupted: Abyssal Echo.
 * A pulse that blinds everyone in range and leaves them navigating by sound alone.
 */
public final class BlindnessAbyssalEcho {
    private static final int RANGE = 32; // blocks
    private static final int BLIND_DURATION = 400; // 20 seconds
    private static final int COOLDOWN = 1200; // 60 seconds
    private static final Set<ServerPlayer> ACTIVE = new HashSet<>();

    private BlindnessAbyssalEcho() {
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
        BlindnessPower.set(player, state); // state unchanged, just marking active
        // Send pulse to all nearby players
        for (ServerPlayer other : player.level().getPlayers(p -> p.distanceToSqr(player) <= RANGE * RANGE)) {
            if (other == player) continue;
            applyBlindness(other, BLIND_DURATION);
            // Also send echolocation pulse to them so they can "see" by sound
            ServerPlayNetworking.send(other, new PowerPayloads.BlindnessEcho(
                    player.getX(), player.getY(), player.getZ(),
                    0.0F,
                    1.0F,
                    "jaymospowers:abyssal_echo"
            ));
        }
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarText(
                net.minecraft.network.chat.Component.translatable("message.jaymospowers.blindness.abyssal_echo.on")
                        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.BOLD)
        ));
    }

    private static void deactivate(ServerPlayer player) {
        ACTIVE.remove(player);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarText(
                net.minecraft.network.chat.Component.translatable("message.jaymospowers.blindness.abyssal_echo.off")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)
        ));
    }

    public static void tick(ServerPlayer player) {
        if (ACTIVE.contains(player)) {
            // Abyssal Echo is a one-shot pulse, not a persistent effect
            // Deactivate immediately after pulse
            deactivate(player);
            // Set cooldown
            BlindnessState state = BlindnessPower.state(player);
            long now = player.level().getGameTime();
            BlindnessPower.set(player, state.withCooldown(BlindnessState.ECHO, now, COOLDOWN));
        }
    }

    private static void applyBlindness(ServerPlayer player, int duration) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.BLINDNESS, duration, 0, false, false, true
        ));
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
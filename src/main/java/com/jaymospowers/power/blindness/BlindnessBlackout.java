package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Corrupted ability: Blackout.
 * Your blindness spreads—nearby players share your blindness, and head/eye damage blinds everyone around you.
 */
public final class BlindnessBlackout {
    private static final int SPREAD_RANGE = 16; // blocks
    private static final int BLIND_DURATION = 200; // 10 seconds
    private static final Set<ServerPlayer> ACTIVE = new HashSet<>();

    private BlindnessBlackout() {
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
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                net.minecraft.network.chat.Component.translatable("message.jaymospowers.blindness.blackout.on")
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD)
        ));
    }

    private static void deactivate(ServerPlayer player) {
        ACTIVE.remove(player);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                net.minecraft.network.chat.Component.translatable("message.jaymospowers.blindness.blackout.off")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)
        ));
    }

    public static void tick(ServerPlayer player) {
        if (ACTIVE.contains(player)) {
            // Spread blindness to nearby players
            for (ServerPlayer other : player.level().getPlayers(p -> p != player && p.distanceToSqr(player) <= SPREAD_RANGE * SPREAD_RANGE)) {
                if (Fate.isCorrupted(other) || other == player) continue; // don't spread to other corrupted
                applyBlindness(other, BLIND_DURATION);
            }
        }
    }

    /** Called when a player takes significant head damage. */
    public static void triggerFromHeadDamage(ServerPlayer victim) {
        // Blind all nearby players who have the Blindness power (and are corrupted, or just anyone?)
        for (ServerPlayer player : victim.level().getPlayers(p -> p.distanceToSqr(victim) <= SPREAD_RANGE * SPREAD_RANGE)) {
            var power = com.jaymospowers.core.Powers.of(player);
            if (power == BlindnessPower.INSTANCE && Fate.isCorrupted(player)) {
                applyBlindness(player, BLIND_DURATION);
            }
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
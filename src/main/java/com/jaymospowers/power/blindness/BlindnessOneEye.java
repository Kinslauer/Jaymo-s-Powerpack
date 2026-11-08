package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.net.PowerPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Server-side one-eye blind: when active, the player's vision is partially blocked.
 * The client handles the visual effect; the server tracks state and cooldowns.
 */
public final class BlindnessOneEye {
    private static final int MAX_DURATION = 600; // 30 seconds max
    private static final Set<ServerPlayer> ACTIVE = new HashSet<>();

    private BlindnessOneEye() {
    }

    public static void init() {
        // State tracked via BlindnessState attachment
    }

    public static void start(ServerPlayer player) {
        ACTIVE.add(player);
        BlindnessState state = BlindnessPower.state(player);
        long now = player.level().getGameTime();
        BlindnessPower.set(player, state.withActive(BlindnessState.ONE_EYE, MAX_DURATION, now));
    }

    public static void stop(ServerPlayer player) {
        ACTIVE.remove(player);
        BlindnessState state = BlindnessPower.state(player);
        long now = player.level().getGameTime();
        // Set cooldown
        BlindnessPower.set(player, state.withCooldown(BlindnessState.ONE_EYE, now, BlindnessPower.ON_EYE_COOLDOWN).withDeafenActive(false));
    }

    public static void tick(ServerPlayer player) {
        BlindnessState state = BlindnessPower.state(player);
        if (!state.isActive(BlindnessState.ONE_EYE)) {
            ACTIVE.remove(player);
        }
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.contains(player);
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
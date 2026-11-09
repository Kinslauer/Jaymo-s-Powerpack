package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Server-side deafen: silences the player's own sounds so they don't give away their position.
 * While active, the player's footsteps, block breaks, etc. are muffled.
 */
public final class BlindnessDeafen {
    private static final Set<ServerPlayer> ACTIVE = new HashSet<>();

    private BlindnessDeafen() {
    }

    public static void init() {
    }

    public static void start(ServerPlayer player) {
        ACTIVE.add(player);
        BlindnessState state = BlindnessPower.state(player);
        BlindnessPower.set(player, state.withDeafenActive(true));
    }

    public static void stop(ServerPlayer player) {
        ACTIVE.remove(player);
        BlindnessState state = BlindnessPower.state(player);
        long now = player.level().getGameTime();
        BlindnessPower.set(player, state.withCooldown(BlindnessState.DEAFEN, now, BlindnessPower.DEAFEN_COOLDOWN).withDeafenActive(false));
    }

    public static void tick(ServerPlayer player) {
        BlindnessState state = BlindnessPower.state(player);
        if (!state.deafenActive()) {
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
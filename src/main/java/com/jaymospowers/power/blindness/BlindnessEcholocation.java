package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.net.PowerPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Server-side echolocation: hears sounds near players with Blindness and sends ring data to their clients.
 * Any sound within range triggers a sonar ring that ripples outward, painting blocks and entities in its path.
 */
public final class BlindnessEcholocation {
    private static final int RANGE = 48; // blocks
    private static final int RING_DURATION = 60; // ticks
    private static final int MAX_RINGS = 8;

    private static final List<ActiveRing> ACTIVE_RINGS = new ArrayList<>();

    private BlindnessEcholocation() {
    }

    public static void init() {
        // Register a sound event listener via the global sound event hook
        // Sounds are played through ServerPlayNetworking - we intercept them here
        // The actual sound detection happens in a mixin on Level.playSound
    }

    /** Called from a mixin when a sound is played in the world. */
    public static void onSoundPlayed(ServerLevel level, SoundEvent sound, SoundSource source, Vec3 pos, float volume, float pitch) {
        if (volume < 0.1F) {
            return; // too quiet
        }
        // Find players with Blindness power within range
        for (ServerPlayer player : level.getPlayers(p -> {
            if (!(p instanceof ServerPlayer sp)) return false;
            var power = com.jaymospowers.core.Powers.of(sp);
            return power == BlindnessPower.INSTANCE && sp.distanceToSqr(pos.x, pos.y, pos.z) <= RANGE * RANGE;
        })) {
            double dist = player.distanceToSqr(pos.x, pos.y, pos.z);
            if (dist > RANGE * RANGE) continue;
            // Send ring to this player's client
            ServerPlayNetworking.send(player, new PowerPayloads.BlindnessEcho(
                    pos.x, pos.y, pos.z,
                    (float) Math.sqrt(dist),
                    volume,
                    sound.getLocation().toString()
            ));
        }
    }

    public static void pulse(ServerPlayer player) {
        // Active echolocation pulse: the player makes a sound that creates a ring
        Vec3 pos = player.position();
        ServerPlayNetworking.send(player, new PowerPayloads.BlindnessEcho(
                pos.x, pos.y, pos.z,
                0.0F,
                1.0F,
                "jaymospowers:blindness_pulse"
        ));
    }

    public static void tick(ServerPlayer player) {
        // Clean up old rings
        ACTIVE_RINGS.removeIf(ring -> ring.age >= RING_DURATION);
    }

    public static void stop(ServerPlayer player) {
        // Nothing persistent to clean up
    }

    public static void clear() {
        ACTIVE_RINGS.clear();
    }

    /** An active echolocation ring on the client. */
    public record ActiveRing(Vec3 origin, float radius, int age, float volume, String soundId) {
    }
}
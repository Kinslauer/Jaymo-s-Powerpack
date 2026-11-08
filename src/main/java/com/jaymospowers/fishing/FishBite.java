package com.jaymospowers.fishing;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * (Track A, Andy: "all three together") The strike window. A fish that swam to the baited hook nibbles it
 * ({@link FishBites}), then takes it ({@link FishingReal#fishAtHook}): the bobber is held under and the player has
 * {@link #STRIKE_TICKS} to strike with use, which starts {@link FishingReal#startFight}. Missing the window lets it spit the
 * hook (with the bait). Standing in the water near the hook spooks the fish, so nothing comes (True Fishing's ideas, our own
 * code). A fish that leaves lives on and swims off.
 */
public final class FishBite {
    /** Ticks the player has to strike once the fish takes the hook (2.5 s). */
    static final int STRIKE_TICKS = 50;
    /** Ticks before another fish comes after a missed or spooked one. */
    static final int MISSED_REST_TICKS = 100;
    /** How hard a taken hook is held under each tick. */
    static final double TAKE_HOLD = 0.08;
    /** A player standing in water this close to the hook (blocks) spooks the fish. */
    static final double SPOOK_RADIUS = 6.0;
    /** How often (ticks) a spooked angler is told why nothing bites. */
    static final int SPOOK_TELL_EVERY = 200;

    private enum Phase { TAKEN, RESTING }

    private static final class Bite {
        final FishingHook hook;
        final int kind;
        final LivingEntity fish;
        Phase phase = Phase.TAKEN;
        long nextAt;

        Bite(FishingHook hook, int kind, LivingEntity fish) {
            this.hook = hook;
            this.kind = kind;
            this.fish = fish;
        }
    }

    /** Server side: the bite each player's hook has going, keyed by player uuid. */
    private static final Map<UUID, Bite> BITES = new HashMap<>();

    private FishBite() {
    }

    /** A fish of {@code kind} has taken {@code player}'s hook: the strike window opens. */
    static void begin(ServerPlayer player, int kind, LivingEntity fish, FishingHook hook) {
        Bite bite = new Bite(hook, kind, fish);
        bite.nextAt = player.level().getGameTime() + STRIKE_TICKS;
        BITES.put(player.getUUID(), bite);
        player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.strike").withStyle(ChatFormatting.GOLD));
    }

    /**
     * Run {@code player}'s hook for one tick while it sits in water and no fight is on (called by the hook mixin before it
     * looks for a bite). Returns true while a bite is going or the player spooks the fish, so no new fish comes.
     */
    public static boolean tick(ServerPlayer player, FishingHook hook) {
        ServerLevel level = (ServerLevel) hook.level();
        long now = level.getGameTime();
        Bite bite = BITES.get(player.getUUID());
        if (bite != null && bite.hook != hook) {
            BITES.remove(player.getUUID());
            bite = null;
        }
        if (spooks(player, hook)) {
            if (bite != null && bite.phase != Phase.RESTING) {
                leave(bite, now);
            }
            if (hook.tickCount % SPOOK_TELL_EVERY == 0) {
                player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.spooked").withStyle(ChatFormatting.GRAY));
            }
            return true;
        }
        if (bite == null) {
            return false;
        }
        if (bite.phase != Phase.RESTING && (bite.fish == null || !bite.fish.isAlive())) {
            leave(bite, now);
            return true;
        }
        switch (bite.phase) {
            case TAKEN -> {
                if (now >= bite.nextAt) {
                    player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.missed").withStyle(ChatFormatting.GRAY));
                    leave(bite, now);
                    return true;
                }
                // the fish holds the bobber under
                hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -TAKE_HOLD, 0.0D));
            }
            case RESTING -> {
                if (now >= bite.nextAt) {
                    BITES.remove(player.getUUID());
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The player pulled the rod with a hook out (called by the hook mixin's retrieve). Returns true when that was a strike
     * on a taken hook, so the fight starts and the hook stays out; false lets the hook come in as usual.
     */
    public static boolean strike(ServerPlayer player, FishingHook hook) {
        Bite bite = BITES.get(player.getUUID());
        if (bite == null || bite.hook != hook) {
            return false;
        }
        BITES.remove(player.getUUID());
        if (bite.phase != Phase.TAKEN || bite.fish == null || !bite.fish.isAlive()) {
            return false;
        }
        FishingReal.startFight(player, bite.kind, bite.fish, hook);
        return true;
    }

    /** Forget {@code player}'s bite. */
    public static void clear(ServerPlayer player) {
        BITES.remove(player.getUUID());
    }

    /** Whether the player is standing in the water close enough to the hook to scare the fish off. */
    static boolean spooks(ServerPlayer player, FishingHook hook) {
        return player.isInWater() && player.distanceToSqr(hook) < SPOOK_RADIUS * SPOOK_RADIUS;
    }

    /** The fish swims off, and the hook rests a while before another comes. */
    private static void leave(Bite bite, long now) {
        bite.phase = Phase.RESTING;
        bite.nextAt = now + MISSED_REST_TICKS;
    }
}

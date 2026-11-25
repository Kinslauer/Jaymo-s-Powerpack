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
    /**
     * (Better Fishing's idea, our own code) Standing right over the hook spooks them too, from the bank or a boat (blocks,
     * across); so does splashing about in the water further off (swimming or wading faster than this, blocks a tick).
     */
    static final double STAND_OVER = 2.5, SPLASH_RADIUS = 10.0, SPLASH_SPEED = 0.12;
    /** How often (ticks) a spooked hook shows its warning ripple, to its angler only, and how wide. */
    static final int RIPPLE_EVERY = 20;
    static final double RIPPLE_RADIUS = 0.7;
    /** A strike this soon after the fish takes it (ticks) sets the hook solid; later, it's only lightly hooked. */
    static final int SOLID_STRIKE_TICKS = 20;

    private enum Phase { TAKEN, RESTING }

    private static final class Bite {
        final FishingHook hook;
        final int kind;
        final LivingEntity fish;
        Phase phase = Phase.TAKEN;
        long nextAt;
        long takenAt;

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
        bite.takenAt = player.level().getGameTime();
        bite.nextAt = bite.takenAt + STRIKE_TICKS;
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
            if (hook.tickCount % RIPPLE_EVERY == 0) {
                ripple(player, level, hook);
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
        // (F2) struck fast, the hook's set solid and holds through the runs; struck late, it's light, and slips out on a slack line
        boolean solid = player.level().getGameTime() - bite.takenAt <= SOLID_STRIKE_TICKS;
        FishingReal.startFight(player, bite.kind, bite.fish, hook, solid);
        player.sendOverlayMessage(Component.translatable(solid ? "message.jaymospowers.fishing.hook_solid" : "message.jaymospowers.fishing.hook_light")
                .withStyle(solid ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
        return true;
    }

    /** Forget {@code player}'s bite. */
    public static void clear(ServerPlayer player) {
        BITES.remove(player.getUUID());
    }

    /**
     * Whether the player is scaring the fish off: standing in the water near the hook, standing right over it (on the bank,
     * a jetty, a boat), or splashing about in the water further off.
     */
    static boolean spooks(ServerPlayer player, FishingHook hook) {
        double d = player.distanceToSqr(hook);
        if (player.isInWater() && d < SPOOK_RADIUS * SPOOK_RADIUS) {
            return true;
        }
        double across = Math.hypot(player.getX() - hook.getX(), player.getZ() - hook.getZ());
        if (across < STAND_OVER && !underIce(hook)) {
            // (a hook down an ice hole is meant to be fished from right beside it)
            return true;
        }
        return player.isInWater() && d < SPLASH_RADIUS * SPLASH_RADIUS
                && player.getDeltaMovement().horizontalDistance() > SPLASH_SPEED;
    }

    private static boolean underIce(FishingHook hook) {
        for (int up = 1; up <= 3; up++) {
            if (hook.level().getBlockState(hook.blockPosition().above(up)).is(net.minecraft.tags.BlockTags.ICE)) {
                return true;
            }
        }
        return false;
    }

    /** A soft ring of ripples round the hook, shown to its angler only: they're too close. */
    private static void ripple(ServerPlayer player, ServerLevel level, FishingHook hook) {
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5.0D;
            level.sendParticles(player, net.minecraft.core.particles.ParticleTypes.FISHING, true, false,
                    hook.getX() + Math.cos(a) * RIPPLE_RADIUS, hook.getY(), hook.getZ() + Math.sin(a) * RIPPLE_RADIUS, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** The fish swims off, and the hook rests a while before another comes. */
    private static void leave(Bite bite, long now) {
        bite.phase = Phase.RESTING;
        bite.nextAt = now + MISSED_REST_TICKS;
    }
}

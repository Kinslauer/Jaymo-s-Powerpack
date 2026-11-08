package com.jaymospowers.fishing;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * (Track A, Andy: "all three together") The bite before the fight. Once a fish reaches the hook
 * ({@link FishingReal#fishAtHook}) it nibbles a few times (the bobber twitches), then takes it (the bobber goes under) and
 * the player has {@link #STRIKE_TICKS} to strike with use, which starts {@link FishingReal#startFight}. Striking on a nibble
 * is too early and spooks the fish; missing the window lets it spit the hook. Standing in the water near the hook spooks the
 * fish, so nothing bites (True Fishing's ideas, our own code). A fish that leaves lives on and swims off.
 */
public final class FishBite {
    /** Nibbles before the take, at least and at most; ticks between nibbles. */
    static final int NIBBLES_LEAST = 1, NIBBLES_MOST = 4;
    static final int NIBBLE_GAP_LEAST = 12, NIBBLE_GAP_MOST = 30;
    /** Ticks the player has to strike once the fish takes the hook (2.5 s). */
    static final int STRIKE_TICKS = 50;
    /** Ticks before another fish comes after a missed or spooked one. */
    static final int MISSED_REST_TICKS = 100;
    /** How hard a nibble twitches the bobber down, how hard the take pulls it under, and holds it there each tick. */
    static final double NIBBLE_DIP = 0.12, TAKE_DIP = 0.35, TAKE_HOLD = 0.08;
    /** A player standing in water this close to the hook (blocks) spooks the fish. */
    static final double SPOOK_RADIUS = 6.0;
    /** How often (ticks) a spooked angler is told why nothing bites. */
    static final int SPOOK_TELL_EVERY = 200;

    private enum Phase { NIBBLING, TAKEN, RESTING }

    private static final class Bite {
        final FishingHook hook;
        final int kind;
        final LivingEntity fish;
        Phase phase = Phase.NIBBLING;
        int nibblesLeft;
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

    /** A fish of {@code kind} has reached {@code player}'s hook: it starts nibbling. */
    static void begin(ServerPlayer player, int kind, LivingEntity fish, FishingHook hook) {
        Bite bite = new Bite(hook, kind, fish);
        bite.nibblesLeft = Mth.nextInt(player.getRandom(), NIBBLES_LEAST, NIBBLES_MOST);
        bite.nextAt = player.level().getGameTime() + Mth.nextInt(player.getRandom(), NIBBLE_GAP_LEAST, NIBBLE_GAP_MOST);
        BITES.put(player.getUUID(), bite);
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
            case NIBBLING -> {
                if (now < bite.nextAt) {
                    return true;
                }
                if (bite.nibblesLeft > 0) {
                    bite.nibblesLeft--;
                    bite.nextAt = now + Mth.nextInt(level.getRandom(), NIBBLE_GAP_LEAST, NIBBLE_GAP_MOST);
                    dip(level, hook, NIBBLE_DIP, 0.12F, 4);
                    return true;
                }
                bite.phase = Phase.TAKEN;
                bite.nextAt = now + STRIKE_TICKS;
                dip(level, hook, TAKE_DIP, 0.35F, 14);
                player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.strike").withStyle(ChatFormatting.GOLD));
            }
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
     * on a taken hook, so the fight starts and the hook stays out; false lets the hook come in as usual (a pull on a nibble
     * is too early and spooks the fish).
     */
    public static boolean strike(ServerPlayer player, FishingHook hook) {
        Bite bite = BITES.get(player.getUUID());
        if (bite == null || bite.hook != hook) {
            return false;
        }
        BITES.remove(player.getUUID());
        if (bite.phase == Phase.NIBBLING) {
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.too_early").withStyle(ChatFormatting.GRAY));
            letGo(bite.fish);
            return false;
        }
        if (bite.phase != Phase.TAKEN || bite.fish == null || !bite.fish.isAlive()) {
            return false;
        }
        FishingReal.startFight(player, bite.kind, bite.fish, hook);
        return true;
    }

    /** Forget {@code player}'s bite. */
    public static void clear(ServerPlayer player) {
        Bite bite = BITES.remove(player.getUUID());
        if (bite != null && bite.phase != Phase.RESTING) {
            letGo(bite.fish);
        }
    }

    /** Whether the player is standing in the water close enough to the hook to scare the fish off. */
    static boolean spooks(ServerPlayer player, FishingHook hook) {
        return player.isInWater() && player.distanceToSqr(hook) < SPOOK_RADIUS * SPOOK_RADIUS;
    }

    /** The fish swims off, and the hook rests a while before another comes. */
    private static void leave(Bite bite, long now) {
        letGo(bite.fish);
        bite.phase = Phase.RESTING;
        bite.nextAt = now + MISSED_REST_TICKS;
    }

    /** A fish that wasn't hooked lives on; only a shark held on the line without its AI can't swim off, so it goes. */
    private static void letGo(LivingEntity fish) {
        if (fish instanceof Mob mob && mob.isNoAi() && fish.isAlive()) {
            fish.discard();
        }
    }

    /** Twitch the bobber down with a small splash. */
    private static void dip(ServerLevel level, FishingHook hook, double down, float volume, int particles) {
        hook.setDeltaMovement(hook.getDeltaMovement().add(0.0D, -down, 0.0D));
        double y = hook.getY() + 0.5D;
        level.sendParticles(ParticleTypes.BUBBLE, hook.getX(), y, hook.getZ(), particles, 0.15D, 0.0D, 0.15D, 0.2D);
        level.sendParticles(ParticleTypes.FISHING, hook.getX(), y, hook.getZ(), particles, 0.15D, 0.0D, 0.15D, 0.2D);
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL,
                volume, 1.0F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.4F);
    }
}

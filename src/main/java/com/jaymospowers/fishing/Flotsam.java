package com.jaymospowers.fishing;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Drifting flotsam (Andy's Let's Do pick): now and then a ripple spot sits on a river or the sea, drifts a few blocks over
 * its five minutes and goes. A hook that lands in one pulls an odd catch instead of waiting for a fish: a purse of coin, a
 * bottle, old gear. A spot is worked out from the world's own clock, so nothing is saved; one that has given its catch
 * stays quiet until the world forgets it. The remains' token of the gone-before (a later patch) is not caught yet.
 */
public final class Flotsam {
    /** Spots are worked out per square of this many blocks, per window of ticks; one square in this many has a spot. */
    static final int CELL = 8;
    static final long WINDOW = 6000L;
    static final int SPOT_ODDS = 4;
    /** How far a spot drifts across its window (blocks), and how near a hook has to land to take it. */
    static final double DRIFT = 5.0, CATCH_RADIUS = 2.0;
    /** Ripples are drawn this often (ticks) for anyone within {@link #SEEN_CELLS} squares of one. */
    static final int RIPPLE_EVERY = 20, SEEN_CELLS = 3;
    /** Coins in a purse, in emeralds' worth (this many, plus a random extra below {@link #PURSE_EXTRA}). */
    static final int PURSE_LEAST = 1, PURSE_EXTRA = 3;
    /** Spots already taken, by square and window, forgotten when the set grows past this. */
    static final int TAKEN_MAX = 4096;
    private static final Set<Long> TAKEN = new HashSet<>();

    private Flotsam() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % RIPPLE_EVERY != 0) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isSpectator() && player.level() instanceof ServerLevel level) {
                    ripple(level, player);
                }
            }
        });
    }

    private static long mix(long a, long b, long c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }

    /** The spot of square ({@code cx}, {@code cz}) at game time {@code now}, or null if the square has none then or it isn't over open water. */
    private static Vec3 spot(ServerLevel level, long cx, long cz, long now) {
        long h = mix(cx, cz, now / WINDOW);
        if (Math.floorMod(h, (long)SPOT_ODDS) != 0) {
            return null;
        }
        double frac = (now % WINDOW) / (double)WINDOW;
        double angle = ((h >>> 8) & 0xFF) / 256.0 * Math.PI * 2.0;
        double x = cx * CELL + ((h >>> 16) & 7) + 0.5 + Math.cos(angle) * DRIFT * frac;
        double z = cz * CELL + ((h >>> 24) & 7) + 0.5 + Math.sin(angle) * DRIFT * frac;
        int bx = (int)Math.floor(x), bz = (int)Math.floor(z);
        if (!level.hasChunkAt(new BlockPos(bx, level.getMinY(), bz))) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
        BlockPos water = new BlockPos(bx, y, bz);
        if (!level.getFluidState(water).is(FluidTags.WATER) || !level.getFluidState(water.below()).is(FluidTags.WATER)) {
            return null;
        }
        return new Vec3(x, y + 0.9, z);
    }

    private static long key(long cx, long cz, long now) {
        return mix(cx, cz, now / WINDOW) ^ cz;
    }

    private static void ripple(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        long px = Math.floorDiv((long)player.getX(), (long)CELL), pz = Math.floorDiv((long)player.getZ(), (long)CELL);
        for (long cx = px - SEEN_CELLS; cx <= px + SEEN_CELLS; cx++) {
            for (long cz = pz - SEEN_CELLS; cz <= pz + SEEN_CELLS; cz++) {
                if (TAKEN.contains(key(cx, cz, now))) {
                    continue;
                }
                Vec3 at = spot(level, cx, cz, now);
                if (at != null) {
                    level.sendParticles(ParticleTypes.FISHING, at.x, at.y, at.z, 4, 0.35, 0.0, 0.35, 0.02);
                }
            }
        }
    }

    /** A hook is pulled in with nothing on it: if it lay in a ripple spot, the spot's odd catch comes to {@code player}. */
    public static void pull(ServerPlayer player, FishingHook hook) {
        if (!(hook.level() instanceof ServerLevel level) || !hook.isInWater()) {
            return;
        }
        long now = level.getGameTime();
        long hx = Math.floorDiv((long)hook.getX(), (long)CELL), hz = Math.floorDiv((long)hook.getZ(), (long)CELL);
        for (long cx = hx - 1; cx <= hx + 1; cx++) {
            for (long cz = hz - 1; cz <= hz + 1; cz++) {
                Vec3 at = spot(level, cx, cz, now);
                if (at == null || TAKEN.contains(key(cx, cz, now))
                    || Math.hypot(at.x - hook.getX(), at.z - hook.getZ()) > CATCH_RADIUS) {
                    continue;
                }
                if (TAKEN.size() > TAKEN_MAX) {
                    TAKEN.clear();
                }
                TAKEN.add(key(cx, cz, now));
                give(level, player, hook, level.getRandom());
                return;
            }
        }
    }

    private static void give(ServerLevel level, ServerPlayer player, FishingHook hook, RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 25) {
            com.jaymospowers.core.KingdomsMoney.pay(player, com.jaymospowers.core.KingdomsMoney.worth(PURSE_LEAST + random.nextInt(PURSE_EXTRA)));
            return;
        }
        ItemStack stack;
        if (roll < 50) {
            stack = new ItemStack(Items.GLASS_BOTTLE);
        } else if (roll < 85) {
            // old gear, mostly worn out
            Item[] gear = {Items.IRON_SWORD, Items.LEATHER_BOOTS, Items.IRON_HELMET, Items.SHEARS, Items.FISHING_ROD, Items.CHAINMAIL_BOOTS};
            stack = new ItemStack(gear[random.nextInt(gear.length)]);
            stack.setDamageValue(stack.getMaxDamage() - 1 - random.nextInt(Math.max(1, stack.getMaxDamage() / 5)));
        } else if (roll < 94) {
            Item[] bits = {Items.BOWL, Items.STICK, Items.STRING, Items.BONE, Items.INK_SAC, Items.LILY_PAD};
            stack = new ItemStack(bits[random.nextInt(bits.length)]);
        } else {
            Item[] finds = {Items.NAME_TAG, Items.NAUTILUS_SHELL, Items.SADDLE};
            stack = new ItemStack(finds[random.nextInt(finds.length)]);
        }
        ItemEntity drop = new ItemEntity(level, hook.getX(), hook.getY(), hook.getZ(), stack);
        double dx = player.getX() - hook.getX(), dy = player.getY() - hook.getY(), dz = player.getZ() - hook.getZ();
        drop.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
        level.addFreshEntity(drop);
    }
}

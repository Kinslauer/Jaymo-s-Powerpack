package com.jaymospowers.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * (Andy, 2026-11-26: "the fish who bit the line was not visible actually fighting. that is very vital") The hooked fish
 * fights where you can see it. It's held just under the surface (its back at the top, breaking it while it thrashes), at
 * the end of the line: the hook rides in its mouth ({@code FishingHookMixin}), so the line runs to the fish itself, not to
 * a float it has left behind. Its runs swing it out wide to the side and the line brings it back; it faces away from you
 * and pulls; as line comes in it comes in toward the bank, and a run takes it back out. Its runs leave a wake, its thrashes
 * splash, and some runs are dives: it sounds for the bottom, bubbles rising, and the line brings it back up after. {@link FishingReal}'s fight tick calls {@link #tick} in place of the old "keep it within three blocks of the
 * float". Server side; the fish and the hook are real entities, so everyone sees it.
 */
final class FishOnLine {
    /** The fish is held this far from the angler at the fight's start, against how far the hook was; never nearer than this (blocks). */
    static final double NEAREST = 1.6D;
    /**
     * How far out it is for the line's share in: {@code (REACH - progress) / (REACH - start)} of the start distance, so it
     * sits where it bit at the start, comes to {@code (REACH - 1) / (REACH - start)} of it when landed, and goes out past it as
     * a run takes line.
     */
    static final float REACH = 1.3F;
    /** How fast the fish's place on the line follows the line (blocks a tick). */
    static final double FOLLOW = 0.12D;
    /** How far a run swings the fish out to the side, and how fast it swings back after (share kept a tick). */
    static final double SWING_MOST = 3.5D, SWING_BACK = 0.95D;
    /** How hard the fish is steered to its place (share of the gap a tick), and the fastest it's moved (blocks a tick). */
    static final double STEER = 0.18D, STEER_MOST = 0.35D;
    /** It holds this far under the surface (blocks, from its top); thrashing, its middle is at the surface. */
    static final double UNDER = 0.05D;
    /** How hard it's held at its depth (share of the gap a tick); weaker while it thrashes. */
    static final double HOLD_DEPTH = 0.25D, HOLD_DEPTH_THRASH = 0.08D;
    /**
     * (Andy, 2026-11-26: "the fish should still be able to dive deep while hooked") A diving fish heads for the bottom, as
     * deep as this many blocks under the surface, at most this fast (blocks a tick); it swings out this share as wide as a
     * run, and the line draws it back up after.
     */
    static final int DIVE_MOST = 12;
    static final double DIVE_SPEED = 0.12D, DIVE_SWING = 0.4D;
    /** Ticks between wake and splash puffs. */
    static final int PUFF_EVERY = 2;

    private static final class Line {
        double cx, cz;
        double startDist;
        double swing;
        int fluidY;
        TagKey<Fluid> fluid;
    }

    private static final Map<UUID, Line> LINES = new HashMap<>();

    private FishOnLine() {
    }

    /** A fight starts with {@code fish} on {@code hook}: hook it in its mouth, and remember where it bit. */
    static void start(ServerPlayer player, LivingEntity fish, FishingHook hook) {
        Line line = new Line();
        line.cx = fish.getX();
        line.cz = fish.getZ();
        line.startDist = Math.max(NEAREST, Math.hypot(fish.getX() - player.getX(), fish.getZ() - player.getZ()));
        BlockPos at = hook.blockPosition();
        line.fluid = player.level().getFluidState(at).is(FluidTags.LAVA) ? FluidTags.LAVA : FluidTags.WATER;
        line.fluidY = top(player.level(), at, line.fluid).getY();
        LINES.put(player.getUUID(), line);
        ((com.jaymospowers.fishing.mixin.FishingHookAccess) hook).jaymospowers$hookIn(fish);
    }

    /** The fight's over, one way or another. */
    static void end(ServerPlayer player) {
        LINES.remove(player.getUUID());
    }

    /**
     * One tick of the fight: where on the line the fish is ({@code progress}, the share of line in), steered there, swung
     * out by a run ({@code run}), held under the surface, thrashing or leaping as {@link FishRun} has it.
     */
    static void tick(ServerPlayer player, LivingEntity fish, FishFight state, float progress, int run, boolean thrash, long now) {
        Line line = LINES.get(player.getUUID());
        if (line == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        // where on the line it is: in from where it bit as line comes in, out past it as a run takes line
        double share = (REACH - progress) / (REACH - FishingReal.START_PROGRESS);
        double want = Math.max(NEAREST, line.startDist * share);
        double ox = line.cx - player.getX(), oz = line.cz - player.getZ();
        double len = Math.hypot(ox, oz);
        if (len > 1.0E-4D) {
            double step = Mth.clamp(want - len, -FOLLOW, FOLLOW);
            double nx = line.cx + ox / len * step, nz = line.cz + oz / len * step;
            // only through water (or lava): drawn in to the bank it stops at the edge, never dragged up onto land
            if (fluidAt(level, nx, line.fluidY, nz, line.fluid)) {
                line.cx = nx;
                line.cz = nz;
            }
        }
        // a run swings it out to the side, and the line draws it back between runs
        boolean diving = run != 0 && FishRun.diving(player);
        line.swing = run != 0 ? Mth.clamp(line.swing + FishRun.RUN_SPEED * state.strength() * run * 2.0D * (diving ? DIVE_SWING : 1.0D),
                -SWING_MOST, SWING_MOST) : line.swing * SWING_BACK;
        double sideX = len > 1.0E-4D ? -oz / len : 0.0D, sideZ = len > 1.0E-4D ? ox / len : 0.0D;
        double tx = line.cx + sideX * line.swing, tz = line.cz + sideZ * line.swing;
        if (!fluidAt(level, tx, line.fluidY, tz, line.fluid)) {
            tx = line.cx;
            tz = line.cz;
            line.swing *= 0.5D;
        }
        boolean leaping = FishRun.jumping(player, now) && !fish.isInLiquid();
        double surface = surface(level, fish, line);
        double ty = thrash ? surface - fish.getBbHeight() * 0.5D : surface - fish.getBbHeight() - UNDER;
        if (diving) {
            // sounding: down for the bottom, no faster than it swims
            ty = Math.max(floor(level, fish, line), fish.getY() - DIVE_SPEED);
        }
        boolean held = fish instanceof Mob mob && mob.isNoAi();
        if (held) {
            // a big shark held without its AI is moved by hand
            double dx = Mth.clamp((tx - fish.getX()) * STEER, -STEER_MOST, STEER_MOST);
            double dz = Mth.clamp((tz - fish.getZ()) * STEER, -STEER_MOST, STEER_MOST);
            double dy = Mth.clamp((ty - fish.getY()) * HOLD_DEPTH, -STEER_MOST, STEER_MOST);
            fish.setPos(fish.getX() + dx, fish.getY() + dy, fish.getZ() + dz);
        } else {
            Vec3 v = fish.getDeltaMovement();
            double vx = Mth.lerp(STEER * 2.0D, v.x, Mth.clamp((tx - fish.getX()) * STEER, -STEER_MOST, STEER_MOST));
            double vz = Mth.lerp(STEER * 2.0D, v.z, Mth.clamp((tz - fish.getZ()) * STEER, -STEER_MOST, STEER_MOST));
            // in the air on a leap it falls as it will; in the water it's held at the top
            double vy = leaping ? v.y : Mth.lerp(thrash ? HOLD_DEPTH_THRASH : HOLD_DEPTH, v.y, Mth.clamp(ty - fish.getY(), -STEER_MOST, STEER_MOST));
            fish.setDeltaMovement(vx, vy, vz);
            if (fish instanceof Mob mob) {
                // the line steers it now, not its wandering
                mob.getNavigation().stop();
            }
        }
        // it faces away from you and pulls: the way it swims while it runs, else straight down the line
        double fx = fish.getX() - player.getX(), fz = fish.getZ() - player.getZ();
        if (run != 0) {
            fx += sideX * run * 2.0D;
            fz += sideZ * run * 2.0D;
        }
        if (fx * fx + fz * fz > 1.0E-4D) {
            float yaw = (float) (Mth.atan2(fz, fx) * Mth.RAD_TO_DEG) - 90.0F;
            float turned = Mth.approachDegrees(fish.getYRot(), yaw, 20.0F);
            fish.setYRot(turned);
            fish.setYBodyRot(turned);
            fish.setYHeadRot(turned);
        }
        puff(level, fish, line, run, thrash, now);
    }

    /** A run leaves a wake behind it, a thrash splashes, and a calm fish just stirs the top now and then. */
    private static void puff(ServerLevel level, LivingEntity fish, Line line, int run, boolean thrash, long now) {
        if (now % PUFF_EVERY != 0L) {
            return;
        }
        boolean lava = line.fluid == FluidTags.LAVA;
        double y = surface(level, fish, line);
        double w = fish.getBbWidth() * 0.5D;
        if (thrash) {
            send(level, lava ? ParticleTypes.LAVA : ParticleTypes.SPLASH, fish.getX(), y, fish.getZ(), 10, w + 0.2D, 0.1D, 0.2D);
            send(level, lava ? ParticleTypes.SMOKE : ParticleTypes.BUBBLE, fish.getX(), y - 0.2D, fish.getZ(), 4, w, 0.1D, 0.05D);
        } else if (run != 0 && fish.getY() + fish.getBbHeight() < y - 1.0D) {
            // deep on a dive: bubbles rising from it
            send(level, lava ? ParticleTypes.SMOKE : ParticleTypes.BUBBLE_COLUMN_UP, fish.getX(), fish.getY() + fish.getBbHeight(), fish.getZ(), 2, w, 0.1D, 0.05D);
        } else if (run != 0) {
            send(level, lava ? ParticleTypes.SMOKE : ParticleTypes.FISHING, fish.getX(), y, fish.getZ(), 3, w, 0.0D, 0.02D);
            send(level, lava ? ParticleTypes.LAVA : ParticleTypes.SPLASH, fish.getX(), y, fish.getZ(), 2, w, 0.05D, 0.1D);
        } else if (now % (PUFF_EVERY * 5) == 0L) {
            send(level, lava ? ParticleTypes.SMOKE : ParticleTypes.BUBBLE, fish.getX(), y - 0.15D, fish.getZ(), 2, w * 0.5D, 0.05D, 0.02D);
        }
    }

    private static void send(ServerLevel level, ParticleOptions type, double x, double y, double z, int count, double spread, double up, double speed) {
        level.sendParticles(type, x, y, z, count, spread, up, spread, speed);
    }

    /** The top of the water (or lava) over the fish, or where it bit if it's out of it (on a leap). */
    private static double surface(ServerLevel level, LivingEntity fish, Line line) {
        BlockPos at = BlockPos.containing(fish.getX(), Math.min(fish.getY() + fish.getBbHeight() * 0.5D, line.fluidY + 0.5D), fish.getZ());
        if (!level.getFluidState(at).is(line.fluid)) {
            at = BlockPos.containing(fish.getX(), line.fluidY, fish.getZ());
        }
        if (!level.getFluidState(at).is(line.fluid)) {
            return line.fluidY + 0.9D;
        }
        BlockPos top = top(level, at, line.fluid);
        FluidState state = level.getFluidState(top);
        return top.getY() + state.getHeight(level, top);
    }

    /** (2026-11-26) The lowest a diving fish goes: just over the bottom under it, at most {@link #DIVE_MOST} under the surface. */
    private static double floor(ServerLevel level, LivingEntity fish, Line line) {
        BlockPos at = BlockPos.containing(fish.getX(), Math.min(fish.getY(), line.fluidY), fish.getZ());
        if (!level.getFluidState(at).is(line.fluid)) {
            return fish.getY();
        }
        for (int i = 0; i < DIVE_MOST && level.getFluidState(at.below()).is(line.fluid) && line.fluidY - at.getY() < DIVE_MOST; i++) {
            at = at.below();
        }
        return at.getY() + 0.1D;
    }

    /** The highest block of the same fluid straight up from {@code at} (as far up as a dive goes down, and a little more). */
    private static BlockPos top(net.minecraft.world.level.Level level, BlockPos at, TagKey<Fluid> fluid) {
        BlockPos top = at;
        for (int i = 0; i < DIVE_MOST + 4 && level.getFluidState(top.above()).is(fluid); i++) {
            top = top.above();
        }
        return top;
    }

    /** Whether there's water (or lava) at {@code (x, z)} at the height it bit at. */
    private static boolean fluidAt(ServerLevel level, double x, int y, double z, TagKey<Fluid> fluid) {
        return level.getFluidState(BlockPos.containing(x, y, z)).is(fluid);
    }
}

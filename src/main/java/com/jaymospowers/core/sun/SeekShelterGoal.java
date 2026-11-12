package com.jaymospowers.core.sun;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A sun-burning undead's shelter search: when the Sun's rays reach any part of it, it looks for real shelter - a cave,
 * anywhere underground, or under a solid roof - within {@link #REACH} blocks and walks there. The same ray test as
 * {@link SunExposure#bodyExposed} decides whether a spot is sheltered, so leaves (and glass, torches, fences) do not
 * count as cover: a mob that thinks it is safe really is safe. When nothing is in reach it keeps going: it walks
 * {@link #ROAM} blocks away from the Sun (the shaded side of hills and walls) and searches again from there, until it
 * finds shelter or the day ends. It never digs.
 */
public class SeekShelterGoal extends Goal {
    // ===== the knobs ================================================================================================
    /** How far the search looks (blocks; vanilla's FleeSunGoal looks 10). */
    static final int REACH = 24;
    /** At most this many sun rays per search, so a bare plain doesn't cost thousands of rays. */
    static final int MAX_RAYS = 240;
    /** Ticks between checks for whether the sun reaches the mob (the check itself casts up to 7 rays). */
    static final int CHECK_EVERY = 20;
    /** When nothing is in reach: how far to walk away from the Sun before searching again (blocks, and up/down). */
    static final int ROAM = 16, ROAM_HEIGHT = 7;
    /** Walking speed (times the mob's own). */
    static final double SPEED = 1.0;
    // ================================================================================================================

    private final PathfinderMob mob;
    private final Level level;
    /** Whether this mob keeps to the shade with vanilla's RestrictSunGoal (skeletons, strays), so avoidSun is put back. */
    private final boolean restrictsSun;
    private int cooldown;
    private @Nullable Vec3 target;

    public SeekShelterGoal(PathfinderMob mob, boolean restrictsSun) {
        this.mob = mob;
        this.level = mob.level();
        this.restrictsSun = restrictsSun;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.cooldown = CHECK_EVERY;
        if (this.mob.getTarget() != null || !this.level.isBrightOutside()
            || !this.mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || this.mob.isInWaterOrRain()) {
            return false;
        }
        if (!SunExposure.bodyExposed(this.level, this.mob)) {
            return false; // already under real shelter
        }
        BlockPos shelter = this.findShelter();
        if (shelter != null) {
            this.target = Vec3.atBottomCenterOf(shelter);
        } else {
            // Nothing in reach: head away from the Sun and search again on arrival (Andy: keep searching till it finds shelter).
            Vec3 sun = SunExposure.sunDirection(this.level, this.mob.position());
            Vec3 toward = this.mob.position().add(sun.x * 8.0, 0.0, sun.z * 8.0);
            this.target = DefaultRandomPos.getPosAway(this.mob, ROAM, ROAM_HEIGHT, toward);
        }
        return this.target != null;
    }

    @Override
    public void start() {
        if (this.mob.getNavigation() instanceof GroundPathNavigation ground) {
            ground.setAvoidSun(false); // RestrictSunGoal would cut the path at the edge of leaf shade
        }
        if (this.target != null) {
            this.mob.getNavigation().moveTo(this.target.x, this.target.y, this.target.z, SPEED);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.mob.getTarget() == null && !this.mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        this.target = null;
        this.cooldown = 0; // arrived (or gave up): check again next tick, and search from here if still in the sun
        if (this.mob.getNavigation() instanceof GroundPathNavigation ground) {
            ground.setAvoidSun(this.restrictsSun && this.level.isBrightOutside());
        }
    }

    /** The nearest real shelter within {@link #REACH} blocks (the whole body out of the sun), or null. */
    private @Nullable BlockPos findShelter() {
        BlockPos pos = this.mob.blockPosition();
        double height = this.mob.getBbHeight();
        int rays = 0;
        for (int r = 1; r <= REACH && rays < MAX_RAYS; r++) {
            BlockPos best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int dx = -r; dx <= r && rays < MAX_RAYS; dx++) {
                for (int dz = -r; dz <= r && rays < MAX_RAYS; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2 && rays < MAX_RAYS; dy++) {
                        BlockPos cand = pos.offset(dx, dy, dz);
                        if (!this.level.isLoaded(cand)
                            || !this.level.getBlockState(cand.below()).isFaceSturdy(this.level, cand.below(), Direction.UP)
                            || this.level.getBlockState(cand).isSolidRender() || this.level.getBlockState(cand.above()).isSolidRender()) {
                            continue; // nothing to stand on, or no room to stand
                        }
                        if (this.level.canSeeSky(cand.above())) {
                            continue; // open sky straight above: not shelter (cheap check before the rays)
                        }
                        Vec3 feet = Vec3.atBottomCenterOf(cand).add(0.0, 0.05, 0.0);
                        rays += 2;
                        if (SunExposure.exposedToSun(this.level, feet) || SunExposure.exposedToSun(this.level, feet.add(0.0, height - 0.1, 0.0))) {
                            continue; // the sun reaches the feet or the head there (leaves and glass let it through)
                        }
                        double score = -pos.distSqr(cand) + (pos.getY() - cand.getY()) * 2.0; // closer, then lower (caves)
                        if (best == null || score > bestScore) {
                            best = cand;
                            bestScore = score;
                        }
                    }
                }
            }
            if (best != null) {
                return best; // the nearest ring that holds any shelter wins
            }
        }
        return null;
    }
}

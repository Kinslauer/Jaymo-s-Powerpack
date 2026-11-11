package com.jaymospowers.core.sun;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A sun-burning undead's shelter search: when the Sun's rays reach it, it looks for real shelter - a cave, anywhere
 * underground, or under a solid roof - within a wide radius and paths toward it. The same ray test as
 * {@link SunExposure#exposedToSun} decides whether a spot is sheltered, so leaves (and glass, torches, fences) do not
 * count as cover: a mob that thinks it is safe really is safe. It keeps looking as the Sun moves, and stays put when
 * no shelter is reachable (no digging - Andy said to ask before that).
 */
public class SeekShelterGoal extends Goal {
    private final PathfinderMob mob;
    private final Level level;
    private int cooldown;
    private BlockPos shelter;

    public SeekShelterGoal(PathfinderMob mob) {
        this.mob = mob;
        this.level = mob.level();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTarget() != null) {
            return false;
        }
        if (!this.level.isBrightOutside()) {
            return false;
        }
        if (!this.mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return false;
        }
        if (this.mob.isInWaterOrRain()) {
            return false;
        }
        if (!SunExposure.exposedToSun(this.level, this.mob.getEyePosition())) {
            return false; // already under real shelter
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.cooldown = 40; // once in a while, not every tick
        this.shelter = this.findShelter();
        return this.shelter != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        if (this.shelter != null) {
            this.mob.getNavigation().moveTo(this.shelter.getX() + 0.5, this.shelter.getY() + 1.0, this.shelter.getZ() + 0.5, 1.0D);
        }
    }

    @Override
    public void stop() {
        this.shelter = null;
    }

    /** The nearest real shelter within {@value #REACH} blocks, or null. */
    private static final int REACH = 24; // wider than vanilla's 10

    private @Nullable BlockPos findShelter() {
        BlockPos pos = this.mob.blockPosition();
        for (int r = 1; r <= REACH; r++) {
            BlockPos best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            int checked = 0;
            for (int dx = -r; dx <= r && checked < 300; dx++) {
                for (int dz = -r; dz <= r && checked < 300; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    checked++;
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos cand = pos.offset(dx, dy, dz);
                        if (!this.level.isLoaded(cand)) {
                            continue;
                        }
                        if (!this.level.getBlockState(cand.below()).isFaceSturdy(this.level, cand.below(), Direction.UP)) {
                            continue; // nothing solid to stand on
                        }
                        if (this.level.canSeeSky(cand)) {
                            continue; // open sky: not shelter (cheap prefilter before the ray)
                        }
                        Vec3 at = Vec3.atBottomCenterOf(cand).add(0.0, 1.62, 0.0);
                        if (SunExposure.exposedToSun(this.level, at)) {
                            continue; // leaves, glass and torches let the rays through: not real shelter
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

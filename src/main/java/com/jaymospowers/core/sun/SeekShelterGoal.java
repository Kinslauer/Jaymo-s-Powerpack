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
import java.lang.reflect.Field;

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
    private boolean avoidSunDisabled;
    private boolean originalAvoidSun;

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
        // Check multiple body points (feet, chest, head) and hitbox corners - burn if any is exposed
        Vec3 eye = this.mob.getEyePosition();
        Vec3 feet = new Vec3(this.mob.getX(), this.mob.getY(), this.mob.getZ());
        Vec3 head = new Vec3(this.mob.getX(), this.mob.getY() + this.mob.getBbHeight(), this.mob.getZ());
        Vec3 chest = new Vec3(this.mob.getX(), this.mob.getY() + this.mob.getBbHeight() / 2.0, this.mob.getZ());
        boolean eyeExposed = SunExposure.exposedToSun(this.level, eye);
        boolean feetExposed = SunExposure.exposedToSun(this.level, feet);
        boolean headExposed = SunExposure.exposedToSun(this.level, head);
        boolean chestExposed = SunExposure.exposedToSun(this.level, chest);
        if (!eyeExposed && !feetExposed && !headExposed && !chestExposed) {
            return false; // already under real shelter
        }
        // Also check hitbox corners at feet and head level
        // Feet corners (bottom of bounding box)
        Vec3 feetCorner1 = new Vec3(this.mob.getBoundingBox().minX, this.mob.getY(), this.mob.getBoundingBox().minZ);
        Vec3 feetCorner2 = new Vec3(this.mob.getBoundingBox().maxX, this.mob.getY(), this.mob.getBoundingBox().minZ);
        Vec3 feetCorner3 = new Vec3(this.mob.getBoundingBox().minX, this.mob.getY(), this.mob.getBoundingBox().maxZ);
        Vec3 feetCorner4 = new Vec3(this.mob.getBoundingBox().maxX, this.mob.getY(), this.mob.getBoundingBox().maxZ);
        // Head corners (top of bounding box)
        Vec3 headCorner1 = new Vec3(this.mob.getBoundingBox().minX, this.mob.getY() + this.mob.getBbHeight(), this.mob.getBoundingBox().minZ);
        Vec3 headCorner2 = new Vec3(this.mob.getBoundingBox().maxX, this.mob.getY() + this.mob.getBbHeight(), this.mob.getBoundingBox().minZ);
        Vec3 headCorner3 = new Vec3(this.mob.getBoundingBox().minX, this.mob.getY() + this.mob.getBbHeight(), this.mob.getBoundingBox().maxZ);
        Vec3 headCorner4 = new Vec3(this.mob.getBoundingBox().maxX, this.mob.getY() + this.mob.getBbHeight(), this.mob.getBoundingBox().maxZ);
        boolean feetCornerExposed = SunExposure.exposedToSun(this.level, feetCorner1)
            || SunExposure.exposedToSun(this.level, feetCorner2)
            || SunExposure.exposedToSun(this.level, feetCorner3)
            || SunExposure.exposedToSun(this.level, feetCorner4);
        boolean headCornerExposed = SunExposure.exposedToSun(this.level, headCorner1)
            || SunExposure.exposedToSun(this.level, headCorner2)
            || SunExposure.exposedToSun(this.level, headCorner3)
            || SunExposure.exposedToSun(this.level, headCorner4);
        if (eyeExposed || feetExposed || headExposed || chestExposed || feetCornerExposed || headCornerExposed) {
            // at least one body point is exposed to sun
        } else {
            return false; // already under real shelter
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.cooldown = 200; // search once every 200 ticks (about 10 seconds), not every time burning
        this.shelter = this.findShelter();
        return this.shelter != null;
    }

    @Override
    public void start() {
        super.start();
        // Disable avoidSun while this goal is active, allowing mobs to path through
        // leaf shade to reach real shelter (fixes skeletons getting stuck under trees)
        if (!this.avoidSunDisabled) {
            try {
                net.minecraft.world.entity.ai.navigation.GroundPathNavigation nav = 
                    (net.minecraft.world.entity.ai.navigation.GroundPathNavigation) this.mob.getNavigation();
                if (nav instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation) {
                    Field avoidSunField = nav.getClass().getDeclaredField("avoidSun");
                    avoidSunField.setAccessible(true);
                    this.originalAvoidSun = avoidSunField.getBoolean(nav);
                    avoidSunField.setBoolean(nav, false);
                }
            } catch (Exception e) {
                // If reflection fails, just continue without toggling
            }
            this.avoidSunDisabled = true;
        }
    }

    @Override
    public void stop() {
        this.shelter = null;
        if (this.avoidSunDisabled) {
            try {
                net.minecraft.world.entity.ai.navigation.GroundPathNavigation nav = 
                    (net.minecraft.world.entity.ai.navigation.GroundPathNavigation) this.mob.getNavigation();
                if (nav instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation) {
                    Field avoidSunField = nav.getClass().getDeclaredField("avoidSun");
                    avoidSunField.setAccessible(true);
                    avoidSunField.setBoolean(nav, this.originalAvoidSun);
                }
            } catch (Exception e) {
                // If reflection fails, just reset the flag
                this.avoidSunDisabled = false;
            }
            this.avoidSunDisabled = false;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone();
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
                        // Also check feet and head positions at the candidate
                        Vec3 feetAt = Vec3.atBottomCenterOf(cand).add(0.0, 0.0, 0.0);
                        Vec3 headAt = Vec3.atBottomCenterOf(cand).add(0.0, mob.getBbHeight(), 0.0);
                        if (SunExposure.exposedToSun(this.level, feetAt) || SunExposure.exposedToSun(this.level, headAt)) {
                            continue; // rays reach feet or head: not real shelter
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

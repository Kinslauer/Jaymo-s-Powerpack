package com.jaymospowers.core.sun;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A sun-burning undead's shelter search: when the Sun's rays reach any part of it, it looks for the nearest spot the
 * Sun can't reach - inside a house, under an overhang, a cave, or the shadow a wall, house or cliff throws on open
 * ground - and walks there. Roofed spots are searched out to {@link #REACH} blocks; open-sky shadows (which cost more
 * rays to find) out to {@link #SHADE_REACH}. The nearest ring that holds a spot it can actually walk to wins, so a
 * house next door beats a cave down the hill. The same ray test as {@link SunExposure#bodyExposed} decides whether a
 * spot is sheltered, so leaves (and glass, torches, fences) do not count as cover: a mob that thinks it is safe really
 * is safe. When nothing is in reach it keeps going: it walks {@link #ROAM} blocks away from the Sun and searches again
 * from there, until it finds shelter or the day ends. It never digs.
 * <p>
 * With {@code fleeMidFight} (zombies), the Sun beats the fight: a zombie that has a target still runs for shade when
 * shade is in reach, keeps its target, and goes straight back on the attack once it is out of the Sun. With no shade in
 * reach it stays in the fight and burns.
 */
public class SeekShelterGoal extends Goal {
    // ===== the knobs ================================================================================================
    /** How far the search looks for roofed shelter: houses, overhangs, caves (blocks; vanilla's FleeSunGoal looks 10). */
    static final int REACH = 24;
    /** How far it looks for open-sky shadow, the shade a wall, house or cliff throws (blocks). */
    static final int SHADE_REACH = 12;
    /** At most this many sun rays per search for roofed spots, and for open-sky shadows, so a bare plain stays cheap. */
    static final int MAX_RAYS = 240, SHADE_RAYS = 360;
    /** At most this many paths tried per search: a spot it can't walk to (a house with its door shut) is skipped. */
    static final int MAX_PATHS = 6, PATHS_PER_RING = 2;
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
    /** Whether a mob with a target still runs for shade (zombies); otherwise a target stops the search (vanilla's way). */
    private final boolean fleeMidFight;
    private int cooldown;
    private @Nullable Vec3 target;
    private @Nullable Path path;

    public SeekShelterGoal(PathfinderMob mob, boolean restrictsSun) {
        this(mob, restrictsSun, false);
    }

    public SeekShelterGoal(PathfinderMob mob, boolean restrictsSun, boolean fleeMidFight) {
        this.mob = mob;
        this.level = mob.level();
        this.restrictsSun = restrictsSun;
        this.fleeMidFight = fleeMidFight;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.cooldown = CHECK_EVERY;
        boolean fighting = this.mob.getTarget() != null;
        if ((fighting && !this.fleeMidFight) || !this.level.isBrightOutside()
            || !this.mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || this.mob.isInWaterOrRain()) {
            return false;
        }
        if (!SunExposure.bodyExposed(this.level, this.mob)) {
            return false; // already under real shelter
        }
        BlockPos shelter = this.findShelter();
        if (shelter != null) {
            this.target = Vec3.atBottomCenterOf(shelter);
        } else if (fighting) {
            return false; // no shade in reach: stay in the fight
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
        if (this.path != null) {
            this.mob.getNavigation().moveTo(this.path, SPEED);
        } else if (this.target != null) {
            this.mob.getNavigation().moveTo(this.target.x, this.target.y, this.target.z, SPEED);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return (this.fleeMidFight || this.mob.getTarget() == null) && !this.mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        this.target = null;
        this.path = null;
        this.cooldown = 0; // arrived (or gave up): check again next tick, and search from here if still in the sun
        if (this.mob.getNavigation() instanceof GroundPathNavigation ground) {
            ground.setAvoidSun(this.restrictsSun && this.level.isBrightOutside());
        }
    }

    /**
     * The nearest spot within reach the Sun can't reach (the whole body out of the sun) and the mob can walk to, or
     * null. Also leaves the path to it in {@link #path}. Spots are taken ring by ring outward, so the first ring that
     * holds a reachable one wins; within a ring, closer and then lower (caves) come first.
     */
    private @Nullable BlockPos findShelter() {
        this.path = null;
        BlockPos pos = this.mob.blockPosition();
        double height = this.mob.getBbHeight();
        double half = Math.max(0.0, this.mob.getBbWidth() * 0.5 - 0.05);
        int[] roofRays = {0};
        int[] shadeRays = {0};
        int paths = 0;
        List<BlockPos> ring = new ArrayList<>();
        List<BlockPos> failed = new ArrayList<>();
        for (int r = 1; r <= REACH; r++) {
            boolean shadeOpen = r <= SHADE_REACH && shadeRays[0] < SHADE_RAYS;
            if (roofRays[0] >= MAX_RAYS && !shadeOpen) {
                break; // both budgets spent
            }
            ring.clear();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos cand = pos.offset(dx, dy, dz);
                        if (!this.level.isLoaded(cand)
                            || !this.level.getBlockState(cand.below()).isFaceSturdy(this.level, cand.below(), Direction.UP)
                            || this.level.getBlockState(cand).isSolidRender() || this.level.getBlockState(cand.above()).isSolidRender()) {
                            continue; // nothing to stand on, or no room to stand
                        }
                        // Roofed spots (houses, overhangs, caves) and open-sky shadow (beside a wall or cliff) have their own budgets.
                        boolean open = this.level.canSeeSky(cand.above());
                        if (open ? !shadeOpen || shadeRays[0] >= SHADE_RAYS : roofRays[0] >= MAX_RAYS) {
                            continue;
                        }
                        if (this.sheltered(cand, height, half, open ? shadeRays : roofRays)) {
                            ring.add(cand.immutable());
                        }
                    }
                }
            }
            if (ring.isEmpty()) {
                continue;
            }
            // closer, then lower (caves)
            ring.sort(Comparator.comparingDouble(c -> pos.distSqr(c) - (pos.getY() - c.getY()) * 2.0));
            int tried = 0;
            for (BlockPos cand : ring) {
                if (failed.stream().anyMatch(f -> f.distManhattan(cand) <= 3)) {
                    continue; // next to a spot it couldn't reach: the same shut house, most likely
                }
                if (paths >= MAX_PATHS) {
                    return null; // can't walk to any of the nearest few: leave it to the walk away from the Sun
                }
                if (tried++ >= PATHS_PER_RING) {
                    break; // try the next ring out
                }
                paths++;
                Path to = this.mob.getNavigation().createPath(cand, 0);
                if (to != null && to.canReach()) {
                    this.path = to;
                    return cand; // the nearest ring that holds shelter it can reach wins
                }
                failed.add(cand);
            }
        }
        return null;
    }

    /**
     * Whether the Sun reaches no part of a mob standing at {@code cand}: the head first (it is out in the light most
     * often, so most spots cost one ray), then the feet, then the four top corners of the hitbox.
     */
    private boolean sheltered(BlockPos cand, double height, double half, int[] rays) {
        Vec3 feet = Vec3.atBottomCenterOf(cand).add(0.0, 0.05, 0.0);
        double top = height - 0.1;
        Vec3[] points = {
            feet.add(0.0, top, 0.0), feet,
            feet.add(-half, top, -half), feet.add(half, top, -half), feet.add(-half, top, half), feet.add(half, top, half),
        };
        for (Vec3 point : points) {
            rays[0]++;
            if (SunExposure.exposedToSun(this.level, point)) {
                return false; // leaves and glass let it through
            }
        }
        return true;
    }
}

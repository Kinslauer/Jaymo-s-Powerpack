package com.jaymospowers.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * One of the Ash's lava fish ({@link Species#lava}), after the real fish of hot springs: they keep to the warm shallow
 * edge of a lake and never stray far from the shore (a pupfish in its spring, a molly in the sulphur seep), and the
 * lake's own rules are theirs: fireproof, swimming in lava as a fish swims in water. Everything else (weight, hunger,
 * a big one hunting the small ones, being put back) is {@link WildFish}'s; a hook only draws them when it hangs in lava
 * ({@link LavaFishing}).
 *
 * <p>Lava has no water paths, so it swims by hand: a slow drift in a chosen heading, turned back toward the nearest
 * shore when it has wandered {@link #SHORE_RANGE} from one, and held near the top of the lava.
 */
public class LavaFish extends WildFish {
	/** Spawns, and stays, this close (blocks) to the lake's shore. */
	static final int SHORE_SPAWN = 4, SHORE_RANGE = 6;
	/** How far it looks for a shore to turn back to (blocks). */
	static final int SEEK = 16;
	/** How hard it drifts along its heading, and how often (1 in this many ticks) it picks a new one. */
	static final double DRIFT = 0.012;
	static final int TURN_EVERY = 50;
	/** How hard it is pushed up toward the top of the lava, and how deep (blocks of lava over it) it sits. */
	static final double LIFT = 0.014;
	static final int SIT_SURFACE = 1, SIT_MID = 3;

	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private Vec3 heading = Vec3.ZERO;

	public LavaFish(EntityType<? extends WildFish> type, Level level) {
		super(type, level);
	}

	/** Lava is its water: the fish's own movement and air rules apply in it. */
	@Override
	public boolean isInWater() {
		return super.isInWater() || isInLava();
	}

	/** Lava at {@code pos}, with a shore (a block that isn't lava) within {@link #SHORE_SPAWN}. */
	static boolean canSpawn(ServerLevelAccessor level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.LAVA) && shore(level, pos, SHORE_SPAWN) > 0;
	}

	/** How many blocks off the nearest block that isn't lava is, going straight along the level of {@code pos} (0 if none within {@code range}). */
	private static int shore(net.minecraft.world.level.LevelReader level, BlockPos pos, int range) {
		return seek(level, pos, range, null);
	}

	/** The nearest shore within {@code range}: its distance, and (in {@code toward}[0], when given) the direction to it. */
	private static int seek(net.minecraft.world.level.LevelReader level, BlockPos pos, int range, int[] toward) {
		int best = 0;
		for (int[] d : DIRS) {
			for (int step = 1; step <= range; step++) {
				BlockPos at = pos.offset(d[0] * step, 0, d[1] * step);
				if (!level.getFluidState(at).is(FluidTags.LAVA)) {
					if (best == 0 || step < best) {
						best = step;
						if (toward != null) {
							toward[0] = d[0];
							toward[1] = d[1];
						}
					}
					break;
				}
			}
		}
		return best;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || !isInLava() || FishingReal.onLine(this) || getTarget() != null) {
			return;
		}
		BlockPos at = blockPosition();
		// held near the top of the lava
		int over = 0;
		while (over < SIT_MID + 1 && server.getFluidState(at.above(over + 1)).is(FluidTags.LAVA)) {
			over++;
		}
		if (over > (species().depth == Species.Depth.SURFACE ? SIT_SURFACE : SIT_MID)) {
			addDeltaMovement(new Vec3(0.0, LIFT, 0.0));
		}
		if (FishBites.taken(this)) {
			// on its way to a hook: FishBites steers it
			return;
		}
		if (heading == Vec3.ZERO || tickCount % TURN_EVERY == 0 && getRandom().nextBoolean()) {
			heading = pick(server, at);
		}
		Vec3 ahead = position().add(heading.scale(SIT_MID));
		if (heading != Vec3.ZERO && !server.getFluidState(BlockPos.containing(ahead)).is(FluidTags.LAVA)) {
			heading = pick(server, at);
		}
		addDeltaMovement(heading.scale(DRIFT));
	}

	/** A new heading: back toward the shore if it has strayed from it, otherwise any way along the lake. */
	private Vec3 pick(ServerLevel level, BlockPos at) {
		int[] toward = new int[2];
		int off = seek(level, at, SEEK, toward);
		if (off > SHORE_RANGE) {
			return new Vec3(toward[0], 0.0, toward[1]).normalize();
		}
		double a = getRandom().nextDouble() * Math.PI * 2.0;
		return new Vec3(Math.cos(a), 0.0, Math.sin(a));
	}
}

package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * How a horde ({@link ZombieHordes}) gets at you, Andy's picks from Undead Nights (its ideas, our own code):
 * <ul>
 * <li><b>Climbing over each other</b> ({@link #climb}): one pressed against a wall with {@link #CROWD} or more of its
 * horde round it climbs up on them, as high as the crowd under it can lift it ({@link #PER_BODY} a body), so a big
 * enough crowd tops a wall.</li>
 * <li><b>Crossing water</b> ({@link #swim}): they keep their heads up and wade or paddle across (they can path over
 * water, and the dead in a horde never turn into drowned: {@code HordeZombieWaterMixin}).</li>
 * <li><b>Breaking through</b> ({@link #dig}): one that's stuck with somewhere to be beats at the block in its way and,
 * in time, breaks it (more of them at it, quicker). How hard a block the horde can break follows the danger, from
 * dirt and glass at first to obsidian at the very end ({@link #HARDNESS}). Doors are left to {@code DoorPounding};
 * nothing that can't be broken by hand is touched; only with mob griefing on.</li>
 * </ul>
 */
final class HordeMoves {
	/** Climbing: this many of its horde within {@link #CROWD_RANGE} (horizontally), and how high each body lets it get. */
	static final int CROWD = 2;
	static final double CROWD_RANGE = 1.6, PER_BODY = 0.9, CLIMB_SPEED = 0.22;
	/** Swimming: pushed up while the water's this deep on it, at most this fast. */
	static final double FLOAT_DEPTH = 0.5, FLOAT_PUSH = 0.05, FLOAT_MAX = 0.12;
	/** Digging: checked this often; stuck when it moved less than this in that time with its goal further than {@link #GOAL_NEAR}. */
	static final int DIG_EVERY = 5, STUCK_EVERY = 40;
	static final double STUCK_MOVE = 0.5, GOAL_NEAR = 2.0;
	/** Ticks of beating a block takes: this, plus this per point of hardness (shared by everyone at it). */
	static final int DIG_BASE = 40, DIG_PER_HARDNESS = 60;
	/** The hardest block it can break by danger: below 0.2, 0.4, 0.6, 0.8, and above (dirt and glass ... obsidian). */
	static final float[] HARDNESS = {0.6F, 2.0F, 3.0F, 6.0F, 50.0F};

	/** Each member's place when it was last looked at, and when. */
	private static final Map<Mob, Vec3> LAST = new WeakHashMap<>();
	private static final Map<Mob, Long> LAST_AT = new WeakHashMap<>();
	/** Blocks being beaten at, per level: the work done on each (ticks). */
	private static final Map<ServerLevel, Map<BlockPos, Integer>> WORK = new WeakHashMap<>();

	private HordeMoves() {
	}

	/** One tick of a horde's members: climbing and swimming every tick, digging every {@link #DIG_EVERY}. */
	static void tick(ServerLevel level, List<Mob> members, Vec3 goal, float danger, boolean diggers) {
		long now = level.getGameTime();
		boolean dig = diggers && now % DIG_EVERY == 0 && level.getGameRules().get(GameRules.MOB_GRIEFING);
		for (Mob mob : members) {
			if (!mob.isAlive() || mob.isRemoved()) {
				continue;
			}
			swim(mob);
			climb(mob, members, goal);
			if (dig && digger(mob)) {
				dig(level, mob, goal, danger, now);
			}
		}
	}

	private static boolean digger(Mob mob) {
		return mob instanceof Zombie || mob instanceof Vindicator || mob instanceof Hoglin;
	}

	// ---- climbing -------------------------------------------------------------------------------------------------------

	private static void climb(Mob mob, List<Mob> members, Vec3 goal) {
		if (!mob.horizontalCollision || mob.isInWater()) {
			return;
		}
		LivingEntity target = mob.getTarget();
		Vec3 to = target != null ? target.position() : goal;
		if (to.y < mob.getY() + 0.5 && mob.position().distanceTo(to) < 3.0) {
			return;
		}
		int crowd = 0;
		double lowest = mob.getY();
		for (Mob other : members) {
			if (other == mob || !other.isAlive()) {
				continue;
			}
			double dx = other.getX() - mob.getX(), dz = other.getZ() - mob.getZ();
			if (dx * dx + dz * dz <= CROWD_RANGE * CROWD_RANGE && other.getY() <= mob.getY() + 0.5 && other.getY() > mob.getY() - 4.0) {
				crowd++;
				lowest = Math.min(lowest, other.getY());
			}
		}
		if (crowd < CROWD || mob.getY() - lowest >= crowd * PER_BODY) {
			return;
		}
		Vec3 v = mob.getDeltaMovement();
		mob.setDeltaMovement(v.x, Math.max(v.y, CLIMB_SPEED), v.z);
		mob.resetFallDistance();
	}

	// ---- swimming -------------------------------------------------------------------------------------------------------

	private static void swim(Mob mob) {
		if (!mob.isInWater() || mob.getFluidHeight(net.minecraft.tags.FluidTags.WATER) < FLOAT_DEPTH) {
			return;
		}
		Vec3 v = mob.getDeltaMovement();
		mob.setDeltaMovement(v.x, Math.min(FLOAT_MAX, v.y + FLOAT_PUSH), v.z);
	}

	// ---- breaking through ------------------------------------------------------------------------------------------------

	private static float hardest(float danger) {
		int i = Math.min(HARDNESS.length - 1, (int) (danger * 5.0F));
		return HARDNESS[i];
	}

	private static void dig(ServerLevel level, Mob mob, Vec3 goal, float danger, long now) {
		LivingEntity target = mob.getTarget();
		Vec3 to = target != null ? target.position() : goal;
		Vec3 here = mob.position();
		double flat = Math.hypot(to.x - here.x, to.z - here.z);
		Vec3 last = LAST.get(mob);
		Long at = LAST_AT.get(mob);
		if (last == null || at == null || now - at >= STUCK_EVERY) {
			LAST.put(mob, here);
			LAST_AT.put(mob, now);
			if (last == null) {
				return;
			}
		}
		boolean stuck = last.distanceTo(here) < STUCK_MOVE && now - at >= STUCK_EVERY / 2 && flat > GOAL_NEAR;
		if (!stuck) {
			return;
		}
		// the block in its way: at its feet or its head, toward where it's going
		double nx = (to.x - here.x) / Math.max(flat, 1.0E-4), nz = (to.z - here.z) / Math.max(flat, 1.0E-4);
		BlockPos ahead = BlockPos.containing(here.x + nx * 0.9, here.y + 0.2, here.z + nz * 0.9);
		float max = hardest(danger);
		BlockPos pick = null;
		for (BlockPos p : new BlockPos[] {ahead.above(), ahead, to.y > here.y + 1.0 ? mob.blockPosition().above(2) : null}) {
			if (p != null && breakable(level, p, max)) {
				pick = p;
				break;
			}
		}
		if (pick == null) {
			return;
		}
		BlockState state = level.getBlockState(pick);
		Map<BlockPos, Integer> work = WORK.computeIfAbsent(level, l -> new HashMap<>());
		int done = work.merge(pick, DIG_EVERY, Integer::sum);
		int need = DIG_BASE + (int) (state.getDestroySpeed(level, pick) * DIG_PER_HARDNESS);
		mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		mob.getLookControl().setLookAt(Vec3.atCenterOf(pick));
		if (done % 20 < DIG_EVERY) {
			level.playSound(null, pick, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.6F, 0.8F + level.getRandom().nextFloat() * 0.3F);
		}
		if (done >= need) {
			work.remove(pick);
			level.destroyBlockProgress(pick.hashCode(), pick, -1);
			level.destroyBlock(pick, true, mob);
		} else {
			level.destroyBlockProgress(pick.hashCode(), pick, Math.min(9, done * 10 / need));
		}
		if (work.size() > 256) {
			work.clear();
		}
	}

	/** Something in the way it can break: solid, breakable by hand, no harder than {@code max}, not a door, not a block entity's (chests, spawners...). */
	private static boolean breakable(ServerLevel level, BlockPos pos, float max) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getCollisionShape(level, pos).isEmpty() || state.getBlock() instanceof DoorBlock) {
			return false;
		}
		BlockEntity be = level.getBlockEntity(pos);
		float hardness = state.getDestroySpeed(level, pos);
		return be == null && hardness >= 0.0F && hardness <= max;
	}
}

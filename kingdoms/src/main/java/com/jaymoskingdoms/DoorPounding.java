package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "zombies should beat and break doors." Vanilla's zombies break wooden doors only on Hard, and only the few born
 * able to. Here every zombie (husks, the drowned on land, zombie villagers, Kitchen's risen players, the Frostbite: any
 * {@link Zombie} but a zombified piglin, a raised thrall or a Bio-built's bud) on any difficulty but Peaceful beats on a
 * shut door that stands between it and what it hunts: arm swings, the door-bang sound, splinters off the door (and,
 * with {@link SpookyDoors}, the door jarred open a crack and creaking shut), cracks growing over both halves until it
 * gives. A wooden door gives in {@link #WOOD_TICKS}; an iron or copper one (any not in {@code #wooden_doors}) takes
 * {@link #STURDY_TICKS}; a door with a lock fitted and locked ({@link Locks}) holds as much longer as the lock slows a
 * player ({@link Locks#BREAK_FACTOR}). Each other zombie pounding the same door adds {@link #GROUP_SHARE} of a blow,
 * up to {@link #MAX_GROUP} of them. A broken door drops nothing, as vanilla's. Villagers within {@link #HEAR} hear it
 * and run from the door ({@link Frights#scare}). The mob_griefing game rule turns it all off, as it does vanilla's.
 */
final class DoorPounding {
	/** Ticks one zombie takes to beat down a wooden door; an iron or copper one. */
	static final float WOOD_TICKS = 100.0F, STURDY_TICKS = 900.0F;
	/** Each zombie past the first adds this share of a blow a tick, and no more than this many count. */
	static final float GROUP_SHARE = 0.6F;
	static final int MAX_GROUP = 6;
	/** A blow lands about this often (ticks), give or take a third. */
	static final int BLOW_EVERY = 14;
	/** How near (blocks, flat) a door's middle must be to pound it; how often a zombie looks for one (ticks). */
	static final double REACH = 2.0;
	static final int LOOK_EVERY = 5;
	/** A door nobody has pounded this long (ticks) heals: its cracks go. */
	static final long FORGET_TICKS = 100L;
	/** How far (blocks) villagers hear the pounding, and how often (ticks) it frightens them again. */
	static final double HEAR = 16.0;
	static final long FRIGHT_EVERY = 40L;
	/** With Spooky Doors: how far a blow jars the door open, how long till it swings back, and how open still counts as shut. */
	static final float RATTLE = 0.08F, SHUT_ENOUGH = 0.25F;
	static final long RATTLE_TICKS = 4L;

	private static final class Damage {
		final int lowerId, upperId;
		float progress;
		long tick = -1L, frightened = Long.MIN_VALUE / 2, rattleShut = -1L;
		int pounders, stage = -1;

		Damage(int lowerId, int upperId) {
			this.lowerId = lowerId;
			this.upperId = upperId;
		}
	}

	private static final Map<GlobalPos, Damage> DOORS = new HashMap<>();
	/** The cracks' breaker ids: negative, so no entity (whose player would not see them) shares one. */
	private static int nextId = -1_000_000;

	private DoorPounding() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
		ServerTickEvents.END_SERVER_TICK.register(DoorPounding::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DOORS.clear());
	}

	private static void loaded(Entity entity) {
		if (!(entity instanceof Zombie zombie) || zombie instanceof ZombifiedPiglin || zombie instanceof BioBud) {
			return;
		}
		var goals = ((MobAccessor)zombie).jaymoskingdoms$goals();
		if (goals.getAvailableGoals().stream().noneMatch(w -> w.getGoal() instanceof Pound)) {
			goals.addGoal(1, new Pound(zombie));
		}
		paths(zombie);
	}

	/** It plans its paths through doors (to the door, where it pounds), as vanilla's door-breakers do. */
	private static void paths(Mob mob) {
		if (mob.getNavigation() instanceof GroundPathNavigation ground) {
			ground.setCanOpenDoors(true);
		}
	}

	/** Whether mob may pound doors now: server, not Peaceful, mob griefing on, not a thrall, not swimming. */
	private static boolean allowed(Mob mob) {
		return mob.level() instanceof ServerLevel level && level.getDifficulty() != Difficulty.PEACEFUL
			&& level.getGameRules().get(GameRules.MOB_GRIEFING) && !Thralls.isThrall(mob) && !mob.isInWater() && !mob.isPassenger();
	}

	/** A door at pos, shut (with Spooky Doors: no more than a crack open). */
	private static boolean shut(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof DoorBlock)) {
			return false;
		}
		if (!state.getValue(DoorBlock.OPEN)) {
			return true;
		}
		float open = SpookyDoors.percentOpen(level, lower(state, pos));
		return open >= 0.0F && open < SHUT_ENOUGH;
	}

	private static BlockPos lower(BlockState state, BlockPos pos) {
		return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.immutable();
	}

	/** Neither wooden nor openable by hand: iron, copper, our own sturdier doors. */
	private static boolean sturdy(BlockState state) {
		return !(state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand() && state.is(BlockTags.WOODEN_DOORS));
	}

	/** The ticks one zombie takes to break the door at lower: by its make, longer with a lock fitted and locked. */
	private static float breakTicks(Level level, BlockPos lower, BlockState state) {
		float ticks = sturdy(state) ? STURDY_TICKS : WOOD_TICKS;
		Locks.Fitted f = Locks.entry(level, lower);
		if (f != null && f.locked()) {
			ticks /= Locks.BREAK_FACTOR[Mth.clamp(f.level(), 1, Locks.BREAK_FACTOR.length) - 1];
		}
		return ticks;
	}

	/**
	 * The shut door beside mob that stands between it and target (its lower half), nearest first; null if none, or if
	 * mob isn't held up (it bumps into something, or its path ends short of its prey).
	 */
	private static @Nullable BlockPos doorBetween(Mob mob, LivingEntity target) {
		Path path = mob.getNavigation().getPath();
		if (!mob.horizontalCollision && path != null && !path.isDone() && path.canReach()) {
			return null;
		}
		Level level = mob.level();
		Vec3 toTarget = target.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
		BlockPos at = mob.blockPosition();
		BlockPos best = null;
		double bestD = REACH * REACH;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 1, 1))) {
			if (!shut(level, p)) {
				continue;
			}
			BlockPos low = lower(level.getBlockState(p), p);
			Vec3 off = Vec3.atBottomCenterOf(low).subtract(mob.position()).multiply(1.0, 0.0, 1.0);
			double d = off.lengthSqr();
			if (d <= bestD && off.dot(toTarget) > 0.0 && toTarget.lengthSqr() > d) {
				bestD = d;
				best = low.immutable();
			}
		}
		return best;
	}

	/** One tick of mob pounding the door at lower; blow: a blow lands this tick. */
	private static void pound(ServerLevel level, Mob mob, BlockPos lower, boolean blow) {
		BlockState state = level.getBlockState(lower);
		GlobalPos key = GlobalPos.of(level.dimension(), lower);
		Damage d = DOORS.computeIfAbsent(key, k -> new Damage(nextId--, nextId--));
		long now = level.getGameTime();
		if (d.tick != now) {
			d.tick = now;
			d.pounders = 0;
		}
		if (d.pounders < MAX_GROUP) {
			d.progress += d.pounders == 0 ? 1.0F : GROUP_SHARE;
		}
		d.pounders++;
		boolean sturdy = sturdy(state);
		Locks.Fitted lock = Locks.entry(level, lower);
		boolean locked = lock != null && lock.locked();
		if (blow) {
			mob.swingForAttack(mob.getUsedItemHand());
			level.levelEvent(sturdy ? 1020 : 1019, lower, 0);
			Vec3 face = Vec3.atCenterOf(lower).add(mob.position().subtract(Vec3.atCenterOf(lower)).multiply(0.4, 0.0, 0.4));
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), face.x, lower.getY() + 1.0, face.z, 8, 0.2, 0.6, 0.2, 0.05);
			if (!sturdy && !locked && SpookyDoors.loaded()) {
				SpookyDoors.operate(level, lower, mob, RATTLE);
				d.rattleShut = now + RATTLE_TICKS;
			}
			if (now - d.frightened >= FRIGHT_EVERY) {
				d.frightened = now;
				Vec3 at = Vec3.atCenterOf(lower);
				for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(lower).inflate(HEAR), Villager::isAlive)) {
					Frights.scare(level, v, at);
				}
			}
		}
		float need = breakTicks(level, lower, state);
		if (d.progress >= need) {
			breakDown(level, lower, state, d);
			DOORS.remove(key);
			return;
		}
		int stage = Mth.clamp((int)(d.progress / need * 10.0F), 0, 9);
		if (stage != d.stage) {
			d.stage = stage;
			level.destroyBlockProgress(d.lowerId, lower, stage);
			level.destroyBlockProgress(d.upperId, lower.above(), stage);
		}
	}

	/** The door gives: splinters and the crash, and it's gone (both halves; nothing dropped, as vanilla's). */
	private static void breakDown(ServerLevel level, BlockPos lower, BlockState state, Damage d) {
		clearCracks(level, lower, d);
		level.levelEvent(1021, lower, 0);
		level.levelEvent(2001, lower, Block.getId(state));
		level.removeBlock(lower, false);
		if (level.getBlockState(lower.above()).getBlock() instanceof DoorBlock) {
			level.removeBlock(lower.above(), false);
		}
	}

	private static void clearCracks(ServerLevel level, BlockPos lower, Damage d) {
		level.destroyBlockProgress(d.lowerId, lower, -1);
		level.destroyBlockProgress(d.upperId, lower.above(), -1);
	}

	/** Spooky Doors' jarred doors swing back shut; doors left alone heal. */
	private static void tick(MinecraftServer server) {
		if (DOORS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<GlobalPos, Damage>> it = DOORS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<GlobalPos, Damage> e = it.next();
			ServerLevel level = server.getLevel(e.getKey().dimension());
			Damage d = e.getValue();
			if (level == null) {
				it.remove();
				continue;
			}
			BlockPos pos = e.getKey().pos();
			long now = level.getGameTime();
			if (d.rattleShut >= 0L && now >= d.rattleShut) {
				d.rattleShut = -1L;
				if (level.getBlockState(pos).getBlock() instanceof DoorBlock) {
					SpookyDoors.operate(level, pos, null, 0.0F);
				}
			}
			if (now - d.tick > FORGET_TICKS || !(level.getBlockState(pos).getBlock() instanceof DoorBlock)) {
				clearCracks(level, pos, d);
				it.remove();
			}
		}
	}

	/** A zombie's goal: beat down the shut door between it and its prey. */
	static final class Pound extends Goal {
		private final Mob mob;
		private @Nullable BlockPos door;
		private int nextBlow;

		Pound(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (mob.tickCount % LOOK_EVERY != 0 || !allowed(mob)) {
				return false;
			}
			if (mob instanceof Zombie zombie && zombie.canBreakDoors()) {
				// vanilla's own Hard-only door breaker would pound alongside ours, ignoring locks: ours does it now
				zombie.setCanBreakDoors(false);
				paths(zombie);
			}
			LivingEntity target = mob.getTarget();
			if (target == null || !target.isAlive()) {
				return false;
			}
			door = doorBetween(mob, target);
			return door != null;
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = mob.getTarget();
			return door != null && target != null && target.isAlive() && allowed(mob) && shut(mob.level(), door)
				&& Vec3.atBottomCenterOf(door).subtract(mob.position()).horizontalDistanceSqr() <= REACH * REACH;
		}

		@Override
		public void start() {
			mob.getNavigation().stop();
			nextBlow = 0;
		}

		@Override
		public void stop() {
			door = null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (door == null || !(mob.level() instanceof ServerLevel level)) {
				return;
			}
			mob.getLookControl().setLookAt(Vec3.atCenterOf(door.above()));
			boolean blow = --nextBlow <= 0;
			if (blow) {
				int spread = BLOW_EVERY / 3;
				nextBlow = BLOW_EVERY - spread + mob.getRandom().nextInt(spread * 2 + 1);
			}
			pound(level, mob, door, blow);
		}
	}
}

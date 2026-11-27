package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patch 5 of the fighting AI (danger sense; the ideas are Hostile Humans', none of its code). What a fighter does about the
 * things in a fight that hurt whoever stands in them. Folk and illagers through {@link DangerGoal}; MCA's villagers through
 * {@link #brainStep} (from {@code Workers.step}, their brain paused while this has their body).
 * <ul>
 * <li><b>Creepers</b>: a creeper within {@link #STRIKE_RANGE} that hasn't started to hiss and is coming at the fighter or its
 * side is struck first (it becomes the fighter's foe). Once it hisses, {@link CreeperFlight} backs everyone out of the blast;
 * a villager lets go of it meanwhile (its brain would walk it back in), and goes for it again once it calms. One who knows
 * creepers ({@link FoeLore#avoids}) keeps off it with a blade, as before.</li>
 * <li><b>Lit charges</b> (TNT, dynamite): {@link CreeperFlight} runs everyone clear of those too.</li>
 * <li><b>Fire and lava</b>: a burning fighter in flames or lava steps out to the nearest spot beside it that doesn't burn, then
 * makes for water within {@link #WATER_REACH} along a path with no lava or fire on it. Paths never go into lava (the
 * pathfinder won't), and the ones made here are checked node by node.</li>
 * <li><b>Water</b>: deep in water in a fight, it swims on at its foe while it has a way to it (or the foe is in the water too),
 * else it heads for the bank nearest its foe; after the fight it keeps heading for the nearest bank till it's out (giving up
 * after {@link #LEAVE_LONGEST}, or once {@link #BANK_MISSES} looks find no way out), and {@code Unstick.wadeOut} heaves it
 * up the bank on its path. A villager that came ashore from a foe it has no way to across the water stays on the bank for
 * {@link #ASHORE_HOLD} (its brain would walk it back in), unless a way opens, the foe comes near or into the water, or it's
 * hurt.</li>
 * <li><b>Webs</b>: stuck in a cobweb with a blade or a sword in hand, it cuts the web after {@link #WEB_CUT}. Only the web goes,
 * and nothing drops (and not at all with mob griefing off).</li>
 * </ul>
 * Never for the downed, the bound or the held, and never a guard making an arrest. Running from a blast ({@link CreeperFlight})
 * comes before all of this: while it runs, this lets go of the legs. A way to water or a bank is walked from the goal's start
 * (a move made while the goals are picked is undone when the goal it replaces stops), and worked out again
 * ({@link #REPATHS} at most) if the walk ends short; its trial paths share {@link PathProbes}' budget.
 */
public final class DangerSense {
	/** A creeper this near (blocks), not yet hissing, coming at the fighter or its side (or this near at all), is struck first. */
	static final double STRIKE_RANGE = 6.0, STRIKE_CLOSE = 3.0;
	/** A creeper struck is gone back for while it stays this near (blocks). */
	static final double STRUCK_KEPT = 12.0;
	/** Creepers are looked for this often (ticks). */
	static final int LOOK = 5;
	/** Burning: water is looked for this far across (blocks), this far up and down, this often (ticks). */
	static final int WATER_REACH = 8, WATER_UP = 2, WATER_DOWN = 3, WATER_LOOK = 20;
	/** Out of the flames: spots this near (blocks across) are tried. */
	static final int OUT_REACH = 2;
	/** A path made here is at most this long (nodes). */
	static final int PATH_NODES = 28;
	/** Banks are looked for this far (blocks) along this many ways, this often (ticks); out of the water it's let be this long after its fight (ticks). */
	static final int BANK_REACH = 10, BANK_WAYS = 16, BANK_LOOK = 20, AFTER_FIGHT = 200;
	/** Water this high up its body (a share of its height) is deep: it swims. */
	static final double DEEP = 0.5;
	/** In a web this long (ticks) with a blade in hand, it cuts it. */
	static final int WEB_CUT = 12;
	/** After a fight it heads for a bank for this long at most (ticks), and gives up once this many looks find no way out. */
	static final int LEAVE_LONGEST = 1200, BANK_MISSES = 3;
	/** A villager come ashore from a foe it can't get to across the water is kept on the bank this long (ticks). */
	static final int ASHORE_HOLD = 200;
	/** A walk to water or a bank that ends short of it is worked out again at most this many times. */
	static final int REPATHS = 2;
	/** How fast it gets out of fire and to water, and to a bank (a share of its walk). */
	static final double FIRE_SPEED = 1.35, BANK_SPEED = 1.1;
	/** A run to water or a bank is given up after this long (ticks). */
	static final int RUN_LONGEST = 160;
	/** Items whose name says they have an edge, besides swords: what a web is cut with. */
	static final String[] EDGES = {"sword", "blade", "knife", "dagger", "katana", "machete", "sabre", "saber", "cutlass", "scimitar", "rapier"};

	enum Run {
		NONE, OUT_OF_FIRE, TO_WATER, TO_BANK, ASHORE
	}

	private static final class State {
		Run run = Run.NONE;
		@Nullable BlockPos dest;
		long runStart, nextWaterLook, nextBankLook, nextCreeperLook;
		long lastFought = Long.MIN_VALUE / 2;
		/** When it was first in the web it's in (game time), or -1. */
		long webSince = -1L;
		/** The way to water or a bank, found and not yet walked (walked from the goal's start, or at once for a villager). */
		@Nullable Path pending;
		double pendingSpeed;
		int pendingReach, repaths, bankMisses;
		/** The foe it went ashore from (no way to it across the water); the hold on the bank, and its next look for a way. */
		@Nullable LivingEntity bankedFrom, ashoreFoe;
		long ashoreUntil, nextAshoreLook;
		/** A creeper it went for: gone back for once it calms. */
		@Nullable Creeper struck;
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();
	/** Vanilla illagers given the goal already (folk add it themselves). */
	private static final Set<Mob> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());

	private DangerSense() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractIllager illager && GIVEN.add(illager)) {
				((MobAccessor)illager).jaymoskingdoms$goals().addGoal(-2, new DangerGoal(illager));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STATES.clear();
			GIVEN.clear();
		});
	}

	/** On its feet and its own: not downed, bound, held, asleep or riding, and not a guard making an arrest. */
	private static boolean able(Mob m) {
		return m.isAlive() && !m.isRemoved() && !m.isNoAi() && !m.isPassenger() && !m.isSleeping() && Temperaments.humanoid(m) && !Downed.isDowned(m)
			&& !Captives.bound(m) && !Grapples.held(m) && !Arrests.busy(m) && !Arrests.held(m);
	}

	/** Whether {@code m} fights at all (a creeper is struck first only by a fighter). */
	private static boolean fights(Mob m, @Nullable LivingEntity foe) {
		if (m.isBaby()) {
			return false;
		}
		if (foe != null) {
			return true;
		}
		return m instanceof Folk f ? f.combatant() : Downed.isGuard(m) || m instanceof AbstractIllager;
	}

	// ---- the step ----------------------------------------------------------------------------------------------------

	/** One tick: true if this has {@code m}'s legs (out of fire, to water, or to a bank). */
	static boolean step(ServerLevel level, Mob m) {
		State s = STATES.get(m);
		if (!able(m)) {
			if (s != null) {
				STATES.remove(m);
			}
			return false;
		}
		LivingEntity foe = Comrades.target(m);
		long now = level.getGameTime();
		if (s == null) {
			if (foe == null && !m.isOnFire() && !m.isInLava() && !m.isInWater() && !webbed(level, m)) {
				return false;
			}
			s = new State();
			STATES.put(m, s);
		}
		if (foe != null) {
			s.lastFought = now;
			s.bankMisses = 0;
		}
		web(level, m, s, now);
		creeper(level, m, s, foe, now);
		// running from a blast comes first: CreeperFlight has the legs meanwhile
		boolean blast = m instanceof PathfinderMob pm && CreeperFlight.fleeing(pm);
		boolean legs = !blast && (fire(level, m, s, now) || water(level, m, s, foe, now) || ashore(m, s, foe, now));
		if (!legs) {
			s.run = Run.NONE;
			s.dest = null;
			s.pending = null;
			if (foe == null && s.struck == null && s.webSince < 0L && now - s.lastFought > AFTER_FIGHT && !m.isOnFire() && !m.isInWater()) {
				STATES.remove(m);
			}
		}
		return legs;
	}

	/** Whether this has {@code m}'s legs just now. */
	static boolean running(Mob m) {
		State s = STATES.get(m);
		return s != null && s.run != Run.NONE;
	}

	/** Sets it walking the way to water or a bank it found, if it hasn't yet. */
	private static void go(Mob m) {
		State s = STATES.get(m);
		if (s != null && s.pending != null && s.run != Run.NONE) {
			Path path = s.pending;
			s.pending = null;
			PathProbes.follow(m, path, s.pendingReach, s.pendingSpeed);
		}
	}

	/** Its walk to {@code s.dest} ended short: worked out again from here (at most {@link #REPATHS} times a run). */
	private static boolean repath(ServerLevel level, Mob m, State s, double speed) {
		if (s.dest == null || s.repaths >= REPATHS || !PathProbes.take()) {
			return false;
		}
		s.repaths++;
		Path path = PathProbes.to(m, s.dest, s.pendingReach);
		return path != null && clean(level, path) && PathProbes.follow(m, path, s.pendingReach, speed);
	}

	/** A way to {@code dest} found: kept to be walked ({@link #go}). */
	private static void found(State s, Run run, BlockPos dest, Path path, int reach, double speed, long now) {
		s.pending = path;
		s.pendingReach = reach;
		s.pendingSpeed = speed;
		s.run = run;
		s.dest = dest;
		s.runStart = now;
		s.repaths = 0;
	}

	// ---- creepers ----------------------------------------------------------------------------------------------------

	private static void creeper(ServerLevel level, Mob m, State s, @Nullable LivingEntity foe, long now) {
		if (m instanceof PathfinderMob pm && CreeperFlight.fleeing(pm)) {
			// backing out of the blast (CreeperFlight has the legs); a villager's brain would walk it back in at its foe
			if (m instanceof Villager && foe != null) {
				if (foe instanceof Creeper c) {
					s.struck = c;
				}
				Comrades.stand(m);
			}
			return;
		}
		// (by game time: its goal's checks run every other tick)
		if (now < s.nextCreeperLook) {
			return;
		}
		s.nextCreeperLook = now + LOOK;
		Creeper was = s.struck;
		if (was != null) {
			if (!was.isAlive() || was.isRemoved() || was.level() != m.level() || was.distanceToSqr(m) > STRUCK_KEPT * STRUCK_KEPT) {
				s.struck = null;
			} else if (foe == null && !CreeperFlight.hissing(was) && !Comrades.busy(m) && !BreakOff.away(m)) {
				// it has calmed: at it again before it can hiss
				Comrades.aim(m, was);
				return;
			}
		}
		if (foe instanceof Creeper || !fights(m, foe) || BreakOff.away(m) || Comrades.busy(m)) {
			return;
		}
		String mine = Comrades.side(m);
		Creeper near = null;
		double best = STRIKE_RANGE * STRIKE_RANGE;
		for (Creeper c : level.getEntitiesOfClass(Creeper.class, m.getBoundingBox().inflate(STRIKE_RANGE), c -> c.isAlive() && !CreeperFlight.hissing(c))) {
			double d = c.distanceToSqr(m);
			LivingEntity at = c.getTarget();
			boolean coming = at == m || at != null && Comrades.side(at).equals(mine) || d < STRIKE_CLOSE * STRIKE_CLOSE;
			if (d < best && coming && !FoeLore.avoids(m, c) && m.hasLineOfSight(c)) {
				best = d;
				near = c;
			}
		}
		if (near == null || foe != null && foe.distanceToSqr(m) < best) {
			return;
		}
		Comrades.aim(m, near);
		s.struck = near;
	}

	// ---- fire and lava -----------------------------------------------------------------------------------------------

	private static boolean fire(ServerLevel level, Mob m, State s, long now) {
		if (m.fireImmune() || m.hasEffect(MobEffects.FIRE_RESISTANCE)) {
			return false;
		}
		boolean lava = m.isInLava();
		if (!lava && !m.isOnFire()) {
			return false;
		}
		if (lava || inFlames(level, m)) {
			BlockPos out = outOfFire(level, m);
			if (out == null) {
				if (lava) {
					m.getJumpControl().jump();
				}
				return false;
			}
			s.run = Run.OUT_OF_FIRE;
			s.dest = out;
			m.getNavigation().stop();
			m.getMoveControl().setWantedPosition(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, FIRE_SPEED);
			if (lava || out.getY() > m.getBlockY()) {
				m.getJumpControl().jump();
			}
			return true;
		}
		if (m.isInWater()) {
			return false;
		}
		if (s.run == Run.TO_WATER && now - s.runStart < RUN_LONGEST
			&& (s.pending != null || !m.getNavigation().isDone() || repath(level, m, s, FIRE_SPEED))) {
			return true;
		}
		if (now < s.nextWaterLook || !PathProbes.room(1)) {
			// (out of trial paths this tick: looked for again next tick)
			return false;
		}
		s.nextWaterLook = now + WATER_LOOK;
		BlockPos water = water(level, m);
		if (water == null) {
			return false;
		}
		PathProbes.take();
		Path path = PathProbes.to(m, water, 1);
		if (path == null || !clean(level, path)) {
			return false;
		}
		found(s, Run.TO_WATER, water, path, 1, FIRE_SPEED, now);
		return true;
	}

	/** Its feet are in something burning (fire, a lit campfire) or on it (magma). */
	private static boolean inFlames(ServerLevel level, Mob m) {
		BlockPos feet = m.blockPosition();
		return NodeEvaluator.isBurningBlock(level.getBlockState(feet)) || NodeEvaluator.isBurningBlock(level.getBlockState(feet.below()))
			|| NodeEvaluator.isBurningBlock(m.getInBlockState());
	}

	/** The nearest spot beside it, within {@link #OUT_REACH}, to stand on that doesn't burn; null if there's none. */
	private static @Nullable BlockPos outOfFire(ServerLevel level, Mob m) {
		BlockPos at = m.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -OUT_REACH; dx <= OUT_REACH; dx++) {
			for (int dz = -OUT_REACH; dz <= OUT_REACH; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos p = at.offset(dx, dy, dz);
					if (!standable(level, p) || burns(level, p)) {
						continue;
					}
					double d = Vec3.atBottomCenterOf(p).distanceToSqr(m.position());
					if (d < bestD) {
						bestD = d;
						best = p;
					}
				}
			}
		}
		return best;
	}

	/** Room for a body at {@code p} (feet and head clear, no fluid there), on something solid. */
	private static boolean standable(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
			&& level.getFluidState(p).isEmpty() && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
	}

	/** Standing at {@code p} burns: fire or lava there, or a burning floor. */
	private static boolean burns(ServerLevel level, BlockPos p) {
		BlockState feet = level.getBlockState(p), floor = level.getBlockState(p.below());
		return NodeEvaluator.isBurningBlock(feet) || NodeEvaluator.isBurningBlock(floor) || level.getFluidState(p).is(FluidTags.LAVA)
			|| level.getFluidState(p.below()).is(FluidTags.LAVA);
	}

	/** The nearest water within {@link #WATER_REACH} with open air over it, ring by ring outward; null if there's none. */
	private static @Nullable BlockPos water(ServerLevel level, Mob m) {
		BlockPos at = m.blockPosition();
		for (int r = 1; r <= WATER_REACH; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = WATER_UP; dy >= -WATER_DOWN; dy--) {
						BlockPos p = at.offset(dx, dy, dz);
						if (level.getFluidState(p).is(FluidTags.WATER) && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
							&& !level.getFluidState(p.above()).is(FluidTags.LAVA)) {
							return p;
						}
					}
				}
			}
		}
		return null;
	}

	/** Not too long, no lava on or beside any node, and no fire after its first steps (it starts out next to some). */
	private static boolean clean(ServerLevel level, Path path) {
		if (path.getNodeCount() > PATH_NODES) {
			return false;
		}
		for (int i = 0; i < path.getNodeCount(); i++) {
			Node n = path.getNode(i);
			if (n.type == PathType.LAVA || n.type == PathType.FIRE || n.type == PathType.DAMAGING) {
				return false;
			}
			BlockPos p = n.asBlockPos();
			if (level.getFluidState(p).is(FluidTags.LAVA) || level.getFluidState(p.below()).is(FluidTags.LAVA) || i >= 2 && BreakOff.harmful(level, p)) {
				return false;
			}
		}
		return true;
	}

	// ---- water -------------------------------------------------------------------------------------------------------

	private static boolean water(ServerLevel level, Mob m, State s, @Nullable LivingEntity foe, long now) {
		boolean deep = m.isInWater() && m.getFluidHeight(FluidTags.WATER) >= m.getBbHeight() * DEEP;
		if (!deep) {
			// (wading up the bank's shallows it keeps on till it's out)
			boolean wading = s.run == Run.TO_BANK && m.isInWater() && (s.pending != null || !m.getNavigation().isDone()) && now - s.runStart < RUN_LONGEST;
			if (!wading && s.run == Run.TO_BANK && foe != null && s.bankedFrom == foe && m instanceof Villager && !Comrades.ranged(m)) {
				// ashore from a foe it has no way to across the water: kept on the bank a while (its brain would walk it back in)
				s.ashoreUntil = now + ASHORE_HOLD;
				s.ashoreFoe = foe;
				s.nextAshoreLook = now + BANK_LOOK;
			}
			return wading;
		}
		if (foe == null && s.run != Run.TO_BANK && (now - s.lastFought > LEAVE_LONGEST || s.bankMisses >= BANK_MISSES)) {
			// never in a fight, or it has tried long enough to get out
			return false;
		}
		if (m.getFluidHeight(FluidTags.WATER) > m.getFluidJumpThreshold()) {
			// head up: it swims
			m.getJumpControl().jump();
		}
		if (s.run == Run.TO_BANK && now - s.runStart < RUN_LONGEST
			&& (s.pending != null || !m.getNavigation().isDone() || repath(level, m, s, BANK_SPEED))) {
			return true;
		}
		if (now < s.nextBankLook || !PathProbes.room(foe != null ? 2 : 1)) {
			// (out of trial paths this tick: looked for again next tick)
			return false;
		}
		s.nextBankLook = now + BANK_LOOK;
		if (foe != null) {
			if (foe.isInWater() || m.distanceToSqr(foe) < 9.0) {
				return false;
			}
			PathProbes.take();
			Path to = PathProbes.to(m, foe, 0);
			if (to != null && to.canReach()) {
				// a way to it: swim on and fight
				return false;
			}
		}
		BlockPos bank = bank(level, m, foe);
		Path path = null;
		if (bank != null) {
			PathProbes.take();
			path = PathProbes.to(m, bank, 0);
		}
		if (bank == null || path == null || !clean(level, path)) {
			if (foe == null) {
				s.bankMisses++;
			}
			return false;
		}
		found(s, Run.TO_BANK, bank, path, 0, BANK_SPEED, now);
		s.bankedFrom = foe;
		return true;
	}

	/** A villager kept on the bank it came out on, facing the foe across the water it has no way to: true while held. */
	private static boolean ashore(Mob m, State s, @Nullable LivingEntity foe, long now) {
		if (now >= s.ashoreUntil) {
			return false;
		}
		if (foe == null || foe != s.ashoreFoe || m.isInWater() || foe.isInWater() || m.hurtTime > 0 || m.distanceToSqr(foe) < 9.0) {
			s.ashoreUntil = 0L;
			return false;
		}
		if (now >= s.nextAshoreLook && PathProbes.take()) {
			s.nextAshoreLook = now + BANK_LOOK;
			Path to = PathProbes.to(m, foe, 0);
			if (to != null && to.canReach()) {
				// a way round to it after all: its brain takes it on
				s.ashoreUntil = 0L;
				return false;
			}
		}
		s.run = Run.ASHORE;
		s.dest = null;
		m.getNavigation().stop();
		m.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		return true;
	}

	/** Dry ground at the water's edge: the first along each of {@link #BANK_WAYS} ways out, the nearest (or, with a foe, the nearest it). */
	private static @Nullable BlockPos bank(ServerLevel level, Mob m, @Nullable LivingEntity foe) {
		Vec3 here = m.position();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (int w = 0; w < BANK_WAYS; w++) {
			double a = Math.PI * 2.0 * w / BANK_WAYS;
			for (int r = 1; r <= BANK_REACH; r++) {
				BlockPos column = BlockPos.containing(here.x + Math.cos(a) * r, here.y, here.z + Math.sin(a) * r);
				BlockPos dry = null;
				for (int dy : new int[] {0, 1, 2, -1}) {
					BlockPos p = column.offset(0, dy, 0);
					if (standable(level, p) && !level.getFluidState(p.below()).is(FluidTags.WATER) && !burns(level, p)) {
						dry = p;
						break;
					}
				}
				if (dry == null) {
					continue;
				}
				Vec3 at = Vec3.atBottomCenterOf(dry);
				double score = foe != null ? at.distanceToSqr(foe.position()) : at.distanceToSqr(here);
				if (score < bestScore) {
					bestScore = score;
					best = dry;
				}
				break;
			}
		}
		return best;
	}

	// ---- webs --------------------------------------------------------------------------------------------------------

	private static boolean webbed(ServerLevel level, Mob m) {
		return level.getBlockState(m.blockPosition()).is(Blocks.COBWEB) || level.getBlockState(m.blockPosition().above()).is(Blocks.COBWEB);
	}

	/** A blade or a sword: what cuts a web. */
	static boolean edged(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(ItemTags.SWORDS)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (String edge : EDGES) {
			if (path.contains(edge)) {
				return true;
			}
		}
		return false;
	}

	private static void web(ServerLevel level, Mob m, State s, long now) {
		if (!webbed(level, m)) {
			s.webSince = -1L;
			return;
		}
		InteractionHand hand = edged(m.getMainHandItem()) ? InteractionHand.MAIN_HAND : edged(m.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
		if (hand == null || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			s.webSince = -1L;
			return;
		}
		// (by game time: this can run twice a tick, or every other tick, as its goal is picked and run)
		if (s.webSince < 0L) {
			s.webSince = now;
		}
		if (now - s.webSince < WEB_CUT) {
			return;
		}
		s.webSince = -1L;
		m.swing(hand, SwingAnimation.DEFAULT, true);
		for (BlockPos p : new BlockPos[] {m.blockPosition(), m.blockPosition().above()}) {
			if (level.getBlockState(p).is(Blocks.COBWEB)) {
				// only the web, and nothing drops
				level.destroyBlock(p, false, m);
			}
		}
	}

	// ---- hooks -------------------------------------------------------------------------------------------------------

	/**
	 * For an MCA villager (from {@code Workers.step}, before {@link BreakOff#brainStep}): true to skip its brain this tick, this
	 * having its body.
	 */
	public static boolean brainStep(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		// (while it runs from a blast step lets go: CreeperFlight walks it through its walk target, which this would erase)
		boolean took = step(level, mob);
		if (took) {
			go(mob);
			if (mob instanceof Villager v) {
				v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			}
		}
		return took;
	}

	/** Danger sense for a mob with goals (folk, illagers): ahead of the fight's goals, it takes the legs only while it runs. */
	public static final class DangerGoal extends Goal {
		private final Mob mob;

		public DangerGoal(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return this.mob.level() instanceof ServerLevel level && step(level, this.mob);
		}

		@Override
		public boolean canContinueToUse() {
			// (a blast to run from: CreeperFlight's run, ahead of this, takes the legs)
			return running(this.mob) && !(this.mob instanceof PathfinderMob pm && CreeperFlight.fleeing(pm));
		}

		@Override
		public void start() {
			go(this.mob);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (this.mob.level() instanceof ServerLevel level && step(level, this.mob)) {
				go(this.mob);
			}
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
			State s = STATES.get(this.mob);
			if (s != null) {
				s.run = Run.NONE;
				s.dest = null;
				s.pending = null;
			}
		}
	}
}

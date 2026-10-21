package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.23) A settlement's guards clear away dead monsters (with Jaymo's Kitchen, whose corpses get back up or lie
 * about). A guard who isn't fighting goes to a monster's corpse in or near the settlement and carries it to the
 * nearest fire pit (fire burning on netherrack, soul sand or magma), lit campfire or lava, and throws it in. With none
 * near, they burn it where it lies, or first carry it a few blocks to bare ground if there's wood or anything else that
 * would catch beside it. A kingdom's guards are its {@link Folk} guards (or, with MCA, its MCA guards); a village's are
 * its iron golems, who only ever throw a corpse into a fire, never light one. Builders put up a fire pit
 * ({@code Buildings.firePit}) for them.
 * <p>
 * (0.5.48) Every iron golem now does it, wherever it is (a village's, a kingdom's, one a player built at home): a golem
 * that isn't fighting picks up a corpse lying near it, carries it to the nearest fire within {@link #GOLEM_PYRE_REACH}
 * (a fire pit, lit campfire or lava) and drops it in from close by. Monsters' corpses at once; any other (an animal,
 * a villager) once it has lain untouched {@link #SETTLE_TICKS}, so a kill you mean to butcher is yours first. Never a
 * player's corpse (their things stay safe), nor a heavy iron golem's (fire doesn't take it). Golems never light a fire,
 * and a fire to put out ({@link Firefighting}) calls them away from a corpse.
 * <p>
 * (0.7.54) A fire is only used if it's safe ({@link #isPyre}): out in the open with nothing that catches beside it, so
 * no corpse is dropped into a hearth in a wooden house again.
 */
public final class Undertakers {
	/** Corpses this close to a settlement's seat are cleared; guards this close to a corpse may answer it. */
	static final double SWEEP = 64.0, GUARD_REACH = 40.0;
	/** A fire pit, lit campfire or lava this close to a corpse is where it's taken. */
	static final int PYRE_REACH = 16, PYRE_DEPTH = 4;
	/** Bare ground (nothing that catches within {@link #CLEAR} blocks) is looked for this far from a corpse. */
	static final int SPOT_REACH = 12, CLEAR = 2;
	/** How often duties are walked (ticks), and new ones handed out; the most corpses taken on per settlement per round. */
	static final int STEP = 5, ASSIGN = 40, MOST_PER_ROUND = 2;
	/** Close enough to pick a corpse up or light it; to throw it into a pyre; to put it down on a spot. */
	static final double REACH = 2.2, THROW = 3.5, PUT_DOWN = 1.6;
	/** How long a duty may take before it's given up (ticks); a corpse given up on is left this long before it's tried again. */
	static final long GIVE_UP = 1200L, RETRY = 1200L;
	/** Walking speeds: a villager (its brain's scale), anything else (navigation). */
	static final float VILLAGER_SPEED = 0.6F;
	static final double SPEED = 1.0;
	/** Seconds a lit corpse is set burning for (Kitchen keeps it burning till it's ash once it's caught). */
	static final float BURN_SECONDS = 8.0F;
	/** (0.5.48) How far a golem looks for a corpse, and how far from it for a fire to drop it in. */
	static final double GOLEM_SIGHT = 24.0;
	static final int GOLEM_PYRE_REACH = 24;
	/** (0.5.48) How long anything but a monster's corpse lies untouched before a golem takes it (ticks: 5 minutes). */
	static final long SETTLE_TICKS = 6000L;
	/** (0.5.48) How close a golem carries a corpse to the fire before it lets go; the most golems sent out per round. */
	static final double DROP = 1.8;
	static final int GOLEMS_PER_ROUND = 4;
	/** (0.5.48) How long a guard called away to a fire is left alone before it's given another corpse (ticks). */
	static final long CALLED_AWAY = 200L;

	enum Stage { FETCH, HAUL, LIGHT }

	private static final class Duty {
		final UUID guard, corpse;
		/** Where it's taken: a pyre ({@code pyre}) or a bare spot; null to light it where it lies. */
		final @Nullable BlockPos to;
		final boolean pyre;
		final long until;
		Stage stage = Stage.FETCH;

		Duty(UUID guard, UUID corpse, @Nullable BlockPos to, boolean pyre, long until) {
			this.guard = guard;
			this.corpse = corpse;
			this.to = to;
			this.pyre = pyre;
			this.until = until;
		}
	}

	private static final Map<UUID, Duty> DUTIES = new HashMap<>();
	/** Corpses no plan could be made for (or a guard gave up on), and when they may be tried again. */
	private static final Map<UUID, Long> LEFT = new HashMap<>();
	/** (0.5.48) Corpses golems have seen lying free: when first, and when last (forgotten once out of sight or picked up). */
	private static final Map<UUID, long[]> SEEN = new HashMap<>();
	/** (0.5.48) Guards called away to a fire, and till when they're left to it. */
	private static final Map<UUID, Long> BUSY = new HashMap<>();

	private Undertakers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Undertakers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DUTIES.clear();
			LEFT.clear();
			SEEN.clear();
			BUSY.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (!KitchenCompat.loaded()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % STEP == 0 && !DUTIES.isEmpty()) {
			for (Iterator<Duty> it = DUTIES.values().iterator(); it.hasNext(); ) {
				if (!step(level, it.next(), now)) {
					it.remove();
				}
			}
		}
		if (now % ASSIGN == 0) {
			LEFT.values().removeIf(until -> until < now);
			BUSY.values().removeIf(until -> until < now);
			for (Faction f : Factions.all(server)) {
				if (!f.fallen() && f.type() != FactionType.BARBARIAN && level.isLoaded(f.seat())) {
					assign(level, f, now);
				}
			}
			golems(level, now);
		}
	}

	/**
	 * (0.5.48) {@code guard} is off to put out a fire ({@link Firefighting}): whatever corpse it has is put down where it
	 * stands, and it's given no other for a while.
	 */
	static void calledAway(ServerLevel level, PathfinderMob guard) {
		long now = level.getGameTime();
		BUSY.put(guard.getUUID(), now + CALLED_AWAY);
		Duty d = DUTIES.remove(guard.getUUID());
		if (d != null && level.getEntity(d.corpse) instanceof Entity corpse && KitchenCompat.carrier(corpse) == guard) {
			KitchenCompat.throwAt(corpse, guard.position());
		}
	}

	/** (0.5.48) Hands free iron golems, wherever they are, the corpses lying near them, to drop into a fire. */
	private static void golems(ServerLevel level, long now) {
		SEEN.values().removeIf(seen -> now - seen[1] > ASSIGN * 3L);
		int taken = 0;
		for (IronGolem golem : level.getEntities(EntityTypeTest.forClass(IronGolem.class), IronGolem::isAlive)) {
			boolean free = taken < GOLEMS_PER_ROUND && golem.getTarget() == null && !golem.isPassenger()
				&& !DUTIES.containsKey(golem.getUUID()) && !BUSY.containsKey(golem.getUUID());
			Entity best = null;
			double bestD = Double.MAX_VALUE;
			for (Entity corpse : level.getEntities((Entity)null, golem.getBoundingBox().inflate(GOLEM_SIGHT, 8.0, GOLEM_SIGHT), KitchenCompat::isBurnableCorpse)) {
				UUID id = corpse.getUUID();
				long[] seen = SEEN.computeIfAbsent(id, k -> new long[] {now, now});
				seen[1] = now;
				if (!free || LEFT.containsKey(id) || claimed(id) || Bearers.claimed(id) || Looters.claimed(id) || !KitchenCompat.isHostile(corpse) && now - seen[0] < SETTLE_TICKS) {
					continue;
				}
				double d = golem.distanceToSqr(corpse);
				if (d < bestD) {
					bestD = d;
					best = corpse;
				}
			}
			if (best == null) {
				continue;
			}
			BlockPos pyre = pyre(level, best.blockPosition(), GOLEM_PYRE_REACH);
			if (pyre == null) {
				// nothing to drop it in (and a golem never lights a fire): left a while
				LEFT.put(best.getUUID(), now + RETRY);
				continue;
			}
			DUTIES.put(golem.getUUID(), new Duty(golem.getUUID(), best.getUUID(), pyre, true, now + GIVE_UP));
			taken++;
			JaymosKingdoms.LOGGER.debug("an iron golem at {} takes the corpse at {} to the fire at {}", golem.blockPosition(), best.blockPosition(), pyre);
		}
	}

	/** Hands {@code f}'s free guards the monster corpses lying about it. */
	private static void assign(ServerLevel level, Faction f, long now) {
		AABB area = new AABB(f.seat()).inflate(SWEEP, 24.0, SWEEP);
		int taken = 0;
		for (Entity corpse : level.getEntities((Entity)null, area, KitchenCompat::isFreeMonsterCorpse)) {
			if (taken >= MOST_PER_ROUND) {
				return;
			}
			if (LEFT.containsKey(corpse.getUUID()) || claimed(corpse.getUUID()) || Bearers.claimed(corpse.getUUID()) || Looters.claimed(corpse.getUUID())) {
				continue;
			}
			// (0.5.48) iron golems see to corpses on their own (golems)
			PathfinderMob guard = freeGuard(level, f, corpse);
			if (guard == null) {
				// nobody free at all
				return;
			}
			BlockPos pyre = pyre(level, corpse.blockPosition(), PYRE_REACH);
			Duty duty;
			if (pyre != null) {
				duty = new Duty(guard.getUUID(), corpse.getUUID(), pyre, true, now + GIVE_UP);
			} else if (clear(level, corpse.blockPosition())) {
				duty = new Duty(guard.getUUID(), corpse.getUUID(), null, false, now + GIVE_UP);
			} else {
				BlockPos spot = spot(level, corpse.blockPosition());
				if (spot == null) {
					// nowhere it could burn without taking the town with it: left for a player
					LEFT.put(corpse.getUUID(), now + RETRY);
					continue;
				}
				duty = new Duty(guard.getUUID(), corpse.getUUID(), spot, false, now + GIVE_UP);
			}
			DUTIES.put(guard.getUUID(), duty);
			taken++;
			JaymosKingdoms.LOGGER.debug("a guard of {} clears the corpse at {} ({})", f.name(), corpse.blockPosition(),
				duty.to == null ? "burned where it lies" : duty.pyre ? "into the fire at " + duty.to : "burned at " + duty.to);
		}
	}

	/** (0.7.98) {@code mob} is carrying a corpse to the fire, or was called away to one. */
	static boolean busy(Entity mob) {
		return DUTIES.containsKey(mob.getUUID()) || BUSY.containsKey(mob.getUUID());
	}

	private static boolean claimed(UUID corpse) {
		for (Duty d : DUTIES.values()) {
			if (d.corpse.equals(corpse)) {
				return true;
			}
		}
		return false;
	}

	/** The nearest of {@code f}'s guards to {@code corpse} that isn't fighting, asleep, at a fire or already on a duty. */
	private static @Nullable PathfinderMob freeGuard(ServerLevel level, Faction f, Entity corpse) {
		AABB box = corpse.getBoundingBox().inflate(GUARD_REACH, 12.0, GUARD_REACH);
		List<PathfinderMob> guards = new ArrayList<>();
		for (Worker w : Workers.near(level, box, w -> w.role() == Folk.GUARD && w.factionId() == f.id())) {
			guards.add(w.mob());
		}
		PathfinderMob best = null;
		double bestD = Double.MAX_VALUE;
		for (PathfinderMob g : guards) {
			if (!g.isAlive() || g.getTarget() != null || g.isSleeping() || g.isBaby() || DUTIES.containsKey(g.getUUID()) || BUSY.containsKey(g.getUUID())
				|| Bearers.busy(g)) {
				continue;
			}
			double d = g.distanceToSqr(corpse);
			if (d < bestD) {
				bestD = d;
				best = g;
			}
		}
		return best;
	}

	/** Walks one duty on; false when it's done (or given up). */
	private static boolean step(ServerLevel level, Duty d, long now) {
		Entity corpse = level.getEntity(d.corpse);
		if (!(level.getEntity(d.guard) instanceof PathfinderMob guard) || !guard.isAlive() || corpse == null || corpse.isRemoved()) {
			return false;
		}
		boolean holding = KitchenCompat.carrier(corpse) == guard;
		if (guard.getTarget() != null || now > d.until) {
			// called away to a fight (or it's taking too long): put it down where they stand
			if (holding) {
				KitchenCompat.throwAt(corpse, guard.position());
			}
			if (now > d.until) {
				LEFT.put(d.corpse, now + RETRY);
			}
			return false;
		}
		switch (d.stage) {
			case FETCH -> {
				if (KitchenCompat.carrier(corpse) != null || corpse.isPassenger()) {
					// someone else got to it first
					return false;
				}
				if (guard.distanceToSqr(corpse) > REACH * REACH) {
					walk(guard, corpse.blockPosition());
					return true;
				}
				if (d.to == null) {
					d.stage = Stage.LIGHT;
					return true;
				}
				if (!KitchenCompat.carry(corpse, guard)) {
					// just let go of, or still tumbling: picked up next time round
					return true;
				}
				if (guard instanceof IronGolem) {
					// (0.5.48) the golem's arms swing down and take it up
					level.broadcastEntityEvent(guard, EntityEvent.START_ATTACKING);
				}
				d.stage = Stage.HAUL;
			}
			case HAUL -> {
				if (!holding) {
					// dropped (it slipped their arms): back to fetch it
					d.stage = Stage.FETCH;
					return true;
				}
				BlockPos to = d.to;
				// (0.5.48) a golem carries it right up to the fire and drops it in, unless it can get no nearer than a throw
				boolean golem = guard instanceof IronGolem;
				double reach = golem ? DROP : d.pyre ? THROW : PUT_DOWN;
				double dist = horizontalDistSqr(guard, to);
				if (dist > reach * reach && !(golem && dist <= THROW * THROW && guard.getNavigation().isDone())) {
					walk(guard, to);
					return true;
				}
				guard.getNavigation().stop();
				guard.getLookControl().setLookAt(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5);
				if (golem) {
					level.broadcastEntityEvent(guard, EntityEvent.START_ATTACKING);
				} else {
					guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				}
				KitchenCompat.throwAt(corpse, Vec3.atBottomCenterOf(to));
				if (d.pyre) {
					// into the fire: it catches there (Kitchen lights a corpse lying in flames or lava)
					return false;
				}
				d.stage = Stage.LIGHT;
			}
			case LIGHT -> {
				if (guard.distanceToSqr(corpse) > (REACH + 1.0) * (REACH + 1.0)) {
					walk(guard, corpse.blockPosition());
					return true;
				}
				if (!corpse.onGround()) {
					// still falling from their arms
					return true;
				}
				guard.getNavigation().stop();
				guard.getLookControl().setLookAt(corpse);
				guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				corpse.igniteForSeconds(BURN_SECONDS);
				level.playSound(null, corpse.blockPosition(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.NEUTRAL, 1.0F,
					0.9F + level.getRandom().nextFloat() * 0.2F);
				return false;
			}
		}
		return true;
	}

	private static double horizontalDistSqr(Entity e, BlockPos pos) {
		double dx = e.getX() - (pos.getX() + 0.5), dz = e.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz;
	}

	static void walk(PathfinderMob guard, BlockPos to) {
		if (guard instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, to, VILLAGER_SPEED, 1);
		} else {
			guard.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, SPEED);
		}
	}

	/** The nearest fire pit, lit campfire or lava within {@code reach} of {@code at}, or null. */
	static @Nullable BlockPos pyre(ServerLevel level, BlockPos at, int reach) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-reach, -PYRE_DEPTH, -reach), at.offset(reach, PYRE_DEPTH, reach))) {
			if (!level.isLoaded(pos) || !isPyre(level, pos)) {
				continue;
			}
			double d = pos.distSqr(at);
			if (d < bestD) {
				bestD = d;
				best = pos.immutable();
			}
		}
		return best;
	}

	/**
	 * Fire burning on something that burns forever (a fire pit), a lit campfire, or lava open to the sky above it.
	 * (0.7.54) Only a safe one: out under the sky (never a hearth indoors) with nothing that catches within
	 * {@link #CLEAR} blocks, since a burning corpse lights what it lies by and burned villages down.
	 */
	static boolean isPyre(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		boolean burning;
		if (state.is(BlockTags.FIRE)) {
			burning = level.getBlockState(pos.below()).is(BlockTags.INFINIBURN_OVERWORLD);
		} else if (state.is(BlockTags.CAMPFIRES)) {
			burning = state.getValueOrElse(CampfireBlock.LIT, false);
		} else {
			burning = state.getFluidState().is(FluidTags.LAVA) && state.getFluidState().isSource() && level.getBlockState(pos.above()).isAir();
		}
		return burning && level.canSeeSky(pos.above()) && safe(level, pos);
	}

	/** (0.7.54) Nothing that catches within {@link #CLEAR} blocks of a fire (other fires and campfires aside). */
	private static boolean safe(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-CLEAR, -1, -CLEAR), pos.offset(CLEAR, 2, CLEAR))) {
			BlockState s = level.getBlockState(p);
			if (s.ignitedByLava() && !s.is(BlockTags.CAMPFIRES) && !s.is(BlockTags.FIRE)) {
				return false;
			}
		}
		return true;
	}

	/** Nothing that catches (wood, wool, leaves, grass...) within {@link #CLEAR} blocks of {@code pos}. */
	static boolean clear(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-CLEAR, -1, -CLEAR), pos.offset(CLEAR, 2, CLEAR))) {
			if (level.getBlockState(p).ignitedByLava()) {
				return false;
			}
		}
		return true;
	}

	/** The nearest bare ground to {@code from} (within {@link #SPOT_REACH}) where a corpse can burn safely, or null. */
	static @Nullable BlockPos spot(ServerLevel level, BlockPos from) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -SPOT_REACH; dx <= SPOT_REACH; dx++) {
			for (int dz = -SPOT_REACH; dz <= SPOT_REACH; dz++) {
				int x = from.getX() + dx, z = from.getZ() + dz;
				BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				if (Math.abs(pos.getY() - from.getY()) > 3 || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
					|| !level.getFluidState(pos).isEmpty()) {
					continue;
				}
				double d = pos.distSqr(from);
				if (d < bestD && clear(level, pos)) {
					bestD = d;
					best = pos;
				}
			}
		}
		return best;
	}
}

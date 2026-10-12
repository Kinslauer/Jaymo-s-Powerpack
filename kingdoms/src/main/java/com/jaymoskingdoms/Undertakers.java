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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.21) A settlement's guards clear away dead monsters (with Jaymo's Kitchen, whose corpses get back up or lie
 * about). A guard who isn't fighting goes to a monster's corpse in or near the settlement and carries it to the
 * nearest fire pit (fire burning on netherrack, soul sand or magma), lit campfire or lava, and throws it in. With none
 * near, they burn it where it lies, or first carry it a few blocks to bare ground if there's wood or anything else that
 * would catch beside it. A kingdom's guards are its {@link Folk} guards; a village's are
 * its iron golems, who only ever throw a corpse into a fire, never light one. Builders put up a fire pit
 * ({@code Buildings.firePit}) for them.
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

	private Undertakers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Undertakers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DUTIES.clear();
			LEFT.clear();
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
			for (Faction f : Factions.all(server)) {
				if (!f.fallen() && f.type() != FactionType.BARBARIAN && level.isLoaded(f.seat())) {
					assign(level, f, now);
				}
			}
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
			if (LEFT.containsKey(corpse.getUUID()) || claimed(corpse.getUUID())) {
				continue;
			}
			BlockPos pyre = pyre(level, corpse.blockPosition());
			// an iron golem can carry a body to the fire, but can't light one
			PathfinderMob guard = freeGuard(level, f, corpse, pyre != null);
			if (guard == null) {
				if (pyre != null) {
					// nobody free at all
					return;
				}
				// only golems about, and nothing to throw it in: left a while
				LEFT.put(corpse.getUUID(), now + RETRY);
				continue;
			}
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

	private static boolean claimed(UUID corpse) {
		for (Duty d : DUTIES.values()) {
			if (d.corpse.equals(corpse)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The nearest of {@code f}'s guards to {@code corpse} that isn't fighting, asleep or already on a duty; a village's
	 * iron golems only if {@code golems} (there's a fire to throw it in).
	 */
	private static @Nullable PathfinderMob freeGuard(ServerLevel level, Faction f, Entity corpse, boolean golems) {
		AABB box = corpse.getBoundingBox().inflate(GUARD_REACH, 12.0, GUARD_REACH);
		List<PathfinderMob> guards = new ArrayList<>();
		if (golems && f.type() == FactionType.VILLAGE) {
			guards.addAll(level.getEntitiesOfClass(IronGolem.class, box, IronGolem::isAlive));
		}
		guards.addAll(level.getEntitiesOfClass(Folk.class, box, g -> g.role() == Folk.GUARD && g.factionId() == f.id()));
		PathfinderMob best = null;
		double bestD = Double.MAX_VALUE;
		for (PathfinderMob g : guards) {
			if (!g.isAlive() || g.getTarget() != null || g.isSleeping() || g.isBaby() || DUTIES.containsKey(g.getUUID())) {
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
				d.stage = Stage.HAUL;
			}
			case HAUL -> {
				if (!holding) {
					// dropped (it slipped their arms): back to fetch it
					d.stage = Stage.FETCH;
					return true;
				}
				BlockPos to = d.to;
				double reach = d.pyre ? THROW : PUT_DOWN;
				if (horizontalDistSqr(guard, to) > reach * reach) {
					walk(guard, to);
					return true;
				}
				guard.getLookControl().setLookAt(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5);
				guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
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

	private static void walk(PathfinderMob guard, BlockPos to) {
		if (guard instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, to, VILLAGER_SPEED, 1);
		} else {
			guard.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, SPEED);
		}
	}

	/** The nearest fire pit, lit campfire or lava to {@code at}, or null. */
	private static @Nullable BlockPos pyre(ServerLevel level, BlockPos at) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-PYRE_REACH, -PYRE_DEPTH, -PYRE_REACH), at.offset(PYRE_REACH, PYRE_DEPTH, PYRE_REACH))) {
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

	/** Fire burning on something that burns forever (a fire pit), a lit campfire, or lava open to the sky above it. */
	private static boolean isPyre(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(BlockTags.FIRE)) {
			return level.getBlockState(pos.below()).is(BlockTags.INFINIBURN_OVERWORLD);
		}
		if (state.is(BlockTags.CAMPFIRES)) {
			return state.getValueOrElse(CampfireBlock.LIT, false);
		}
		return state.getFluidState().is(FluidTags.LAVA) && state.getFluidState().isSource() && level.getBlockState(pos.above()).isAir();
	}

	/** Nothing that catches (wood, wool, leaves, grass...) within {@link #CLEAR} blocks of {@code pos}. */
	private static boolean clear(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-CLEAR, -1, -CLEAR), pos.offset(CLEAR, 2, CLEAR))) {
			if (level.getBlockState(p).ignitedByLava()) {
				return false;
			}
		}
		return true;
	}

	/** The nearest bare ground to {@code from} (within {@link #SPOT_REACH}) where a corpse can burn safely, or null. */
	private static @Nullable BlockPos spot(ServerLevel level, BlockPos from) {
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

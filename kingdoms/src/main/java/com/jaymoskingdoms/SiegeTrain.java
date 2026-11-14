package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-14): "can we have warring kingdoms bring siege engines?" (after Siegeworks, whose art is all rights
 * reserved: our own engines, code and models). A realm's war party ({@link WarParties}) with soldiers enough brings a
 * siege train: a {@link Mangonel}, or from a realm rich enough to keep guns ({@link Muskets#ARM_TREASURY}) a field
 * {@link Cannon}; a great war's party from such a realm brings both. Each engine takes {@link #CREW} of the party's
 * soldiers, who push it behind the march ({@link Haul}), set it down short of the enemy's seat (a mangonel
 * {@link #MANGONEL_STANDOFF} off, to throw over the walls; a gun {@link #CANNON_STANDOFF} off, where it can see) and
 * work it through the siege, then push it home. Kill its crew and it stands silent where it was left; break it and it's
 * gone. Engines aren't saved, as parties aren't.
 */
final class SiegeTrain {
	/** Soldiers pushing each engine, and how many the party keeps marching beside them. */
	static final int CREW = 2, MARCHERS_KEPT = 3;
	/** How far short of the enemy's seat each engine is set down (blocks). */
	static final double MANGONEL_STANDOFF = 34.0, CANNON_STANDOFF = 20.0;
	/** Where engines muster: this far behind the soldiers, this far apart. */
	static final double MUSTER_BEHIND = 4.0, MUSTER_APART = 4.0;
	/** Its crew push from this far behind its middle, this far to either side (blocks); set down, they keep within this of it. */
	static final double PUSH_BEHIND = 1.9, PUSH_ASIDE = 0.7;
	static final int CREW_KEEP = 4;
	/** An engine looks this far up and down for its mark (blocks). */
	static final double MARK_HEIGHT = 24.0;

	/** Every soldier crewing an engine now. In memory, as the parties are. */
	private static final Set<UUID> CREWS = new HashSet<>();

	private SiegeTrain() {
	}

	/** {@code mob} crews a party's engine. */
	static boolean crew(Entity mob) {
		return CREWS.contains(mob.getUUID());
	}

	static @Nullable Haul haul(@Nullable Entity engine) {
		return engine instanceof Mangonel m ? m.haul : engine instanceof Cannon c ? c.haul : null;
	}

	// ---- mustering ---------------------------------------------------------------------------------------------------

	/** {@code party}'s engines, set down behind its soldiers at {@code muster} with their crews. */
	static void muster(ServerLevel level, WarParties.Party party, Faction from, Faction target, BlockPos muster, boolean great) {
		if (!from.type().isRealm()) {
			return;
		}
		List<PathfinderMob> hands = new ArrayList<>();
		for (UUID id : party.soldiers) {
			if (level.getEntity(id) instanceof PathfinderMob m && !m.isPassenger() && !m.entityTags().contains(WarParties.CASTER_TAG)) {
				hands.add(m);
			}
		}
		boolean guns = Muskets.afford(from, Muskets.ARM_TREASURY);
		boolean[] cannon = guns && great ? new boolean[] {true, false} : new boolean[] {guns};
		double dx = muster.getX() - target.seat().getX(), dz = muster.getZ() - target.seat().getZ();
		double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		float yaw = (float) (Mth.atan2(dx, -dz) * Mth.RAD_TO_DEG);
		for (int i = 0; i < cannon.length; i++) {
			if (hands.size() < CREW || party.soldiers.size() - party.crew.size() - CREW < MARCHERS_KEPT) {
				return;
			}
			double side = (i == 0 ? 1.0 : -1.0) * MUSTER_APART;
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(
				muster.getX() + dx / len * MUSTER_BEHIND - dz / len * side, muster.getY(), muster.getZ() + dz / len * MUSTER_BEHIND + dx / len * side));
			if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()) {
				continue;
			}
			Entity engine;
			if (cannon[i]) {
				Cannon c = Cannon.place(level, from, at, yaw);
				c.haul = new Haul();
				engine = c;
			} else {
				Mangonel m = new Mangonel(Mangonel.TYPE, level);
				m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
				level.addFreshEntity(m);
				engine = m;
			}
			Haul haul = haul(engine);
			for (int n = 0; n < CREW; n++) {
				PathfinderMob hand = hands.removeLast();
				haul.crew.add(hand.getUUID());
				party.crew.add(hand.getUUID());
				CREWS.add(hand.getUUID());
			}
			party.engines.add(engine.getUUID());
		}
	}

	// ---- driving -----------------------------------------------------------------------------------------------------

	/** Each of {@code party}'s engines pushed on toward the siege (or home), or worked where it's set down. */
	static void drive(ServerLevel level, WarParties.Party party, @Nullable Faction from, @Nullable Faction target) {
		boolean home = party.phase == WarParties.Phase.RETURN || target == null;
		for (UUID id : List.copyOf(party.engines)) {
			Entity engine = level.getEntity(id);
			Haul haul = haul(engine);
			if (haul == null || engine.isRemoved()) {
				// broken, or its land unloaded: its crew fight on with the rest
				party.engines.remove(id);
				continue;
			}
			List<PathfinderMob> crew = haul.crew(level);
			if (crew.isEmpty()) {
				if (home) {
					// its crew gone home out of sight: so is it
					party.engines.remove(id);
					poof(level, engine);
				}
				continue;
			}
			BlockPos goal = home ? from == null ? null : from.seat() : target.seat();
			if (goal == null) {
				haul.to = null;
				continue;
			}
			double standoff = home ? WarParties.HOME_REACH : engine instanceof Cannon ? CANNON_STANDOFF : MANGONEL_STANDOFF;
			double dx = goal.getX() + 0.5 - engine.getX(), dz = goal.getZ() + 0.5 - engine.getZ();
			double far = Math.sqrt(dx * dx + dz * dz);
			if (far <= standoff) {
				haul.to = null;
				if (!home) {
					work(engine, crew);
				}
				continue;
			}
			haul.to = new Vec3(goal.getX() + 0.5, engine.getY(), goal.getZ() + 0.5);
			engine.ejectPassengers();
			for (int i = 0; i < crew.size(); i++) {
				PathfinderMob hand = crew.get(i);
				hand.setHomeTo(engine.blockPosition(), CREW_KEEP);
				if (Rulers.target(hand) != null) {
					continue;
				}
				double side = (i % 2 == 0 ? 1.0 : -1.0) * PUSH_ASIDE;
				double x = engine.getX() - dx / far * PUSH_BEHIND - dz / far * side, z = engine.getZ() - dz / far * PUSH_BEHIND + dx / far * side;
				if (hand.distanceToSqr(x, engine.getY(), z) > 1.0) {
					hand.getNavigation().moveTo(x, engine.getY(), z, WarParties.MARCH_SPEED);
				}
			}
		}
	}

	/** Set down: its crew keep by it, and a gun's first hand sits to it (a mangonel's works it standing: {@link Mangonel#tick}). */
	private static void work(Entity engine, List<PathfinderMob> crew) {
		for (PathfinderMob hand : crew) {
			hand.setHomeTo(engine.blockPosition(), CREW_KEEP);
		}
		PathfinderMob first = crew.getFirst();
		if (engine instanceof Cannon && engine.getPassengers().isEmpty() && !first.isPassenger() && first.distanceToSqr(engine) < Haul.PUSH_REACH * Haul.PUSH_REACH) {
			first.startRiding(engine);
		}
	}

	/** {@code mob} is pushing an engine of {@code party}'s along now (so the party walks him, not his own mind). */
	static boolean pushing(ServerLevel level, WarParties.Party party, PathfinderMob mob) {
		Haul haul = serving(level, party, mob);
		return haul != null && haul.to != null;
	}

	/** The engine of {@code party}'s that {@code mob} crews, while it still stands; else null (he marches with the rest). */
	static @Nullable Haul serving(ServerLevel level, WarParties.Party party, PathfinderMob mob) {
		if (!party.crew.contains(mob.getUUID())) {
			return null;
		}
		for (UUID id : party.engines) {
			Haul haul = haul(level.getEntity(id));
			if (haul != null && haul.crew.contains(mob.getUUID())) {
				return haul;
			}
		}
		return null;
	}

	/** {@code party} is over. Its engines go with it if {@code gone} (it went home); else they stand where they were left. */
	static void end(ServerLevel level, WarParties.Party party, boolean gone) {
		CREWS.removeAll(party.crew);
		party.crew.clear();
		if (gone) {
			for (UUID id : party.engines) {
				Entity engine = level.getEntity(id);
				if (engine != null) {
					poof(level, engine);
				}
			}
		}
		party.engines.clear();
	}

	private static void poof(ServerLevel level, Entity engine) {
		level.sendParticles(ParticleTypes.POOF, engine.getX(), engine.getY() + 1.0, engine.getZ(), 12, 0.6, 0.6, 0.6, 0.02);
		engine.ejectPassengers();
		engine.discard();
	}

	static void clear() {
		CREWS.clear();
	}

	// ---- marks and harm ----------------------------------------------------------------------------------------------

	/**
	 * What an engine at {@code from} worked by {@code operator} shoots at: his own foe if it's between {@code near} and
	 * {@code far} blocks off, else the nearest enemy fighter (a guard or archer of a realm his is at war with, or a
	 * player his realm counts a foe) in that band; or null.
	 */
	static @Nullable LivingEntity mark(ServerLevel level, PathfinderMob operator, Vec3 from, double near, double far) {
		LivingEntity foe = Rulers.target(operator);
		if (foe != null && foe.isAlive() && inBand(from, foe, near, far)) {
			return foe;
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(far, MARK_HEIGHT, far),
			e -> e.isAlive() && !e.isSpectator() && inBand(from, e, near, far))) {
			boolean fighter = e instanceof Player ? McaCompat.foe(operator, e) : e instanceof Worker w && (w.role() == Folk.GUARD || w.role() == Folk.ARCHER);
			double d = e.distanceToSqr(from);
			if (fighter && d < bestD && harms(operator, e)) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	private static boolean inBand(Vec3 from, Entity e, double near, double far) {
		double dx = e.getX() - from.x, dz = e.getZ() - from.z;
		double flat = dx * dx + dz * dz;
		return flat >= near * near && flat <= far * far;
	}

	/**
	 * Whether a siege engine's shot, worked by {@code thrower}, may hurt {@code victim}: monsters, the fighters and folk
	 * of a realm his is at war with, and players not sworn to his own; never his own realm's people, nor a town's
	 * ordinary villagers and golems.
	 */
	static boolean harms(PathfinderMob thrower, LivingEntity victim) {
		if (victim == thrower || !(thrower instanceof Worker w) || !(thrower.level() instanceof ServerLevel level)) {
			return false;
		}
		if (victim instanceof Player p) {
			Faction theirs = Factions.of(level.getServer(), p.getUUID());
			return !p.isCreative() && !p.isSpectator() && (theirs == null || theirs.id() != w.factionId());
		}
		if (victim instanceof Worker o) {
			return o.factionId() != w.factionId()
				&& (McaCompat.foe(thrower, victim) || thrower instanceof Folk a && victim instanceof Folk b && !a.sameSide(b));
		}
		return victim instanceof Enemy;
	}

	/** Client side: how far {@code engine} rolled forward since it stood at {@code was} (blocks). */
	static float rolled(Entity engine, Vec3 was) {
		Vec3 forward = Vec3.directionFromRotation(0.0F, engine.getYRot());
		return (float) ((engine.getX() - was.x) * forward.x + (engine.getZ() - was.z) * forward.z);
	}
}

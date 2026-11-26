package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Andy (2026-11-26, a clip of a barbarian camp packed into a knot round its fire): "mobs seem to crowd like this for whatever
 * reason. this is an unnatural behavior and should be prevented."
 * <p>
 * The cause: when people fight people, every fighter on a side walks straight at its foe, and nothing tells the fifth or
 * the eighth man that there is no room left. A band that outnumbers its foes is even ordered to pick afresh
 * ({@link BandLeaders}, "Spread out, take them!"), and most pick the same few. Powers' {@code Crowds} only ever made foes
 * of a <i>player</i> take turns, and only the goal-driven ones; MCA's villagers (a camp's raiders, a realm's guards) fight
 * through their brains and never waited at all.
 * <p>
 * So a fight among people takes turns too: at most {@link #ACTIVE} (more round something big, {@link #slots}) come at one
 * foe hand to hand; the rest hold off on a ring round the fight ({@link #RING}), each with elbow room
 * ({@link #ELBOW}), facing it and shifting round, until a turn ({@link #TURN_TICKS}) is up or one in front falls back.
 * Archers, casters, bosses and anything too big to circle never wait; one struck while it waits fights back at once.
 * Goal-driven fighters stand still in {@code MeleeAttackGoalMixin} and hold their blows in {@link MobCombat}; MCA's
 * villagers through {@link #brainStep} (their brain paused while they wait). A player's own foes are Powers' to order;
 * here only the MCA villagers among them, whom Powers can't move, are given turns, in the room Powers leaves.
 */
public final class Turns {
	/** At most this many come at one man-sized foe at once; one more round a foe this wide, and one more round one this wide. */
	static final int ACTIVE = 3;
	static final double WIDE = 1.0, HUGE = 1.8;
	/** Fighters this near their foe count toward its crowd (blocks). */
	static final double RANGE = 14.0;
	/** Those waiting keep about this far from the foe; nearer than this they back off (blocks). */
	static final double RING = 5.0, RING_IN = 3.8;
	/** A waiting fighter keeps this far from any other fighter where it stands (blocks). */
	static final double ELBOW = 1.6;
	/** How far round the ring (degrees) a waiting fighter shifts each repath, and how often it repaths (ticks). */
	static final double DRIFT = 20.0;
	static final int REPATH = 20;
	/** A turn lasts this long (ticks) while others wait. */
	static final int TURN_TICKS = 100;
	/** Struck this recently (ticks) by someone in reach, a waiting fighter fights back instead. */
	static final int HIT_BACK = 40;
	static final double HIT_REACH = 3.5;
	/** Looked at this often (ticks). */
	static final int STEP = 5;
	/** Anything this healthy is a boss: it never waits. Anything this wide or wider can't circle. */
	static final float BOSS_HEALTH = 100.0F;
	static final double TOO_WIDE = 1.4;
	/** Walking pace round the ring. */
	static final double PACE = 0.8;
	/** The tag Powers' Crowds puts on a foe waiting its turn at a player. */
	static final String POWERS_WAITING = "jaymospowers.waiting_turn";

	/** When each fighter's turn began. */
	private static final Map<Mob, Long> TURNS = new WeakHashMap<>();
	/** Who is waiting now, and on whom. */
	private static final Map<Mob, LivingEntity> WAITING = new WeakHashMap<>();

	private Turns() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Turns::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TURNS.clear();
			WAITING.clear();
		});
	}

	/** {@code m} is waiting its turn in a fight among people (not one of Powers' crowds round a player). */
	public static boolean waiting(Mob m) {
		return WAITING.containsKey(m);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % STEP != 0) {
			return;
		}
		Set<Mob> waitingNow = Collections.newSetFromMap(new IdentityHashMap<>());
		for (ServerLevel level : server.getAllLevels()) {
			long now = level.getGameTime();
			Map<LivingEntity, List<Mob>> crowds = new IdentityHashMap<>();
			for (Mob m : Comrades.fighters(level)) {
				LivingEntity foe = Comrades.target(m);
				if (foe != null && melee(m) && m.level() == level && m.distanceToSqr(foe) < RANGE * RANGE) {
					crowds.computeIfAbsent(foe, f -> new ArrayList<>()).add(m);
				}
			}
			for (Map.Entry<LivingEntity, List<Mob>> e : crowds.entrySet()) {
				order(level, e.getKey(), e.getValue(), now, waitingNow);
			}
		}
		for (Mob m : List.copyOf(WAITING.keySet())) {
			if (!waitingNow.contains(m)) {
				WAITING.remove(m);
			}
		}
	}

	/** One foe's crowd: the first few keep (or take) their turn, the rest wait on the ring. */
	private static void order(ServerLevel level, LivingEntity foe, List<Mob> crowd, long now, Set<Mob> waitingNow) {
		int slots = slots(foe);
		if (foe instanceof Player) {
			// Powers orders a player's goal-driven foes (Crowds): its villagers wait in what room they leave
			int goalDriven = 0;
			for (Mob m : crowd) {
				if (!(m instanceof Villager)) {
					goalDriven++;
				}
			}
			crowd.removeIf(m -> !(m instanceof Villager));
			slots = Math.max(1, slots - goalDriven);
		}
		if (crowd.size() <= slots) {
			return;
		}
		crowd.sort(Comparator.<Mob>comparingInt(m -> {
			Long since = TURNS.get(m);
			return since != null && now - since < TURN_TICKS ? 0 : since == null ? 1 : 2;
		}).thenComparingDouble(m -> m.distanceToSqr(foe)));
		List<Mob> holding = new ArrayList<>();
		for (int i = 0; i < crowd.size(); i++) {
			Mob m = crowd.get(i);
			if (i < slots || struck(m)) {
				TURNS.putIfAbsent(m, now);
				if (WAITING.containsKey(m)) {
					// (its turn again: a fresh one)
					TURNS.put(m, now);
				}
				continue;
			}
			if (TURNS.containsKey(m) && now - TURNS.get(m) >= TURN_TICKS) {
				// (its turn is over: to the back, the next one fresh)
				TURNS.remove(m);
			}
			if (!WAITING.containsKey(m) && m instanceof PathfinderMob pm) {
				pm.getNavigation().stop();
			}
			WAITING.put(m, foe);
			waitingNow.add(m);
			holding.add(m);
		}
		for (Mob m : holding) {
			circle(m, foe, crowd, now);
		}
	}

	/** How many come at {@code foe} at once: more round something big. */
	static int slots(LivingEntity foe) {
		double w = foe.getBbWidth();
		return ACTIVE + (w >= WIDE ? 1 : 0) + (w >= HUGE ? 1 : 0);
	}

	/** A fighter that fights hand to hand and can circle: not an archer or caster, not a boss, not too big, not riding. */
	private static boolean melee(Mob m) {
		return m instanceof PathfinderMob && !(m instanceof RangedAttackMob) && !Comrades.ranged(m) && m.getBbWidth() < TOO_WIDE
			&& m.getMaxHealth() < BOSS_HEALTH && !m.isPassenger() && !Comrades.busy(m) && !Downed.isDowned(m);
	}

	/** Struck a moment ago by someone in reach: it hits back rather than wait. */
	private static boolean struck(Mob m) {
		LivingEntity by = m.getLastHurtByMob();
		return by != null && by.isAlive() && m.tickCount - m.getLastHurtByMobTimestamp() < HIT_BACK && m.distanceToSqr(by) < HIT_REACH * HIT_REACH;
	}

	/** A waiting fighter keeps its distance round the ring, out of the others' way, watching the fight. */
	private static void circle(Mob m, LivingEntity foe, List<Mob> crowd, long now) {
		m.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		if (!(m instanceof PathfinderMob pm)) {
			return;
		}
		double d = m.distanceTo(foe);
		boolean crowded = false;
		for (Mob other : crowd) {
			if (other != m && other.distanceToSqr(m) < ELBOW * ELBOW) {
				crowded = true;
				break;
			}
		}
		if ((now + m.getId()) % REPATH >= STEP && d >= RING_IN && !crowded && !pm.getNavigation().isDone()) {
			return;
		}
		Vec3 from = m.position().subtract(foe.position());
		double bearing = from.horizontalDistanceSqr() < 1.0E-4 ? m.getRandom().nextDouble() * Math.PI * 2.0 : Math.atan2(from.z, from.x);
		double side = m.getUUID().getLeastSignificantBits() % 2 == 0 ? 1.0 : -1.0;
		// round the ring a little, and further while the spot it would take is someone else's
		for (int tries = 0; tries < 8; tries++) {
			double angle = bearing + Math.toRadians(DRIFT * (tries + 1)) * side;
			Vec3 to = foe.position().add(Math.cos(angle) * RING, 0.0, Math.sin(angle) * RING);
			if (free(m, to, crowd) || tries == 7) {
				pm.getNavigation().moveTo(to.x, foe.getY(), to.z, PACE);
				return;
			}
		}
	}

	private static boolean free(Mob m, Vec3 spot, List<Mob> crowd) {
		for (Mob other : crowd) {
			if (other != m && other.position().subtract(spot).horizontalDistanceSqr() < ELBOW * ELBOW) {
				return false;
			}
		}
		return true;
	}

	/**
	 * For an MCA villager (from {@code Workers.step}): true while it waits its turn, here or in one of Powers' crowds round
	 * a player; its brain waits (it would only walk back into the press) and the ring is walked for it.
	 */
	public static boolean brainStep(Mob m) {
		if (WAITING.containsKey(m)) {
			return true;
		}
		return m.entityTags().contains(POWERS_WAITING);
	}
}

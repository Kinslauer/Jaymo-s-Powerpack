package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.raid.Raider;
import org.jspecify.annotations.Nullable;

/**
 * A band's leader gives the same four orders a player gives their companions (docs/Pawn-Plan.md row E1, foe side):
 * shouted aloud ({@link Callouts}, {@code lead.*}), and the band visibly does it.
 * <ul>
 * <li><b>Help</b>: one of the band is down and nobody has them: the nearest of the band goes to get them up
 * ({@link FieldMedics#send}).</li>
 * <li><b>Come</b>: the leader is badly hurt ({@link #HURT_SHARE}): the band turns on whoever is hurting him.</li>
 * <li><b>Go</b>: the band outnumbers its foes: everyone picks afresh by their own leanings and spreads out.</li>
 * </ul>
 * A leader who is down or dead gives no orders; when one goes down a man of his band shouts it ({@code leaderdown}) and
 * the rest fight each by their own leanings. Leaders: a camp's chief, goblin king or orc warchief, a folk role that leads,
 * a captain ({@link Captains}), an illager warlord, a raid's captain.
 */
public final class BandLeaders {
	/** A band is the leader's side within this (blocks); a leader orders no oftener than this (ticks); looked at this often (ticks). */
	static final double BAND = 20.0;
	static final int ORDER_GAP = 200, EVERY = 40;
	/** A leader under this share of his health calls his band to him. */
	static final float HURT_SHARE = 0.4F;

	private static final Map<UUID, Long> ORDERED = new HashMap<>();
	/** Leaders seen fighting: their side, for the call when they go down. */
	private static final Map<UUID, String> LEADING = new HashMap<>();
	private static final Map<UUID, Mob> LEADERS = new HashMap<>();

	private BandLeaders() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ORDERED.clear();
			LEADING.clear();
			LEADERS.clear();
		});
	}

	/** {@code m} leads a band. */
	static boolean isLeader(LivingEntity m) {
		if (m instanceof Folk folk && folk.leader()) {
			return true;
		}
		if (m instanceof Worker w && CampChiefs.isRole(w.role())) {
			return true;
		}
		return Captains.is(m) || Illagers.warlord(m) || m instanceof Raider r && r.isCaptain() || RivalParties.leads(m);
	}

	/** The leader of {@code m}'s band near it, or null. */
	static @Nullable LivingEntity leaderOf(ServerLevel level, Mob m, String side) {
		if (isLeader(m)) {
			return null;
		}
		LivingEntity best = null;
		double bestD = BAND * BAND;
		for (Mob l : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(BAND), l -> l != m && l.isAlive() && isLeader(l))) {
			double d = l.distanceToSqr(m);
			if (d < bestD && Comrades.side(l).equals(side)) {
				bestD = d;
				best = l;
			}
		}
		return best;
	}

	/** From {@link Comrades}' tick: every leader in a fight may give an order. */
	static void tick(ServerLevel level, List<Mob> fighters, int now) {
		if (now % EVERY != 0) {
			return;
		}
		fallen(level);
		long time = level.getGameTime();
		for (Mob leader : fighters) {
			if (!isLeader(leader)) {
				continue;
			}
			String side = Comrades.side(leader);
			LEADING.put(leader.getUUID(), side);
			LEADERS.put(leader.getUUID(), leader);
			Long last = ORDERED.get(leader.getUUID());
			if (last != null && time - last < ORDER_GAP) {
				continue;
			}
			List<Mob> band = new ArrayList<>();
			for (Mob m : fighters) {
				if (m != leader && m.distanceToSqr(leader) < BAND * BAND && Comrades.side(m).equals(side)) {
					band.add(m);
				}
			}
			if (band.isEmpty()) {
				continue;
			}
			if (help(level, leader, side, band) || come(leader, band) || go(level, leader, side, band)) {
				ORDERED.put(leader.getUUID(), time);
			}
		}
	}

	private static boolean help(ServerLevel level, Mob leader, String side, List<Mob> band) {
		for (Mob body : level.getEntitiesOfClass(Mob.class, leader.getBoundingBox().inflate(BAND),
			b -> b.isAlive() && Downed.isDowned(b) && !FieldMedics.claimed(b) && !Downed.tended(b) && Comrades.side(b).equals(side))) {
			PathfinderMob nearest = null;
			double best = Double.MAX_VALUE;
			for (Mob m : band) {
				if (m instanceof PathfinderMob pm && !FieldMedics.busy(pm) && m.distanceToSqr(body) < best) {
					best = m.distanceToSqr(body);
					nearest = pm;
				}
			}
			if (nearest != null) {
				Callouts.say(leader, "lead.help", body, true, body.getDisplayName());
				FieldMedics.send(nearest, body);
				return true;
			}
		}
		return false;
	}

	private static boolean come(Mob leader, List<Mob> band) {
		LivingEntity hurter = leader.getLastHurtByMob();
		if (leader.getHealth() >= leader.getMaxHealth() * HURT_SHARE || hurter == null || !hurter.isAlive() || Downed.isDowned(hurter)
			|| Comrades.side(hurter).equals(Comrades.side(leader)) || hurter.distanceToSqr(leader) > BAND * BAND) {
			return false;
		}
		Callouts.say(leader, "lead.come", hurter, true, hurter.getDisplayName());
		for (Mob m : band) {
			if (!Comrades.busy(m)) {
				Comrades.aim(m, hurter);
			}
		}
		return true;
	}

	private static boolean go(ServerLevel level, Mob leader, String side, List<Mob> band) {
		int foes = Comrades.candidates(level, leader, side).size();
		if (band.size() < 2 || foes == 0 || band.size() + 1 <= foes) {
			return false;
		}
		Callouts.say(leader, "lead.go", null, true);
		for (Mob m : band) {
			Comrades.think(level, m, true);
		}
		return true;
	}

	/** Leaders who were fighting and are down or dead now: one of their band shouts it, once. */
	private static void fallen(ServerLevel level) {
		for (var it = LEADERS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Mob leader = e.getValue();
			if (leader.level() != level) {
				continue;
			}
			boolean gone = !leader.isAlive() || leader.isRemoved() || Downed.isDowned(leader);
			if (!gone) {
				if (!Comrades.fought(leader)) {
					// (the fight is over: no longer watched)
					it.remove();
					LEADING.remove(e.getKey());
				}
				continue;
			}
			String side = LEADING.remove(e.getKey());
			it.remove();
			ORDERED.remove(e.getKey());
			if (side == null) {
				continue;
			}
			Component name = leader.getDisplayName();
			for (Mob m : level.getEntitiesOfClass(Mob.class, leader.getBoundingBox().inflate(BAND), m -> m != leader && Comrades.fighter(m)
				&& Comrades.side(m).equals(side))) {
				if (Callouts.say(m, "leaderdown", leader, true, name)) {
					break;
				}
			}
		}
	}
}

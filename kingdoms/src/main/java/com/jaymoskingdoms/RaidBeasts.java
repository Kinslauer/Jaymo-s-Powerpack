package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * (gv_raid_beasts, after Guard Villagers' raiders: its idea only) A war party plundering a town goes for its livestock as well
 * as its storehouse ({@link Raids}' plunder step asks {@link #pick}): a raider with nothing in his hands and no foe on him
 * singles out a cow, sheep, pig or chicken of the town (never a pet) within {@link #REACH} of him, runs it down and kills it
 * for its meat. A town loses at most {@link #PER_DAY} beasts a day to raiders, so a raid thins the herd and does not empty it;
 * a town whose guards or players cut the raiders down first keeps what is left, and breeds it back (the beasts' own business),
 * which is the stewardship: a defended herd is the one worth keeping.
 */
public final class RaidBeasts {
	/** How far from a raider (blocks) and from the town's seat an animal must be to be picked. */
	static final double REACH = 28.0, TOWN = 72.0;
	/** The chance, each pick, that a free raider turns to the livestock. */
	static final float CHANCE = 0.25F;
	/** How often the hunt is driven (ticks); how close the raider must be to strike (blocks); his blow; his pace. */
	static final int EVERY = 10;
	static final double STRIKE = 2.2;
	static final float DAMAGE = 6.0F, PACE = 1.15F;
	/** The most livestock a town loses to raiders in a day; the longest one hunt lasts (ticks). */
	static final int PER_DAY = 6, HUNT_TICKS = 600;
	/** The people this near the kill hear of it (blocks). */
	static final double HEARD = 64.0;

	private static final class Hunt {
		final UUID beast;
		final long started;

		Hunt(UUID beast, long started) {
			this.beast = beast;
			this.started = started;
		}
	}

	private static final Map<UUID, Hunt> HUNTS = new HashMap<>();
	/** Beasts a town has lost to raiders: the day, and how many. */
	private static final Map<Integer, long[]> LOST = new HashMap<>();

	private RaidBeasts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RaidBeasts::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HUNTS.clear();
			LOST.clear();
		});
	}

	/** Called by {@link Raids} for a free raider: true if he is on a beast (so the raid leaves him to it). */
	static boolean pick(ServerLevel level, Mob raider, Faction town) {
		if (HUNTS.containsKey(raider.getUUID())) {
			return true;
		}
		if (level.getRandom().nextFloat() >= CHANCE || lost(level, town) >= PER_DAY) {
			return false;
		}
		Animal best = null;
		double bestD = Double.MAX_VALUE;
		Vec3 seat = Vec3.atCenterOf(town.seat());
		for (Animal a : level.getEntitiesOfClass(Animal.class, raider.getBoundingBox().inflate(REACH), a -> a.isAlive() && !(a instanceof TamableAnimal)
			&& !a.isLeashed() && a.position().distanceToSqr(seat) <= TOWN * TOWN)) {
			double d = a.distanceToSqr(raider);
			if (d < bestD && !hunted(a)) {
				bestD = d;
				best = a;
			}
		}
		if (best == null) {
			return false;
		}
		HUNTS.put(raider.getUUID(), new Hunt(best.getUUID(), level.getGameTime()));
		return true;
	}

	private static boolean hunted(Animal a) {
		return HUNTS.values().stream().anyMatch(h -> h.beast.equals(a.getUUID()));
	}

	private static long lost(ServerLevel level, Faction town) {
		long[] l = LOST.get(town.id());
		return l == null || l[0] != level.getDayTime() / 24000L ? 0L : l[1];
	}

	private static void tick(MinecraftServer server) {
		if (HUNTS.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		for (Map.Entry<UUID, Hunt> e : new ArrayList<>(HUNTS.entrySet())) {
			Hunt h = e.getValue();
			if (!(level.getEntity(e.getKey()) instanceof Mob raider) || !raider.isAlive() || !(level.getEntity(h.beast) instanceof Animal beast) || !beast.isAlive()
				|| now - h.started > HUNT_TICKS || raider.getTarget() != null) {
				HUNTS.remove(e.getKey());
				continue;
			}
			raider.getLookControl().setLookAt(beast, 30.0F, 30.0F);
			if (raider.distanceToSqr(beast) > STRIKE * STRIKE) {
				raider.getNavigation().moveTo(beast, PACE);
				continue;
			}
			raider.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			beast.hurtServer(level, level.damageSources().mobAttack(raider), DAMAGE);
			if (!beast.isAlive()) {
				HUNTS.remove(e.getKey());
				slain(level, raider, beast);
			}
		}
	}

	/** A beast of the town is dead: counted against its day, and the people near told once. */
	private static void slain(ServerLevel level, Mob raider, Animal beast) {
		Faction town = Factions.at(level.getServer(), level, beast.blockPosition());
		if (town == null) {
			return;
		}
		long day = level.getDayTime() / 24000L;
		long[] l = LOST.computeIfAbsent(town.id(), id -> new long[] {day, 0L});
		if (l[0] != day) {
			l[0] = day;
			l[1] = 0L;
		}
		if (l[1]++ == 0L) {
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(beast) <= HEARD * HEARD) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.raid.beasts", town.name()));
				}
			}
		}
	}
}

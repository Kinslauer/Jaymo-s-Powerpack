package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * Before the fight (docs/Pawn-Plan.md row F5; after the pawns who ready themselves and warn you of what's ahead in
 * Dragon's Dogma, a commercial game: ideas only).
 * <ul>
 * <li><b>Friend</b>: a companion who sees foes ahead ({@link WorldTiers#hostile}, within {@link #SPOT}) before anyone
 * has started anything says so ("ready"), and your companions close up round you for {@link #READY_TICKS}. They don't
 * start the fight: you do.</li>
 * <li><b>Foe</b>: a camp's band that sees you coming before you've seen them goes to ground: they crouch where they
 * are and keep still ({@link #HIDE_TICKS} at most), and spring all at once ("ambush") when you come within
 * {@link #SPRING}, when you look straight at one of them close by, or when one of them is hurt.</li>
 * </ul>
 */
public final class Ambushes {
	/** Foes are seen this far off (blocks); a band lies in wait for someone coming within this (blocks), but not nearer than {@link #SPRING}. */
	static final double SPOT = 24.0, WATCH = 36.0, SPRING = 8.0;
	/** A band is the hiders' side within this of the one who saw you (blocks). */
	static final double BAND = 12.0;
	/** Looking at a hider this close (blocks), this squarely (cosine), with a clear sight of them, finds them. */
	static final double SEEN_NEAR = 18.0, SEEN_LOOK = 0.97;
	/** How often players are looked at (ticks); companions close up this long (ticks); readying again not before this (ticks). */
	static final int EVERY = 20, READY_TICKS = 200, READY_GAP = 900;
	/** A band lies in wait this long at most (ticks), and doesn't go to ground again for this long (ticks). */
	static final int HIDE_TICKS = 600, HIDE_GAP = 1200;

	private static final class Lying {
		final List<Mob> band;
		final ServerPlayer prey;
		final long until;

		Lying(List<Mob> band, ServerPlayer prey, long until) {
			this.band = band;
			this.prey = prey;
			this.until = until;
		}
	}

	private static final List<Lying> LYING = new ArrayList<>();
	private static final Map<Mob, Lying> HIDING = new IdentityHashMap<>();
	private static final Map<UUID, Long> READIED = new HashMap<>();
	private static final Map<UUID, Long> HID = new HashMap<>();

	private Ambushes() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Ambushes::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LYING.clear();
			HIDING.clear();
			READIED.clear();
			HID.clear();
		});
	}

	/** {@code m} is lying in wait. */
	public static boolean hiding(Mob m) {
		return HIDING.containsKey(m);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!LYING.isEmpty()) {
			for (Lying l : new ArrayList<>(LYING)) {
				step(l, now);
			}
		}
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		READIED.values().removeIf(t -> t < now);
		HID.values().removeIf(t -> t < now);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || player.isCreative() || Downed.isDowned(player)) {
				continue;
			}
			ready(player, now);
			lieInWait(player, now);
		}
	}

	// ---- friend: weapons out

	private static void ready(ServerPlayer player, long now) {
		if (READIED.containsKey(player.getUUID())) {
			return;
		}
		ServerLevel level = player.level();
		List<Villager> yours = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(SPOT),
			v -> !v.isBaby() && !v.isSleeping() && !Downed.isDowned(v) && Followers.takesOrders(v, player));
		if (yours.isEmpty() || yours.stream().anyMatch(v -> Comrades.target(v) != null)) {
			return;
		}
		for (Villager v : yours) {
			for (Mob foe : level.getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(SPOT),
				m -> m.isAlive() && !Downed.isDowned(m) && !hiding(m) && WorldTiers.hostile(m))) {
				if (Comrades.target(foe) != null || !v.hasLineOfSight(foe)) {
					continue;
				}
				READIED.put(player.getUUID(), now + READY_GAP);
				Callouts.say(v, "ready", foe, false, foe.getDisplayName());
				for (Villager w : yours) {
					QuickOrders.close(w, player, READY_TICKS);
				}
				return;
			}
		}
	}

	// ---- foe: lying in wait

	private static void lieInWait(ServerPlayer player, long now) {
		ServerLevel level = player.level();
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(WATCH), m -> m.isAlive() && !Downed.isDowned(m)
			&& Temperaments.humanoid(m) && !hiding(m) && !HID.containsKey(m.getUUID()) && Comrades.target(m) == null && WorldTiers.hostile(m)
			&& !CampSuppers.eating(m))) {
			double d = m.distanceToSqr(player);
			if (d < SPRING * SPRING * 2.0 || !m.hasLineOfSight(player) || seen(player, m)) {
				continue;
			}
			String side = Comrades.side(m);
			List<Mob> band = level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(BAND), o -> o.isAlive() && !Downed.isDowned(o)
				&& Temperaments.humanoid(o) && !hiding(o) && Comrades.target(o) == null && Comrades.side(o).equals(side) && !Comrades.busy(o));
			if (band.size() < 2) {
				continue;
			}
			Lying l = new Lying(band, player, now + HIDE_TICKS);
			LYING.add(l);
			for (Mob o : band) {
				HIDING.put(o, l);
				HID.put(o.getUUID(), now + HIDE_GAP);
				hold(o);
			}
			return;
		}
	}

	/** {@code player} is looking right at {@code m}, near enough to make it out. */
	private static boolean seen(ServerPlayer player, Mob m) {
		if (player.distanceToSqr(m) > SEEN_NEAR * SEEN_NEAR || !player.hasLineOfSight(m)) {
			return false;
		}
		Vec3 to = m.getEyePosition().subtract(player.getEyePosition()).normalize();
		return player.getViewVector(1.0F).dot(to) > SEEN_LOOK;
	}

	private static void step(Lying l, long now) {
		l.band.removeIf(m -> {
			boolean gone = !m.isAlive() || m.isRemoved() || Downed.isDowned(m);
			if (gone) {
				HIDING.remove(m);
				m.setShiftKeyDown(false);
			}
			return gone;
		});
		ServerPlayer prey = l.prey;
		boolean spring = false;
		boolean giveUp = l.band.isEmpty() || now >= l.until || prey.isRemoved() || !prey.isAlive() || prey.level() != l.band.getFirst().level();
		if (!giveUp) {
			for (Mob m : l.band) {
				if (m.hurtTime > 0 || m.distanceToSqr(prey) < SPRING * SPRING || seen(prey, m) && now % 5 == 0) {
					spring = true;
					break;
				}
			}
			if (!spring && prey.distanceToSqr(l.band.getFirst()) > WATCH * WATCH * 1.5) {
				giveUp = true;
			}
		}
		if (spring || giveUp) {
			LYING.remove(l);
			Mob shouter = null;
			for (Mob m : l.band) {
				HIDING.remove(m);
				m.setShiftKeyDown(false);
				if (spring && !giveUp) {
					Comrades.aim(m, prey);
					if (shouter == null || m.distanceToSqr(prey) < shouter.distanceToSqr(prey)) {
						shouter = m;
					}
				}
			}
			if (shouter != null) {
				Callouts.say(shouter, "ambush", prey, true, prey.getDisplayName());
			}
			return;
		}
		for (Mob m : l.band) {
			hold(m);
			m.getLookControl().setLookAt(prey, 10.0F, 10.0F);
		}
	}

	/** Down low and still. */
	private static void hold(Mob m) {
		m.setShiftKeyDown(true);
		m.getNavigation().stop();
		Comrades.stand(m);
		if (m.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
			m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
	}
}

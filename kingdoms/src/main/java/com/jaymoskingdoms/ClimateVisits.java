package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * A villager or guard who comes over to a player with something to say or give (docs/Climate-Plan.md, track F: the weather-wise
 * who tell the season coming, {@link WeatherWise}; whoever sees you shiver or bake, {@link Notices}). It is the walk
 * {@link FaintWakers} makes, kept for any errand: they walk to you, say their piece (a bubble, never chat) and, if the errand has
 * somewhere to show you (a fire, shade), lead you there, waiting when you fall behind.
 * <p>
 * They are left be by everything that asks: a visit ends the moment they're fighting, down, bound, asleep, busy
 * ({@link TalkBusy#occupied}) or talking with anyone ({@link Approaches#talking}, MCA's talk menu: opening it means they stop
 * for you). One visit per player at a time.
 */
final class ClimateVisits {
	/** How often visits are stepped (ticks). */
	static final int EVERY = 10;
	/** Close enough to speak (blocks), how long they try to reach you before giving up, and how long a lead goes on for (ticks). */
	static final double REACH = 2.4;
	static final int GIVE_UP = 400, LEAD_TICKS = 600;
	/** Leading: they stop and wait once you're this far behind (blocks), and you've arrived within this of the spot. */
	static final double WAIT_RANGE = 6.0, LOSE_RANGE = 32.0, ARRIVE = 3.0;
	/** Walking speeds: a villager's walk target, a guard's navigation. */
	static final float VILLAGER_SPEED = 0.6F;
	static final double FOLK_SPEED = 1.0;

	/** What the visitor does. */
	interface Script {
		/** They have reached {@code player}: say and do their piece. A spot to lead them to, or null for nothing more. */
		@Nullable BlockPos reach(ServerLevel level, ServerPlayer player, Mob mob);

		/** They have led {@code player} to the spot. */
		default void there(ServerLevel level, ServerPlayer player, Mob mob) {
		}
	}

	private static final class Visit {
		final Mob mob;
		final ServerPlayer player;
		final Script script;
		final long since;
		@Nullable BlockPos lead;
		long leadSince;

		Visit(Mob mob, ServerPlayer player, Script script, long since) {
			this.mob = mob;
			this.player = player;
			this.script = script;
			this.since = since;
		}
	}

	private static final Map<UUID, Visit> VISITS = new HashMap<>();
	private static final Map<Mob, UUID> VISITORS = new IdentityHashMap<>();

	private ClimateVisits() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ClimateVisits::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VISITS.clear();
			VISITORS.clear();
		});
	}

	/** Someone is on their way to {@code player}, or leading them. */
	static boolean busy(UUID player) {
		return VISITS.containsKey(player);
	}

	/** {@code mob} is on such an errand. */
	static boolean visiting(Mob mob) {
		return VISITORS.containsKey(mob);
	}

	/**
	 * Whether {@code mob} is free to go to someone: awake and up, unbound, not fighting, not busy, not in a talk and not already on
	 * an errand. The same rules {@link Approaches} comes over by.
	 */
	static boolean free(Mob mob) {
		return mob instanceof PathfinderMob pm && pm.isAlive() && !pm.isBaby() && !pm.isSleeping() && !pm.isPassenger() && !Downed.isDowned(pm)
			&& !Captives.bound(pm) && !visiting(pm) && !HearthFolk.errand(pm) && !TalkBusy.occupied(pm) && Approaches.free(pm) && !Firesides.sitting(pm);
	}

	/** Sends {@code mob} over to {@code player}; false if either is already on an errand. */
	static boolean start(ServerPlayer player, Mob mob, Script script) {
		if (VISITS.containsKey(player.getUUID()) || !free(mob)) {
			return false;
		}
		VISITS.put(player.getUUID(), new Visit(mob, player, script, player.level().getGameTime()));
		VISITORS.put(mob, player.getUUID());
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (VISITS.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		for (Visit v : java.util.List.copyOf(VISITS.values())) {
			if (!step(v)) {
				end(v);
			}
		}
	}

	private static void end(Visit v) {
		VISITS.remove(v.player.getUUID());
		VISITORS.remove(v.mob);
		release(v.mob);
	}

	/** One step of a visit; false once it's over. */
	private static boolean step(Visit v) {
		Mob mob = v.mob;
		ServerPlayer player = v.player;
		if (!(mob.level() instanceof ServerLevel level) || mob.isRemoved() || !mob.isAlive() || mob.level() != player.level() || player.isRemoved()
			|| !player.isAlive() || Downed.isDowned(player) || PowersCompat.fainted(player) || mob.isSleeping() || Downed.isDowned(mob)
			|| Captives.bound(mob) || mob.getTarget() != null || mob instanceof PathfinderMob pm && (TalkBusy.occupied(pm) || Approaches.talking(pm))) {
			return false;
		}
		long now = level.getGameTime();
		if (v.lead == null) {
			if (now - v.since > GIVE_UP || mob.distanceTo(player) > LOSE_RANGE) {
				return false;
			}
			if (mob.distanceTo(player) > REACH) {
				walkTo(mob, player);
				return true;
			}
			hold(mob, player);
			v.lead = v.script.reach(level, player, mob);
			v.leadSince = now;
			return v.lead != null;
		}
		if (now - v.leadSince > LEAD_TICKS || mob.distanceTo(player) > LOSE_RANGE) {
			return false;
		}
		if (player.blockPosition().closerThan(v.lead, ARRIVE) || mob.blockPosition().closerThan(v.lead, ARRIVE)) {
			v.script.there(level, player, mob);
			return false;
		}
		if (mob.distanceTo(player) > WAIT_RANGE) {
			// you've fallen behind: they stop and look back for you
			hold(mob, player);
			return true;
		}
		walkTo(mob, v.lead);
		return true;
	}

	private static void walkTo(Mob mob, ServerPlayer player) {
		if (mob instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(player, true), VILLAGER_SPEED, 1);
		} else {
			mob.getNavigation().moveTo(player, FOLK_SPEED);
		}
	}

	private static void walkTo(Mob mob, BlockPos spot) {
		if (mob instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, spot, VILLAGER_SPEED, 1);
		} else {
			mob.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, FOLK_SPEED);
		}
	}

	private static void hold(Mob mob, ServerPlayer player) {
		if (mob instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		if (!mob.isPassenger()) {
			mob.getNavigation().stop();
		}
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
	}

	private static void release(Mob mob) {
		if (mob instanceof Villager v && v.isAlive()) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
	}
}

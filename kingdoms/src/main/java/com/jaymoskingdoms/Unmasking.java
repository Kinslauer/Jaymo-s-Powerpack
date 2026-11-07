package com.jaymoskingdoms;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.473) Andy (2026-11-01): "if you go down within a city, the guards will unmask you before deciding what to do, your
 * corpse will still have the costume on and it can be removed after death to identify you as well".
 * <ul>
 * <li>A player downed on a settlement's land behind a mask (a costume's mask or hood, a Rig Helm, a bandit mask) its
 * people don't see through: the nearest free guard of that settlement within {@link #GUARD_RANGE} walks over, kneels and
 * takes it off ({@link #UNMASK_TICKS} at their side; Powers' {@code CorpseCostume.unmask}). The settlement knows who it
 * is from then ({@link Identities#link}, "guard"), with whatever the costume did there.</li>
 * <li>Only then is anything decided ({@link #awaits}): the tending ({@link Downed}), a charge's sentence ({@link Busts})
 * and executions (Powers' {@code MobExecutions}, spared meanwhile) wait for the unmasking, and then go by who it really
 * is.</li>
 * <li>A dead player's body lying in town with the costume's mask still on (Powers' tags, {@link #CORPSE_MASK}) is seen
 * to the same way: a guard pulls the costume off (it's left by the body), and the town knows whose body it is
 * ({@link Identities#corpseUnmasked}).</li>
 * </ul>
 */
final class Unmasking {
	/** How far a guard is called from (blocks); how close is at their side. */
	static final double GUARD_RANGE = 24.0, REACH = 1.8;
	/** How long the guard kneels at their side before the mask comes off; how long he may take altogether (ticks). */
	static final int UNMASK_TICKS = 40, GIVE_UP_TICKS = 20 * 30;
	/** A guard who couldn't get to them: the town lets things take their course this long before trying again (ticks). */
	static final long RETRY_TICKS = 20L * 60;
	/** The guard's pace. */
	static final float SPEED = 0.9F;
	/** Downed players are looked over this often; costumed bodies this often, this far round each player (ticks, blocks). */
	static final int CHECK = 10, CORPSE_CHECK = 40;
	static final double CORPSE_LOOK = 48.0;
	/** Lines each (lang {@code talk.jaymoskingdoms.unmask.<key>.<n>}). */
	static final int LINES = 3;
	/** Powers' tag on a dead player's body whose costume hid the face: the identity's key. */
	static final String CORPSE_MASK = "jaymospowers_corpse_mask:";

	/** A guard on his way to unmask someone (a downed player, or a body). */
	private static final class Job {
		final Entity target;
		final PathfinderMob guard;
		final int faction;
		final String key;
		final long until;
		int ticks;

		Job(Entity target, PathfinderMob guard, int faction, String key, long until) {
			this.target = target;
			this.guard = guard;
			this.faction = faction;
			this.key = key;
			this.until = until;
		}
	}

	/** Who is being unmasked (their id) → the job. Not saved. */
	private static final Map<UUID, Job> JOBS = new HashMap<>();
	/** Who a guard failed to unmask (their id) → when they may be tried again. Not saved. */
	private static final Map<UUID, Long> RESTING = new HashMap<>();

	private Unmasking() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Unmasking::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JOBS.clear();
			RESTING.clear();
		});
	}

	/**
	 * Whether what happens to downed {@code player} waits on the guards unmasking them: they're being unmasked now, or
	 * they lie masked on a settlement's land that doesn't know them, with one of its guards near.
	 */
	static boolean awaits(ServerPlayer player) {
		if (JOBS.containsKey(player.getUUID())) {
			return true;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		Faction f = wants(level, player);
		return f != null && guardFor(level, f, player) != null;
	}

	/** {@code guard} is off unmasking someone. */
	static boolean busy(Entity guard) {
		for (Job j : JOBS.values()) {
			if (j.guard == guard) {
				return true;
			}
		}
		return false;
	}

	/** The settlement whose guards would unmask {@code player} where they lie, or null. */
	private static @Nullable Faction wants(ServerLevel level, ServerPlayer player) {
		if (resting(player, level.getGameTime()) || level != Factions.level(level.getServer()) || !player.isAlive() || !Downed.isDowned(player) || player.isCreative() || player.isSpectator()
			|| Identities.key(player) == null) {
			return null;
		}
		Faction f = Theft.lawAt(level, player.blockPosition());
		return f != null && Identities.hidden(player, f.id()) ? f : null;
	}

	private static boolean resting(Entity target, long now) {
		Long until = RESTING.get(target.getUUID());
		return until != null && until > now;
	}

	/** The nearest of {@code f}'s guards free to come over to {@code target}, or null. */
	private static @Nullable PathfinderMob guardFor(ServerLevel level, Faction f, Entity target) {
		List<Worker> near = Underworld.guards(level, f, target.getBoundingBox().inflate(GUARD_RANGE));
		return near.stream().map(Worker::mob)
			.filter(m -> m != target && !m.isSleeping() && !m.isPassenger() && !m.isNoAi() && (m.getTarget() == null || m.getTarget() == target)
				&& !Arrests.busy(m) && !Downed.helping(m) && !busy(m) && m.distanceToSqr(target) <= GUARD_RANGE * GUARD_RANGE)
			.min(Comparator.comparingDouble(m -> m.distanceToSqr(target)))
			.orElse(null);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (Iterator<Job> it = JOBS.values().iterator(); it.hasNext();) {
			Job j = it.next();
			if (!step(level, j, now)) {
				it.remove();
				release(j.guard);
				// (done, or not: either way not tried again for a while; done, there's no mask left to try)
				RESTING.put(j.target.getUUID(), now + RETRY_TICKS);
			}
		}
		if (now % CHECK != 0) {
			return;
		}
		RESTING.values().removeIf(until -> until <= now);
		boolean corpses = now % CORPSE_CHECK == 0;
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (!JOBS.containsKey(player.getUUID())) {
				Faction f = wants(level, player);
				String key = Identities.key(player);
				PathfinderMob guard = f == null ? null : guardFor(level, f, player);
				if (guard != null && key != null) {
					start(level, player, guard, f, key, now, "downed");
				}
			}
			if (corpses && !player.isSpectator()) {
				bodies(level, player, now);
			}
		}
	}

	/** Costumed bodies near {@code player} lying on a settlement's land: a guard comes for each. */
	private static void bodies(ServerLevel level, ServerPlayer player, long now) {
		for (Entity body : level.getEntities((Entity)null, player.getBoundingBox().inflate(CORPSE_LOOK), e -> corpseKey(e) != null)) {
			if (JOBS.containsKey(body.getUUID()) || resting(body, now)) {
				continue;
			}
			Faction f = Theft.lawAt(level, body.blockPosition());
			PathfinderMob guard = f == null ? null : guardFor(level, f, body);
			if (guard != null) {
				start(level, body, guard, f, corpseKey(body), now, "body");
			}
		}
	}

	private static @Nullable String corpseKey(Entity e) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(CORPSE_MASK)) {
				return tag.substring(CORPSE_MASK.length());
			}
		}
		return null;
	}

	private static void start(ServerLevel level, Entity target, PathfinderMob guard, Faction f, String key, long now, String what) {
		JOBS.put(target.getUUID(), new Job(target, guard, f.id(), key, now + GIVE_UP_TICKS));
		if (guard.getTarget() == target) {
			guard.setTarget(null);
		}
		Speech.say(level, guard, Component.translatable("talk.jaymoskingdoms.unmask." + what + "." + level.getRandom().nextInt(LINES)), Speech.YELLOW, 16.0);
	}

	/** One tick of a guard's way to unmasking someone; false when it's over (done, or given up). */
	private static boolean step(ServerLevel level, Job j, long now) {
		PathfinderMob guard = j.guard;
		Entity target = j.target;
		if (now > j.until || !guard.isAlive() || guard.isRemoved() || guard.level() != level || Downed.isDowned(guard) || target.isRemoved()
			|| target.level() != level) {
			return false;
		}
		if (target instanceof ServerPlayer player) {
			if (!player.isAlive() || !Downed.isDowned(player) || !j.key.equals(Identities.key(player))) {
				// up again, dead, or unmasked already
				return false;
			}
		} else if (!j.key.equals(corpseKey(target))) {
			// someone else took the costume off
			return false;
		}
		if (guard.getTarget() == target) {
			guard.setTarget(null);
		}
		if (guard.distanceToSqr(target) > REACH * REACH) {
			guard.setShiftKeyDown(false);
			if (now % 10 == 0) {
				if (guard instanceof Villager v) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, target.blockPosition(), SPEED, 1);
				} else {
					guard.getNavigation().moveTo(target, SPEED);
				}
			}
			return true;
		}
		// at their side: kneeling over them, hands at the mask
		guard.getNavigation().stop();
		if (guard instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		guard.getLookControl().setLookAt(target.getX(), target.getY() + 0.2, target.getZ());
		guard.setShiftKeyDown(true);
		if (++j.ticks % 20 == 0) {
			guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		if (j.ticks < UNMASK_TICKS) {
			return true;
		}
		guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + 0.4, target.getZ(), 4, 0.2, 0.1, 0.2, 0.0);
		Faction f = Factions.get(level.getServer(), j.faction);
		if (target instanceof ServerPlayer player) {
			PowersCompat.unmaskPlayer(player);
			if (f != null) {
				// the guard sees the face: the town knows who it is now (and what the costume did there is theirs)
				Identities.link(level, player, j.key, f, guard, "guard");
			}
		} else {
			// Powers leaves the costume by the body and tells us who learns whose body it is (Identities.corpseUnmasked)
			PowersCompat.stripCorpse(target, guard);
		}
		return false;
	}

	private static void release(PathfinderMob guard) {
		guard.setShiftKeyDown(false);
		if (guard instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
	}
}

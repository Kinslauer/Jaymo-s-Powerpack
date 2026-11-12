package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * Getting the fallen up in the middle of a fight, on both sides (docs/Pawn-Plan.md, rows F1 and F4; after the pawns who
 * mind the fallen in Dragon's Dogma, a commercial game: ideas only). {@link Downed} already has a settlement's folk
 * tend the downed once the fighting has stopped; this is the field version:
 * <ul>
 * <li>A fighter who minds the fallen ({@link Comrades.Leaning#MEDIC}, main or second) goes to a downed one of its own
 * side ({@link Comrades#side}) within {@link #CALL}, drops its foe, kneels and gets them up in {@link #TEND_TICKS}: a
 * camp's raider does it for his fellows as much as your companion does it for you. Any hit on the one tending starts it
 * over, so a fight you thought won comes back unless you stop them.</li>
 * <li>{@link #send}: anyone else, when told to ({@link QuickOrders#HELP}, or a band leader's shout, {@link BandLeaders}).</li>
 * <li>A friend going down is called out once by one of its side ("down"), and the one coming says so ("tending").</li>
 * </ul>
 * Done is Powers' revive and bandage tags ({@link Downed#REVIVE_TAG}, {@link Downed#BANDAGED_TAG}), as the town's
 * tending. A downed player is tended by their own companions this way; strangers still come by their temperament
 * ({@link Downed}).
 */
public final class FieldMedics {
	/** How far a medic comes (blocks); close enough to tend; given up past this far (blocks). */
	static final double CALL = 16.0, REACH = 1.8, GIVE_UP = 32.0;
	/** How long getting someone up takes in the field (ticks); how often new downed are looked for (ticks). */
	static final int TEND_TICKS = 140, LOOK_TICKS = 20;
	/** Walking pace to them (of their speed). */
	static final float PACE = 0.9F;
	/** Each downed body is called out once in this long (ticks). */
	static final long DOWN_CALL_GAP = 1200L;

	private static final class Medic {
		final PathfinderMob helper;
		int ticks;

		Medic(PathfinderMob helper) {
			this.helper = helper;
		}
	}

	private static final Map<LivingEntity, Medic> TENDED = new WeakHashMap<>();
	private static final Map<Entity, LivingEntity> HELPING = new WeakHashMap<>();
	private static final Map<UUID, Long> CALLED = new HashMap<>();

	private FieldMedics() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FieldMedics::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TENDED.clear();
			HELPING.clear();
			CALLED.clear();
		});
	}

	/** {@code e} is on one knee over someone (or on the way to them). */
	public static boolean busy(Entity e) {
		return HELPING.containsKey(e);
	}

	/** Someone in the field has {@code body}. */
	public static boolean claimed(LivingEntity body) {
		return TENDED.containsKey(body);
	}

	/** {@code helper} goes to get {@code body} up. False if either is taken already, or they aren't fit to. */
	static boolean send(PathfinderMob helper, LivingEntity body) {
		if (busy(helper) || claimed(body) || Downed.tended(body) || !able(helper) || helper.level() != body.level()) {
			return false;
		}
		TENDED.put(body, new Medic(helper));
		HELPING.put(helper, body);
		Comrades.stand(helper);
		Callouts.say(helper, "tending", body, false, body.getDisplayName());
		return true;
	}

	/** On their feet and free to help. */
	private static boolean able(PathfinderMob m) {
		return m.isAlive() && !m.isRemoved() && !m.isBaby() && !m.isNoAi() && !Downed.isDowned(m) && !Downed.helping(m)
			&& !m.isSleeping() && Temperaments.humanoid(m);
	}

	private static void tick(MinecraftServer server) {
		if (!TENDED.isEmpty()) {
			for (Map.Entry<LivingEntity, Medic> e : new ArrayList<>(TENDED.entrySet())) {
				step(e.getKey(), e.getValue());
			}
		}
		if (server.getTickCount() % LOOK_TICKS != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		CALLED.values().removeIf(at -> now - at > DOWN_CALL_GAP);
		List<LivingEntity> seen = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			if (Downed.isDowned(player) && player.isAlive() && !seen.contains(player)) {
				seen.add(player);
				look(level, player, now);
			}
			for (Mob body : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(Comrades.SCAN),
				m -> m.isAlive() && Downed.isDowned(m) && Temperaments.humanoid(m))) {
				if (!seen.contains(body)) {
					seen.add(body);
					look(level, body, now);
				}
			}
		}
	}

	/** A downed body: called out by its side, and a medic of its side sent, if there is one near. */
	private static void look(ServerLevel level, LivingEntity body, long now) {
		if (claimed(body) || Downed.tended(body) || body.entityTags().contains(Downed.REVIVE_TAG) || body.isPassenger()) {
			return;
		}
		String side = Comrades.side(body);
		List<PathfinderMob> near = level.getEntitiesOfClass(PathfinderMob.class, body.getBoundingBox().inflate(CALL),
			m -> m != body && able(m) && !busy(m) && Comrades.side(m).equals(side));
		if (near.isEmpty()) {
			return;
		}
		if (!CALLED.containsKey(body.getUUID())) {
			CALLED.put(body.getUUID(), now);
			for (PathfinderMob m : near) {
				if (m.hasLineOfSight(body) && Callouts.say(m, "down", body, false, body.getDisplayName())) {
					break;
				}
			}
		}
		PathfinderMob medic = null;
		double best = Double.MAX_VALUE;
		for (PathfinderMob m : near) {
			if (!Comrades.leanings(m).has(Comrades.Leaning.MEDIC)) {
				continue;
			}
			double d = m.distanceToSqr(body);
			if (d < best) {
				best = d;
				medic = m;
			}
		}
		if (medic != null) {
			send(medic, body);
		}
	}

	/** One tick of a medic at work: walking over, then on one knee. */
	private static void step(LivingEntity body, Medic medic) {
		PathfinderMob helper = medic.helper;
		if (!(body.level() instanceof ServerLevel level) || !body.isAlive() || body.isRemoved() || !Downed.isDowned(body)
			|| !able(helper) || helper.level() != body.level() || helper.distanceToSqr(body) > GIVE_UP * GIVE_UP || body.isPassenger()
			|| body.isOnFire() || Downed.tended(body)) {
			release(body);
			return;
		}
		Comrades.stand(helper);
		if (helper.distanceToSqr(body) > REACH * REACH) {
			helper.setShiftKeyDown(false);
			if (body.tickCount % 10 == 0) {
				walk(helper, body);
			}
			return;
		}
		helper.getNavigation().stop();
		if (helper instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		helper.getLookControl().setLookAt(body.getX(), body.getY() + 0.2, body.getZ());
		helper.setShiftKeyDown(true);
		if (helper.hurtTime > 0) {
			// a blow knocks them off it: start over
			medic.ticks = 0;
			return;
		}
		if (++medic.ticks % 25 == 0) {
			helper.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.sendParticles(ParticleTypes.HEART, body.getX(), body.getY() + 0.6, body.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
		}
		if (medic.ticks >= TEND_TICKS) {
			release(body);
			body.addTag(Downed.REVIVE_TAG);
			body.addTag(Downed.BANDAGED_TAG);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, body.getX(), body.getY() + 0.8, body.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
			if (body instanceof ServerPlayer player && helper instanceof Villager v) {
				SharedHistory.revived(v, player);
			}
		}
	}

	private static void walk(PathfinderMob helper, LivingEntity to) {
		if (helper instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, to.blockPosition(), PACE, 1);
		} else {
			helper.getNavigation().moveTo(to, PACE + 0.2);
		}
	}

	private static void release(LivingEntity body) {
		Medic medic = TENDED.remove(body);
		if (medic != null) {
			HELPING.remove(medic.helper);
			medic.helper.setShiftKeyDown(false);
		}
	}

	/** Who has {@code body} in the field, or null. */
	static @Nullable PathfinderMob medicOf(LivingEntity body) {
		Medic m = TENDED.get(body);
		return m == null ? null : m.helper;
	}
}

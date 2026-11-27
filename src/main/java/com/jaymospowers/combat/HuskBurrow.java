package com.jaymospowers.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import com.jaymospowers.aether.world.SkySpringFeature;
import com.jaymospowers.power.zfighter.ZFighterKingdoms;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Husks wait buried in the desert sand. Andy (2026-11-26): "i really like the lunge the necrophages do, that should extend
 * to our zombies and husks" (see {@link LungeGoal}); Hunter's Road adds that a husk digs out of the sand when someone comes
 * near and digs back under when they lose him.
 * <ul>
 * <li><b>Buried:</b> invisible, silent and frozen (no AI), and not pickable ({@code BuriedPickMixin}): nothing to see from
 * afar, nothing to aim at, and a bow can't find it. It stays buried until a player (not creative or spectating) comes within
 * {@link #WAKE_RANGE} blocks, or it is hurt, or the sand under it goes.</li>
 * <li><b>Rising:</b> about {@link #RISE_TICKS} ticks (a second): it shows, crouched and then upright, in a spray of sand
 * with a digging sound, and then goes for whoever woke it.</li>
 * <li><b>Digging back under:</b> a husk that has had no target for {@link #LOSE_TICKS} ticks, standing on sand (sand blocks
 * only: {@link BlockTags#SAND}), after dark, with nobody within {@link #HIDE_FROM} blocks, sinks the same way
 * ({@link #SINK_TICKS}) and is buried again. A husk carrying or wearing anything stays up: an invisible one would show it.</li>
 * <li><b>How they come about:</b> a husk that spawns naturally on sand after dark starts buried {@link #SPAWN_CHANCE} of the
 * time ({@link #spawned}), and any husk may dig under as above.</li>
 * </ul>
 * All of it only where the nearest player ({@link #AWARE_RANGE} blocks) has reached Kingdoms' world tier Dread
 * ({@link SkySpringFeature#DREAD_TIER}); without Kingdoms, no husk is ever buried. A husk saved while buried is buried
 * when it loads again (its tag is kept in its save).
 * <p>
 * After {@code EmergeFromGroundGoal_Excavator}, {@code ReturnToGroundGoal_Excavator} and {@code ExcavatorMob} in The
 * Conjunction of the Spheres by Tenebris Mors, MIT (see {@code licenses/conjunction-of-the-spheres}): a mob that waits
 * in the ground, comes out when a target is close, and goes back after a while without one. Theirs is a goal set on a
 * custom mob with its own animations; this is ours, on vanilla's husk, driven from the server tick.
 */
public final class HuskBurrow {
	/** The tag a buried husk carries (it is kept in the save). */
	public static final String BURIED_TAG = "jaymospowers.buried";
	/** A player this close (blocks) wakes a buried husk, and a husk won't dig under with one this close. */
	public static final double WAKE_RANGE = 7.0, HIDE_FROM = 14.0;
	/** The nearest player within this (blocks) must have reached Dread for a husk to be buried at spawn or to dig under. */
	public static final double AWARE_RANGE = 128.0;
	/** Ticks to rise out of the sand and to sink into it. */
	public static final int RISE_TICKS = 20, SINK_TICKS = 20;
	/** Ticks without a target before an awake husk digs under. */
	public static final int LOSE_TICKS = 200;
	/** The share of husks that spawn naturally on sand after dark that start buried. */
	public static final float SPAWN_CHANCE = 0.5F;
	/** How often (ticks) each husk is looked at. */
	public static final int LOOK_EVERY = 10;
	/** The sand spray: particles a tick while rising or sinking, and ticks between digging sounds. */
	public static final int SPRAY = 6, SOUND_EVERY = 4;

	/** Every loaded husk (the server's), and the ones mid-rise and mid-sink with their ticks left. */
	private static final Set<Husk> HUSKS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<Husk, Integer> RISING = new IdentityHashMap<>();
	private static final Map<Husk, Integer> SINKING = new IdentityHashMap<>();
	/** When an awake husk was first seen with no target (game time). */
	private static final Map<Husk, Long> LOST = new IdentityHashMap<>();

	private HuskBurrow() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Husk husk) {
				HUSKS.add(husk);
				if (husk.entityTags().contains(BURIED_TAG)) {
					// saved while buried (or mid-rise): buried again, until someone comes near
					hide(husk);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Husk husk) {
				forget(husk);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof Husk husk && husk.isAlive()) {
				if (hidden(husk)) {
					rise(husk, null);
				} else if (SINKING.remove(husk) != null) {
					// hurt halfway under: it comes up again
					thaw(husk);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(HuskBurrow::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HUSKS.clear();
			RISING.clear();
			SINKING.clear();
			LOST.clear();
		});
	}

	/**
	 * Whether {@code entity} is a buried husk, from what both sides know (it is invisible and has no AI): nothing to draw, nothing
	 * to pick with the crosshair.
	 */
	public static boolean hidden(Entity entity) {
		return entity instanceof Husk husk && husk.isNoAi() && husk.isInvisible();
	}

	/**
	 * A husk has just spawned ({@code ZombieLungeMixin}, after its equipment is given): naturally, on sand, after dark, near a
	 * player at Dread, it starts buried {@link #SPAWN_CHANCE} of the time.
	 */
	public static void spawned(Husk husk, EntitySpawnReason reason) {
		if (reason != EntitySpawnReason.NATURAL || !(husk.level() instanceof ServerLevel level) || husk.getRandom().nextFloat() >= SPAWN_CHANCE
			|| !canDigUnder(husk, level, false)) {
			return;
		}
		bury(husk);
	}

	private static void forget(Husk husk) {
		HUSKS.remove(husk);
		RISING.remove(husk);
		SINKING.remove(husk);
		LOST.remove(husk);
	}

	/** Whether {@code husk}, awake, may be buried: after dark, upright on sand, bare, with a player at Dread about and none near. */
	private static boolean canDigUnder(Husk husk, ServerLevel level, boolean grounded) {
		if (!level.isDarkOutside() || grounded && !husk.onGround() || husk.isBaby() || husk.isPassenger() || husk.isVehicle() || husk.isInWater()
			|| !husk.getBlockStateOn().is(BlockTags.SAND) || carrying(husk)) {
			return false;
		}
		Player near = level.getNearestPlayer(husk.getX(), husk.getY(), husk.getZ(), AWARE_RANGE, entity -> !entity.isSpectator());
		if (near == null || ZFighterKingdoms.worldTier(near) < SkySpringFeature.DREAD_TIER) {
			return false;
		}
		return husk.distanceToSqr(near) > HIDE_FROM * HIDE_FROM;
	}

	/** Anything in a hand or on the body: an invisible husk would show it. */
	private static boolean carrying(Husk husk) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!husk.getItemBySlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Buried: tagged, invisible, silent, frozen, no target. */
	private static void bury(Husk husk) {
		husk.addTag(BURIED_TAG);
		husk.setTarget(null);
		hide(husk);
	}

	private static void hide(Husk husk) {
		RISING.remove(husk);
		SINKING.remove(husk);
		LOST.remove(husk);
		husk.getNavigation().stop();
		husk.setAggressive(false);
		husk.setNoAi(true);
		husk.setInvisible(true);
		husk.setSilent(true);
		husk.setPose(Pose.STANDING);
		husk.setDeltaMovement(0.0, 0.0, 0.0);
	}

	/** Shows and thaws {@code husk}: visible, loud, with its mind back. */
	private static void thaw(Husk husk) {
		husk.setInvisible(false);
		husk.setSilent(false);
		husk.setNoAi(false);
		if (husk.getPose() == Pose.CROUCHING) {
			husk.setPose(Pose.STANDING);
		}
	}

	/** {@code husk} starts to dig out, for {@code waker} (or whoever hurt it: null). */
	private static void rise(Husk husk, Player waker) {
		if (RISING.containsKey(husk)) {
			return;
		}
		husk.setInvisible(false);
		husk.setSilent(false);
		husk.setPose(Pose.CROUCHING);
		RISING.put(husk, RISE_TICKS);
		if (waker != null && !waker.isCreative() && !waker.isSpectator()) {
			husk.setTarget(waker);
		}
		if (husk.level() instanceof ServerLevel level) {
			level.playSound(null, husk.getX(), husk.getY(), husk.getZ(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.0F, 0.7F);
		}
	}

	private static void tick(MinecraftServer server) {
		if (HUSKS.isEmpty()) {
			return;
		}
		for (Map.Entry<Husk, Integer> entry : new ArrayList<>(RISING.entrySet())) {
			rising(entry.getKey(), entry.getValue());
		}
		for (Map.Entry<Husk, Integer> entry : new ArrayList<>(SINKING.entrySet())) {
			sinking(entry.getKey(), entry.getValue());
		}
		for (Husk husk : new ArrayList<>(HUSKS)) {
			if (husk.isRemoved() || !husk.isAlive() || !(husk.level() instanceof ServerLevel level)) {
				forget(husk);
			} else if ((husk.tickCount + husk.getId()) % LOOK_EVERY == 0 && !RISING.containsKey(husk) && !SINKING.containsKey(husk)) {
				look(husk, level);
			}
		}
	}

	/** A buried husk wakes for anyone near; an awake one that has lost its target for long enough digs under. */
	private static void look(Husk husk, ServerLevel level) {
		if (husk.entityTags().contains(BURIED_TAG)) {
			if (!hidden(husk)) {
				hide(husk);
			}
			if (!husk.getBlockStateOn().is(BlockTags.SAND)) {
				// the sand it lay in is gone
				husk.removeTag(BURIED_TAG);
				thaw(husk);
				return;
			}
			Player waker = level.getNearestPlayer(husk.getX(), husk.getY(), husk.getZ(), WAKE_RANGE,
				entity -> !entity.isSpectator() && !((Player) entity).isCreative());
			if (waker != null) {
				rise(husk, waker);
			}
			return;
		}
		if (husk.getTarget() != null && husk.getTarget().isAlive()) {
			LOST.remove(husk);
			return;
		}
		long now = level.getGameTime();
		Long since = LOST.putIfAbsent(husk, now);
		if (since == null || now - since < LOSE_TICKS || !canDigUnder(husk, level, true)) {
			return;
		}
		sink(husk);
	}

	private static void sink(Husk husk) {
		LOST.remove(husk);
		husk.getNavigation().stop();
		husk.setNoAi(true);
		husk.setPose(Pose.CROUCHING);
		SINKING.put(husk, SINK_TICKS);
		if (husk.level() instanceof ServerLevel level) {
			level.playSound(null, husk.getX(), husk.getY(), husk.getZ(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
		}
	}

	private static void rising(Husk husk, int left) {
		if (!(husk.level() instanceof ServerLevel level) || husk.isRemoved() || !husk.isAlive()) {
			RISING.remove(husk);
			return;
		}
		spray(level, husk, left);
		if (left > RISE_TICKS / 2) {
			husk.setPose(Pose.CROUCHING);
		} else if (husk.getPose() == Pose.CROUCHING) {
			husk.setPose(Pose.STANDING);
		}
		if (left > 1) {
			RISING.put(husk, left - 1);
			return;
		}
		RISING.remove(husk);
		husk.removeTag(BURIED_TAG);
		thaw(husk);
	}

	private static void sinking(Husk husk, int left) {
		if (!(husk.level() instanceof ServerLevel level) || husk.isRemoved() || !husk.isAlive()) {
			SINKING.remove(husk);
			return;
		}
		Player near = level.getNearestPlayer(husk.getX(), husk.getY(), husk.getZ(), WAKE_RANGE,
			entity -> !entity.isSpectator() && !((Player) entity).isCreative());
		if (near != null) {
			// someone came while it was going under: it comes back up at them
			SINKING.remove(husk);
			thaw(husk);
			husk.setTarget(near);
			return;
		}
		spray(level, husk, left);
		if (left > 1) {
			SINKING.put(husk, left - 1);
			return;
		}
		SINKING.remove(husk);
		bury(husk);
	}

	/** Sand thrown up round {@code husk}'s feet, and the digging sound now and then. */
	private static void spray(ServerLevel level, Husk husk, int left) {
		BlockState ground = husk.getBlockStateOn();
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), husk.getX(), husk.getY() + 0.1, husk.getZ(),
				SPRAY, 0.4, 0.1, 0.4, 0.15);
		}
		if (left % SOUND_EVERY == 0) {
			level.playSound(null, husk.getX(), husk.getY(), husk.getZ(), SoundEvents.SAND_HIT, SoundSource.HOSTILE, 0.9F, 0.8F);
		}
	}
}

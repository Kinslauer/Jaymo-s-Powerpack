package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.Downed;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.45, Andy's NOEA picks) The rush attacks, four techniques bought with TP and set on G like the others. Each picks
 * its foe (the locked one, else whoever is nearest in front, {@link #FIND_RANGE} at most), locks it for the length of the
 * move ({@link ZFighterGrab#lock}) and plays the combo's own poses ({@link ZFighterCombo#MOVE}):
 * <ul>
 * <li>Dragon Rush: a dash through the foe, one huge hit, a rising dragon-shaped trail of ki behind him.</li>
 * <li>Pack Fang: a fast flurry of ten blows with lunges and weaves, the last one heavy.</li>
 * <li>Hammerlock Pummel: rush in, take hold, three body blows, throw.</li>
 * <li>Skyward Chase: kick it up, flash after it, hammer it down into the ground ({@link ZFighterImpact} lands it).</li>
 * </ul>
 */
public final class ZFighterRush {
	static final double FIND_RANGE = 14.0;
	/** No fall damage this long after a rush (he may end it high up). */
	static final int SAFE_TICKS = 80;

	// ---- Dragon Rush ----
	static final float DRAGON_KI = 30.0F;
	static final int DRAGON_COOLDOWN = 260;
	static final double DRAGON_SPEED = 1.5, DRAGON_REACH = 1.3;
	static final int DRAGON_MAX = 16, DRAGON_THROUGH = 6;
	/** The big hit, in fists; and the foe thrown this far along and up. */
	static final float DRAGON_SHARE = 3.0F;
	static final double DRAGON_PUSH = 1.6, DRAGON_LIFT = 0.7;
	static final float DRAGON_POISE = 14.0F;
	/** The trail: this many coils behind him, spaced this far, each smaller. */
	static final int TRAIL_SEGMENTS = 12;
	static final double TRAIL_SPACING = 0.5, TRAIL_WIDTH = 0.55;

	// ---- Pack Fang ----
	static final float FANG_KI = 35.0F;
	static final int FANG_COOLDOWN = 300;
	static final int FANG_HITS = 10, FANG_EVERY = 3, FANG_LUNGE_TICKS = 6;
	static final float FANG_SHARE = 0.38F, FANG_LAST_SHARE = 0.9F;
	static final double FANG_SPEED = 1.1, FANG_REACH = 1.4, FANG_WEAVE = 0.12;
	static final float FANG_POISE = 1.5F;
	/** The poses the flurry runs through. */
	static final int[] FANG_MOVES = {ZFighterCombo.JAB, ZFighterCombo.REVERSE_PUNCH, ZFighterCombo.FRONT_KICK, ZFighterCombo.ELBOW,
		ZFighterCombo.ROUNDHOUSE, ZFighterCombo.JAB, ZFighterCombo.REVERSE_PUNCH, ZFighterCombo.RISING_KNEE, ZFighterCombo.ELBOW,
		ZFighterCombo.DOUBLE_PALM};

	// ---- Hammerlock Pummel ----
	static final float HAMMER_KI = 30.0F;
	static final int HAMMER_COOLDOWN = 260;
	static final double HAMMER_SPEED = 1.3, HAMMER_REACH = 1.3;
	static final int HAMMER_GRAB_MAX = 10, HAMMER_BLOWS = 3, HAMMER_BLOW_EVERY = 6, HAMMER_END = 8;
	static final float HAMMER_BLOW_SHARE = 0.9F, HAMMER_THROW = 1.3F;
	static final int[] HAMMER_MOVES = {ZFighterCombo.ELBOW, ZFighterCombo.RISING_KNEE, ZFighterCombo.REVERSE_PUNCH};

	// ---- Skyward Chase ----
	static final float SKY_KI = 40.0F;
	static final int SKY_COOLDOWN = 320;
	static final double SKY_SPEED = 1.4, SKY_REACH = 1.3;
	static final int SKY_REACH_MAX = 14, SKY_FLASH_AFTER = 9, SKY_HAMMER_AFTER = 4, SKY_MAX = 70;
	static final float SKY_KICK_SHARE = 1.2F, SKY_HAMMER_SHARE = 2.0F, SKY_SLAM_SHARE = 0.9F, SKY_POISE = 10.0F;
	static final double SKY_LIFT = 1.05, SKY_DROP_FOE = 1.9, SKY_DROP_SELF = 1.3;

	private static final class Run {
		final int technique;
		final UUID foe;
		final long start;
		int serial;
		int stage;
		long stageAt;
		int hits;
		Vec3 dir = Vec3.ZERO;

		Run(int technique, UUID foe, long start) {
			this.technique = technique;
			this.foe = foe;
			this.start = start;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static final Map<UUID, Long> SAFE = new HashMap<>();

	private ZFighterRush() {
	}

	public static boolean active(Player player) {
		return RUNS.containsKey(player.getUUID());
	}

	/** G with a rush on it: begins it if there is a foe and the ki; returns its cooldown in ticks, or 0 if nothing began. */
	static int start(ServerPlayer player, ZFighterPower.Live live, int technique) {
		if (active(player) || ZFighterGrab.holding(player) || ZFighterPoise.stunned(player) || live.guarding || live.charging
			|| ZFighterLineage.ape(player) || live.spiritFrom >= 0 || live.waveFrom >= 0) {
			return 0;
		}
		LivingEntity foe = find(player);
		if (foe == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_rush_nofoe").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		float cost;
		int cooldown;
		switch (technique) {
			case ZFighterTraining.DRAGON_RUSH -> {
				cost = DRAGON_KI;
				cooldown = DRAGON_COOLDOWN;
			}
			case ZFighterTraining.PACK_FANG -> {
				cost = FANG_KI;
				cooldown = FANG_COOLDOWN;
			}
			case ZFighterTraining.HAMMERLOCK -> {
				cost = HAMMER_KI;
				cooldown = HAMMER_COOLDOWN;
			}
			default -> {
				cost = SKY_KI;
				cooldown = SKY_COOLDOWN;
			}
		}
		if (!ZFighterPower.spend(player, live, cost)) {
			return 0;
		}
		ServerLevel level = player.level();
		Run run = new Run(technique, foe.getUUID(), level.getGameTime());
		run.dir = foe.getBoundingBox().getCenter().subtract(player.getBoundingBox().getCenter()).normalize();
		RUNS.put(player.getUUID(), run);
		ZFighterCombo.stop(player);
		int move = technique == ZFighterTraining.DRAGON_RUSH ? ZFighterCombo.DRAGON_FIST : technique == ZFighterTraining.PACK_FANG ? ZFighterCombo.JAB
			: technique == ZFighterTraining.HAMMERLOCK ? ZFighterCombo.DOUBLE_PALM : ZFighterCombo.FLYING_KNEE;
		pose(player, run, move, 16);
		ZFighterImpact.rushSound(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5F,
			technique == ZFighterTraining.DRAGON_RUSH ? 1.6F : 1.9F);
		return cooldown;
	}

	/** The foe: the locked one if it is in range, else the nearest in front that he can see. */
	private static @Nullable LivingEntity find(ServerPlayer player) {
		LivingEntity locked = LockOn.target(player);
		if (locked != null && valid(player, locked) && player.distanceTo(locked) <= FIND_RANGE) {
			return locked;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity entity : player.level().getEntities(player, new AABB(eye, eye).inflate(FIND_RANGE))) {
			if (!(entity instanceof LivingEntity living) || !valid(player, living)) {
				continue;
			}
			Vec3 to = living.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > FIND_RANGE || distance < 1.0E-3) {
				continue;
			}
			double facing = to.scale(1.0 / distance).dot(look);
			double score = distance * (2.0 - facing);
			if (facing >= 0.6 && score < bestScore && player.hasLineOfSight(living)) {
				best = living;
				bestScore = score;
			}
		}
		return best;
	}

	private static boolean valid(ServerPlayer player, LivingEntity living) {
		if (living == player || !living.isAlive() || !living.isAttackable() || living.isSpectator() || living.isPassengerOfSameVehicle(player)
			|| Downed.isDowned(living)) {
			return false;
		}
		return !(living instanceof Player other) || player.canHarmPlayer(other);
	}

	private static void pose(ServerPlayer player, Run run, int move, int ticks) {
		run.serial = run.serial + 1 & 0x7FFF;
		player.setAttached(ZFighterCombo.MOVE, run.serial << 16 | Math.min(255, ticks) << 8 | move);
	}

	/** Every tick, per Z Fighter: the move runs; afterwards his fall is forgiven for a while. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long safe = SAFE.get(player.getUUID());
		if (safe != null) {
			if (now < safe) {
				player.resetFallDistance();
			} else {
				SAFE.remove(player.getUUID());
			}
		}
		Run run = RUNS.get(player.getUUID());
		if (run == null) {
			return;
		}
		LivingEntity foe = level.getEntity(run.foe) instanceof LivingEntity living ? living : null;
		if (!player.isAlive() || player.isSpectator() || Downed.isDowned(player) || !Powers.has(player, ZFighterPower.INSTANCE) || foe == null
			|| !foe.isAlive() || foe.level() != level) {
			stop(player);
			return;
		}
		long age = now - run.start;
		player.resetFallDistance();
		switch (run.technique) {
			case ZFighterTraining.DRAGON_RUSH -> dragon(player, live, run, foe, age, now);
			case ZFighterTraining.PACK_FANG -> fang(player, live, run, foe, age);
			case ZFighterTraining.HAMMERLOCK -> hammerlock(player, live, run, foe, age);
			default -> skyward(player, live, run, foe, age, now);
		}
	}

	/** The move is over (or broken off): the foe is let go, the pose cleared. */
	static void stop(ServerPlayer player) {
		Run run = RUNS.remove(player.getUUID());
		if (run == null) {
			return;
		}
		player.removeAttached(ZFighterCombo.MOVE);
		ZFighterGrab.release(player);
		SAFE.put(player.getUUID(), player.level().getGameTime() + SAFE_TICKS);
	}

	static void drop(ServerPlayer player) {
		stop(player);
		SAFE.remove(player.getUUID());
	}

	private static float fists(ServerPlayer player) {
		return (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	/** A blow of the ordinary kind (capped like a punch). */
	private static boolean punch(ServerPlayer player, LivingEntity foe, float damage) {
		foe.setInvulnerableTime(0);
		boolean hurt = foe.hurtServer(player.level(), player.damageSources().playerAttack(player), damage);
		Mastery.used(player);
		return hurt;
	}

	/** A heavy blow of ki-and-fist, beyond the punch cap (as the combo's finishers). */
	private static boolean heavy(ServerPlayer player, LivingEntity foe, float damage) {
		foe.setInvulnerableTime(0);
		boolean hurt = foe.hurtServer(player.level(), ZFighterKi.source(player), damage);
		Mastery.used(player);
		return hurt;
	}

	private static void impactFx(ServerPlayer player, ZFighterPower.Live live, LivingEntity foe, float shake, boolean big) {
		ServerLevel level = player.level();
		Vec3 at = foe.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.CRIT, true, false, at.x, at.y, at.z, big ? 18 : 6, 0.25, 0.25, 0.25, 0.4);
		level.sendParticles(ZFighterPower.dust(live), true, false, at.x, at.y, at.z, big ? 16 : 5, 0.25, 0.25, 0.25, 0.1);
		if (big) {
			level.sendParticles(ParticleTypes.EXPLOSION, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, at.x, at.y, at.z, big ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
			big ? 1.2F : 0.8F, big ? 0.7F : 1.0F + level.getRandom().nextFloat() * 0.3F);
		Shakes.send(player, shake, at);
	}

	private static double gap(ServerPlayer player, LivingEntity foe) {
		return foe.getBoundingBox().getCenter().distanceTo(player.getBoundingBox().getCenter()) - foe.getBbWidth() * 0.5;
	}

	// ---------------------------------------------------------------- Dragon Rush

	private static void dragon(ServerPlayer player, ZFighterPower.Live live, Run run, LivingEntity foe, long age, long now) {
		ServerLevel level = player.level();
		if (run.stage == 0) {
			ZFighterGrab.lock(foe);
			Vec3 to = foe.getBoundingBox().getCenter().subtract(player.getBoundingBox().getCenter());
			if (to.lengthSqr() > 1.0E-4) {
				run.dir = to.normalize();
			}
			trail(level, player, live, run.dir, age);
			if (ZFighterImpact.rush(player, foe, DRAGON_SPEED, DRAGON_REACH)) {
				float fists = fists(player);
				heavy(player, foe, fists * DRAGON_SHARE);
				ZFighterKi.push(foe, run.dir, DRAGON_PUSH, DRAGON_LIFT);
				ZFighterImpact.launched(foe, player, fists * ZFighterCombo.SLAM_SHARE);
				ZFighterPoise.wear(foe, DRAGON_POISE);
				impactFx(player, live, foe, 1.0F, true);
				rising(level, live, foe.position());
				run.stage = 1;
				run.stageAt = now;
			} else if (age >= DRAGON_MAX) {
				stop(player);
			}
			return;
		}
		// on through the foe, the dragon rising behind him
		trail(level, player, live, run.dir, age);
		player.setDeltaMovement(run.dir.scale(DRAGON_SPEED * 0.8));
		player.syncVelocity = true;
		if (now - run.stageAt >= DRAGON_THROUGH) {
			player.setDeltaMovement(player.getDeltaMovement().scale(0.2));
			stop(player);
		}
	}

	/** The dragon: a head of light and coils behind it that weave side to side and climb, smaller toward the tail. */
	private static void trail(ServerLevel level, ServerPlayer player, ZFighterPower.Live live, Vec3 dir, long age) {
		Vec3 head = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		int color = ZFighterPower.auraColor(live);
		for (int k = 0; k < TRAIL_SEGMENTS; k++) {
			double phase = age * 0.9 - k * 0.8;
			Vec3 at = head.subtract(dir.scale(k * TRAIL_SPACING)).add(side.scale(Math.sin(phase) * TRAIL_WIDTH))
				.add(0.0, 0.12 * k + Math.cos(phase) * 0.25, 0.0);
			float size = Math.max(0.6F, 2.0F - k * 0.12F);
			level.sendParticles(new DustParticleOptions(color, size), true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		Vec3 snout = head.add(dir.scale(0.6));
		level.sendParticles(ParticleTypes.END_ROD, true, false, snout.x, snout.y, snout.z, 2, 0.1, 0.1, 0.1, 0.02);
		level.sendParticles(ParticleTypes.FLAME, true, false, snout.x, snout.y + 0.15, snout.z, 1, 0.05, 0.05, 0.05, 0.01);
	}

	/** After the hit the dragon climbs: a spiral of ki rising off where the foe stood. */
	private static void rising(ServerLevel level, ZFighterPower.Live live, Vec3 at) {
		int color = ZFighterPower.auraColor(live);
		for (int i = 0; i < 18; i++) {
			double a = i * 0.7;
			double r = 0.7 - i * 0.02;
			level.sendParticles(new DustParticleOptions(color, Math.max(0.8F, 2.0F - i * 0.07F)), true, false, at.x + Math.cos(a) * r, at.y + i * 0.3,
				at.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	// ---------------------------------------------------------------- Pack Fang

	private static void fang(ServerPlayer player, ZFighterPower.Live live, Run run, LivingEntity foe, long age) {
		ServerLevel level = player.level();
		ZFighterGrab.lock(foe);
		long since = age - FANG_LUNGE_TICKS;
		if (since < 0) {
			// the lunge in
			if (gap(player, foe) > FANG_REACH) {
				ZFighterImpact.rush(player, foe, FANG_SPEED, FANG_REACH);
			}
			return;
		}
		// closing the gap between blows, weaving side to side once close
		if (gap(player, foe) > FANG_REACH) {
			ZFighterImpact.rush(player, foe, FANG_SPEED, FANG_REACH);
		} else {
			Vec3 to = foe.getBoundingBox().getCenter().subtract(player.getBoundingBox().getCenter());
			Vec3 side = new Vec3(-to.z, 0.0, to.x);
			side = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize();
			double sign = (since / FANG_EVERY) % 2 == 0 ? 1.0 : -1.0;
			player.setDeltaMovement(side.scale(sign * FANG_WEAVE));
			player.syncVelocity = true;
		}
		int i = (int)(since / FANG_EVERY);
		if (since % FANG_EVERY != 0 || i >= FANG_HITS) {
			if (i > FANG_HITS) {
				stop(player);
			}
			return;
		}
		boolean last = i == FANG_HITS - 1;
		pose(player, run, FANG_MOVES[i], FANG_EVERY + 2);
		float fists = fists(player);
		boolean hurt = last ? heavy(player, foe, fists * FANG_LAST_SHARE) : punch(player, foe, fists * FANG_SHARE);
		Vec3 at = foe.getBoundingBox().getCenter();
		if (hurt) {
			// the claws: two slashes crossing
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, true, false, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.0);
			level.sendParticles(ParticleTypes.CRIT, true, false, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.3);
			ZFighterPoise.wear(foe, FANG_POISE);
		}
		Vec3 motion = foe.getDeltaMovement();
		foe.setDeltaMovement(0.0, Math.min(motion.y, 0.1), 0.0);
		foe.syncVelocity = true;
		level.playSound(null, at.x, at.y, at.z, i % 2 == 0 ? SoundEvents.FOX_BITE : SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F,
			1.2F + 0.04F * i);
		Shakes.send(player, 0.12F + (last ? 0.4F : 0.0F), at);
		if (last) {
			ZFighterKi.push(foe, at.subtract(player.getBoundingBox().getCenter()), 1.3, 0.3);
			impactFx(player, live, foe, 0.6F, true);
			ZFighterImpact.launched(foe, player, fists * ZFighterCombo.SLAM_SHARE);
		}
	}

	// ---------------------------------------------------------------- Hammerlock Pummel

	private static void hammerlock(ServerPlayer player, ZFighterPower.Live live, Run run, LivingEntity foe, long age) {
		if (run.stage == 0) {
			ZFighterGrab.lock(foe);
			if (ZFighterImpact.rush(player, foe, HAMMER_SPEED, HAMMER_REACH) || age >= HAMMER_GRAB_MAX && gap(player, foe) < 3.0) {
				ZFighterGrab.seize(player, foe, true);
				run.stage = 1;
				run.stageAt = player.level().getGameTime();
				pose(player, run, ZFighterCombo.DOUBLE_PALM, 6);
			} else if (age >= HAMMER_GRAB_MAX) {
				stop(player);
			}
			return;
		}
		long since = player.level().getGameTime() - run.stageAt;
		int blow = (int)(since / HAMMER_BLOW_EVERY);
		if (since % HAMMER_BLOW_EVERY == 0 && since > 0 && blow <= HAMMER_BLOWS && run.hits < blow) {
			run.hits = blow;
			int move = HAMMER_MOVES[blow - 1];
			pose(player, run, move, HAMMER_BLOW_EVERY);
			float fists = fists(player);
			if (punch(player, foe, fists * HAMMER_BLOW_SHARE)) {
				ZFighterPoise.wear(foe, 2.0F);
				impactFx(player, live, foe, 0.3F, false);
			}
			return;
		}
		if (since == (long)(HAMMER_BLOWS + 1) * HAMMER_BLOW_EVERY) {
			pose(player, run, ZFighterCombo.DOUBLE_PALM, HAMMER_END);
			ZFighterGrab.throwHeld(player, HAMMER_THROW);
			impactFx(player, live, foe, 0.8F, true);
			return;
		}
		if (since >= (long)(HAMMER_BLOWS + 1) * HAMMER_BLOW_EVERY + HAMMER_END) {
			stop(player);
		}
	}

	// ---------------------------------------------------------------- Skyward Chase

	private static void skyward(ServerPlayer player, ZFighterPower.Live live, Run run, LivingEntity foe, long age, long now) {
		ServerLevel level = player.level();
		switch (run.stage) {
			case 0 -> {
				ZFighterGrab.lock(foe);
				if (ZFighterImpact.rush(player, foe, SKY_SPEED, SKY_REACH)) {
					// the kick that sends it up
					pose(player, run, ZFighterCombo.RISING_KNEE, 8);
					float fists = fists(player);
					punch(player, foe, fists * SKY_KICK_SHARE);
					foe.setDeltaMovement(0.0, SKY_LIFT, 0.0);
					foe.syncVelocity = true;
					foe.needsSync = true;
					ZFighterImpact.launched(foe, player, fists * SKY_SLAM_SHARE);
					impactFx(player, live, foe, 0.6F, true);
					run.stage = 1;
					run.stageAt = now;
				} else if (age >= SKY_REACH_MAX) {
					stop(player);
				}
			}
			case 1 -> {
				ZFighterGrab.lock(foe);
				if (now - run.stageAt >= SKY_FLASH_AFTER) {
					flash(player, live, foe);
					pose(player, run, ZFighterCombo.AXE_KICK, SKY_HAMMER_AFTER + 6);
					run.stage = 2;
					run.stageAt = now;
				}
			}
			case 2 -> {
				// hanging over it, the leg raised
				ZFighterGrab.lock(foe);
				player.setDeltaMovement(Vec3.ZERO);
				player.syncVelocity = true;
				if (now - run.stageAt >= SKY_HAMMER_AFTER) {
					float fists = fists(player);
					heavy(player, foe, fists * SKY_HAMMER_SHARE);
					foe.setDeltaMovement(0.0, -SKY_DROP_FOE, 0.0);
					foe.syncVelocity = true;
					foe.needsSync = true;
					player.setDeltaMovement(0.0, -SKY_DROP_SELF, 0.0);
					player.syncVelocity = true;
					ZFighterPoise.wear(foe, SKY_POISE);
					Vec3 at = foe.getBoundingBox().getCenter();
					level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.2F, 0.8F);
					impactFx(player, live, foe, 0.9F, true);
					run.stage = 3;
					run.stageAt = now;
				}
			}
			default -> {
				if (foe.onGround() && now - run.stageAt > 2 || age >= SKY_MAX || now - run.stageAt > 30) {
					stop(player);
				}
			}
		}
	}

	/** He vanishes where he is and is above the foe: a column of afterimages left behind. */
	private static void flash(ServerPlayer player, ZFighterPower.Live live, LivingEntity foe) {
		ServerLevel level = player.level();
		Vec3 start = player.position();
		Vec3 dest = foe.position().add(0.0, foe.getBbHeight() + 0.3, 0.0);
		if (!level.noCollision(player, player.getBoundingBox().move(dest.subtract(start)))) {
			dest = foe.position().add(0.0, Math.max(0.0, foe.getBbHeight() * 0.5), 0.0);
			if (!level.noCollision(player, player.getBoundingBox().move(dest.subtract(start)))) {
				return;
			}
		}
		DustParticleOptions image = ZFighterPower.dust(live);
		for (double h = 0.1; h < player.getBbHeight(); h += 0.3) {
			level.sendParticles(image, true, false, start.x, start.y + h, start.z, 3, 0.15, 0.05, 0.15, 0.0);
		}
		level.sendParticles(ParticleTypes.POOF, true, false, start.x, start.y + 1.0, start.z, 6, 0.2, 0.4, 0.2, 0.02);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8F, 1.9F);
		player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), player.getYRot(), 40.0F, false);
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.CLOUD, true, false, dest.x, dest.y + 0.5, dest.z, 6, 0.2, 0.2, 0.2, 0.02);
	}
}

package com.jaymospowers.rig;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.11.107) The <b>Unibeam</b> (Mind level 4, whole set): the chest reactor's own beam. The arms draw back and the
 * reactor whines up ({@link #CHARGE_TICKS}), then a thick beam pours from the chest along the look for
 * {@link #BEAM_TICKS}, burning through everything in its line (it pierces, sets alight and pushes back, and bends to the
 * lock like the palm blast) but leaving blocks alone. You're braced while it fires (slowed). Its pose is everyone's to see
 * ({@link RigMoves#UNIBEAM}).
 */
public final class RigUnibeam {
	/** Charge it takes, its cooldown (ticks), the whine-up and the beam's length (ticks). */
	static final int COST = 300, COOLDOWN = 200;
	public static final int CHARGE_TICKS = 12, BEAM_TICKS = 16;
	/** Reach (blocks), how wide it catches (blocks either side), and a hit every this many ticks. */
	static final double RANGE = 40.0, WIDTH = 0.6;
	static final int HIT_EVERY = 2;
	/** Each hit's damage, the fire it leaves (seconds) and its push (blocks/tick along the beam). */
	static final float DAMAGE = 3.0F, BURN_SECONDS = 4.0F;
	static final double PUSH = 0.25;
	/** The brace while it fires: slowness (level) on you. */
	static final int BRACE_SLOW = 2;

	private RigUnibeam() {
	}

	/** The key: whine up, if the Mind has it and the suit has the charge. */
	static void use(ServerPlayer player, RigActives.State state) {
		if (state.unibeam >= 0 || state.unibeamCooldown > 0) {
			return;
		}
		if (!RigActives.needs(player, Rig.UNIBEAM_LEVEL)) {
			return;
		}
		if (!Rig.spend(player, COST)) {
			RigMind.empty(player);
			return;
		}
		state.unibeam = 0;
		state.unibeamCooldown = COOLDOWN;
		RigMoves.start(player, RigMoves.UNIBEAM);
		RigMind.say(player, "unibeam", 1.0F);
	}

	/** Every tick while it's under way. */
	static void tick(ServerPlayer player, RigActives.State state) {
		if (state.unibeam < 0) {
			return;
		}
		if (!Rig.fullSet(player) || !player.isAlive()) {
			state.unibeam = -1;
			player.removeAttached(RigMoves.MOVE);
			return;
		}
		int t = state.unibeam++;
		ServerLevel level = player.level();
		Vec3 chest = chest(player);
		if (t < CHARGE_TICKS) {
			// the reactor whining up: light gathering into the chest
			float share = t / (float)CHARGE_TICKS;
			if (t % 3 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F,
					0.8F + share * 1.2F);
			}
			for (int i = 0; i < 3; i++) {
				Vec3 from = chest.add(level.getRandom().nextGaussian() * 0.6, level.getRandom().nextGaussian() * 0.6, level.getRandom().nextGaussian() * 0.6);
				Vec3 in = chest.subtract(from).scale(0.25);
				level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 0, in.x, in.y, in.z, 1.0);
			}
			return;
		}
		int beam = t - CHARGE_TICKS;
		if (beam >= BEAM_TICKS) {
			state.unibeam = -1;
			return;
		}
		if (beam == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.8F);
			com.jaymospowers.core.Shakes.send(player, 1.5F);
		}
		if (beam % 4 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5F, 1.6F);
		}
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, BRACE_SLOW, false, false, false));
		Vec3 look = aim(player, chest);
		Vec3 end = chest.add(look.scale(RANGE));
		BlockHitResult block = level.clip(new ClipContext(chest, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		// the beam: a thick white-hot core with sparks round it, and a splash where it lands
		double length = chest.distanceTo(end);
		for (double d = 0.6; d < length; d += 0.4) {
			Vec3 p = chest.add(look.scale(d));
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.0);
			if (level.getRandom().nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.15, 0.15, 0.15, 0.02);
			}
		}
		level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 4, 0.2, 0.2, 0.2, 0.03);
		level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 3, 0.1, 0.1, 0.1, 0.1);
		if (beam % HIT_EVERY != 0) {
			return;
		}
		DamageSource source = level.damageSources().playerAttack(player);
		for (LivingEntity target : caught(player, chest, end)) {
			target.hurtServer(level, source, DAMAGE);
			target.igniteForSeconds(BURN_SECONDS);
			target.push(look.x * PUSH, look.y * PUSH * 0.5, look.z * PUSH);
			target.syncVelocity = true;
		}
	}

	/** Where the beam leaves: the reactor in the middle of the chest. */
	private static Vec3 chest(ServerPlayer player) {
		return player.getEyePosition().add(0.0, -0.5, 0.0).add(player.getLookAngle().scale(0.35));
	}

	/** Along the look, or on to the lock when it's roughly ahead and in sight (as the palm blast bends). */
	private static Vec3 aim(ServerPlayer player, Vec3 chest) {
		Vec3 look = player.getLookAngle();
		LivingEntity lock = RigActives.locked(player);
		if (lock != null) {
			Vec3 to = lock.getBoundingBox().getCenter().subtract(chest);
			if (to.length() <= RANGE && to.normalize().dot(look) > RigActives.PALM_LOCK_DOT && player.hasLineOfSight(lock)) {
				return to.normalize();
			}
		}
		return look;
	}

	/** Every living thing the beam passes through (it pierces). */
	private static List<LivingEntity> caught(ServerPlayer player, Vec3 from, Vec3 to) {
		List<LivingEntity> caught = new ArrayList<>();
		for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(WIDTH + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			AABB box = entity.getBoundingBox().inflate(WIDTH);
			if (box.contains(from) || box.clip(from, to).isPresent()) {
				caught.add(entity);
			}
		}
		return caught;
	}
}

package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.111, Scan D) Keen, the sonar bat: small and dark, leathery wings down its arms, huge ears, a pug snout, tiny
 * red eyes. 14 health, fists of 3. It sees in the dark, and falls never hurt it. It flies: hold jump in the air to beat
 * up and where you look, let go to glide, sneak to drop; sneak under a ceiling and it hangs there. Scanned from a bat,
 * or Kingdoms' vampire bat or blightwing ({@code XenoDna}).
 * <ul>
 * <li>R, Sonar: everything within {@link #SONAR_RANGE} blocks is outlined for {@link #SONAR_TICKS} ticks, for you alone.</li>
 * <li>G, Screech: whatever flies near you is knocked out of the air.</li>
 * <li>V, Drain: a bite that heals you by what it takes.</li>
 * <li>B, Echo: a dash where you look; in the dark you vanish for a moment as you go.</li>
 * </ul>
 */
public final class Keen extends XenoForm {
	// ---- tuning ----
	public static final double FLAP_SPEED = 0.7, FLAP_EASE = 0.25, FLAP_LIFT = 0.15;
	public static final double GLIDE_FALL = -0.1, GLIDE_DRIFT = 0.05;
	public static final double SONAR_RANGE = 24.0;
	public static final int SONAR_TICKS = 120, SONAR_COOLDOWN = 200;
	public static final double SCREECH_RADIUS = 16.0, SCREECH_DOWN = 1.2;
	public static final float SCREECH_DAMAGE = 3.0F;
	public static final int SCREECH_COOLDOWN = 200;
	public static final double DRAIN_REACH = 3.0;
	public static final float DRAIN_DAMAGE = 4.0F, DRAIN_HEAL = 3.0F;
	public static final int DRAIN_COOLDOWN = 60;
	public static final double ECHO_SPEED = 1.8;
	public static final int ECHO_COOLDOWN = 60, ECHO_HIDE = 30, ECHO_DARK = 7;

	public Keen() {
		super("keen", 0.6F, 14.0F, 3.0, 0.08, 0.0, 0.0, Items.LEATHER, 0x4A3C40);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Bat;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.sonar = 0;
		live.perched = false;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					live.sonar = SONAR_TICKS;
					sound(player, SoundEvents.BAT_AMBIENT, 1.0F, 0.5F);
					cooldown(player, live, R, SONAR_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					for (LivingEntity e : around(player, SCREECH_RADIUS)) {
						if (e.onGround() || e.isInWater()) {
							continue;
						}
						e.hurtServer(level, player.damageSources().playerAttack(player), SCREECH_DAMAGE);
						e.removeEffect(MobEffects.SLOW_FALLING);
						e.removeEffect(MobEffects.LEVITATION);
						e.setDeltaMovement(e.getDeltaMovement().x * 0.2, -SCREECH_DOWN, e.getDeltaMovement().z * 0.2);
						e.syncVelocity = true;
						if (e instanceof ServerPlayer flyer && flyer.isFallFlying()) {
							flyer.stopFallFlying();
						}
					}
					ring(level, ParticleTypes.SONIC_BOOM, player.position().add(0.0, 1.0, 0.0), 2.0, 6);
					sound(player, SoundEvents.BAT_HURT, 2.0F, 0.4F);
					cooldown(player, live, G, SCREECH_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				LivingEntity target = aimed(player, DRAIN_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.hurtServer(level, player.damageSources().playerAttack(player), DRAIN_DAMAGE);
				player.heal(DRAIN_HEAL);
				beam(level, target.getBoundingBox().getCenter(), player.getEyePosition(), ParticleTypes.DAMAGE_INDICATOR, 5);
				live.hurt = true;
				sound(player, SoundEvents.BAT_HURT, 1.0F, 1.4F);
				cooldown(player, live, V, DRAIN_COOLDOWN);
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					live.perched = false;
					player.setDeltaMovement(player.getLookAngle().scale(ECHO_SPEED));
					player.syncVelocity = true;
					if (level.getMaxLocalRawBrightness(player.blockPosition()) <= ECHO_DARK) {
						player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ECHO_HIDE, 0, false, false));
					}
					sound(player, SoundEvents.BAT_TAKEOFF, 1.0F, 1.2F);
					cooldown(player, live, B, ECHO_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	private static boolean flapping(ServerPlayer player, XenoformPower.Live live) {
		return !live.perched && !player.onGround() && player.getLastClientInput().jump() && !player.isInWater();
	}

	private static boolean gliding(ServerPlayer player, XenoformPower.Live live) {
		return !live.perched && !player.onGround() && !player.getLastClientInput().shift() && !player.isInWater() && player.getDeltaMovement().y < 0.0;
	}

	/** A solid ceiling right over its head. */
	private static boolean ceiling(ServerPlayer player) {
		AABB box = player.getBoundingBox();
		BlockPos above = BlockPos.containing(player.getX(), box.maxY + 0.3, player.getZ());
		return player.level().getBlockState(above).isFaceSturdy(player.level(), above, net.minecraft.core.Direction.DOWN);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		player.resetFallDistance();
		if (player.tickCount % 100 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, false, false));
		}
		// hang from a ceiling: sneak under one; jump to let go
		if (live.perched) {
			if (!ceiling(player) || player.getLastClientInput().jump()) {
				live.perched = false;
			} else {
				player.setDeltaMovement(Vec3.ZERO);
				player.syncVelocity = true;
			}
		} else if (player.getLastClientInput().shift() && !player.onGround() && ceiling(player)) {
			live.perched = true;
			player.setDeltaMovement(Vec3.ZERO);
			player.syncVelocity = true;
			sound(player, SoundEvents.BAT_AMBIENT, 0.6F, 1.2F);
		} else if (flapping(player, live)) {
			Vec3 want = player.getLookAngle().scale(FLAP_SPEED).add(0.0, FLAP_LIFT, 0.0);
			player.setDeltaMovement(player.getDeltaMovement().lerp(want, FLAP_EASE));
			player.syncVelocity = true;
			if (player.tickCount % 6 == 0) {
				sound(player, SoundEvents.BAT_TAKEOFF, 0.4F, 1.4F);
			}
		} else if (gliding(player, live)) {
			Vec3 m = player.getDeltaMovement();
			Vec3 drift = player.getLookAngle().horizontal().scale(GLIDE_DRIFT);
			player.setDeltaMovement(m.x + drift.x, Math.max(m.y, GLIDE_FALL), m.z + drift.z);
			player.syncVelocity = true;
		}
		if (live.sonar > 0) {
			live.sonar--;
			if (live.sonar % 5 == 0) {
				sonar(player, level);
			}
		}
	}

	/** Every creature in range outlined in pale points, sent to this player alone. */
	private static void sonar(ServerPlayer player, ServerLevel level) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SONAR_RANGE),
			e -> e != player && e.isAlive() && e.distanceTo(player) <= SONAR_RANGE)) {
			AABB b = e.getBoundingBox();
			for (double y : new double[] {b.minY, b.maxY}) {
				level.sendParticles(player, ParticleTypes.END_ROD, true, false, b.minX, y, b.minZ, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(player, ParticleTypes.END_ROD, true, false, b.maxX, y, b.minZ, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(player, ParticleTypes.END_ROD, true, false, b.minX, y, b.maxZ, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(player, ParticleTypes.END_ROD, true, false, b.maxX, y, b.maxZ, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return !player.onGround();
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return flapping(player, live);
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		if (flapping(player, live)) {
			return XenoformState.FLY_WINGS;
		}
		return gliding(player, live) ? XenoformState.FLY_GLIDE : XenoformState.FLY_NONE;
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.SMOKE, player.position().add(0.0, 1.0, 0.0), 40, 0.8, 0.05);
		sound(player, SoundEvents.BAT_TAKEOFF, 1.6F, 0.5F);
		return true;
	}
}

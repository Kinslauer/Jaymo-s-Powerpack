package com.jaymospowers.rig;

import com.jaymospowers.core.Shakes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.87) The takeover save (Mind level 4, whole set): when a hit leaves you under {@link #HEALTH} health and the Core
 * has the charge, the Mind takes control for a moment. Your view snaps to the threat and the screen flashes (client:
 * {@code RigClient}), the thrusters throw you away from it, the Core floods you with absorption, and once the throw is
 * spent the thrusters catch you in a hover. Then it needs {@link #COOLDOWN} ticks to be ready again (kept on the Core,
 * {@link Rig#TAKEOVER_READY}).
 */
public final class RigTakeover {
	/** Below this health (3 hearts), just hurt. */
	static final float HEALTH = 6.0F;
	/** Charge, cooldown (ticks: 5 minutes). */
	static final int COST = 150, COOLDOWN = 6000;
	/** The throw: away (b/t) and up. */
	static final double PUSH = 1.3, LIFT = 0.65;
	/** What counts as the threat: the last attacker within this, else the nearest hostile or projectile within {@link #NEAR}. */
	static final double ATTACKER_RANGE = 24.0, NEAR = 16.0;
	/** Absorption: ticks, amplifier (1: 4 hearts). */
	static final int ABSORB_TICKS = 300, ABSORB_LEVEL = 1;
	/** How long after the throw the hover catches you (ticks). */
	static final int CATCH_AFTER = 14;

	private RigTakeover() {
	}

	/** Seconds until the worn Core's takeover is ready (0: ready, or no Core). */
	static int secondsLeft(ServerPlayer player) {
		ItemStack core = Rig.core(player);
		if (core.isEmpty()) {
			return 0;
		}
		long left = core.getOrDefault(Rig.TAKEOVER_READY, 0L) - player.level().getGameTime();
		// a Core carried between worlds (or a reset clock) shouldn't wait for ever
		return left <= 0 || left > COOLDOWN ? 0 : (int)((left + 19) / 20);
	}

	static void tick(ServerPlayer player) {
		if (!player.isAlive() || player.hurtTime <= 0 || player.getHealth() >= HEALTH || player.isCreative() || !Rig.fullSet(player)
			|| Rig.level(player) < Rig.TAKEOVER_LEVEL || secondsLeft(player) > 0) {
			return;
		}
		ItemStack core = Rig.core(player);
		if (!Rig.spend(player, COST)) {
			return;
		}
		ServerLevel level = player.level();
		core.set(Rig.TAKEOVER_READY, level.getGameTime() + COOLDOWN);

		Vec3 threat = threat(player);
		Vec3 away = threat == null ? Vec3.ZERO : player.position().subtract(threat).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(PUSH);
		player.setDeltaMovement(away.x, LIFT, away.z);
		player.syncVelocity = true;
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ABSORB_TICKS, ABSORB_LEVEL), player);
		RigActives.hoverSoon(player, CATCH_AFTER);

		// everyone sees and hears the thrusters fire
		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 24, 0.25, 0.1, 0.25, 0.12);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 0.6F);
		// and the wearer's screen shows the Mind taking the controls
		Vec3 from = threat == null ? player.position().add(player.getLookAngle()) : threat;
		ServerPlayNetworking.send(player, new RigNet.Takeover(from));
		Shakes.send(player, 3.0F, from);
		RigMind.sayAlways(player, "takeover", 0.7F);
	}

	/** Where the danger is: the last attacker, else the nearest hostile or incoming projectile; null if none is near. */
	private static @Nullable Vec3 threat(ServerPlayer player) {
		LivingEntity attacker = player.getLastHurtByMob();
		if (attacker != null && attacker.isAlive() && attacker.level() == player.level() && attacker.distanceTo(player) < ATTACKER_RANGE) {
			return attacker.position();
		}
		Entity nearest = null;
		double best = NEAR * NEAR;
		for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(NEAR),
			e -> e.isAlive() && (e instanceof Mob mob && mob instanceof Enemy || e instanceof Projectile p && p.getOwner() != player))) {
			double d = entity.distanceToSqr(player);
			if (d < best) {
				best = d;
				nearest = entity;
			}
		}
		return nearest == null ? null : nearest.position();
	}
}

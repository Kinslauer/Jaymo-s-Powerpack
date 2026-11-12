package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.core.Debris;
import com.jaymospowers.core.GroundCracks;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Tripped;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

/**
 * Bulwark's suplex, the second ending of its overhead grab: sneak and press the grab key while holding a creature and
 * instead of hurling it, Bulwark heaves it back over its head and drives it into the ground behind, head first. The
 * impact hurts, cracks the ground in a ring and shakes the screen, and trips or staggers what was held.
 */
public final class BulwarkSuplex {
	// ---- tuning ----
	/** Ticks the heave takes, damage of the impact, the radius of the crack ring (blocks) and the jolt to what stands near. */
	public static final int HEAVE_TICKS = 8;
	public static final float DAMAGE = 16.0F, NEAR_DAMAGE = 5.0F;
	public static final double RING_RADIUS = 3.0, NEAR_PUSH = 0.9;
	/** The length of the trip (ticks) for a humanoid, and the slowness for anything else (ticks, level). */
	public static final int TRIP_TICKS = 70, STUN_TICKS = 50, STUN_LEVEL = 3;
	public static final float SHAKE = 1.4F;
	public static final double SHAKE_RANGE = 18.0;
	public static final int COLOR = 0xB8C4D0;

	private static final class Heave {
		final int target;
		int age;

		Heave(int target) {
			this.target = target;
		}
	}

	private static final Map<UUID, Heave> HEAVES = new HashMap<>();

	private BulwarkSuplex() {
	}

	/** Whether a suplex is under way. */
	public static boolean active(ServerPlayer player) {
		return HEAVES.containsKey(player.getUUID());
	}

	/** Starts the suplex on {@code held} (already let down from the shoulders). */
	public static void begin(ServerPlayer player, LivingEntity held) {
		held.stopRiding();
		HEAVES.put(player.getUUID(), new Heave(held.getId()));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.2F, 0.5F);
	}

	/** Every tick as Bulwark: the held creature swings back over its head and down. */
	public static void tick(ServerPlayer player) {
		Heave heave = HEAVES.get(player.getUUID());
		if (heave == null) {
			return;
		}
		ServerLevel level = player.level();
		if (!(level.getEntity(heave.target) instanceof LivingEntity target) || !target.isAlive()) {
			HEAVES.remove(player.getUUID());
			return;
		}
		heave.age++;
		double progress = Math.min(1.0, heave.age / (double)HEAVE_TICKS);
		Vec3 behind = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(-1.0);
		double reach = player.getBbHeight() * 0.8;
		// from straight overhead, back and down to the ground behind
		double angle = progress * Mth.HALF_PI;
		Vec3 at = new Vec3(player.getX() + behind.x * reach * Math.sin(angle), player.getY() + (player.getBbHeight() + 0.2) * Math.cos(angle),
			player.getZ() + behind.z * reach * Math.sin(angle));
		carry(player, target, at);
		if (heave.age >= HEAVE_TICKS) {
			HEAVES.remove(player.getUUID());
			impact(player, target, at, behind);
		}
	}

	/** Bulwark is over: whoever it was heaving is let go where it is. */
	public static void clear(ServerPlayer player) {
		HEAVES.remove(player.getUUID());
	}

	private static void carry(ServerPlayer player, LivingEntity target, Vec3 to) {
		if (target instanceof ServerPlayer caught) {
			caught.teleportTo(player.level(), to.x, to.y, to.z, Relative.ROTATION, 0.0F, 0.0F, false);
		} else {
			target.setPos(to.x, to.y, to.z);
		}
		target.setDeltaMovement(Vec3.ZERO);
		target.syncVelocity = true;
		target.resetFallDistance();
	}

	private static void impact(ServerPlayer player, LivingEntity target, Vec3 at, Vec3 behind) {
		ServerLevel level = player.level();
		target.hurtServer(level, player.damageSources().playerAttack(player), BulwarkFury.boost(player, DAMAGE));
		if (target.isAlive() && !Tripped.bind(target, player, TRIP_TICKS)) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_TICKS, STUN_LEVEL, false, true));
		}
		target.setDeltaMovement(behind.x * 0.2, 0.15, behind.z * 0.2);
		target.syncVelocity = true;
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(RING_RADIUS), e -> e != player && e != target
			&& e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e) && !e.isPassengerOfSameVehicle(player))) {
			near.hurtServer(level, player.damageSources().playerAttack(player), BulwarkFury.boost(player, NEAR_DAMAGE));
			Vec3 away = near.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? behind : away.normalize();
			near.setDeltaMovement(away.x * NEAR_PUSH, 0.4, away.z * NEAR_PUSH);
			near.syncVelocity = true;
		}
		BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
		GroundCracks.crack(level, under, level.getRandom(), Math.max(2, (int)Math.round(RING_RADIUS)));
		Debris.smash(level, at, 1.4, player);
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), (float)RING_RADIUS, COLOR);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.6F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
		Shakes.heavy(player, at, SHAKE_RANGE, SHAKE);
	}
}

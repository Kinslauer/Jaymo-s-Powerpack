package com.jaymospowers.power.xenoform.minds;

import com.jaymospowers.power.wind.WindNet;
import com.jaymospowers.power.xenoform.GreatFoes;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The small helpers every mind and shape move uses (the same ones the forms have, for classes that aren't forms). */
final class MindsKit {
	private MindsKit() {
	}

	/** The player's own pets and mounts, and anyone on their team. */
	static boolean spared(ServerPlayer player, Entity e) {
		return e instanceof OwnableEntity pet && pet.getOwner() == player || player.isAlliedTo(e);
	}

	/** A creature the moves may hit: alive, not the player, a spectator, a stand, a passenger, a pet or a team mate. */
	static boolean fair(ServerPlayer player, Entity e) {
		return e instanceof LivingEntity living && living.isAlive() && e != player && !e.isSpectator() && !(e instanceof ArmorStand)
			&& !e.isPassengerOfSameVehicle(player) && !spared(player, e);
	}

	/** Every fair creature within {@code radius} of {@code at}. */
	static List<LivingEntity> near(ServerPlayer player, Vec3 at, double radius) {
		return player.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
			e -> fair(player, e) && e.position().add(0.0, e.getBbHeight() * 0.5, 0.0).distanceToSqr(at) <= radius * radius);
	}

	/** The fair creature under the crosshair within {@code reach}, or null. */
	static @Nullable LivingEntity aimed(ServerPlayer player, double reach, Predicate<Entity> filter) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));
		AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box, e -> fair(player, e) && filter.test(e), 0.3F);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/** Shoves {@code target} away from {@code from}, {@code out} along the ground and {@code up} upward. */
	static void shove(LivingEntity target, Vec3 from, double out, double up) {
		if (GreatFoes.is(target)) {
			return;
		}
		Vec3 push = target.position().subtract(from).horizontal();
		push = push.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : push.normalize();
		target.setDeltaMovement(target.getDeltaMovement().add(push.scale(out)).add(0.0, up, 0.0));
		target.syncVelocity = true;
	}

	static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	static void soundAt(net.minecraft.server.level.ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** The player's look flattened to the ground, unit length. */
	static Vec3 flat(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		return flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
	}

	/** A coloured frame round the screen of {@code player} (the wind's screen effect, open to any power). */
	static void vignette(ServerPlayer player, int color, int ticks) {
		ServerPlayNetworking.send(player, new WindNet.ScreenFx(WindNet.ScreenFx.VIGNETTE, color, ticks));
	}
}

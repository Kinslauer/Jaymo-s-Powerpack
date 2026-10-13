package com.jaymoskingdoms;

import ckathode.weaponmod.entity.projectile.EntityProjectile;
import ckathode.weaponmod.item.RangedComponent;
import com.jaymoskingdoms.Classes.PlayerClass;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.6.0, stage 3) A Marksman's guns, which are Balkon's WeaponMod's (so this is only touched while
 * {@link Javelins#WEAPONMOD_LOADED}): a shot from a gun in {@code #jaymoskingdoms:guns} hits harder, flies further and
 * bends a little toward the nearest living thing near its line (the aim assist); the gun reloads and winds up faster
 * ({@code RangedComponentMixin}). Every number is in {@link ClassPerks}.
 */
public final class WeaponModGuns {
	private WeaponModGuns() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof EntityProjectile<?> shot && shot.getOwner() instanceof ServerPlayer sp && Classes.is(sp, PlayerClass.MARKSMAN)
				&& shot.getWeaponItem() != null && shot.getWeaponItem().is(ClassPerks.GUNS)) {
				aim(level, shot, sp);
			}
		});
	}

	/** The shot's velocity, bent toward the nearest target and lengthened; its damage raised. */
	private static void aim(ServerLevel level, EntityProjectile<?> shot, ServerPlayer shooter) {
		Vec3 v = shot.getDeltaMovement();
		double speed = v.length();
		if (speed <= 0.0) {
			return;
		}
		Vec3 dir = v.scale(1.0 / speed);
		Vec3 eye = shot.position();
		double cosLimit = Math.cos(Math.toRadians(ClassPerks.MARKSMAN_AIM_DEGREES));
		LivingEntity best = null;
		double bestCos = cosLimit;
		AABB box = new AABB(eye, eye.add(dir.scale(ClassPerks.MARKSMAN_AIM_REACH))).inflate(4.0);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != shooter && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(shooter))) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d < 1.0 || d > ClassPerks.MARKSMAN_AIM_REACH) {
				continue;
			}
			double cos = to.dot(dir) / d;
			if (cos > bestCos && shooter.hasLineOfSight(target)) {
				bestCos = cos;
				best = target;
			}
		}
		if (best != null) {
			dir = best.getBoundingBox().getCenter().subtract(eye).normalize();
		}
		shot.setDeltaMovement(dir.scale(speed * (1.0 + ClassPerks.MARKSMAN_RANGE)));
		shot.setExtraDamage(shot.extraDamage + shot.getDamage(shooter) * (float) ClassPerks.MARKSMAN_DAMAGE);
	}

	/**
	 * From {@code RangedComponentMixin}: the ticks {@code gun} has left of its use ({@code count} counts down from the
	 * use duration) as a Marksman's gun reads them, so it reloads and winds up {@link ClassPerks#MARKSMAN_RELOAD} faster.
	 */
	public static int quicker(RangedComponent component, ItemStack gun, Entity user, int count) {
		if (!(user instanceof ServerPlayer sp) || !Classes.is(sp, PlayerClass.MARKSMAN) || !gun.is(ClassPerks.GUNS)) {
			return count;
		}
		int use = component.getUseDuration(gun);
		int elapsed = use - count;
		return use - (int) (elapsed / (1.0 - ClassPerks.MARKSMAN_RELOAD));
	}
}

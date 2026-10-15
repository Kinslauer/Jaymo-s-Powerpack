package com.jaymospowers.power.chrono.future;

import com.jaymospowers.armory.TaczGuns;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Guns in the future: the dead and the echoes there carry our TaCZ flintlocks (musket, blunderbuss, flintlock pistol;
 * the pack has no modern guns) and fire them at what they're fighting. A mob can't pull a TaCZ trigger, so the shot is
 * ours: a ball (or the blunderbuss's spread of shot) traced from the muzzle, stopped by walls, hurting the first thing
 * it meets, with smoke, a flash and the bang. Then a reload. No ammunition is used. Nothing here without TaCZ.
 */
public final class FutureGuns {
	public static final String TAG = "jaymospowers.gunner";

	/** A kind of gun: its TaCZ name, how far it shoots, the damage per ball, balls per shot, stray (degrees), reload (ticks). */
	record Gun(String name, double range, float damage, int balls, float spread, int reload) {
	}

	static final Gun MUSKET = new Gun("musket", 32.0, 9.0F, 1, 1.5F, 100);
	static final Gun PISTOL = new Gun("flintlock_pistol", 18.0, 6.0F, 1, 3.0F, 60);
	static final Gun BLUNDERBUSS = new Gun("blunderbuss", 10.0, 2.5F, 6, 9.0F, 90);
	static final Gun[] GUNS = {MUSKET, PISTOL, BLUNDERBUSS};
	/** Nobody fires at point blank (they'd rather swing). */
	static final double NEAREST = 2.5;
	/** The chance a gun drops from a dead gunner. */
	static final float DROP_CHANCE = 0.1F;

	private static final Map<Mob, Long> NEXT = new WeakHashMap<>();

	private FutureGuns() {
	}

	/** The gun a mob holds, if it's one of ours. */
	static Gun held(Mob mob) {
		ItemStack hand = mob.getMainHandItem();
		for (Gun gun : GUNS) {
			if (TaczGuns.is(hand, gun.name)) {
				return gun;
			}
		}
		return null;
	}

	/** Hands a mob a random one of our guns (nothing without TaCZ). Returns whether it's armed. */
	public static boolean arm(Mob mob, RandomSource random) {
		ItemStack gun = TaczGuns.gun(GUNS[random.nextInt(GUNS.length)].name);
		if (gun.isEmpty()) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, gun);
		mob.setDropChance(EquipmentSlot.MAINHAND, DROP_CHANCE);
		mob.addTag(TAG);
		NEXT.put(mob, mob.level().getGameTime() + 20 + random.nextInt(60));
		return true;
	}

	/** Every few ticks for a gunner: fire at its target when loaded, in range and in sight. */
	public static void tick(ServerLevel level, Mob mob) {
		Gun gun = held(mob);
		LivingEntity foe = mob.getTarget();
		if (gun == null || foe == null || !foe.isAlive()) {
			return;
		}
		long now = level.getGameTime();
		if (now < NEXT.getOrDefault(mob, 0L)) {
			return;
		}
		double d = mob.distanceTo(foe);
		if (d < NEAREST || d > gun.range || !mob.hasLineOfSight(foe)) {
			return;
		}
		NEXT.put(mob, now + gun.reload + level.getRandom().nextInt(30));
		fire(level, mob, foe, gun);
	}

	private static void fire(ServerLevel level, Mob mob, LivingEntity foe, Gun gun) {
		RandomSource random = level.getRandom();
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		Vec3 eye = mob.getEyePosition();
		Vec3 aim = foe.getBoundingBox().getCenter().subtract(eye).normalize();
		Vec3 muzzle = eye.add(aim.scale(0.9));
		for (int i = 0; i < gun.balls; i++) {
			Vec3 dir = stray(aim, gun.spread, random);
			Vec3 end = eye.add(dir.scale(gun.range));
			HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
			if (wall.getType() != HitResult.Type.MISS) {
				end = wall.getLocation();
			}
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(mob, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != mob, eye.distanceToSqr(end));
			if (hit != null) {
				end = hit.getLocation();
				hit.getEntity().hurtServer(level, level.damageSources().mobProjectile(mob, mob), gun.damage);
			} else if (wall.getType() != HitResult.Type.MISS) {
				level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 3, 0.05, 0.05, 0.05, 0.1);
			}
			// the smoke trail of the ball
			double len = end.distanceTo(muzzle);
			for (double t = 0.0; t < len; t += 1.5) {
				Vec3 p = muzzle.add(dir.scale(t));
				level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.02);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.01);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE,
			gun == BLUNDERBUSS ? 1.2F : 0.9F, (gun == PISTOL ? 1.9F : 1.6F) + random.nextFloat() * 0.2F);
	}

	private static Vec3 stray(Vec3 aim, float degrees, RandomSource random) {
		double s = Math.toRadians(degrees);
		return aim.add(random.nextGaussian() * s, random.nextGaussian() * s, random.nextGaussian() * s).normalize();
	}
}

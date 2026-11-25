package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "add the boomerang to desert bandit and other ranger npc weapon pool". WeaponMod's boomerang (only while it is loaded,
 * through {@link WeaponModBoomerangs}) is carried by desert bandits (a masked bandit of a bandit camp or hold on arid land: sand or
 * badlands), forest rangers ({@link ForestRangers}) and orc rangers ({@link Orcs#RANGER}, folk or MCA). Each carries a few
 * ({@link MobAmmo.Kind#BOOMERANGS}); one is spent per throw.
 * <p>
 * It is thrown at a foe {@link WeaponSense.Arm#THROWN}'s band away (6 to 16 blocks) in sight. The boomerang flies back on its own
 * (WeaponMod's flight); one that reaches its thrower is caught ({@link #CATCH_RANGE}) and counts again, so it is thrown again; one
 * that doesn't (it struck something and fell, or landed short) lies where it falls, and the thrower goes to pick it up when its foe
 * isn't on top of it ({@link WeaponSense}, {@link MobAmmo}): a thrown weapon is gone until it is picked up.
 */
final class Boomerangs {
	/** Between throws (ticks), plus up to {@link #JITTER} more. */
	static final int COOLDOWN = 50, JITTER = 30;
	/** How hard it is thrown, and how wide it may stray (degrees). */
	static final float SPEED = 1.5F, SPREAD = 3.0F;
	/** A returning boomerang this near its thrower's body (blocks) is caught, once it has been out this long (ticks); one still out after {@link #LIFETIME} is let be. */
	static final double CATCH_RANGE = 1.8;
	static final int CATCH_AFTER = 8, LIFETIME = 400;
	/** What each kind of carrier's boomerang is made of (WeaponMod's materials). */
	static final String BANDIT_MATERIAL = "iron", RANGER_MATERIAL = "wood", ORC_MATERIAL = "stone";

	/** A boomerang in the air and who threw it. */
	private record Flight(Projectile boomerang, Mob thrower, long since) {
	}

	private static final List<Flight> FLIGHTS = new ArrayList<>();
	private static final java.util.Map<Mob, Long> NEXT = new java.util.WeakHashMap<>();

	private Boomerangs() {
	}

	/** What {@code mob}'s boomerang is made of, or null if it doesn't carry one (never without WeaponMod). */
	static @Nullable String material(Mob mob) {
		if (!Javelins.WEAPONMOD_LOADED || !(mob.level() instanceof ServerLevel level)) {
			return null;
		}
		if (mob.entityTags().contains(ForestRangers.TAG)) {
			return RANGER_MATERIAL;
		}
		if (mob instanceof Worker w && Orcs.ranged(w.role())) {
			return ORC_MATERIAL;
		}
		if (Bandits.wears(mob)) {
			Palette land = Palette.at(level, mob.blockPosition());
			if (land == Palette.DESERT || land == Palette.BADLANDS) {
				return BANDIT_MATERIAL;
			}
		}
		return null;
	}

	/** Whether {@code mob} carries boomerangs and has one left. */
	static boolean armed(Mob mob) {
		return material(mob) != null && MobAmmo.has(mob, MobAmmo.Kind.BOOMERANGS);
	}

	/** Throws one at {@code foe} if it is ready and there is one left; true if thrown. The caller has checked the range, the line and the foe. */
	static boolean throwAt(ServerLevel level, Mob mob, LivingEntity foe) {
		String material = material(mob);
		long now = level.getGameTime();
		if (material == null || now < NEXT.getOrDefault(mob, 0L) || !MobAmmo.has(mob, MobAmmo.Kind.BOOMERANGS)) {
			return false;
		}
		Projectile thrown = WeaponModBoomerangs.create(level, mob, material);
		if (thrown == null) {
			return false;
		}
		NEXT.put(mob, now + COOLDOWN + level.getRandom().nextInt(JITTER));
		// (MobAmmo) one of the ones it carries; if it isn't caught it lies where it falls till someone picks it up
		MobAmmo.spend(mob, MobAmmo.Kind.BOOMERANGS);
		MobAmmo.canBePickedUp(thrown);
		Javelins.mark(thrown);
		double dx = foe.getX() - mob.getX(), dz = foe.getZ() - mob.getZ();
		double dy = foe.getY(0.5) - thrown.getY();
		double flat = Math.sqrt(dx * dx + dz * dz);
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		Projectile.spawnProjectileUsingShoot(thrown, level, net.minecraft.world.item.ItemStack.EMPTY, dx, dy + flat * 0.05, dz, SPEED, SPREAD);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 0.8F, 1.3F + level.getRandom().nextFloat() * 0.2F);
		FLIGHTS.add(new Flight(thrown, mob, now));
		return true;
	}

	/** From the server tick: a boomerang back at its thrower is caught (and counts again), one that is gone or past its time is forgotten. */
	static void tick() {
		for (int i = FLIGHTS.size() - 1; i >= 0; i--) {
			Flight f = FLIGHTS.get(i);
			Projectile b = f.boomerang();
			Mob mob = f.thrower();
			if (b.isRemoved() || !mob.isAlive() || mob.level() != b.level() || b.level().getGameTime() - f.since() > LIFETIME) {
				FLIGHTS.remove(i);
				continue;
			}
			if (b.level().getGameTime() - f.since() >= CATCH_AFTER && !Downed.isDowned(mob)
				&& b.position().distanceToSqr(mob.getBoundingBox().getCenter()) <= CATCH_RANGE * CATCH_RANGE) {
				FLIGHTS.remove(i);
				b.discard();
				MobAmmo.give(mob, MobAmmo.Kind.BOOMERANGS, 1);
				mob.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
				mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.5F, 1.2F);
			}
		}
	}
}

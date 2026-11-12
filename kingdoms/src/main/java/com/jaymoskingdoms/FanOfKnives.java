package com.jaymoskingdoms;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Rogue mobs throw a Fan of Knives (Andy, 2026-11-12: yes, they should), the same rule as the players' spell: up to
 * {@link #MOST} of their own knives, never more than they carry ({@link MobAmmo.Kind#KNIVES}). Each lands as the knife it
 * was, for anyone to pick up; the rogue gathers his again once nothing is after him. Thrown by a camp's Knife chief
 * ({@link Crews}) when his foe is too far to lunge at, and by an assassin ({@link Assassins}) when his mark backs off.
 */
public final class FanOfKnives {
	/** Knives in one fan, its spread (degrees), how near and how far the foe must be (blocks), each knife's damage. */
	static final int MOST = 3;
	static final float SPREAD = 24.0F, DAMAGE = 4.0F;
	static final double MIN = 4.0, MAX = 14.0;
	/** What a rogue throws when the knife in his hand isn't one he'd throw (Powers' iron dagger). */
	static final Identifier KNIFE = Identifier.fromNamespaceAndPath("jaymospowers", "iron_dagger");

	private FanOfKnives() {
	}

	/** {@code mob} throws up to three knives at {@code foe}; true if any went. */
	public static boolean cast(ServerLevel level, Mob mob, LivingEntity foe) {
		double d = mob.distanceTo(foe);
		if (d < MIN || d > MAX || !mob.hasLineOfSight(foe) || !MobAmmo.has(mob, MobAmmo.Kind.KNIVES)) {
			return false;
		}
		Item knife = MobAmmo.knife(mob.getMainHandItem()) ? mob.getMainHandItem().getItem() : BuiltInRegistries.ITEM.getValue(KNIFE);
		if (knife == Items.AIR) {
			return false;
		}
		int thrown = 0;
		for (int i = 0; i < MOST && MobAmmo.spend(mob, MobAmmo.Kind.KNIVES); i++) {
			float yaw = (i - (MOST - 1) / 2.0F) * SPREAD / (MOST - 1);
			CaptainWays.throwBlade(level, mob, foe, new ItemStack(knife), DAMAGE, yaw, false);
			thrown++;
		}
		if (thrown == 0) {
			return false;
		}
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 0.8F, 1.6F);
		return true;
	}
}

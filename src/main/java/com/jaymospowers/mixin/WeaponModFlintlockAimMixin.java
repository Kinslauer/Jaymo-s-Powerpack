package com.jaymospowers.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * WeaponMod's flintlock pistol takes its aim quicker. Andy, 2026-11-22: "flintlock should also have a quicker aim
 * acquisition as in you need to hold right click for less time to get an accurate shot". Its fire reads the ticks it was
 * held ready like a bow's draw (a full second for its tightest spread); here that hold reads as {@link #AIM_SPEED} times
 * as long, so the tightest spread comes after {@code 20 / AIM_SPEED} ticks. A Marksman's quicker wind-up (Kingdoms'
 * {@code RangedComponentMixin}, on the release) comes on top.
 */
@Mixin(targets = "ckathode.weaponmod.item.RangedCompFlintlock")
abstract class WeaponModFlintlockAimMixin {
	/** How much faster the pistol's aim settles than WeaponMod's own (2.5: the tightest spread after 8 ticks, 0.4 s). */
	private static final double AIM_SPEED = 2.5;
	/** WeaponMod's use duration for every gun: {@code count} counts down from it while the gun is held. */
	private static final int USE_TICKS = 72000;

	@ModifyVariable(method = "fire", at = @At("HEAD"), argsOnly = true)
	private int jaymospowers$quickerAim(int count, ItemStack stack, Level level, LivingEntity shooter) {
		int held = USE_TICKS - count;
		return USE_TICKS - (int) Math.min(USE_TICKS, held * AIM_SPEED);
	}
}

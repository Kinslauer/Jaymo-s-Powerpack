package com.jaymoskingdoms.mixin.weaponmod;

import ckathode.weaponmod.item.RangedComponent;
import com.jaymoskingdoms.WeaponModGuns;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * (0.6.0) A Marksman's guns reload and wind up faster ({@link WeaponModGuns#quicker}): the ticks the gun has been held
 * read as more than they are, both while it reloads and when it's let go to fire.
 */
@Mixin(value = RangedComponent.class, remap = false)
abstract class RangedComponentMixin {
	@ModifyVariable(method = "onUsingTick", at = @At("HEAD"), argsOnly = true)
	private int jaymoskingdoms$quickerReload(int count, Level level, LivingEntity user, ItemStack stack) {
		return WeaponModGuns.quicker((RangedComponent) (Object) this, stack, user, count);
	}

	@ModifyVariable(method = "releaseUsing", at = @At("HEAD"), argsOnly = true)
	private int jaymoskingdoms$quickerWindup(int count, ItemStack stack, Level level, LivingEntity user) {
		return WeaponModGuns.quicker((RangedComponent) (Object) this, stack, user, count);
	}
}

package com.jaymospowers.mixin;

import com.jaymospowers.core.Kneel;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** (0.17.7) A bow or crossbow loosed from one knee ({@link Kneel}) flies truer: its spread × {@link Kneel#STEADY}. */
@Mixin(ProjectileWeaponItem.class)
abstract class KneelShotMixin {
	@ModifyVariable(method = "shoot", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float jaymospowers$steadyKnee(float inaccuracy, @Local(argsOnly = true, ordinal = 0) LivingEntity shooter) {
		return shooter instanceof Player player && Kneel.kneeling(player) ? inaccuracy * Kneel.STEADY : inaccuracy;
	}
}

package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.MobAmmo;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A skeleton whose tattered quiver is empty fights with its bow in its hands, as if it held no bow ({@link MobAmmo}). */
@Mixin(AbstractSkeleton.class)
abstract class SkeletonQuiverMixin {
	@ModifyExpressionValue(method = "reassessWeaponGoal", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
	private boolean jaymoskingdoms$arrowsLeft(boolean holdsBow) {
		return holdsBow && MobAmmo.has((AbstractSkeleton) (Object) this, MobAmmo.Kind.ARROWS);
	}
}

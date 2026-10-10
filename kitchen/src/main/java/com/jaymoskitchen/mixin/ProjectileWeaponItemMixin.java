package com.jaymoskitchen.mixin;

import com.jaymoskitchen.quiver.Quivers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** When a bow fires arrows it got from a quiver, take them out of the quiver. */
@Mixin(ProjectileWeaponItem.class)
abstract class ProjectileWeaponItemMixin {
	@Inject(method = "useAmmo", at = @At("RETURN"))
	private static void jaymoskitchen$drainQuiver(ItemStack weapon, ItemStack projectile, LivingEntity holder,
		boolean forceInfinite, CallbackInfoReturnable<ItemStack> cir) {
		Quivers.afterUseAmmo(projectile);
	}
}

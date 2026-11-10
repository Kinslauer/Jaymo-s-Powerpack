package com.jaymospowers.mixin;

import com.jaymospowers.armory.GunBlast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A WeaponMod gun has fired (its own recoil, sound and puff are done): the musket's, blunderbuss's and flintlock's
 * fire, smoke and fire animation ({@link GunBlast}). Its other ranged weapons are left as they are.
 */
@Mixin(targets = "ckathode.weaponmod.item.RangedComponent")
abstract class WeaponModGunBlastMixin {
	@Inject(method = "postShootingEffects", at = @At("TAIL"))
	private void jaymospowers$blast(ItemStack stack, LivingEntity shooter, Level level, CallbackInfo ci) {
		GunBlast.weaponModFired(shooter, stack);
	}
}

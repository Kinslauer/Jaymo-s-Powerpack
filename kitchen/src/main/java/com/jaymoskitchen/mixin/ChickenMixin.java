package com.jaymoskitchen.mixin;

import com.jaymoskitchen.farm.LayingHens;
import net.minecraft.world.entity.animal.chicken.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Laying hens ({@link LayingHens}): a fed hen lays at dawn, an unfed one's egg timer runs slower. */
@Mixin(Chicken.class)
abstract class ChickenMixin {
	@Inject(method = "aiStep", at = @At("HEAD"))
	private void jaymoskitchen$layingHen(CallbackInfo ci) {
		LayingHens.tick((Chicken) (Object) this);
	}
}

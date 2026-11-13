package com.jaymospowers.client.tundra.mixin;

import com.jaymospowers.client.tundra.TundraGrass;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The tundra's and the lichen woodland's grass is a gradient, not each biome's flat colour (see {@link TundraGrass}). Runs
 * before Kitchen's seasonal tint, which sits on top of what this returns.
 */
@Mixin(value = Biome.class, priority = 500)
abstract class TundraGrassMixin {
	@Inject(method = "getGrassColor", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$tundraGradient(double x, double z, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(TundraGrass.grass((Biome)(Object)this, x, z, cir.getReturnValueI()));
	}
}

package com.jaymoskitchen.mixin;

import com.jaymoskitchen.corpse.Corpses;
import com.jaymoskitchen.corpse.Gibs;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.5.21) Andy: "creepers should not leave corpses when they explode." A creeper that goes off is marked to leave no
 * body (should anything let it die the usual way after its blast) and bursts into a few chunks of itself ({@link Gibs}).
 */
@Mixin(Creeper.class)
abstract class CreeperMixin {
	@Inject(method = "explodeCreeper", at = @At("TAIL"))
	private void jaymoskitchen$burst(CallbackInfo ci) {
		Creeper self = (Creeper) (Object) this;
		if (!self.level().isClientSide()) {
			self.addTag(Corpses.NO_CORPSE_TAG);
			Gibs.creeperWentOff(self);
		}
	}
}

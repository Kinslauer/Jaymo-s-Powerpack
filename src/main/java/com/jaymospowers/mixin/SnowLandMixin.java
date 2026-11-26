package com.jaymospowers.mixin;

import com.jaymospowers.core.FallingSnow;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A falling layer of snow lands its own way (FallingSnow#land): onto the snow there, layer on layer, or melting in water. */
@Mixin(FallingBlockEntity.class)
abstract class SnowLandMixin {
	@Inject(method = "tick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/state/BlockState;canBeReplaced(Lnet/minecraft/world/item/context/BlockPlaceContext;)Z"), cancellable = true)
	private void jaymospowers$snowLands(CallbackInfo ci) {
		if (FallingSnow.land((FallingBlockEntity) (Object) this)) {
			ci.cancel();
		}
	}
}

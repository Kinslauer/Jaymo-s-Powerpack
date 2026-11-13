package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.sailing.ShipWorks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A workshop aboard a ship ({@link ShipWorks}) has no block in the world, so when a player fills it from the deck its
 * "I changed" mustn't land on the world block at its stand-in position.
 */
@Mixin(BlockEntity.class)
abstract class BlockEntityShipWorksMixin {
	@Inject(method = "setChanged()V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$aboardChanged(CallbackInfo ci) {
		if (ShipWorks.aboard((BlockEntity)(Object)this)) {
			ci.cancel();
		}
	}
}

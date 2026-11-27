package com.jaymospowers.mixin;

import com.jaymospowers.alchemy.bomb.BombStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Portal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What the last two bombs stop ({@link BombStatus}): a snuffed body (or one standing in a snuffed place) does not go through a
 * portal, and a body under the healing-stopper dust is not invisible.
 */
@Mixin(Entity.class)
abstract class BombStatusMixin {
	@Inject(method = "setAsInsidePortal", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$snuffedPortal(Portal portal, BlockPos pos, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (self instanceof LivingEntity living ? BombStatus.snuffed(living) : BombStatus.zone(self.level(), pos)) {
			ci.cancel();
		}
	}

	@Inject(method = "isInvisible", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$unhealedShows(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && (Object) this instanceof LivingEntity living && BombStatus.unhealed(living)) {
			cir.setReturnValue(false);
		}
	}
}

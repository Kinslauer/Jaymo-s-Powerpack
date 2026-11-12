package com.jaymospowers.gear.arrow.mixin;

import com.jaymospowers.gear.arrow.HeadFx;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A headed arrow's landing (see HeadFx): after it strikes something, and before it sticks in a block. */
@Mixin(AbstractArrow.class)
abstract class AbstractArrowHeadMixin {
	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void jaymospowers$headStrikes(EntityHitResult hit, CallbackInfo ci) {
		HeadFx.hitEntity((AbstractArrow) (Object) this, hit);
	}

	@Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$headLands(BlockHitResult hit, CallbackInfo ci) {
		if (HeadFx.hitBlock((AbstractArrow) (Object) this, hit)) {
			ci.cancel();
		}
	}
}

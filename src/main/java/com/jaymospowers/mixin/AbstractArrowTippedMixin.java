package com.jaymospowers.mixin;

import com.jaymospowers.fighting.TippedClouds;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A tipped arrow that has struck a body or a block leaves a small lingering cloud of its potion (see TippedClouds). */
@Mixin(AbstractArrow.class)
abstract class AbstractArrowTippedMixin {
	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void jaymospowers$cloudOnBody(EntityHitResult hit, CallbackInfo ci) {
		TippedClouds.hit((AbstractArrow) (Object) this, hit.getLocation());
	}

	@Inject(method = "onHitBlock", at = @At("TAIL"))
	private void jaymospowers$cloudOnBlock(BlockHitResult hit, CallbackInfo ci) {
		TippedClouds.hit((AbstractArrow) (Object) this, hit.getLocation());
	}
}

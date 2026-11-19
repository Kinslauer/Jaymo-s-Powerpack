package com.jaymospowers.mixin;

import com.jaymospowers.combat.MetalHits;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An arrow striking a metal block throws a few sparks (MetalHits#arrowOnBlock), read before it stops and sticks. */
@Mixin(AbstractArrow.class)
abstract class ArrowSparkMixin {
	@Inject(method = "onHitBlock", at = @At("HEAD"))
	private void jaymospowers$sparkOnMetal(BlockHitResult hit, CallbackInfo ci) {
		MetalHits.arrowOnBlock((AbstractArrow) (Object) this, hit);
	}
}

package com.jaymospowers.mixin;

import com.jaymospowers.core.Impacts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Landings and heavy footsteps shake the screen (see Impacts). */
@Mixin(Entity.class)
abstract class ImpactShakeMixin {
	@Inject(method = "checkFallDamage", at = @At("HEAD"))
	private void jaymospowers$landed(double ya, boolean onGround, BlockState onState, BlockPos pos, CallbackInfo ci) {
		Entity self = (Entity)(Object)this;
		if (onGround && self.fallDistance > 0.0 && self instanceof LivingEntity living && !self.level().isClientSide()) {
			Impacts.landed(living, self.fallDistance);
		}
	}

	@Inject(method = "walkingStepSound", at = @At("HEAD"))
	private void jaymospowers$stepped(BlockPos onPos, BlockState onState, CallbackInfo ci) {
		if ((Object)this instanceof LivingEntity living && !living.level().isClientSide()) {
			Impacts.stepped(living);
		}
	}
}

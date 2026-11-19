package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.WebSkim;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A web slinger skimming (see {@link WebSkim}) stands on the water's surface, the way Overclock's runners do. */
@Mixin(LivingEntity.class)
abstract class WebSkimMixin {
	@Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$skimOnWater(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && fluid.is(FluidTags.WATER) && WebSkim.skimming(player)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getLiquidCollisionShape", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$skimShape(CallbackInfoReturnable<VoxelShape> cir) {
		if ((Object) this instanceof Player player && WebSkim.skimming(player)) {
			cir.setReturnValue(Shapes.block());
		}
	}
}

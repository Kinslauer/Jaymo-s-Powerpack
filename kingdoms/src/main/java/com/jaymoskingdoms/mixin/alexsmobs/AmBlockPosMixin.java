package com.jaymoskingdoms.mixin.alexsmobs;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Alex's Mobs finds "the block at a point" by cutting each coordinate toward zero ({@code (int) x}), so wherever x or z
 * is below zero it looks one block over. Its anaconda's body pieces use it to stand on the ground and to bend over
 * steps, so on that side of the world they sank into the ground and came apart from each other (Andy, 2026-11-25:
 * "it's clipping into the ground, its segmented like ... detached"). This gives the block the point is really in, as
 * the game does ({@link BlockPos#containing}). Skipped without Alex's Mobs.
 */
@Pseudo
@Mixin(targets = "com.github.alexthe666.alexsmobs.misc.AMBlockPos", remap = false)
abstract class AmBlockPosMixin {
	@Inject(method = "fromCoords(DDD)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymoskingdoms$floor(double x, double y, double z, CallbackInfoReturnable<BlockPos> cir) {
		cir.setReturnValue(BlockPos.containing(x, y, z));
	}
}

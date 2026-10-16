package com.jaymospowers.mixin;

import com.jaymospowers.core.Leaves;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leaves are soft ({@link Leaves}), so mobs path through them as through open air: they wade through hedges and walk
 * out of bushes, and never plan a walk across the top of leaves they'd sink into. (Vanilla calls leaves a wall to go
 * around and a floor to stand on, so a mob that sank in found every way out blocked.)
 */
@Mixin(WalkNodeEvaluator.class)
abstract class LeavesPathMixin {
	@Inject(method = "getPathTypeFromState", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$softLeaves(BlockGetter level, BlockPos pos, CallbackInfoReturnable<PathType> cir) {
		if (Leaves.is(level.getBlockState(pos))) {
			cir.setReturnValue(PathType.OPEN);
		}
	}
}

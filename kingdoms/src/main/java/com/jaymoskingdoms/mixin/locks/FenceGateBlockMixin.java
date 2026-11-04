package com.jaymoskingdoms.mixin.locks;

import com.jaymoskingdoms.Locks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.497) Redstone and wind charges don't open a locked fence gate. Player use goes through the use callback ({@link Locks}); the folk's
 * {@code Doorways} and MCA have their own checks.
 */
@Mixin(FenceGateBlock.class)
abstract class FenceGateBlockMixin {
	@Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$lockedRedstone(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston, CallbackInfo ci) {
		if (!level.isClientSide() && !state.getValue(FenceGateBlock.OPEN) && Locks.shut(level, pos)) {
			ci.cancel();
		}
	}

	@ModifyExpressionValue(method = "onExplosionHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Explosion;canTriggerBlocks()Z"))
	private boolean jaymoskingdoms$lockedWind(boolean original, BlockState state, ServerLevel level, BlockPos pos, Explosion explosion) {
		return original && (state.getValue(FenceGateBlock.OPEN) || !Locks.shut(level, pos));
	}
}

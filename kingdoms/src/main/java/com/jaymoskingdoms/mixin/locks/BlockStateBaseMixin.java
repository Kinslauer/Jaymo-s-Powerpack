package com.jaymoskingdoms.mixin.locks;

import com.jaymoskingdoms.Locks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.497) Breaking a locked block without its key is slow ({@link Locks#slowBreak}). Hooked here, on the public method
 * of the state, so blocks that override the protected one are covered, and the crack matches on both sides.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
	@ModifyReturnValue(method = "getDestroyProgress", at = @At("RETURN"))
	private float jaymoskingdoms$locked(float original, Player player, BlockGetter level, BlockPos pos) {
		return Locks.slowBreak((BlockState)(Object)this, player, level, pos, original);
	}

	@Inject(method = "affectNeighborsAfterRemoval", at = @At("HEAD"))
	private void jaymoskingdoms$forgetLock(ServerLevel level, BlockPos pos, boolean movedByPiston, CallbackInfo ci) {
		Locks.forgetRemoved(level, pos, (BlockState)(Object)this);
	}
}

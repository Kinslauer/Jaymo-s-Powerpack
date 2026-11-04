package com.jaymoskingdoms.mixin.locks;

import com.jaymoskingdoms.Locks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.497) A locked trapdoor stays shut for whoever has no key (use, wind charge, MCA go through {@code toggle}; redstone through {@code neighborChanged}) ({@link Locks}). */
@Mixin(TrapDoorBlock.class)
abstract class TrapDoorBlockMixin {
	@Inject(method = "toggle", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$locked(BlockState state, Level level, BlockPos pos, @Nullable Player player, CallbackInfo ci) {
		if (!state.getValue(TrapDoorBlock.OPEN) && Locks.barred(level, pos, player)) {
			ci.cancel();
		}
	}

	@Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$lockedRedstone(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston, CallbackInfo ci) {
		if (!level.isClientSide() && !state.getValue(TrapDoorBlock.OPEN) && Locks.shut(level, pos)) {
			ci.cancel();
		}
	}
}

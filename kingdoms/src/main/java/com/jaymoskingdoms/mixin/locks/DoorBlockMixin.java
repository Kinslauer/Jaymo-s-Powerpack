package com.jaymoskingdoms.mixin.locks;

import com.jaymoskingdoms.Locks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.497) A locked door stays shut for whoever has no key: villagers' AI, wind charges, MCA and our own folk all open it
 * through {@code setOpen}; redstone through {@code neighborChanged} ({@link Locks}).
 */
@Mixin(DoorBlock.class)
abstract class DoorBlockMixin {
	@Inject(method = "setOpen", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$locked(@Nullable Entity source, Level level, BlockState state, BlockPos pos, boolean shouldOpen, CallbackInfo ci) {
		if (shouldOpen && Locks.barred(level, pos, source)) {
			ci.cancel();
		}
	}

	@Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$lockedRedstone(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston, CallbackInfo ci) {
		if (!level.isClientSide() && !state.getValue(DoorBlock.OPEN) && Locks.shut(level, pos)) {
			ci.cancel();
		}
	}
}

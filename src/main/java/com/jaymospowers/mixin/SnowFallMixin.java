package com.jaymospowers.mixin;

import com.jaymospowers.core.FallingSnow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Snow left on nothing falls instead of vanishing (FallingSnow): when the block under a layer goes, the layer waits a
 * moment ({@link FallingSnow#DELAY}) and drops like sand. Snow that can't lie on what is under it (ice, a slab's top
 * half...) still breaks as it always has.
 */
@Mixin(SnowLayerBlock.class)
abstract class SnowFallMixin extends Block {
	SnowFallMixin(Properties properties) {
		super(properties);
	}

	@Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$fall(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random, CallbackInfoReturnable<BlockState> cir) {
		if (direction == Direction.DOWN && FallingSnow.loose(level, pos, neighbour)) {
			ticks.scheduleTick(pos, this, FallingSnow.DELAY);
			cir.setReturnValue(state);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		FallingSnow.tick(state, level, pos);
	}
}

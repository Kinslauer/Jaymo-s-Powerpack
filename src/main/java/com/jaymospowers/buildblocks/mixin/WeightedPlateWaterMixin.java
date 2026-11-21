package com.jaymospowers.buildblocks.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The {@code waterlogged} property on a weighted pressure plate; the rest is {@link BasePlateWaterMixin}. */
@Mixin(WeightedPressurePlateBlock.class)
public abstract class WeightedPlateWaterMixin {
	@Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
	private void jaymospowers$property(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
		builder.add(BlockStateProperties.WATERLOGGED);
	}
}

package com.jaymospowers.buildblocks.mixin;

import com.jaymospowers.buildblocks.Waterlogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Doors can be waterlogged (see {@link Waterlogging}): the property, the water in the space, and a bucket's say. */
@Mixin(DoorBlock.class)
public abstract class DoorWaterMixin extends Block implements SimpleWaterloggedBlock {
	public DoorWaterMixin(Properties properties) {
		super(properties);
	}

	@Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
	private void jaymospowers$property(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
		builder.add(BlockStateProperties.WATERLOGGED);
	}

	@Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$wetWhenPlaced(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
		cir.setReturnValue(Waterlogging.placed(cir.getReturnValue(), context));
	}

	/** The upper half is set from the lower, which may be wet when the space above it is not, or the other way round. */
	@Inject(method = "setPlacedBy", at = @At("HEAD"))
	private void jaymospowers$noteUpper(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
		Waterlogging.noteUpper(level, pos);
	}

	@Inject(method = "setPlacedBy", at = @At("TAIL"))
	private void jaymospowers$fixUpper(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
		Waterlogging.applyUpper(level, pos);
	}

	@Inject(method = "updateShape", at = @At("HEAD"))
	private void jaymospowers$keepFlowing(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random, CallbackInfoReturnable<BlockState> cir) {
		Waterlogging.tick(state, level, ticks, pos);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return Waterlogging.fluid(state, super.getFluidState(state));
	}

	@Override
	public boolean canPlaceLiquid(LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
		return Waterlogging.canPlace(state, fluid);
	}
}

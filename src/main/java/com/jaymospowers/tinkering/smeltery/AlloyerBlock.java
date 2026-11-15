package com.jaymospowers.tinkering.smeltery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The alloyer ({@link AlloyerEntity}). An empty hand reads its pool; whoever sets it down or tends it is its keeper. */
public class AlloyerBlock extends MoltenBlock {
	public AlloyerBlock(Block.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(MelterBlock.FACING, Direction.NORTH).setValue(MelterBlock.LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(MelterBlock.FACING, MelterBlock.LIT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(MelterBlock.FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		if (by instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof AlloyerEntity alloyer) {
			alloyer.keep(player);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof AlloyerEntity alloyer) {
			alloyer.keep(server);
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AlloyerEntity(pos, state);
	}
}

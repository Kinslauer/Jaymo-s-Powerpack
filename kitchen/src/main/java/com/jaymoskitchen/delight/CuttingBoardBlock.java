package com.jaymoskitchen.delight;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's cutting board, MIT; its model and art, our own code.) Right-click it with something that can be
 * cut ({@link Chopping}) to lay it on the board, then right-click with a knife ({@link Delight#KNIVES}: the primitive,
 * bone and iron knives) to cut it up; an empty hand takes it back. Sneak-click it with an empty hand for the prep
 * board and the chopped dishes, as at Cooking for Blockheads' cutting board.
 */
public class CuttingBoardBlock extends BaseEntityBlock {
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 1, 15);

	public CuttingBoardBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HorizontalDirectionalBlock.FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CuttingBoardEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CuttingBoardEntity board)) {
			return InteractionResult.PASS;
		}
		ItemStack lying = board.item();
		if (!lying.isEmpty()) {
			if (!held.is(Delight.KNIVES)) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (level instanceof ServerLevel server) {
				cut(server, pos, board, player, hand, held);
			}
			return InteractionResult.SUCCESS;
		}
		if (held.is(Delight.KNIVES) || Chopping.cut(held).isEmpty()) {
			if (!held.isEmpty() && !held.is(Delight.KNIVES) && !level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.board.cant_cut"));
			}
			return held.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			board.setItem(held.copyWithCount(1));
			held.consume(1, player);
			level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CuttingBoardEntity board) || board.item().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack taken = board.take();
			if (!player.getInventory().add(taken)) {
				Block.popResource(level, pos.above(), taken);
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static void cut(ServerLevel level, BlockPos pos, CuttingBoardEntity board, Player player, InteractionHand hand, ItemStack knife) {
		ItemStack lying = board.item();
		List<ItemStack> pieces = Chopping.cut(lying);
		if (pieces.isEmpty()) {
			// (laid there before a recipe changed) hand it back
			Block.popResourceFromFace(level, pos, Direction.UP, board.take());
			return;
		}
		board.take();
		for (ItemStack piece : pieces) {
			Block.popResourceFromFace(level, pos, Direction.UP, piece);
		}
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, lying.getItem()), pos.getX() + 0.5, pos.getY() + 0.15, pos.getZ() + 0.5,
			8, 0.15, 0.05, 0.15, 0.05);
		level.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 0.7F, 1.4F);
		knife.hurtAndBreak(1, player, hand);
	}
}

package com.jaymoskitchen.butchery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.5.51) A blood grate: a shallow iron basin with a grate over it, set under a meat hook to catch a carcass's blood as
 * it's bled out. It holds {@link #MAX} levels of {@link Butchery#MB_PER_LEVEL} each; a glass bottle takes one out as a
 * {@link BloodBottleItem}, and a comparator reads how full it is. Blood magic draws from it too ({@link Butchery#drawGrates}).
 */
public class BloodGrateBlock extends Block {
	public static final int MAX = 8;
	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, MAX);
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 5.0, 16.0);

	public BloodGrateBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!held.is(Items.GLASS_BOTTLE)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		int now = state.getValue(LEVEL);
		if (now <= 0) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server) {
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Butchery.BLOOD_BOTTLE)));
			server.setBlockAndUpdate(pos, state.setValue(LEVEL, now - 1));
			server.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 0.7F);
			com.jaymoskitchen.KitchenBlood.send(server, pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5, 4, 0.2, 0.05, 0.2, 0.0);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.blood_grate.holds", state.getValue(LEVEL) * Butchery.MB_PER_LEVEL,
				MAX * Butchery.MB_PER_LEVEL));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return Math.round(state.getValue(LEVEL) * 15.0F / MAX);
	}
}

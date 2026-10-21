package com.jaymospowers.tinkering.kiln;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * What's set down in a kiln to fire ({@link Kiln}): drawn as the items themselves. Right-click with more of the same to
 * add (sneak for all you hold), with an empty hand to take it all out; breaking it drops it.
 */
public class KilnLoadBlock extends Block implements EntityBlock {
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 8, 14);

	public KilnLoadBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new KilnLoadEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != Kiln.KILN_LOAD_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<KilnLoadEntity>) KilnLoadEntity::serverTick;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof KilnLoadEntity load && load.accepts(stack)) {
			if (!level.isClientSide()) {
				load.insert(stack, player.isShiftKeyDown(), player.hasInfiniteMaterials());
				level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.8F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof KilnLoadEntity load) {
			if (!level.isClientSide()) {
				ItemStack taken = load.take();
				if (!taken.isEmpty()) {
					player.getInventory().placeItemBackInInventory(taken, Prediction.SERVER_ONLY);
				}
				level.removeBlock(pos, false);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}

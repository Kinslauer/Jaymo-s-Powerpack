package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.Tinkering;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A casting table (a cast on top, metal poured over it) or basin (metal poured in makes a block). Right-click with a
 * cast or an item to set it down; with an empty hand to take the result, then the cast.
 */
public class CastingBlock extends Block implements EntityBlock {
	private static final VoxelShape TABLE = Shapes.join(Shapes.or(Block.box(0, 10, 0, 16, 16, 16), Block.box(0, 0, 0, 4, 10, 4),
		Block.box(12, 0, 0, 16, 10, 4), Block.box(0, 0, 12, 4, 10, 16), Block.box(12, 0, 12, 16, 10, 16)),
		Block.box(1, 15, 1, 15, 16, 15), BooleanOp.ONLY_FIRST);
	private static final VoxelShape BASIN = Shapes.join(Block.box(0, 0, 0, 16, 16, 16), Block.box(2, 4, 2, 14, 16, 14), BooleanOp.ONLY_FIRST);

	public final boolean basin;

	public CastingBlock(Block.Properties properties, boolean basin) {
		super(properties);
		this.basin = basin;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return basin ? BASIN : TABLE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CastingEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != Tinkering.CASTING_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<CastingEntity>) CastingEntity::serverTick;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof CastingEntity casting && !casting.busy() && casting.cast().isEmpty()
			&& (stack.getItem() instanceof CastItem || CastItem.Shape.of(stack) != null) && !basin) {
			if (!level.isClientSide()) {
				casting.putCast(stack);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof CastingEntity casting) {
			if (!level.isClientSide()) {
				ItemStack taken = casting.take();
				if (!taken.isEmpty()) {
					player.getInventory().placeItemBackInInventory(taken, Prediction.SERVER_ONLY);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}

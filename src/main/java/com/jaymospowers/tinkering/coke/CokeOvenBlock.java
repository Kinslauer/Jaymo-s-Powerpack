package com.jaymospowers.tinkering.coke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The coke oven's mouth ({@link CokeOvenEntity}): set it in the middle of one wall of the 3x3x3, facing out. It glows
 * while it bakes, and the glass gauge beside the mouth shows how full the creosote tank is ({@link #FILL}). Right-click
 * with logs or coal to load it, with a bucket or clay bucket to draw off creosote, with an empty hand to take what's
 * baked (sneaking: what's still waiting).
 */
public class CokeOvenBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	/** The creosote gauge, empty to full. */
	public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 4);

	public CokeOvenBlock(Block.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(FILL, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT, FILL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CokeOvenEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != CokeAndSteel.COKE_OVEN_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<CokeOvenEntity>) CokeOvenEntity::serverTick;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CokeOvenEntity oven) || stack.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		ItemStack filled = oven.filled(stack);
		if (filled != null) {
			if (!level.isClientSide()) {
				oven.draw();
				player.setItemInHand(hand, net.minecraft.world.item.ItemUtils.createFilledResult(stack, player, filled));
				level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 0.8F, 0.8F);
			}
			return InteractionResult.SUCCESS;
		}
		if (oven.accepts(stack)) {
			if (!level.isClientSide()) {
				oven.insert(stack, player.hasInfiniteMaterials());
				level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 0.8F, 0.7F);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CokeOvenEntity oven)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack taken = oven.take(player.isShiftKeyDown());
			if (!taken.isEmpty()) {
				player.getInventory().placeItemBackInInventory(taken, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Smoke from the mouth and the crackle of a slow fire while it bakes. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		if (random.nextDouble() < 0.1) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 0.7F, false);
		}
		Direction out = state.getValue(FACING);
		double x = pos.getX() + 0.5 + out.getStepX() * 0.52, z = pos.getZ() + 0.5 + out.getStepZ() * 0.52;
		level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 0.8, z, 0.0, 0.02, 0.0);
	}
}

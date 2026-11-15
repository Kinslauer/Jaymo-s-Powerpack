package com.jaymoskitchen.mill.steam;

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
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A small steam boiler (GT New Horizons picks, patch 8): a riveted copper or bronze drum ({@link BoilerMetal}) set on a
 * firebox, with a gauge glass on its front ({@link #FACING}) that shows how much water is in it ({@link #WATER}). Filled
 * a bucket at a time (a bucket, Jaymo's Powers' clay bucket: item tag {@code jaymoskitchen:boiler_water}) or by any
 * pipe that speaks Fabric's fluid transfer. The fire below boils it and a steam engine beside or on top of it runs on
 * the steam ({@link BoilerEntity}).
 *
 * <p>Its look says how it is ({@link #HEAT}): cold, hot (it ticks, and steams and bubbles while it boils), and red-hot,
 * when it has boiled dry over a fire and the empty drum glows. Pour water into it then and it bursts.
 */
public class BoilerBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** The gauge glass: empty, low, half, full. */
	public static final IntegerProperty WATER = IntegerProperty.create("water", 0, 3);
	/** Cold, hot, red-hot (dry over the fire). */
	public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 2);

	public final BoilerMetal metal;

	public BoilerBlock(BoilerMetal metal, BlockBehaviour.Properties properties) {
		super(properties);
		this.metal = metal;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATER, 0).setValue(HEAT, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WATER, HEAT);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** A bucket of water (or a clay one) pours in, if there's room for all of it. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Steam.BOILER_WATER) || !(level.getBlockEntity(pos) instanceof BoilerEntity boiler)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			if (!boiler.pour(BoilerEntity.BUCKET)) {
				return InteractionResult.FAIL;
			}
			level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
			if (!player.hasInfiniteMaterials()) {
				ItemStackTemplate left = stack.getCraftingRemainder();
				ItemStack empty = left == null ? ItemStack.EMPTY : left.create();
				stack.shrink(1);
				if (stack.isEmpty()) {
					player.setItemInHand(hand, empty);
				} else if (!empty.isEmpty()) {
					player.getInventory().placeItemBackInInventory(empty, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Hot: it ticks, and while it boils steam curls off the top and it bubbles. Red-hot: it smokes and hisses. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int heat = state.getValue(HEAT);
		if (heat == 0) {
			return;
		}
		double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
		if (heat == 1 && state.getValue(WATER) > 0) {
			level.addParticle(ParticleTypes.WHITE_SMOKE, x + (random.nextDouble() - 0.5) * 0.6, y, z + (random.nextDouble() - 0.5) * 0.6,
				0.0, 0.04 + random.nextDouble() * 0.03, 0.0);
			if (random.nextInt(4) == 0) {
				level.playLocalSound(x, y, z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.4F, 0.6F + random.nextFloat() * 0.3F, false);
			}
		}
		if (heat == 2) {
			level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.9, pos.getY() + random.nextDouble(),
				z + (random.nextDouble() - 0.5) * 0.9, 0.0, 0.01, 0.0);
			if (random.nextInt(3) == 0) {
				level.playLocalSound(x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.15F, 1.8F + random.nextFloat() * 0.2F, false);
			}
		}
		if (random.nextInt(heat == 2 ? 3 : 10) == 0) {
			// the metal ticking as it heats
			level.playLocalSound(x, y - 0.5, z, SoundEvents.COPPER_STEP, SoundSource.BLOCKS, 0.3F, 1.6F + random.nextFloat() * 0.3F, false);
		}
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BoilerEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, Steam.BOILER_ENTITY, BoilerEntity::serverTick);
	}
}

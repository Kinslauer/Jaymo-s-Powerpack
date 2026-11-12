package com.jaymoskitchen.climate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The hearth and the icebox (Andy's Hardrock picks, after Cold Sweat's hearth and icebox, GPL-3.0, ideas only; our own
 * code and art). Fed fuel, the hearth fills the closed room around it with warmth and the icebox fills it with cool air
 * ({@link HearthEntity}, {@link Warmth#room}). The hearth burns coal, charcoal and wood, the icebox ice and snow.
 */
public class HearthBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** True for the icebox. */
	public final boolean cold;

	public HearthBlock(boolean cold, BlockBehaviour.Properties properties) {
		super(properties);
		this.cold = cold;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HearthEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, Warmth.HEARTH_ENTITY, HearthEntity::serverTick);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		int burn = Warmth.burnTicks(held, cold);
		if (burn <= 0) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.getBlockEntity(pos) instanceof HearthEntity entity && !level.isClientSide()) {
			if (!entity.feed(burn)) {
				return InteractionResult.PASS;
			}
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof HearthEntity entity) {
			if (player instanceof net.minecraft.server.level.ServerPlayer server) {
				entity.open(server);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** The screen's Add fuel button: the player at {@code pos} feeds it from their pockets. */
	static void feed(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
		if (player.level().isLoaded(pos) && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0
			&& player.level().getBlockEntity(pos) instanceof HearthEntity entity) {
			entity.feedFrom(player);
			entity.open(player);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
		if (cold) {
			// cold air spilling over the lid
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.SNOWFLAKE, x + (random.nextDouble() - 0.5) * 0.8, y + 1.02, z + (random.nextDouble() - 0.5) * 0.8,
					0.0, -0.01, 0.0);
			}
			return;
		}
		Direction facing = state.getValue(FACING);
		double out = 0.52;
		double fx = x + facing.getStepX() * out + (random.nextDouble() - 0.5) * 0.4 * Math.abs(facing.getStepZ());
		double fz = z + facing.getStepZ() * out + (random.nextDouble() - 0.5) * 0.4 * Math.abs(facing.getStepX());
		level.addParticle(ParticleTypes.FLAME, fx, y + 0.25 + random.nextDouble() * 0.2, fz, 0.0, 0.0, 0.0);
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 1.1, z, 0.0, 0.05, 0.0);
		}
		if (random.nextInt(10) == 0) {
			com.jaymoskitchen.sound.KitchenSounds.fireLocal(level, x, y, z);
		}
	}
}

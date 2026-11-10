package com.jaymospowers.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * The sluice (Andy's TFC Hardrock picks): a plank trough. Fill it with gravel or sand (up to {@link #MOST}), set it beside
 * running water (a flowing block, not a still source) and it washes one load every {@link #WASH_TICKS} ticks on its own,
 * a little better than panning ({@link #BETTER}), dropping what it finds beside it.
 */
public class SluiceBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final int MOST = 8;
	public static final IntegerProperty LOAD = IntegerProperty.create("load", 0, MOST);
	/** Ticks a load takes, and how much better a sluice finds than a pan. */
	static final int WASH_TICKS = 100;
	static final float BETTER = 1.5F;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

	public SluiceBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOAD, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LOAD);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!(held.getItem() instanceof net.minecraft.world.item.BlockItem item) || !Panning.pannable(item.getBlock().defaultBlockState())) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		int load = state.getValue(LOAD);
		if (load >= MOST) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			server.setBlock(pos, state.setValue(LOAD, load + 1), Block.UPDATE_ALL);
			server.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
			server.scheduleTick(pos, this, WASH_TICKS);
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int load = state.getValue(LOAD);
		if (load <= 0) {
			return;
		}
		if (running(level, pos)) {
			level.setBlock(pos, state.setValue(LOAD, load - 1), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.4F, 1.4F);
			level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.0);
			ItemStack found = Panning.wash(random, BETTER);
			if (!found.isEmpty()) {
				Direction out = state.getValue(FACING);
				Block.popResource(level, pos.relative(out), found);
			}
		}
		if (load - 1 > 0 || !running(level, pos)) {
			level.scheduleTick(pos, this, WASH_TICKS);
		}
	}

	/** Flowing water beside it or above it. */
	static boolean running(Level level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (d == Direction.DOWN) {
				continue;
			}
			FluidState fluid = level.getFluidState(pos.relative(d));
			if (fluid.is(FluidTags.WATER) && !fluid.isSource()) {
				return true;
			}
		}
		return false;
	}
}

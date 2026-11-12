package com.jaymospowers.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Andy: "you can keep the 3 x 3 that you made for a larger bonfire type size requiring 9x the material". Nine
 * {@link FirepitBlock firepits} laid in a 3x3 ({@link #tryMerge}, unlit and without grates) become one bonfire: this
 * centre block, holding the fire, its fuel and the campfire block entity, and eight {@link BonfireEdgeBlock edges} carrying
 * the big stone ring. Breaking any part breaks it all.
 * <p>
 * A campfire underneath like the firepit (lit the same ways, cooks four foods, the spit goes on top), but it gives full
 * light, burns harder, and eats its fuel {@link #HUNGER} times as fast as a firepit; it starts with the nine pits' fuel.
 */
public class BonfireBlock extends CampfireBlock {
	// ---- tuning ----
	public static final int MAX_FUEL = 63;
	/** How many times as fast as a firepit a bonfire burns through its fuel. */
	public static final int HUNGER = 9;
	public static final int BURN_TICKS = FirepitBlock.BURN_TICKS / HUNGER;
	public static final int LIGHT = 15;

	public static final IntegerProperty FUEL = IntegerProperty.create("fuel", 0, MAX_FUEL);
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);

	public BonfireBlock(BlockBehaviour.Properties properties) {
		super(true, 2, properties);
		this.registerDefaultState(this.defaultBlockState().setValue(FUEL, 0).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FUEL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** If the firepit at pos completes a 3x3 of unlit, grateless firepits, turns them into a bonfire. */
	public static void tryMerge(Level level, BlockPos pos) {
		for (int cx = -1; cx <= 1; cx++) {
			for (int cz = -1; cz <= 1; cz++) {
				BlockPos centre = pos.offset(cx, 0, cz);
				if (allPits(level, centre)) {
					merge(level, centre);
					return;
				}
			}
		}
	}

	private static boolean allPits(Level level, BlockPos centre) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockState state = level.getBlockState(centre.offset(dx, 0, dz));
				if (!state.is(GroundSticks.FIREPIT) || state.getValue(LIT) || state.getValue(FirepitBlock.GRATE) != FirepitBlock.Grate.NONE
					|| state.getValue(WATERLOGGED)) {
					return false;
				}
			}
		}
		return true;
	}

	private static void merge(Level level, BlockPos centre) {
		int fuel = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				fuel += level.getBlockState(centre.offset(dx, 0, dz)).getValue(FirepitBlock.FUEL);
			}
		}
		for (BonfireEdgeBlock.Part part : BonfireEdgeBlock.Part.values()) {
			level.setBlock(centre.offset(part.dx, 0, part.dz),
				GroundSticks.BONFIRE_EDGE.defaultBlockState().setValue(BonfireEdgeBlock.PART, part), Block.UPDATE_ALL);
		}
		level.setBlock(centre, GroundSticks.BONFIRE.defaultBlockState().setValue(FUEL, Math.min(MAX_FUEL, fuel)), Block.UPDATE_ALL);
		level.playSound(null, centre, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.gameEvent(null, GameEvent.BLOCK_CHANGE, centre);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		InteractionResult fed = FirepitBlock.feed(stack, state, level, pos, player, FUEL, MAX_FUEL);
		return fed != null ? fed : super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		boolean wasLit = oldState.is(this) && oldState.getValue(LIT);
		boolean lit = state.getValue(LIT);
		if (lit && !wasLit) {
			level.scheduleTick(pos, this, state.getValue(FUEL) == 0 ? 10 : BURN_TICKS);
		}
		if (lit != wasLit && !level.isClientSide()) {
			// the ring glows and burns with the fire
			for (BonfireEdgeBlock.Part part : BonfireEdgeBlock.Part.values()) {
				BlockPos edge = pos.offset(part.dx, 0, part.dz);
				BlockState there = level.getBlockState(edge);
				if (there.is(GroundSticks.BONFIRE_EDGE) && there.getValue(BonfireEdgeBlock.LIT) != lit) {
					level.setBlock(edge, there.setValue(BonfireEdgeBlock.LIT, lit), Block.UPDATE_ALL);
				}
			}
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		BonfireEdgeBlock.collapse(level, pos);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		super.tick(state, level, pos, random);
		if (!state.getValue(LIT)) {
			return;
		}
		int fuel = state.getValue(FUEL) - 1;
		if (fuel <= 0 || level.isRainingAt(pos.above()) && random.nextInt(HUNGER) == 0) {
			level.setBlock(pos, state.setValue(LIT, false).setValue(FUEL, Math.max(0, fuel)), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
			level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
			return;
		}
		level.setBlock(pos, state.setValue(FUEL, fuel), Block.UPDATE_ALL);
		level.scheduleTick(pos, this, BURN_TICKS);
	}
}

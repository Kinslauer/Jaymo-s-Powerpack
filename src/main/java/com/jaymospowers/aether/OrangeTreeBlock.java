package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The orange tree: a two-block bush that ripens through ages 0 to 3 on random ticks in light, like a sweet berry bush.
 * Ripe (age 3), a right-click or breaking it gives 1 to 3 oranges (the loot table gives the breaking ones); picked, it
 * drops back to age 1 and grows again.
 */
public class OrangeTreeBlock extends DoublePlantBlock {
	// ---- tuning ----
	/** A random tick ripens it with 1 chance in this many, with this much light above the bush. */
	public static final int GROW_CHANCE = 5, GROW_LIGHT = 9;
	/** The age a picked tree goes back to, and the oranges a right-click gives. */
	public static final int PICKED_AGE = 1, MIN_ORANGES = 1, MAX_ORANGES = 3;

	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
	private static final int RIPE = 3;
	private static final VoxelShape LOWER = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
	private static final VoxelShape UPPER = Block.box(2.0, 0.0, 2.0, 14.0, 10.0, 14.0);

	public OrangeTreeBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(AGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(AGE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER : LOWER;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(AGE) < RIPE;
	}

	private static BlockPos lowerPos(BlockState state, BlockPos pos) {
		return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
	}

	/** Sets the age on both halves (whichever of them is still this block). */
	private void setAge(ServerLevel level, BlockPos lower, int age) {
		for (BlockPos p : new BlockPos[] {lower, lower.above()}) {
			BlockState s = level.getBlockState(p);
			if (s.is(this)) {
				level.setBlock(p, s.setValue(AGE, age), Block.UPDATE_CLIENTS);
			}
		}
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int age = state.getValue(AGE);
		BlockPos lower = lowerPos(state, pos);
		if (age < RIPE && random.nextInt(GROW_CHANCE) == 0 && level.getMaxLocalRawBrightness(lower.above(2)) >= GROW_LIGHT) {
			this.setAge(level, lower, age + 1);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(AGE) < RIPE) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			int count = MIN_ORANGES + server.getRandom().nextInt(MAX_ORANGES - MIN_ORANGES + 1);
			Block.popResource(server, pos, new ItemStack(AetherFlora.ORANGE, count));
			server.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + server.getRandom().nextFloat() * 0.4F);
			this.setAge(server, lowerPos(state, pos), PICKED_AGE);
		}
		return InteractionResult.SUCCESS;
	}
}

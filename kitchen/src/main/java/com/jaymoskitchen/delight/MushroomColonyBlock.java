package com.jaymoskitchen.delight;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's mushroom colonies, MIT; their art, our own code.) A mushroom grown on rich soil into a colony: it
 * thickens over time (bone meal helps), shears take one mushroom off at a time and a knife cuts the whole lot, and a
 * mature one picked with shears comes up whole, to be set down elsewhere.
 */
public class MushroomColonyBlock extends VegetationBlock implements BonemealableBlock {
	/** It may be planted where the light is below this (like any mushroom), unless set on rich soil and the like. */
	public static final int PLACING_LIGHT_LEVEL = 13;
	public static final IntegerProperty COLONY_AGE = BlockStateProperties.AGE_3;
	private static final int MAX_AGE = 3;
	private static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[] {
		Block.box(4.0D, 0.0D, 4.0D, 12.0D, 8.0D, 12.0D),
		Block.box(3.0D, 0.0D, 3.0D, 13.0D, 10.0D, 13.0D),
		Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D),
		Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D)
	};
	/** A growing random tick thickens it with chance 1 in this, on soil that grows colonies. */
	private static final int GROW_ODDS = 4;
	/** Bone meal thickens it by this many stages (1 to this). */
	private static final int BONEMEAL_MAX = 2;
	/** Block particles: a snip with shears, a chop with a knife. */
	private static final int SNIP_PARTICLES = 3, CHOP_PARTICLES = 10;

	public final Holder<Item> mushroomType;

	public MushroomColonyBlock(Holder<Item> mushroomType, Properties properties) {
		super(properties);
		this.mushroomType = mushroomType;
		registerDefaultState(stateDefinition.any().setValue(COLONY_AGE, 0));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		int age = state.getValue(COLONY_AGE);
		if (age > 0) {
			ItemStack mushrooms = getCloneItemStack(level, pos, state, false);
			if (held.is(Items.SHEARS) || held.is(ConventionalItemTags.SHEAR_TOOLS)) {
				level.setBlock(pos, state.setValue(COLONY_AGE, age - 1), Block.UPDATE_CLIENTS);
				level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
				popResource(level, pos, mushrooms);
				if (level instanceof ServerLevel server) {
					held.hurtAndBreak(1, player, hand);
					server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						SNIP_PARTICLES, 0.1, 0.1, 0.1, 0.001D);
				}
				return InteractionResult.SUCCESS;
			}
			if (held.is(Delight.KNIVES)) {
				mushrooms.setCount(age);
				level.setBlock(pos, state.setValue(COLONY_AGE, 0), Block.UPDATE_CLIENTS);
				level.playSound(null, pos, getSoundType(state).getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
				popResource(level, pos, mushrooms);
				if (level instanceof ServerLevel server) {
					held.hurtAndBreak(1, player, hand);
					server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						CHOP_PARTICLES, 0.2, 0.2, 0.2, 0.1D);
				}
				return InteractionResult.SUCCESS;
			}
		}
		return super.useItemOn(held, state, level, pos, player, hand, hit);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE_BY_AGE[state.getValue(COLONY_AGE)];
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.isSolidRender();
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState floor = level.getBlockState(pos.below());
		if (floor.is(BlockTags.OVERRIDES_MUSHROOM_LIGHT_REQUIREMENT) || floor.is(DelightFarm.GROWS_MUSHROOM_COLONIES)) {
			return true;
		}
		return level.getRawBrightness(pos, 0) < PLACING_LIGHT_LEVEL && mayPlaceOn(state, level, pos);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int age = state.getValue(COLONY_AGE);
		if (age < MAX_AGE && level.getBlockState(pos.below()).is(DelightFarm.GROWS_MUSHROOM_COLONIES) && random.nextInt(GROW_ODDS) == 0) {
			level.setBlock(pos, state.setValue(COLONY_AGE, age + 1), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(mushroomType.value());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COLONY_AGE);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		return state.getValue(COLONY_AGE) < MAX_AGE;
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		int age = Math.min(MAX_AGE, state.getValue(COLONY_AGE) + Mth.nextInt(level.getRandom(), 1, BONEMEAL_MAX));
		level.setBlock(pos, state.setValue(COLONY_AGE, age), Block.UPDATE_CLIENTS);
	}
}

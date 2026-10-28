package com.jaymospowers.frost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An icicle (26.4's, built for 26.3): a spike of ice hanging from a ceiling or standing on a floor, a base, middles and a
 * tip, like pointed dripstone. A hanging one under ice or snow grows down in the dark, up to {@link #MAX_LENGTH} long;
 * one that loses its ceiling falls and hurts what it lands on, then shatters. Landing on a standing tip counts the fall
 * double. Any icicle melts near a bright light ({@link #MELT_LIGHT} block light or more) or where water boils away (the
 * Nether).
 */
public class IcicleBlock extends Block implements Fallable {
	// ---- tuning ----
	public static final int MAX_LENGTH = 5;
	/** One random tick in this many, a dark hanging tip grows (dripstone is 1 in ~5.7 with water; ice is slower). */
	public static final int GROW_ODDS = 12;
	/** It only grows with block light at most this, and melts at this much or more. */
	public static final int GROW_LIGHT = 4, MELT_LIGHT = 11;
	/** A falling icicle hurts this much a block fallen, up to the cap; landing on a standing tip counts the fall this much. */
	public static final float FALL_HURT = 2.0F, LAND_MULTIPLIER = 2.0F;
	public static final int FALL_HURT_MAX = 30;

	public static final EnumProperty<Direction> TIP_DIRECTION = BlockStateProperties.VERTICAL_DIRECTION;
	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

	private static final VoxelShape BASE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
	private static final VoxelShape MIDDLE = Block.box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);
	private static final VoxelShape TIP_DOWN = Block.box(5.0, 5.0, 5.0, 11.0, 16.0, 11.0);
	private static final VoxelShape TIP_UP = Block.box(5.0, 0.0, 5.0, 11.0, 11.0, 11.0);

	/** Where along the icicle a block is: the base meets the ceiling (or floor), the tip is the point. */
	public enum Part implements StringRepresentable {
		BASE("base"), MIDDLE("middle"), TIP("tip");

		private final String name;

		Part(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public IcicleBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(TIP_DIRECTION, Direction.DOWN).setValue(PART, Part.TIP));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TIP_DIRECTION, PART);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(PART)) {
			case BASE -> BASE;
			case MIDDLE -> MIDDLE;
			case TIP -> state.getValue(TIP_DIRECTION) == Direction.DOWN ? TIP_DOWN : TIP_UP;
		};
	}

	private boolean same(BlockState other, Direction tip) {
		return other.is(this) && other.getValue(TIP_DIRECTION) == tip;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction tip = state.getValue(TIP_DIRECTION);
		BlockPos support = pos.relative(tip.getOpposite());
		BlockState held = level.getBlockState(support);
		return this.same(held, tip) || held.isFaceSturdy(level, support, tip);
	}

	/** The part a block at {@code pos} pointing {@code tip} should be, from its neighbours. */
	private Part partAt(LevelReader level, BlockPos pos, Direction tip) {
		if (this.same(level.getBlockState(pos.relative(tip)), tip)) {
			return this.same(level.getBlockState(pos.relative(tip.getOpposite())), tip) ? Part.MIDDLE : Part.BASE;
		}
		return Part.TIP;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction tip = context.getNearestLookingVerticalDirection().getOpposite();
		for (Direction dir : new Direction[] {tip, tip.getOpposite()}) {
			BlockState state = this.defaultBlockState().setValue(TIP_DIRECTION, dir);
			if (this.canSurvive(state, level, pos)) {
				return state.setValue(PART, this.partAt(level, pos, dir));
			}
		}
		return null;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (direction.getAxis() != Direction.Axis.Y) {
			return state;
		}
		if (!this.canSurvive(state, level, pos)) {
			// (falls or breaks on the next tick: no entities from in here)
			ticks.scheduleTick(pos, this, 1);
			return state;
		}
		return state.setValue(PART, this.partAt(level, pos, state.getValue(TIP_DIRECTION)));
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (this.canSurvive(state, level, pos)) {
			return;
		}
		if (state.getValue(TIP_DIRECTION) == Direction.DOWN) {
			FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
			falling.setHurtsEntities(FALL_HURT, FALL_HURT_MAX);
			falling.disableDrop();
		} else {
			level.destroyBlock(pos, true);
		}
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(PART) == Part.TIP;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int light = level.getBrightness(LightLayer.BLOCK, pos);
		if (light >= MELT_LIGHT || level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.8F);
			level.destroyBlock(pos, false);
			return;
		}
		if (state.getValue(TIP_DIRECTION) != Direction.DOWN || light > GROW_LIGHT || random.nextInt(GROW_ODDS) != 0) {
			return;
		}
		BlockPos below = pos.below();
		if (!level.getBlockState(below).isAir() || !level.getBlockState(below.below()).isAir()) {
			return;
		}
		// walk up to the root: only so long, and only under ice or snow
		BlockPos root = pos;
		int length = 1;
		while (this.same(level.getBlockState(root.above()), Direction.DOWN)) {
			root = root.above();
			if (++length >= MAX_LENGTH) {
				return;
			}
		}
		BlockState ceiling = level.getBlockState(root.above());
		if (ceiling.is(BlockTags.ICE) || ceiling.is(Blocks.SNOW_BLOCK) || ceiling.is(Blocks.POWDER_SNOW)) {
			level.setBlockAndUpdate(below, this.defaultBlockState().setValue(TIP_DIRECTION, Direction.DOWN).setValue(PART, Part.TIP));
		}
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		if (state.getValue(TIP_DIRECTION) == Direction.UP && state.getValue(PART) == Part.TIP) {
			entity.causeFallDamage(fallDistance + 2.0, LAND_MULTIPLIER, level.damageSources().stalagmite());
		} else {
			super.fallOn(level, state, pos, entity, fallDistance);
		}
	}

	@Override
	public DamageSource getFallDamageSource(Entity entity) {
		return entity.damageSources().fallingStalactite(entity);
	}

	@Override
	public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity entity) {
		if (!entity.isSilent()) {
			level.levelEvent(2001, pos, Block.getId(entity.getBlockState()));
		}
	}
}

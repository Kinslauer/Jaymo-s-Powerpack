package com.jaymospowers.aether.mob;

import com.jaymospowers.aether.AetherFlora;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A moa egg standing in the world. It hatches in three stages: on woven skyroot sticks (a nest) each stage takes
 * {@link #NEST_STAGE} ticks plus up to {@link #SPREAD} (about 12000 in all); anywhere else {@link #OTHER_STAGE}. An egg a
 * player placed hatches a chick that player raised (it can be saddled when grown); a wild nest's egg a wild chick.
 * {@code low} is an egg sitting on the 8-pixel sticks, drawn and shaped 8 pixels lower so it rests on them.
 */
public class MoaEggBlock extends Block {
	// ---- tuning ----
	/** Ticks a stage takes on a nest, elsewhere, and the random extra on each. */
	public static final int NEST_STAGE = 4000, OTHER_STAGE = 8000, SPREAD = 300;

	public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 2);
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
	public static final BooleanProperty OWNED = BooleanProperty.create("owned");
	public static final BooleanProperty LOW = BooleanProperty.create("low");

	private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 12.0, 12.0);
	private static final VoxelShape SHAPE_LOW = Block.box(4.0, -8.0, 4.0, 12.0, 4.0, 12.0);

	public MoaEggBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(KIND, 0).setValue(STAGE, 0).setValue(OWNED, false).setValue(LOW, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND, STAGE, OWNED, LOW);
	}

	/** An egg of {@code kind} to stand on {@code below}. */
	public BlockState stateFor(Moa.Kind kind, boolean owned, BlockState below) {
		return this.defaultBlockState().setValue(KIND, kind.ordinal()).setValue(OWNED, owned).setValue(LOW, below.is(AetherFlora.WOVEN_SKYROOT_STICKS));
	}

	public static Moa.Kind kindOf(BlockState state) {
		return Moa.Kind.byIndex(state.getValue(KIND));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(LOW) ? SHAPE_LOW : SHAPE;
	}

	private static boolean stands(LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState state = level.getBlockState(below);
		return state.is(AetherFlora.WOVEN_SKYROOT_STICKS) || state.isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return stands(level, pos);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos,
		BlockState neighborState, RandomSource random) {
		if (direction == Direction.DOWN) {
			if (!stands(level, pos)) {
				return Blocks.AIR.defaultBlockState();
			}
			return state.setValue(LOW, neighborState.is(AetherFlora.WOVEN_SKYROOT_STICKS));
		}
		return state;
	}

	private int delay(BlockState state, LevelReader level, BlockPos pos, RandomSource random) {
		int stage = level.getBlockState(pos.below()).is(AetherFlora.WOVEN_SKYROOT_STICKS) ? NEST_STAGE : OTHER_STAGE;
		return stage + random.nextInt(SPREAD);
	}

	/** Starts the hatching clock of an egg placed by world generation (which never runs {@code onPlace}). */
	public void startClock(ScheduledTickAccess ticks, LevelReader level, BlockPos pos, BlockState state, RandomSource random) {
		ticks.scheduleTick(pos, this, this.delay(state, level, pos, random));
	}

	@Override
	protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (level instanceof ServerLevel server && !oldState.is(this)) {
			level.scheduleTick(pos, this, this.delay(state, server, pos, server.getRandom()));
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int stage = state.getValue(STAGE);
		if (stage < 2) {
			level.setBlock(pos, state.setValue(STAGE, stage + 1), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
			level.scheduleTick(pos, this, this.delay(state, level, pos, random));
			return;
		}
		// hatch
		level.removeBlock(pos, false);
		level.playSound(null, pos, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 0.8F, 1.3F);
		level.levelEvent(2001, pos, Block.getId(state));
		Moa chick = AetherMobs.MOA.create(level, EntitySpawnReason.TRIGGERED);
		if (chick != null) {
			double y = state.getValue(LOW) ? pos.getY() - 0.5 : pos.getY();
			chick.snapTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			chick.setKind(kindOf(state));
			chick.setRaised(state.getValue(OWNED));
			chick.setAge(-Moa.CHICK_TICKS);
			chick.setHome(pos);
			level.addFreshEntity(chick);
		}
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(Moa.eggOf(kindOf(state)));
	}
}

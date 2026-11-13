package com.jaymoskingdoms.blossomland;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A paper hornets' nest, hung under the leaves of a maple or a ginkgo (they start by themselves there,
 * {@link #tryStart}). The hornets chew wood into paper and build it out in layers ({@link #PULP}, up to
 * {@link #MOST_PULP}); shears take the outer layers off as paper pulp and leave the nest. Cutting or breaking a nest
 * that is not smoked (a lit campfire beneath it, as for bees) brings its hornets down on whoever did it. A nest sends
 * out up to {@link #KEEPS} hornets by day ({@link Hornet}).
 */
public final class HornetNest extends Block {
	public static final int MOST_PULP = 3;
	public static final IntegerProperty PULP = IntegerProperty.create("pulp", 0, MOST_PULP);
	/** Hornets a nest keeps flying, how far it counts them, and how many more come out when it is touched. */
	static final int KEEPS = 3, COUNT_RANGE = 16, ROUSED = 2;
	/** A leaf's random tick starts a nest under it one time in this many, with no other nest this near. */
	static final int START_ONE_IN = 600, APART = 10;
	private static final VoxelShape SHAPE = Block.box(3.0, 2.0, 3.0, 13.0, 16.0, 13.0);

	HornetNest(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(PULP, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PULP);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState held = level.getBlockState(pos.above());
		return held.is(BlockTags.LEAVES) || held.isFaceSturdy(level, pos.above(), Direction.DOWN, SupportType.CENTER);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
			BlockState neighbour, RandomSource random) {
		return direction == Direction.UP && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
			: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(PULP) < MOST_PULP && random.nextInt(4) == 0) {
			level.setBlock(pos, state.setValue(PULP, state.getValue(PULP) + 1), Block.UPDATE_CLIENTS);
		}
		if (level.isBrightOutside() && hornets(level, pos).size() < KEEPS) {
			send(level, pos, null);
		}
	}

	private static List<Hornet> hornets(ServerLevel level, BlockPos pos) {
		return level.getEntitiesOfClass(Hornet.class, new AABB(pos).inflate(COUNT_RANGE), Hornet::isAlive);
	}

	/** One hornet out of the nest's mouth, angry at {@code at} if there is someone to be angry at. */
	private static void send(ServerLevel level, BlockPos pos, LivingEntity at) {
		BlockPos out = pos.below();
		if (!level.getBlockState(out).getCollisionShape(level, out).isEmpty()) {
			return;
		}
		Hornet hornet = BlossomAnimals.HORNET.create(level, EntitySpawnReason.TRIGGERED);
		if (hornet == null) {
			return;
		}
		hornet.snapTo(out.getX() + 0.5, out.getY() + 0.2, out.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		hornet.setNest(pos);
		level.addFreshEntity(hornet);
		if (at != null) {
			hornet.anger(at);
		}
	}

	/** Every hornet of the nest goes for {@code at}, and more come out. */
	private static void rouse(ServerLevel level, BlockPos pos, LivingEntity at) {
		if (at instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		for (Hornet hornet : hornets(level, pos)) {
			hornet.anger(at);
		}
		for (int i = 0; i < ROUSED; i++) {
			send(level, pos, at);
		}
		level.playSound(null, pos, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 0.6F);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Items.SHEARS)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			int pulp = state.getValue(PULP);
			if (pulp > 0) {
				popResource(level, pos, new ItemStack(BlossomAnimals.PAPER_PULP, pulp));
				level.setBlock(pos, state.setValue(PULP, 0), Block.UPDATE_CLIENTS);
				level.playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 0.8F);
				stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
				level.gameEvent(player, GameEvent.SHEAR, pos);
			}
			if (!CampfireBlock.isSmokeyPos(level, pos)) {
				rouse(server, pos, player);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server && !CampfireBlock.isSmokeyPos(level, pos)) {
			rouse(server, pos, player);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** From a maple or ginkgo leaf's random tick: now and then a nest starts in the air under it. */
	static void tryStart(ServerLevel level, BlockPos leaf, RandomSource random) {
		BlockPos under = leaf.below();
		if (random.nextInt(START_ONE_IN) != 0 || !level.getBlockState(under).isAir() || !level.getBlockState(under.below()).isAir()) {
			return;
		}
		for (BlockPos p : BlockPos.betweenClosed(under.offset(-APART, -4, -APART), under.offset(APART, 4, APART))) {
			if (level.getBlockState(p).getBlock() instanceof HornetNest) {
				return;
			}
		}
		level.setBlock(under, BlossomAnimals.HORNET_NEST.defaultBlockState(), Block.UPDATE_ALL);
	}
}

package com.jaymoskingdoms.wild.nest;

import com.jaymoskingdoms.Hearing;
import com.jaymoskingdoms.wild.SpiderKinds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks, de-eggs) A spider's egg sac, hung from a nest's roof or sitting in a cluster on its floor (the silk
 * deeps' coat, a fed nest laying more, the spiders' lair where the dead wake). It keeps watch for passers-by (a scheduled
 * look every {@link #LOOK_EVERY} ticks or so): when someone who isn't a spider comes within {@link #HATCH_RANGE}, it splits
 * open into {@link #BROOD_LEAST}-{@link #BROOD_MOST} spiderlings ({@link SpiderKinds#hatch}) that come for whoever woke them.
 * Broken by hand it splits open on the one breaking it. It burns, and a nest whose silk and eggs burned stays dead
 * ({@link SpiderNests#scar}).
 * <p>
 * One laid with a first look far off ({@link #lay}) keeps still until then: the eggs on a lair's cache give the one who
 * wakes beside them time to get up.
 */
public class EggSacBlock extends Block {
	// ---- tuning ----
	/** Someone this near (blocks) wakes it. */
	static final double HATCH_RANGE = 3.5;
	/** How often it looks (ticks, plus up to LOOK_JITTER more). */
	static final int LOOK_EVERY = 20, LOOK_JITTER = 10;
	/** How many spiderlings come out. */
	static final int BROOD_LEAST = 2, BROOD_MOST = 4;

	public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
	private static final VoxelShape STANDING = Block.box(3.0, 0.0, 3.0, 13.0, 9.0, 13.0), HUNG = Block.box(3.0, 5.0, 3.0, 13.0, 16.0, 13.0);

	public EggSacBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(HANGING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HANGING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(HANGING) ? HUNG : STANDING;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState hung = this.defaultBlockState().setValue(HANGING, true);
		BlockState standing = this.defaultBlockState();
		boolean wantsHung = context.getClickedFace() == Direction.DOWN;
		for (BlockState s : wantsHung ? new BlockState[] {hung, standing} : new BlockState[] {standing, hung}) {
			if (s.canSurvive(context.getLevel(), context.getClickedPos())) {
				return s;
			}
		}
		return null;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return state.getValue(HANGING) ? Block.canSupportCenter(level, pos.above(), Direction.DOWN) || level.getBlockState(pos.above()).isSolid()
			: Block.canSupportCenter(level, pos.below(), Direction.UP) || level.getBlockState(pos.below()).isSolid();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
			BlockState neighbourState, RandomSource random) {
		Direction holds = state.getValue(HANGING) ? Direction.UP : Direction.DOWN;
		if (direction == holds && !state.canSurvive(level, pos)) {
			return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	/** An egg sac laid at {@code pos} (hung from the roof, or on the floor), that first looks for passers-by in {@code firstLook} ticks. */
	public static void lay(LevelAccessor level, BlockPos pos, boolean hanging, int firstLook) {
		level.setBlock(pos, SpiderNests.EGG_SAC.defaultBlockState().setValue(HANGING, hanging), Block.UPDATE_CLIENTS);
		level.scheduleTick(pos, SpiderNests.EGG_SAC, Math.max(1, firstLook));
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		level.scheduleTick(pos, this, LOOK_EVERY);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		LivingEntity waker = waker(level, pos);
		if (waker != null) {
			hatch(level, pos, waker);
		} else {
			level.scheduleTick(pos, this, LOOK_EVERY + random.nextInt(LOOK_JITTER + 1));
		}
	}

	/** A sac that lost its look (an old save, a moved chunk) picks it up again. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, LOOK_EVERY);
		}
	}

	/** Whoever near is enough to wake it: anyone alive who isn't a spider, a bat or the like, or a player out of play. */
	private static @Nullable LivingEntity waker(ServerLevel level, BlockPos pos) {
		Vec3 c = Vec3.atCenterOf(pos);
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(HATCH_RANGE), e -> e.isAlive()
			&& !(e instanceof Spider) && !(e instanceof BroodQueen) && !(e instanceof AmbientCreature)
			&& !(e instanceof Player p && (p.isCreative() || p.isSpectator())) && e.distanceToSqr(c) <= HATCH_RANGE * HATCH_RANGE);
		LivingEntity best = null;
		for (LivingEntity e : near) {
			if (best == null || e.distanceToSqr(c) < best.distanceToSqr(c)) {
				best = e;
			}
		}
		return best;
	}

	/** The sac splits open: its spiderlings come for {@code waker}. */
	static void hatch(ServerLevel level, BlockPos pos, @Nullable LivingEntity waker) {
		level.removeBlock(pos, false);
		Vec3 at = Vec3.atCenterOf(pos);
		int n = BROOD_LEAST + level.getRandom().nextInt(BROOD_MOST - BROOD_LEAST + 1);
		SpiderKinds.hatch(level, at, n, waker);
		level.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.HOSTILE, 1.0F, 1.4F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.05);
		Hearing.noise(level, at, 12.0, null, Hearing.Kind.ACTION);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server && !player.isCreative()) {
			// it splits open on the one breaking it
			Vec3 at = Vec3.atCenterOf(pos);
			SpiderKinds.hatch(server, at, BROOD_LEAST + server.getRandom().nextInt(BROOD_MOST - BROOD_LEAST + 1), player);
			level.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.HOSTILE, 1.0F, 1.4F);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		SpiderNests.burned(level, pos);
	}
}

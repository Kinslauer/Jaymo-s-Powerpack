package com.jaymospowers.eden;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The gravity compressor: gravilite set in a frame. Given a redstone signal it turns the pull of the ground over in
 * the column above it, {@link #REACH_PER_POWER} blocks for each level of the signal: whatever is in that column falls
 * upward and hangs at the top of it. A lift, or a trap.
 */
public class GravityCompressorBlock extends Block {
	// ---- tuning ----
	/** Blocks of column for each level of redstone power, how often it works (ticks), and how fast loose things rise per working. */
	public static final int REACH_PER_POWER = 2, PERIOD = 2;
	public static final double LIFT = 0.12, LIFT_MOST = 0.45;
	/** The levitation a living thing in the column is kept under (level, and ticks it lasts past the last working). */
	public static final int LEVITATION = 2, LEVITATION_TICKS = 8;

	public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

	public GravityCompressorBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWER);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(POWER, context.getLevel().getBestNeighborSignal(context.getClickedPos()));
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (state.getValue(POWER) > 0) {
			level.scheduleTick(pos, this, PERIOD);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		int power = level.getBestNeighborSignal(pos);
		if (power != state.getValue(POWER)) {
			level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_CLIENTS);
			if (power > 0 && state.getValue(POWER) == 0) {
				level.scheduleTick(pos, this, PERIOD);
			}
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int power = state.getValue(POWER);
		if (power <= 0) {
			return;
		}
		int reach = power * REACH_PER_POWER;
		AABB column = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 1 + reach, pos.getZ() + 1);
		for (Entity entity : level.getEntities((Entity) null, column, e -> e.isAlive() && !e.isSpectator() && !e.isNoGravity())) {
			if (entity instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEVITATION_TICKS, LEVITATION, true, false, false));
				living.resetFallDistance();
			} else {
				Vec3 motion = entity.getDeltaMovement();
				entity.setDeltaMovement(motion.x, Math.min(LIFT_MOST, motion.y + LIFT), motion.z);
			}
		}
		if (random.nextInt(4) == 0) {
			level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2 + random.nextInt(reach), pos.getZ() + 0.5, 1, 0.25, 0.3, 0.25, 0.01);
		}
		level.scheduleTick(pos, this, PERIOD);
	}
}

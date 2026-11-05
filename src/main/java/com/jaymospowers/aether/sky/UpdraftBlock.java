package com.jaymospowers.aether.sky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.20.96) Andy (seven-mods plan, patch 10): the air over an updraft vent ({@link UpdraftVentBlock}), a column that
 * carries whatever is in it up between the island layers. Unseen and untouchable, it shows only as cloud motes; it lifts
 * fast, slower for anyone sneaking (to hover and step off), and forgets a fall. It holds while every block under it down to
 * the vent is updraft; a block set in the column cuts it there.
 */
public class UpdraftBlock extends Block {
	/** The most upward speed it gives, and how much each tick adds; sneaking, the most. */
	static final double LIFT_MOST = 0.85, LIFT_STEP = 0.14, HOVER_MOST = 0.12;

	public UpdraftBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	/** The block under an updraft holds it up: more updraft, or its vent. */
	static boolean holds(BlockState below) {
		return below.getBlock() instanceof UpdraftBlock || below.getBlock() instanceof UpdraftVentBlock;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
		BlockState neighbour, RandomSource random) {
		if (direction == Direction.DOWN && !holds(neighbour)) {
			return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		Vec3 v = entity.getDeltaMovement();
		double most = entity.isShiftKeyDown() ? HOVER_MOST : LIFT_MOST;
		entity.setDeltaMovement(v.x, Math.min(most, Math.max(v.y, 0.0) + LIFT_STEP), v.z);
		entity.resetFallDistance();
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.CLOUD, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
				0.0, 0.25 + random.nextDouble() * 0.2, 0.0);
		}
	}
}

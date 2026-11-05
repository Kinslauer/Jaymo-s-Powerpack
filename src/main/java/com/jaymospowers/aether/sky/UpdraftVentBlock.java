package com.jaymospowers.aether.sky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.96) An updraft vent: holystone breathing a column of rising air ({@link UpdraftBlock}) straight up from it, as far
 * as {@link #HEIGHT} blocks or the first thing in the way. Breaking what blocks it lets the column climb again.
 */
public class UpdraftVentBlock extends Block {
	/** How high its column climbs (enough to reach the upper islets from the lowland tops). */
	public static final int HEIGHT = 110;

	public UpdraftVentBlock(Properties properties) {
		super(properties);
	}

	/** Fills the column over {@code vent} with updraft, from the vent up to the first block in the way. */
	public static void fill(LevelAccessor level, BlockPos vent, int flags) {
		BlockPos.MutableBlockPos at = vent.mutable();
		BlockState air = SkyBlocks.UPDRAFT.defaultBlockState();
		for (int i = 1; i <= HEIGHT; i++) {
			at.move(0, 1, 0);
			if (level.isOutsideBuildHeight(at)) {
				return;
			}
			BlockState here = level.getBlockState(at);
			if (here.getBlock() instanceof UpdraftBlock) {
				continue;
			}
			if (!here.isAir()) {
				return;
			}
			level.setBlock(at, air, flags);
		}
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		if (!level.isClientSide()) {
			level.scheduleTick(pos, this, 2);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
		if (!level.isClientSide()) {
			level.scheduleTick(pos, this, 2);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		fill(level, pos, Block.UPDATE_ALL);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.05, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
				0.0, 0.3, 0.0);
		}
	}
}

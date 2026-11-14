package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Leaves of a sky tree that shed: now and then a leaf on the tree (not one a player set) drops a {@link LeafPileBlock leaf
 * pile} of its own kind on the ground straight below, if nothing is in the way and the spot is free.
 */
public class SheddingLeavesBlock extends UntintedParticleLeavesBlock {
	// ---- tuning ----
	/** A random tick sheds with 1 chance in this many, and a leaf falls at most this far. */
	public static final int SHED_CHANCE = 60, SHED_DROP = 12;

	private final String pile;

	public SheddingLeavesBlock(String pile, float particleChance, ParticleOptions particle, BlockBehaviour.Properties properties) {
		super(particleChance, particle, AmbientLeavesBlockSoundPlayer.noAmbientSound(), properties);
		this.pile = pile;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return !state.getValue(PERSISTENT);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (this.decaying(state)) {
			super.randomTick(state, level, pos, random);
			return;
		}
		if (random.nextInt(SHED_CHANCE) != 0) {
			return;
		}
		BlockPos.MutableBlockPos at = pos.mutable().move(Direction.DOWN);
		for (int i = 0; i < SHED_DROP && level.getBlockState(at).isAir(); i++) {
			at.move(Direction.DOWN);
		}
		BlockState ground = level.getBlockState(at);
		BlockPos spot = at.above();
		Block pileBlock = AetherFlora.get(this.pile);
		if (pileBlock == null || spot.equals(pos) || !level.getBlockState(spot).isAir() || ground.getBlock() instanceof LeavesBlock
			|| !ground.isFaceSturdy(level, at, Direction.UP)) {
			return;
		}
		level.setBlockAndUpdate(spot, pileBlock.defaultBlockState().setValue(LeafPileBlock.PERSISTENT, false));
	}
}

package com.jaymoskingdoms.glowwood;

import com.jaymoskingdoms.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A giant glowcap's cap: it glows, and now and then lets a luminous mote drift down from its open underside (ported from Spore & Stem, Apache-2.0). */
public class GlowcapCapBlock extends HugeMushroomBlock {
	/** One tick in this many a mote falls where the underside is open; its colour and size. */
	private static final int MOTE_ONE_IN = 14;
	private static final int MOTE_COLOUR = 0x6BF2E3;
	private static final float MOTE_SIZE = 0.7F;

	public GlowcapCapBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(MOTE_ONE_IN) == 0 && level.isEmptyBlock(pos.below())) {
			level.addParticle(Fx.mote(MOTE_COLOUR, MOTE_SIZE), pos.getX() + random.nextDouble(), pos.getY() - 0.1, pos.getZ() + random.nextDouble(),
				0.0, -0.01, 0.0);
		}
	}
}

package com.jaymoskingdoms.glowwood;

import com.jaymoskingdoms.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A giant inkcap's cap: wherever its underside is open to the air, it drips ink (ported from Spore & Stem, Apache-2.0). */
public class InkcapCapBlock extends HugeMushroomBlock {
	/** One tick in this many a drop falls; its colour and size. */
	private static final int DRIP_ONE_IN = 6;
	private static final int INK_COLOUR = 0x15101E;
	private static final float INK_SIZE = 0.5F;

	public InkcapCapBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(DRIP_ONE_IN) == 0 && level.isEmptyBlock(pos.below())) {
			level.addParticle(Fx.miasma(INK_COLOUR, INK_SIZE), pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() - 0.05,
				pos.getZ() + 0.15 + random.nextDouble() * 0.7, 0.0, -0.02, 0.0);
		}
	}
}

package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A carpet of moss that creeps: lying on plain holystone, out of the full sun and with water close (or in the rain), it
 * works the stone under it into mossy holystone and sends a new carpet onto plain holystone beside it. On stone it has
 * already eaten, or on anything else, it only lies there.
 */
public class CreepingMossBlock extends CarpetBlock {
	// ---- tuning ----
	/** A random tick eats the stone with 1 chance in this many. */
	public static final int EAT_CHANCE = 6;
	/** Water counts within this far (and one level down). */
	public static final int WATER_RANGE = 3;
	/** Sky light at or over this is full sun: too dry unless it rains. */
	public static final int SUN = 15;

	public CreepingMossBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos below = pos.below();
		if (random.nextInt(EAT_CHANCE) != 0 || !level.getBlockState(below).is(AetherBlocks.HOLYSTONE) || !damp(level, pos)) {
			return;
		}
		level.setBlockAndUpdate(below, AetherBlocks.MOSSY_HOLYSTONE.defaultBlockState());
		BlockPos spot = pos.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
		if (level.getBlockState(spot).isAir() && level.getBlockState(spot.below()).is(AetherBlocks.HOLYSTONE)) {
			level.setBlockAndUpdate(spot, state);
		}
	}

	private static boolean damp(ServerLevel level, BlockPos pos) {
		if (level.isRainingAt(pos.above())) {
			return true;
		}
		if (level.getBrightness(LightLayer.SKY, pos) >= SUN) {
			return false;
		}
		for (BlockPos at : BlockPos.betweenClosed(pos.offset(-WATER_RANGE, -1, -WATER_RANGE), pos.offset(WATER_RANGE, 0, WATER_RANGE))) {
			if (level.getFluidState(at).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}
}

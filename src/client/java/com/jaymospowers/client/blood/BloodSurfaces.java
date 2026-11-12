package com.jaymospowers.client.blood;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What blood lands on changes how it lies (a pick from Cruor's ideas): cloth soaks it up into a small dark stain that
 * lasts, earth drinks it into a dull patch that's soon gone, glass and ice let it smear thin and wide, and the rest
 * keeps it as before. A pool on a thin floor (planks, a slab, a trapdoor, leaves, wool, hay, scaffolding) with open air
 * under it soaks through and drips from the underside while it's fresh ({@link #soaksThrough}).
 */
public final class BloodSurfaces {
	/** How blood lies on a surface: its size, opacity and darkness (shares), and how long a passing pool lasts (share). */
	public enum Surface {
		PLAIN(1.0F, 1.0F, 1.0F, 1.0F),
		SOAKS(0.75F, 1.15F, 0.7F, 1.5F),
		DRINKS(0.9F, 0.75F, 0.82F, 0.5F),
		SMEARS(1.25F, 0.55F, 1.0F, 1.0F);

		public final float size, alpha, dark, life;

		Surface(float size, float alpha, float dark, float life) {
			this.size = size;
			this.alpha = alpha;
			this.dark = dark;
			this.life = life;
		}
	}

	/** A fresh pool soaks through for this long (ticks), dripping this often at most (chance a tick at full size). */
	static final int SOAK_TICKS = 600;
	static final float SOAK_DRIP = 0.04F;

	private BloodSurfaces() {
	}

	/** What the blood landing at (x, y, z) lies on: the block under it, or a carpet or snow layer it lies in. */
	public static Surface under(BlockGetter level, double x, double y, double z) {
		BlockPos at = BlockPos.containing(x, y - 0.02, z);
		BlockState state = level.getBlockState(at);
		if (state.isAir()) {
			state = level.getBlockState(at.below());
		}
		return of(state);
	}

	static Surface of(BlockState state) {
		if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(Blocks.HAY_BLOCK) || state.is(Blocks.MOSS_CARPET)
			|| state.is(Blocks.MOSS_BLOCK)) {
			return Surface.SOAKS;
		}
		if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.FARMLAND)
			|| state.is(Blocks.SOUL_SOIL) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.SNOW)
			|| state.is(Blocks.SNOW_BLOCK)) {
			return Surface.DRINKS;
		}
		if (state.is(BlockTags.ICE) || state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES)) {
			return Surface.SMEARS;
		}
		return Surface.PLAIN;
	}

	/**
	 * The block blood lying at (x, y, z) soaks through and drips from, or null: a thin floor with nothing solid under it.
	 */
	public static @org.jspecify.annotations.Nullable BlockPos soaksThrough(BlockGetter level, double x, double y, double z) {
		BlockPos at = BlockPos.containing(x, y - 0.02, z);
		BlockState state = level.getBlockState(at);
		if (!thin(state)) {
			return null;
		}
		BlockState below = level.getBlockState(at.below());
		return below.getCollisionShape(level, at.below()).isEmpty() && below.getFluidState().isEmpty() ? at : null;
	}

	private static boolean thin(BlockState state) {
		if (state.getBlock() instanceof SlabBlock) {
			return state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE;
		}
		return state.is(BlockTags.PLANKS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL)
			|| state.is(Blocks.HAY_BLOCK) || state.is(Blocks.SCAFFOLDING);
	}
}

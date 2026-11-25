package com.jaymospowers.client.blood;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What blood lands on changes how it lies (a pick from Cruor's ideas): cloth soaks it up into a small dark stain that
 * lasts, earth, sand and gravel drink it into a darker patch that dries and goes sooner, glass, metal and ice make it bead
 * into small drops that wipe off soon, snow turns it pink, and the rest keeps it as before. A pool on a thin floor (planks, a slab, a trapdoor, leaves, wool, hay, scaffolding) with open air
 * under it soaks through and drips from the underside while it's fresh ({@link #soaksThrough}).
 */
public final class BloodSurfaces {
	/**
	 * How blood lies on a surface: its size, opacity and darkness (shares), how long a passing pool lasts (share), how fast
	 * a lasting stain dries to brown (share), and whether it turns pink.
	 */
	public enum Surface {
		PLAIN(1.0F, 1.0F, 1.0F, 1.0F, 1.0F, false),
		SOAKS(0.75F, 1.15F, 0.7F, 1.5F, 1.0F, false),
		DRINKS(0.9F, 0.85F, 0.6F, 0.4F, 2.5F, false),
		BEADS(0.6F, 0.9F, 1.0F, 0.2F, 0.5F, false),
		SNOW(1.0F, 1.0F, 1.0F, 0.8F, 1.0F, true);

		public final float size, alpha, dark, life, dry;
		public final boolean pink;

		Surface(float size, float alpha, float dark, float life, float dry, boolean pink) {
			this.size = size;
			this.alpha = alpha;
			this.dark = dark;
			this.life = life;
			this.dry = dry;
			this.pink = pink;
		}
	}

	/** Blood on snow: this share of the way to this colour. */
	static final float PINK_MIX = 0.5F;
	static final int PINK = 0xE58B9B;

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
			|| state.is(Blocks.SOUL_SOIL) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.DIRT_PATH)) {
			return Surface.DRINKS;
		}
		if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
			return Surface.SNOW;
		}
		if (state.is(BlockTags.ICE) || state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES)
			|| metal(state)) {
			return Surface.BEADS;
		}
		return Surface.PLAIN;
	}

	/** Whether {@code state} rings like metal (iron, copper, gold-plated, netherite, an anvil, chains, lanterns). */
	private static boolean metal(BlockState state) {
		SoundType sound = state.getSoundType();
		return sound == SoundType.METAL || sound == SoundType.ANVIL || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.CHAIN
			|| sound == SoundType.LANTERN || sound == SoundType.COPPER || sound == SoundType.COPPER_BULB || sound == SoundType.COPPER_GRATE
			|| sound == SoundType.HEAVY_CORE || sound == SoundType.IRON;
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

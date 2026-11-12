package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.Fx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The air of the other lands, each its own (Andy: crimson, warped, the soul sand valley, the basalt deltas and the End had
 * none of their own). Typed effects only ({@code docs/MagicFx.md}): motes and miasma banks, never recoloured dust. The
 * Nether wastes keep their ash ({@link SkiesClient}).
 * <ul>
 * <li><b>Crimson forest</b>: red spores drift up through the fungus, with low dark-red haze banks between the stems.</li>
 * <li><b>Warped forest</b>: teal spores drift and sink, a cold green-blue haze lying in the hollows.</li>
 * <li><b>Soul sand valley</b>: wisps rise off the soil, and pale blue banks of mist lie low over the valley floor.</li>
 * <li><b>Basalt deltas</b>: thick grey ash falls, heavy smoke banks hang over the rock, and now and then a spark rises from the lava.</li>
 * <li><b>The End</b>: violet motes drift in the dark, with pale ones among them, slow as if the air held its breath.</li>
 * </ul>
 * A few spots round the camera are tried each tick; low-end mode tries half as many.
 */
final class DimensionAir {
	// ---- tuning ----
	/** Spots tried a tick, and how far round the camera (blocks) and how far above or below it they lie. */
	static final int TRIES = 8;
	static final double REACH = 16.0, REACH_UP = 8.0;
	/** The chance, at a spot in the open air, of each kind of thing. */
	static final float MOTE = 0.5F, HAZE = 0.12F, FALL = 0.7F, SPARK = 0.08F, WISP = 0.35F;
	/** Colours: crimson spore and haze, warped spore and haze, soul haze, basalt smoke and spark, End pale. */
	static final int CRIMSON = 0xD0243A, CRIMSON_HAZE = 0x6A1220, WARPED = 0x2BE0B8, WARPED_HAZE = 0x12505A, SOUL_HAZE = 0x6FC8F0, BASALT_SMOKE = 0x6E6E76,
		BASALT_SPARK = 0xFF9A3C, END_PALE = 0xEDE6FF;

	private DimensionAir() {
	}

	static void tick(ClientLevel level, Vec3 eye, RandomSource random) {
		boolean end = level.dimension() == Level.END;
		if (!end && level.dimension() != Level.NETHER) {
			return;
		}
		int tries = CloudDecks.lowEnd() ? TRIES / 2 : TRIES;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < tries; i++) {
			double x = eye.x + (random.nextDouble() - 0.5) * 2.0 * REACH, y = eye.y + (random.nextDouble() - 0.4) * 2.0 * REACH_UP,
				z = eye.z + (random.nextDouble() - 0.5) * 2.0 * REACH;
			at.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
			if (!level.getBlockState(at).isAir()) {
				continue;
			}
			if (end) {
				theEnd(level, random, x, y, z);
				continue;
			}
			ResourceKey<Biome> biome = level.getBiome(at).unwrapKey().orElse(null);
			if (biome == Biomes.CRIMSON_FOREST) {
				spores(level, random, x, y, z, CRIMSON, CRIMSON_HAZE, 0.02);
			} else if (biome == Biomes.WARPED_FOREST) {
				spores(level, random, x, y, z, WARPED, WARPED_HAZE, -0.004);
			} else if (biome == Biomes.SOUL_SAND_VALLEY) {
				soul(level, random, at, x, y, z);
			} else if (biome == Biomes.BASALT_DELTAS) {
				deltas(level, random, x, y, z);
			}
		}
	}

	/** Spores rising (or sinking) through a fungus forest, and a haze bank now and then. */
	private static void spores(ClientLevel level, RandomSource random, double x, double y, double z, int colour, int haze, double lift) {
		if (random.nextFloat() < MOTE) {
			level.addParticle(Fx.mote(colour, 0.22F + random.nextFloat() * 0.18F), x, y, z, (random.nextDouble() - 0.5) * 0.01, lift + random.nextDouble() * 0.01,
				(random.nextDouble() - 0.5) * 0.01);
		}
		if (random.nextFloat() < HAZE) {
			level.addParticle(Fx.miasma(haze, 1.6F + random.nextFloat()), x, y, z, (random.nextDouble() - 0.5) * 0.004, 0.0, (random.nextDouble() - 0.5) * 0.004);
		}
	}

	/** Wisps off soul sand and soil, and low pale banks over the valley floor. */
	private static void soul(ClientLevel level, RandomSource random, BlockPos.MutableBlockPos at, double x, double y, double z) {
		if (random.nextFloat() < WISP) {
			int top = at.getY();
			for (int down = 1; down <= 3; down++) {
				var below = level.getBlockState(at.setY(top - down));
				if (below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL)) {
					level.addParticle(ParticleTypes.SOUL, x, top - down + 1.1, z, 0.0, 0.03 + random.nextDouble() * 0.02, 0.0);
					break;
				}
			}
			at.setY(top);
		}
		if (random.nextFloat() < HAZE) {
			level.addParticle(Fx.miasma(SOUL_HAZE, 2.0F + random.nextFloat() * 1.2F), x, y, z, (random.nextDouble() - 0.5) * 0.004, 0.0, (random.nextDouble() - 0.5) * 0.004);
		}
	}

	/** Ash falling thick, smoke banks and the odd spark off the lava. */
	private static void deltas(ClientLevel level, RandomSource random, double x, double y, double z) {
		if (random.nextFloat() < FALL) {
			level.addParticle(random.nextBoolean() ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH, x, y, z, 0.0, -0.01 - random.nextDouble() * 0.02, 0.0);
		}
		if (random.nextFloat() < HAZE) {
			level.addParticle(Fx.miasma(BASALT_SMOKE, 2.2F + random.nextFloat()), x, y, z, (random.nextDouble() - 0.5) * 0.006, 0.004, (random.nextDouble() - 0.5) * 0.006);
		}
		if (random.nextFloat() < SPARK) {
			level.addParticle(Fx.spark(BASALT_SPARK, 0.18F), x, y, z, (random.nextDouble() - 0.5) * 0.02, 0.04 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.02);
		}
	}

	/** Violet motes drifting slowly in the dark, pale ones among them. */
	private static void theEnd(ClientLevel level, RandomSource random, double x, double y, double z) {
		if (random.nextFloat() < MOTE) {
			ParticleOptions mote = random.nextInt(4) == 0 ? Fx.mote(END_PALE, 0.2F) : Fx.VOID;
			level.addParticle(mote, x, y, z, (random.nextDouble() - 0.5) * 0.008, (random.nextDouble() - 0.5) * 0.006, (random.nextDouble() - 0.5) * 0.008);
		}
	}
}

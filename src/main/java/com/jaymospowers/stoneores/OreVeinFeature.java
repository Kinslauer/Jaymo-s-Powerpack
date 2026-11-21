package com.jaymospowers.stoneores;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

/**
 * One large vein of ore (Clouds and Bits, world bits): a crooked run of overlapping lumps, {@code size} ore blocks in all, laid
 * over whatever the {@code target} rule accepts (andesite, tuff, or the Nether's stone) and nothing else, so a vein is only
 * as big as the pocket of rock it was started in. Each block is now and then ({@code rare_chance}) the {@code rare} block
 * instead: a coal block in a coal vein, a raw gold block in a Nether gold vein. It keeps within {@link #REACH} blocks of where it
 * starts, so it never leaves the chunks being generated.
 */
public record OreVeinFeature(RuleTest target, BlockState ore, Optional<BlockState> rare, float rareChance, int size) implements Feature {
	public static final MapCodec<OreVeinFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		RuleTest.CODEC.fieldOf("target").forGetter(OreVeinFeature::target),
		BlockState.CODEC.fieldOf("ore").forGetter(OreVeinFeature::ore),
		BlockState.CODEC.optionalFieldOf("rare").forGetter(OreVeinFeature::rare),
		Codec.FLOAT.optionalFieldOf("rare_chance", 0.0F).forGetter(OreVeinFeature::rareChance),
		Codec.INT.fieldOf("size").forGetter(OreVeinFeature::size)
	).apply(i, OreVeinFeature::new));

	// ---- tuning ----
	/** How far from its start a vein may wander (blocks), and the lumps' radius range. */
	static final int REACH = 9;
	static final float LUMP_MIN = 1.3F, LUMP_MAX = 2.4F;
	/** The share of a lump's blocks that are laid (the rest stay rock, so the vein is ragged). */
	static final float DENSITY = 0.8F;

	@Override
	public MapCodec<OreVeinFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int placed = 0;
		double x = origin.getX() + 0.5, y = origin.getY() + 0.5, z = origin.getZ() + 0.5;
		float yaw = random.nextFloat() * Mth.TWO_PI;
		float pitch = (random.nextFloat() - 0.5F) * 0.6F;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int step = 0; step < size * 4 && placed < size; step++) {
			float r = LUMP_MIN + random.nextFloat() * (LUMP_MAX - LUMP_MIN);
			int ri = Mth.ceil(r);
			for (int dx = -ri; dx <= ri && placed < size; dx++) {
				for (int dy = -ri; dy <= ri && placed < size; dy++) {
					for (int dz = -ri; dz <= ri && placed < size; dz++) {
						if (dx * dx + dy * dy + dz * dz > r * r || random.nextFloat() > DENSITY) {
							continue;
						}
						at.set(Mth.floor(x) + dx, Mth.floor(y) + dy, Mth.floor(z) + dz);
						if (Math.abs(at.getX() - origin.getX()) > REACH || Math.abs(at.getZ() - origin.getZ()) > REACH
							|| level.isOutsideBuildHeight(at) || !target.testAgainstWorldState(level, at, random)) {
							continue;
						}
						boolean odd = rare.isPresent() && random.nextFloat() < rareChance;
						level.setBlock(at, odd ? rare.get() : ore, Block.UPDATE_CLIENTS);
						placed++;
					}
				}
			}
			yaw += (random.nextFloat() - 0.5F) * 0.9F;
			pitch = Mth.clamp(pitch + (random.nextFloat() - 0.5F) * 0.5F, -0.6F, 0.6F);
			x += Mth.cos(yaw) * Mth.cos(pitch) * r;
			z += Mth.sin(yaw) * Mth.cos(pitch) * r;
			y += Mth.sin(pitch) * r;
			if (Math.abs(x - origin.getX()) > REACH - 2 || Math.abs(z - origin.getZ()) > REACH - 2) {
				yaw += Mth.PI;
			}
		}
		return placed > 0;
	}
}

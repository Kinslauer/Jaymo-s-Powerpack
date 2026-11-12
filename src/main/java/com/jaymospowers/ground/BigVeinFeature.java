package com.jaymospowers.ground;

import com.jaymospowers.frost.IceCaves;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

/**
 * Andy's TFC Hardrock picks ("make veins bigger and rarer... diamond and netherite become IMPOSSIBLY rare"): one big ore
 * body, in place of vanilla's many small blobs (BigVeins takes those out). A vein is a crooked run of overlapping lumps,
 * {@code size} ore blocks in all, laid over the stone it meets ({@code ore} over stone, {@code deep} over deepslate, or
 * over Nether stone when {@code nether}); a {@code buried} vein skips blocks that touch air, so it can't be seen from a
 * cave. It keeps within {@link #REACH} blocks of where it starts, so it never reaches past the chunks being generated.
 * How rare each is lives in its placed feature (worldgen/placed_feature/big_vein_*.json, a rarity filter).
 */
public record BigVeinFeature(BlockState ore, Optional<BlockState> deep, int size, boolean buried, boolean nether) implements Feature {
	public static final MapCodec<BigVeinFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BlockState.CODEC.fieldOf("ore").forGetter(BigVeinFeature::ore),
		BlockState.CODEC.optionalFieldOf("deep").forGetter(BigVeinFeature::deep),
		com.mojang.serialization.Codec.INT.fieldOf("size").forGetter(BigVeinFeature::size),
		com.mojang.serialization.Codec.BOOL.optionalFieldOf("buried", false).forGetter(BigVeinFeature::buried),
		com.mojang.serialization.Codec.BOOL.optionalFieldOf("nether", false).forGetter(BigVeinFeature::nether)
	).apply(i, BigVeinFeature::new));

	/** How far from its start a vein may wander (blocks), and the lumps' radius range. */
	static final int REACH = 12;
	static final float LUMP_MIN = 1.2F, LUMP_MAX = 2.6F;
	/** The share of a lump's blocks that are ore. */
	static final float DENSITY = 0.7F;

	@Override
	public MapCodec<BigVeinFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int placed = 0;
		double x = origin.getX() + 0.5, y = origin.getY() + 0.5, z = origin.getZ() + 0.5;
		float yaw = random.nextFloat() * Mth.TWO_PI;
		float pitch = (random.nextFloat() - 0.5F) * 0.8F;
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
							|| level.isOutsideBuildHeight(at)) {
							continue;
						}
						BlockState there = level.getBlockState(at);
						BlockState into = into(there);
						// (in the ice caves the vein runs on through the ice, in a pocket of stone)
						BlockState stone = into == null && !nether ? IceCaves.standIn(level, at, there) : null;
						if (stone != null) {
							into = into(stone);
						}
						if (into == null || buried && touchesAir(level, at)) {
							continue;
						}
						level.setBlock(at, into, Block.UPDATE_CLIENTS);
						IceCaves.pocket(level, level::getBlockState, at.immutable(), (near, rock) -> {
							if (IceCaves.writable(level, near) && level.getBlockState(near).is(IceCaves.ROCK)) {
								level.setBlock(near, rock, Block.UPDATE_CLIENTS);
							}
						});
						placed++;
					}
				}
			}
			// the run wanders on, bending a little
			yaw += (random.nextFloat() - 0.5F) * 0.9F;
			pitch = Mth.clamp(pitch + (random.nextFloat() - 0.5F) * 0.5F, -0.7F, 0.7F);
			x += Mth.cos(yaw) * Mth.cos(pitch) * r;
			z += Mth.sin(yaw) * Mth.cos(pitch) * r;
			y += Mth.sin(pitch) * r;
			if (Math.abs(x - origin.getX()) > REACH - 2 || Math.abs(z - origin.getZ()) > REACH - 2) {
				// turn back toward the start
				yaw += Mth.PI;
			}
		}
		return placed > 0;
	}

	private @Nullable BlockState into(BlockState there) {
		if (nether) {
			return there.is(BlockTags.BASE_STONE_NETHER) ? ore : null;
		}
		if (there.is(BlockTags.STONE_ORE_REPLACEABLES)) {
			return ore;
		}
		if (there.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
			return deep.orElse(ore);
		}
		return null;
	}

	private static boolean touchesAir(WorldGenLevel level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (level.getBlockState(pos.relative(d)).isAir()) {
				return true;
			}
		}
		return false;
	}
}

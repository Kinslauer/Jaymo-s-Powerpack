package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A party's last camp: a small sealed room cut in solid rock, built in code, with a dead fire at its middle, three to five of the
 * gone-before ({@link Remains}) lying round it and a barrel of what they had left. Two templates: a square room in stone
 * (template 0) and a long one in deepslate (template 1). It is only placed where the rock all round is whole, so nothing about
 * it shows from outside.
 */
public record LastCamp(int template) implements Feature {
	public static final MapCodec<LastCamp> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.INT.fieldOf("template").forGetter(LastCamp::template)).apply(i, LastCamp::new));
	/** Each template's inside: half its width, half its depth, and its height (blocks). */
	private static final int[][] SIZE = {{3, 3, 3}, {4, 2, 3}};
	/** How many lie round the fire, least and most. */
	static final int LEAST = 3, MOST = 5;
	static final ResourceKey<LootTable> STORES = ResourceKey.create(Registries.LOOT_TABLE, JaymosKingdoms.id("remains/camp_stores"));

	@Override
	public MapCodec<LastCamp> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int hx = SIZE[this.template][0], hz = SIZE[this.template][1], h = SIZE[this.template][2];
		boolean deep = this.template == 1;
		if (RemainsFeature.village(level, origin)) {
			return false;
		}
		// the rock must be whole all round: every block of the room and its walls is solid, none wet
		for (int x = -hx - 1; x <= hx + 1; x++) {
			for (int z = -hz - 1; z <= hz + 1; z++) {
				for (int y = -1; y <= h; y++) {
					BlockState s = level.getBlockState(origin.offset(x, y, z));
					if (s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced()) {
						return false;
					}
				}
			}
		}
		Block wall = deep ? Blocks.DEEPSLATE_BRICKS : Blocks.STONE_BRICKS, floor = deep ? Blocks.COBBLED_DEEPSLATE : Blocks.COBBLESTONE;
		for (int x = -hx - 1; x <= hx + 1; x++) {
			for (int z = -hz - 1; z <= hz + 1; z++) {
				for (int y = -1; y <= h; y++) {
					boolean inside = Math.abs(x) <= hx && Math.abs(z) <= hz && y >= 0 && y < h;
					BlockPos at = origin.offset(x, y, z);
					if (inside) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					} else if (y == -1 || Math.abs(x) >= hx || Math.abs(z) >= hz || y == h - 1 || y == h) {
						Block lining = y == -1 ? floor : random.nextInt(6) == 0 ? (deep ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.CRACKED_STONE_BRICKS) : wall;
						if (Math.abs(x) == hx + 1 || Math.abs(z) == hz + 1 || y == -1 || y == h) {
							level.setBlock(at, lining.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
		level.setBlock(origin, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), Block.UPDATE_CLIENTS);
		List<BlockPos> ring = new ArrayList<>();
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) == 2 && Math.abs(x) <= hx && Math.abs(z) <= hz) {
					ring.add(origin.offset(x, 0, z));
				}
			}
		}
		Collections.shuffle(ring, new java.util.Random(random.nextLong()));
		int lie = Math.min(ring.size(), LEAST + random.nextInt(MOST - LEAST + 1));
		for (int i = 0; i < lie; i++) {
			BlockPos at = ring.get(i);
			level.setBlock(at, Remains.BLOCK.defaultBlockState().setValue(RemainsBlock.KIND, Remains.CAVE).setValue(RemainsBlock.POSE, random.nextInt(3))
				.setValue(RemainsBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)), Block.UPDATE_CLIENTS);
		}
		BlockPos barrel = origin.offset(hx, 0, -hz);
		level.setBlock(barrel, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
		RandomizableContainer.setBlockEntityLootTable(level, random, barrel, STORES);
		BlockPos web = origin.offset(-hx, h - 1, hz);
		level.setBlock(web, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		return true;
	}
}

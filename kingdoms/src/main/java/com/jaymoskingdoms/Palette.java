package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a kingdom's buildings are made of, matched to its village so they blend in: stone and oak on the plains,
 * cobble and spruce in the taiga, stone and spruce in the snow, terracotta and acacia on the savanna, sandstone in
 * the desert, red sandstone in the badlands, mossy stone and jungle wood in the jungle, mud brick and mangrove in the
 * swamp, stone and cherry among the cherry trees.
 */
record Palette(BlockState wall, BlockState mossy, BlockState cracked, BlockState floor, BlockState trim, BlockState stairs, BlockState slab,
	BlockState fortified, BlockState path, BlockState log, BlockState planks, BlockState woodSlab, BlockState fence, BlockState trapdoor) {

	static final Palette PLAINS = new Palette(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
		Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
		Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
		Blocks.COBBLESTONE.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(),
		Blocks.OAK_SLAB.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState());
	static final Palette TAIGA = new Palette(Blocks.COBBLESTONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
		Blocks.STONE.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(),
		Blocks.COBBLESTONE_STAIRS.defaultBlockState(), Blocks.COBBLESTONE_SLAB.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(),
		Blocks.GRAVEL.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
		Blocks.SPRUCE_SLAB.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
	static final Palette SNOWY = new Palette(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
		Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.POLISHED_DIORITE.defaultBlockState(),
		Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
		Blocks.PACKED_ICE.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
		Blocks.SPRUCE_SLAB.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
	static final Palette SAVANNA = new Palette(Blocks.TERRACOTTA.defaultBlockState(), named("orange_terracotta"),
		named("brown_terracotta"), Blocks.ACACIA_PLANKS.defaultBlockState(), Blocks.STRIPPED_ACACIA_LOG.defaultBlockState(),
		Blocks.ACACIA_STAIRS.defaultBlockState(), Blocks.ACACIA_SLAB.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(),
		Blocks.COARSE_DIRT.defaultBlockState(), Blocks.ACACIA_LOG.defaultBlockState(), Blocks.ACACIA_PLANKS.defaultBlockState(),
		Blocks.ACACIA_SLAB.defaultBlockState(), Blocks.ACACIA_FENCE.defaultBlockState(), Blocks.ACACIA_TRAPDOOR.defaultBlockState());
	static final Palette DESERT = new Palette(Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
		Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.CHISELED_SANDSTONE.defaultBlockState(),
		Blocks.SANDSTONE_STAIRS.defaultBlockState(), Blocks.SANDSTONE_SLAB.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
		Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.JUNGLE_LOG.defaultBlockState(), Blocks.JUNGLE_PLANKS.defaultBlockState(),
		Blocks.JUNGLE_SLAB.defaultBlockState(), Blocks.JUNGLE_FENCE.defaultBlockState(), Blocks.JUNGLE_TRAPDOOR.defaultBlockState());
	static final Palette BADLANDS = new Palette(Blocks.CUT_RED_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
		Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.CHISELED_RED_SANDSTONE.defaultBlockState(),
		Blocks.RED_SANDSTONE_STAIRS.defaultBlockState(), Blocks.RED_SANDSTONE_SLAB.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
		named("red_terracotta"), Blocks.DARK_OAK_LOG.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(),
		Blocks.DARK_OAK_SLAB.defaultBlockState(), Blocks.DARK_OAK_FENCE.defaultBlockState(), Blocks.DARK_OAK_TRAPDOOR.defaultBlockState());
	static final Palette JUNGLE = new Palette(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
		Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.JUNGLE_PLANKS.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
		Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState(), Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
		Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.JUNGLE_LOG.defaultBlockState(), Blocks.JUNGLE_PLANKS.defaultBlockState(),
		Blocks.JUNGLE_SLAB.defaultBlockState(), Blocks.JUNGLE_FENCE.defaultBlockState(), Blocks.JUNGLE_TRAPDOOR.defaultBlockState());
	static final Palette SWAMP = new Palette(Blocks.MUD_BRICKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState(),
		Blocks.MUD_BRICKS.defaultBlockState(), Blocks.MANGROVE_PLANKS.defaultBlockState(), Blocks.STRIPPED_MANGROVE_LOG.defaultBlockState(),
		Blocks.MUD_BRICK_STAIRS.defaultBlockState(), Blocks.MUD_BRICK_SLAB.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
		Blocks.PACKED_MUD.defaultBlockState(), Blocks.MANGROVE_LOG.defaultBlockState(), Blocks.MANGROVE_PLANKS.defaultBlockState(),
		Blocks.MANGROVE_SLAB.defaultBlockState(), Blocks.MANGROVE_FENCE.defaultBlockState(), Blocks.MANGROVE_TRAPDOOR.defaultBlockState());
	static final Palette CHERRY = new Palette(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
		Blocks.CALCITE.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState(), Blocks.STRIPPED_CHERRY_LOG.defaultBlockState(),
		Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
		Blocks.GRAVEL.defaultBlockState(), Blocks.CHERRY_LOG.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState(),
		Blocks.CHERRY_SLAB.defaultBlockState(), Blocks.CHERRY_FENCE.defaultBlockState(), Blocks.CHERRY_TRAPDOOR.defaultBlockState());

	/** A coloured block (they're looked up by name: 26.3 keeps them in collections, not fields). */
	private static BlockState named(String name) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(name)).defaultBlockState();
	}

	/**
	 * The palette for the village whose bell is at {@code pos}. A bell often stands on a river, a beach or a shore that runs
	 * through its town, so those don't count: the town's biome is the commonest other one round the bell.
	 */
	static Palette at(ServerLevel level, BlockPos pos) {
		Holder<Biome> here = level.getBiome(pos);
		if (!waterside(here)) {
			return of(here);
		}
		java.util.Map<Palette, Integer> votes = new java.util.LinkedHashMap<>();
		for (int r = 16; r <= 48; r += 16) {
			for (int i = 0; i < 8; i++) {
				double a = i / 8.0 * Math.PI * 2.0;
				Holder<Biome> biome = level.getBiome(pos.offset((int)Math.round(Math.cos(a) * r), 0, (int)Math.round(Math.sin(a) * r)));
				if (!waterside(biome)) {
					votes.merge(of(biome), 1, Integer::sum);
				}
			}
		}
		return votes.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(of(here));
	}

	private static boolean waterside(Holder<Biome> biome) {
		String name = biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
		return name.contains("river") || name.contains("beach") || name.contains("ocean") || name.contains("shore");
	}

	/** The palette for the village standing in {@code biome}. */
	static Palette of(Holder<Biome> biome) {
		String name = biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("plains");
		// (0.7.250) before the snow's "grove": the cherry grove was read as snowy land, so no settlement ever had the
		// cherry look, the Blossom tongue or the ronin harness
		if (name.contains("cherry")) {
			return CHERRY;
		}
		if (name.contains("badlands")) {
			return BADLANDS;
		} else if (name.contains("desert")) {
			return DESERT;
		} else if (name.contains("savanna")) {
			return SAVANNA;
		} else if (name.contains("snow") || name.contains("frozen") || name.contains("ice") || name.contains("grove")) {
			return SNOWY;
		} else if (name.contains("taiga")) {
			return TAIGA;
		} else if (name.contains("jungle")) {
			return JUNGLE;
		} else if (name.contains("swamp")) {
			return SWAMP;
		}
		return PLAINS;
	}
}

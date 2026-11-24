package com.jaymospowers.compat;

import com.jaymospowers.aether.AetherBlocks;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Alex's Caves Continued (LGPL-3.0, shipped unchanged): its Magnetic Caves are built from the Aether's magnetic stone
 * (Andy, 2026-11-24: "i'd like parity between the magnetic cave biome and the aether magnetic biome, in terms of blocks.
 * i dont like the mod's red and blue"). Where Alex's lays galena the caves get our ferrosite, and its scarlet and azure
 * energized galena bands become ichorite, the brown rock our ferrosite spires carry
 * ({@code mixin/alexscaves/MagneticCaveStoneMixin}, {@code FerrocaveStoneMixin}). The biome's own feature list
 * ({@code data/alexscaves/worldgen/biome/magnetic_caves.json}, only read when Alex's is loaded) drops the galena ores and
 * swarf, and grows our ferrosite pillars, spikes, ferrosite sand and magnetic
 * shrooms on the cave floors ({@code jaymospowers:cave_*} placed features). Its buried magnetic ruins are placed through
 * {@link #RUIN_STONE} ({@code UndergroundRuinsStoneMixin}), which swaps the same stone in them.
 * <p>
 * Alex's neodymium stays (Andy: "yes bring it back with our theme"): its scarlet and azure nodes still grow, and every
 * scarlet and azure texture is redrawn in our palette by {@code tools/alexscaves_recolour.py} (scarlet as the rust of
 * ferrosite sand, azure as the slate blue of the magnetic shrooms; {@code assets/alexscaves/textures}).
 * <p>
 * Alex's builds its cave stone rules while it starts, which may be before Powers has made its blocks; if so, that step
 * waits ({@link #deferred}) and {@link #init} runs it once the Aether's blocks exist.
 */
public final class AlexsCavesCompat {
	public static final String MOD_ID = "alexscaves";
	/** The Aether's blocks exist, so Alex's cave stone rules can be built with them. */
	public static volatile boolean ready;
	/** Alex's asked to build its cave stone rules before {@link #ready}; {@link #init} builds them. */
	public static volatile boolean deferred;

	public static final Supplier<Block> FERROSITE = () -> AetherBlocks.FERROSITE;
	public static final Supplier<Block> ICHORITE = () -> AetherBlocks.ICHORITE;

	/** Alex's magnetic blocks in its ruins (by id, so nothing of it is needed to load) and ours in their place. */
	private static final Map<String, Supplier<Block>> SWAPS = Map.of(
		"galena", FERROSITE,
		"energized_galena_scarlet", ICHORITE,
		"energized_galena_azure", ICHORITE,
		"energized_galena_neutral", ICHORITE,
		"galena_iron_ore", () -> Blocks.IRON_ORE);

	/** Swaps Alex's galena and energized galena for ours as a ruin is placed. Never saved, so its codec is a stub. */
	public static final StructureProcessor RUIN_STONE = new StructureProcessor() {
		@Override
		public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pivot, BlockPos origin,
				StructureTemplate.@Nullable StructureBlockInfo info, StructurePlaceSettings settings) {
			if (info == null) {
				return null;
			}
			Identifier id = BuiltInRegistries.BLOCK.getKey(info.state().getBlock());
			Supplier<Block> ours = MOD_ID.equals(id.getNamespace()) ? SWAPS.get(id.getPath()) : null;
			return ours == null ? info : new StructureTemplate.StructureBlockInfo(info.pos(), ours.get().defaultBlockState(), null);
		}

		@Override
		public MapCodec<? extends StructureProcessor> codec() {
			return MapCodec.unit(this);
		}
	};

	private AlexsCavesCompat() {
	}

	/** After the Aether's blocks are made: builds Alex's cave stone rules if it asked too early. */
	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return;
		}
		ready = true;
		if (deferred) {
			deferred = false;
			com.github.alexmodguy.alexscaves.server.level.surface.ACSurfaceRules.setup();
		}
	}
}

package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.625) Andy (2026-11-06), of Dungeons and Taverns' buildings: "same with the tavern". With D&T installed
 * ({@link DntMine#MOD}, All Rights Reserved; read at run time from the player's own copy, nothing of theirs copied into
 * ours), a kingdom's builders put up D&T's tavern house instead of ours, in the wood of the land round its seat (D&T has
 * thirteen: oak, spruce, birch, dark oak, acacia, jungle, mangrove, cherry, pale, swamp, desert, snowy, dappled). Built
 * from storage a block at a time like any building ({@link Construction}); the yard pieces D&T joins round it in the
 * wild (stalls, carts, a well) aren't built. Null when D&T isn't there (or the house can't be read): our own tavern.
 */
final class DntBuildings {
	private static final Map<String, Optional<Blueprint>> CACHE = new HashMap<>();

	private DntBuildings() {
	}

	static void clear() {
		CACHE.clear();
	}

	/** D&T's tavern house for the land round {@code faction}'s seat, or null. */
	static @Nullable Blueprint tavern(ServerLevel level, Faction faction) {
		if (!FabricLoader.getInstance().isModLoaded(DntMine.MOD)) {
			return null;
		}
		String wood = wood(level.getBiome(faction.seat()));
		Blueprint bp = read(level.getServer(), "tavern/tavern_house_" + wood);
		return bp != null ? bp : read(level.getServer(), "tavern/tavern_house_oak");
	}

	/** Which of D&T's tavern woods suits a biome. */
	static String wood(Holder<Biome> biome) {
		String id = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
		if (id.contains("snow") || id.contains("frozen") || id.contains("ice") || id.contains("grove") || id.contains("peaks")) {
			return "snowy";
		}
		if (id.contains("desert") || id.contains("badlands")) {
			return "desert";
		}
		if (id.contains("mangrove")) {
			return "mangrove";
		}
		if (id.contains("swamp")) {
			return "swamp";
		}
		if (id.contains("jungle") || id.contains("bamboo")) {
			return "jungle";
		}
		if (id.contains("savanna")) {
			return "acacia";
		}
		if (id.contains("cherry")) {
			return "cherry";
		}
		if (id.contains("pale")) {
			return "pale";
		}
		if (id.contains("dark_forest")) {
			return "dark_oak";
		}
		if (id.contains("birch")) {
			return "birch";
		}
		if (id.contains("taiga")) {
			return "spruce";
		}
		return "oak";
	}

	private static @Nullable Blueprint read(MinecraftServer server, String path) {
		return CACHE.computeIfAbsent(path, p -> {
			Optional<StructureTemplate> template;
			try {
				template = server.getStructureTemplateManager().get(Identifier.fromNamespaceAndPath("nova_structures", p));
			} catch (RuntimeException e) {
				JaymosKingdoms.LOGGER.warn("Dungeons and Taverns' {} can't be read: {}", p, e.toString());
				return Optional.empty();
			}
			return template.map(t -> Blueprint.fromTemplate("tavern", t.save(new CompoundTag())));
		}).orElse(null);
	}
}

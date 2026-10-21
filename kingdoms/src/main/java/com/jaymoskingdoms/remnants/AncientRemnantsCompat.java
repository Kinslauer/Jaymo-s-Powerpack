package com.jaymoskingdoms.remnants;

import com.jaymoskingdoms.JaymosKingdoms;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * (0.7.145) Ancient Remnants' own monoliths (Obscuria's mod, its Obscuria Modding License allows compatibility layers),
 * when it's loaded: each of its monoliths found in the loaded chunks round a player is remembered as an Aether monolith,
 * its element by its biome, and wakes a Giant like ours ({@link Monoliths}). Nothing of the mod's is touched: its
 * structures are only read, by id. Ancient Remnants has no Fabric 26.3 build yet, so for now this sleeps.
 */
final class AncientRemnantsCompat {
	static final String MOD = "ancient_remnants";
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);
	/** How often the chunks round each player are looked over (ticks), and how far (chunks). */
	static final int SCAN_TICKS = 600, SCAN_CHUNKS = 5;
	/** A structure of the mod's whose id ends with this is a monolith (its monuments aren't). */
	static final String MONOLITH_SUFFIX = "_monolith";
	/** One of its monoliths this near a known one (blocks) is that one. */
	static final double SAME_RANGE = 48.0;

	private AncientRemnantsCompat() {
	}

	static void register() {
		if (LOADED) {
			JaymosKingdoms.LOGGER.info("Ancient Remnants is loaded: its monoliths wake the Aether's Giants");
		}
	}

	/** Remembers every Ancient Remnants monolith in the loaded chunks round the Overworld's players. */
	static void scan(ServerLevel level) {
		if (!LOADED) {
			return;
		}
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			ChunkPos middle = player.chunkPosition();
			for (int dx = -SCAN_CHUNKS; dx <= SCAN_CHUNKS; dx++) {
				for (int dz = -SCAN_CHUNKS; dz <= SCAN_CHUNKS; dz++) {
					int cx = middle.x() + dx, cz = middle.z() + dz;
					if (!level.hasChunk(cx, cz)) {
						continue;
					}
					ChunkAccess chunk = level.getChunk(cx, cz);
					for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
						Identifier id = registry.getKey(e.getKey());
						if (id == null || !id.getNamespace().equals(MOD) || !id.getPath().endsWith(MONOLITH_SUFFIX)) {
							continue;
						}
						for (long ref : e.getValue()) {
							ChunkPos from = ChunkPos.unpack(ref);
							if (!level.hasChunk(from.x(), from.z())) {
								continue;
							}
							StructureStart start = level.getChunk(from.x(), from.z()).getStartForStructure(e.getKey());
							if (start != null && start.isValid()) {
								found(level, start);
							}
						}
					}
				}
			}
		}
	}

	private static void found(ServerLevel level, StructureStart start) {
		BlockPos centre = start.getBoundingBox().getCenter();
		if (Monoliths.nearest(level, centre, SAME_RANGE) != null) {
			return;
		}
		BlockPos foot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre);
		Element element = Element.of(level.getBiome(foot));
		Monoliths.remember(level, new Monoliths.Monolith(foot.immutable(), element, Monoliths.AWAKE, false));
		JaymosKingdoms.LOGGER.info("Ancient Remnants' monolith at {} carries the {} remnant", foot, element.key());
	}
}

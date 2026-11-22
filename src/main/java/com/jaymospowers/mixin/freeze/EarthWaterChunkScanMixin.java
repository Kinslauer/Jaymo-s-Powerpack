package com.jaymospowers.mixin.freeze;

import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Earth &amp; Water (potatowolfie, all rights reserved; shipped unchanged, nothing of it copied) looks at every block of
 * every chunk as it loads, to schedule a tick for five of its blocks. In fresh land that is 16 x 16 x height reads per
 * chunk: 110 to 204 samples in each of three freezes on the Steam Deck (2026-11-18). Its scan is skipped for a chunk
 * none of whose sections' palettes hold one of the five blocks it looks for; where one does, the scan runs as it always
 * did. (Any block of the mod was too wide: its stones and the like are in most chunks, and the scan still ran in a 37 s
 * freeze on 2026-11-19, 216 samples.)
 * Skipped when Earth &amp; Water isn't there.
 */
@Pseudo
@Mixin(targets = "potatowolfie.earth_and_water.EarthWater", remap = false)
abstract class EarthWaterChunkScanMixin {
	private static final String NAMESPACE = "earth-and-water";
	/** The blocks its scan schedules a tick for (its ModBlocks: the chiseled prismarines and dripstone bricks, the spawner). */
	private static final java.util.Set<String> SCANNED = java.util.Set.of("chiseled_dark_prismarine", "chiseled_prismarine_bricks",
		"chiseled_dark_dripstone_bricks", "chiseled_dripstone_bricks", "reinforced_spawner");
	private static final Predicate<BlockState> IS_THEIRS = state -> {
		net.minecraft.resources.Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return NAMESPACE.equals(id.getNamespace()) && SCANNED.contains(id.getPath());
	};

	@Inject(method = "scheduleBlockTicksForChunk", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymospowers$skipChunksWithoutTheirBlocks(ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
		for (LevelChunkSection section : chunk.getSections()) {
			if (section.maybeHas(IS_THEIRS)) {
				return;
			}
		}
		ci.cancel();
	}
}

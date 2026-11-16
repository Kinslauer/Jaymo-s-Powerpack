package com.jaymospowers.mixin;

import com.jaymospowers.compat.Stacked;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a One Dimension world CustomChunks runs a chunk's natural spawning once for each band (the Aether, the Overworld, the
 * Nether, ...). A band with no player near the chunk is skipped, as separate dimensions would be: hostile mobs made there
 * despawned at once, and animals piled up in lands nobody was in. Every band's pass looked up biomes, ground and
 * structures, and the Deck server froze 34 s behind as a player joined (2026-11-16 5:57 PM, a profile: 61% of the server
 * thread in those passes).
 */
@Mixin(NaturalSpawner.class)
abstract class BandSpawnNearPlayersMixin {
	/** Vanilla's despawn distance (128), plus the chunk's half-width and a spawn group's walk, measured from its middle. */
	private static final int REACH = 128 + 16;

	@Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$onlyBandsWithPlayers(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state,
		List<MobCategory> categories, CallbackInfo ci) {
		int[] band = Stacked.spawningBand();
		if (band == null) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		double x = pos.getMiddleBlockX(), z = pos.getMiddleBlockZ();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			double dx = player.getX() - x, dz = player.getZ() - z;
			double dy = player.getY() < band[0] ? band[0] - player.getY() : player.getY() >= band[1] ? player.getY() - band[1] : 0.0;
			if (dx * dx + dz * dz + dy * dy <= (double) REACH * REACH) {
				return;
			}
		}
		ci.cancel();
	}
}

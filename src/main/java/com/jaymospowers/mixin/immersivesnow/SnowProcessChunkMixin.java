package com.jaymospowers.mixin.immersivesnow;

import com.jaymospowers.core.Loaded;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Immersive Snow Reloaded (MIT, shipped unchanged, nothing of it copied) lays and melts snow on a chunk each tick. Setting
 * a block updates the blocks around it, and the freeze check looks two blocks over, so a chunk at the edge of the loaded
 * land read the next chunk, which was only scheduled: the server thread waited for it to be made (the Deck froze 22, 31
 * and 41 s on 2026-11-18, about 450 profile samples under this). A chunk is now left alone until it and all eight around
 * it are made and loaded; it gets its turn once the land beside it is there. Skipped when the mod isn't there.
 */
@Pseudo
@Mixin(targets = "net.cerealcamera.immersive_snow_reloaded.Logic", remap = false)
abstract class SnowProcessChunkMixin {
	@Inject(method = "processChunk", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$onlyWithNeighbours(ServerLevel level, ChunkPos pos, CallbackInfo ci) {
		if (!Loaded.made(level, pos.getMinBlockX() - 16, pos.getMinBlockZ() - 16, pos.getMaxBlockX() + 16, pos.getMaxBlockZ() + 16)) {
			ci.cancel();
		}
	}
}

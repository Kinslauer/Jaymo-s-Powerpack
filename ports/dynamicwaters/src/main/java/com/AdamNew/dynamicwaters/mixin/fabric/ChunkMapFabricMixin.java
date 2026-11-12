package com.AdamNew.dynamicwaters.mixin.fabric;

import com.AdamNew.dynamicwaters.DynamicWaters;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public class ChunkMapFabricMixin {
	@Inject(method = "markChunkPendingToSend(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("TAIL"))
	private static void dynamicwaters$onChunkWatch(ServerPlayer player, LevelChunk chunk, CallbackInfo ci) {
		DynamicWaters.ModEvents.onChunkWatch(player, player.level(), chunk.getPos().x(), chunk.getPos().z());
	}
}

package com.AdamNew.dynamicwaters.mixin.fabric;

import com.AdamNew.dynamicwaters.DynamicWaters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
public class PlayerChunkSenderFabricMixin {
	@Inject(method = "sendChunk", at = @At("TAIL"))
	private static void dynamicwaters$onChunkSent(ServerGamePacketListenerImpl listener, ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
		DynamicWaters.onChunkSent(listener.player, level, chunk);
	}
}

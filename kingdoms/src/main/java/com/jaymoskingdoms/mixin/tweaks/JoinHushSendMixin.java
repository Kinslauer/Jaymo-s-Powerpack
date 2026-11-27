package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.JoinHush;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat, system lines, advancement and recipe updates sent to a player who can't see the world yet are held back by
 * {@link JoinHush} until they can (every send to a player comes through here; {@code send(Packet)} calls this one).
 */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class JoinHushSendMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$hush(Packet<?> packet, @Nullable ChannelFutureListener listener, CallbackInfo ci) {
		if ((Object)this instanceof ServerGamePacketListenerImpl game && game.player != null && JoinHush.hold(game.player, packet, listener)) {
			ci.cancel();
		}
	}
}

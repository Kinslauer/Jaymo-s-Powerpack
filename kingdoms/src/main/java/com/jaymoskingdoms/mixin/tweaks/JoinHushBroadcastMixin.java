package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.JoinHush;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An advancement announcement for a player who can't see the world yet is said once they can, to everyone, not while
 * they're still loading in ({@link JoinHush#holdAnnouncement}; the two-argument broadcast calls this one).
 */
@Mixin(PlayerList.class)
abstract class JoinHushBroadcastMixin {
	@Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$hushAnnouncement(Component message, Function<ServerPlayer, Component> perViewer, boolean overlay, CallbackInfo ci) {
		if (JoinHush.holdAnnouncement(message, perViewer, overlay)) {
			ci.cancel();
		}
	}
}

package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.Revival;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reincarnate clicked on the death screen while a final death is held for a reviver ({@link Revival}): the death goes
 * ahead first, so the respawn that follows is an ordinary one.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class RevivalRespawnMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleClientCommand", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
		shift = At.Shift.AFTER))
	private void jaymospowers$deathFirst(ServerboundClientCommandPacket packet, CallbackInfo ci) {
		if (packet.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN && Revival.held(this.player)) {
			Revival.respawnPressed(this.player);
		}
	}
}

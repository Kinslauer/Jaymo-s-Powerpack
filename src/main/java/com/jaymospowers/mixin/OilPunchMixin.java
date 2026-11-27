package com.jaymospowers.mixin;

import com.jaymospowers.alchemy.oil.Oils;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The punch the client sends when it swings at nothing: an oiled blade swung at the air is a swing used ({@link Oils#punched}). */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class OilPunchMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePunch", at = @At("HEAD"))
	private void jaymospowers$oilPunched(ServerboundPunchPacket packet, CallbackInfo ci) {
		Oils.punched(this.player);
	}
}

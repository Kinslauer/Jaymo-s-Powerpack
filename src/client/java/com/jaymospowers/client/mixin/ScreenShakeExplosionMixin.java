package com.jaymospowers.client.mixin;

import com.jaymospowers.client.sound.Blasts;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every explosion near you (a creeper, TNT, a ki blast) shakes your screen from where it went off (see ScreenShake).
 * (0.20.56) Its size is told to Blasts before its sound plays, so the boom and the shake reach you together.
 */
@Mixin(ClientPacketListener.class)
abstract class ScreenShakeExplosionMixin {
	@Inject(method = "handleExplosion", at = @At("HEAD"))
	private void jaymospowers$coming(ClientboundExplodePacket packet, CallbackInfo ci) {
		Blasts.explosionComing(packet.center(), packet.radius());
	}

	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void jaymospowers$shake(ClientboundExplodePacket packet, CallbackInfo ci) {
		Blasts.explosionDone();
	}
}

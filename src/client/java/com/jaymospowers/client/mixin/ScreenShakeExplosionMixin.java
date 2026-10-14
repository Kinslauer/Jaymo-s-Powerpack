package com.jaymospowers.client.mixin;

import com.jaymospowers.client.ScreenShake;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every explosion near you (a creeper, TNT, a ki blast) shakes your screen from where it went off (see ScreenShake). */
@Mixin(ClientPacketListener.class)
abstract class ScreenShakeExplosionMixin {
	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void jaymospowers$shake(ClientboundExplodePacket packet, CallbackInfo ci) {
		ScreenShake.explosion(packet.center(), packet.radius());
	}
}

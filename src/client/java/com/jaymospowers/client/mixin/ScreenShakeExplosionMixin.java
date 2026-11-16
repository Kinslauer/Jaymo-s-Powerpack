package com.jaymospowers.client.mixin;

import com.jaymospowers.client.blast.BlastFx;
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
 * Its debris, dust, flash and ringing ears (BlastFx) start here too, while its blocks are still there on this side.
 */
@Mixin(ClientPacketListener.class)
abstract class ScreenShakeExplosionMixin {
	@Inject(method = "handleExplosion", at = @At("HEAD"))
	private void jaymospowers$coming(ClientboundExplodePacket packet, CallbackInfo ci) {
		Blasts.explosionComing(packet.center(), packet.radius());
	}

	/** On the game's thread (the head also runs once on the network thread, which hands the packet over). */
	@Inject(method = "handleExplosion", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V"))
	private void jaymospowers$debris(ClientboundExplodePacket packet, CallbackInfo ci) {
		BlastFx.explosion(packet.center(), packet.radius(), packet.blockCount());
	}

	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void jaymospowers$shake(ClientboundExplodePacket packet, CallbackInfo ci) {
		Blasts.explosionDone();
	}
}

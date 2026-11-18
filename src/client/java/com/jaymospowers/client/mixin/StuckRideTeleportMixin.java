package com.jaymospowers.client.mixin;

import com.jaymospowers.JaymosPowers;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A teleport from the server isn't lost on a ride the server has already ended. Vanilla skips the move for a passenger
 * but still answers with where it stands, so a player whose client kept a vehicle the server had let go of answered every
 * teleport from where they were, the server refused it and sent it again, and they rubber-banded until they died
 * (Jungxxv, 2026-11-18 12:36 PM: 509 "moved too quickly" in 50 s after a /tp). A teleport further than {@link #REACH}
 * from a ride the client still holds now gets the player off it first; a teleport that far has dismounted them on the
 * server already. The ride's type and id are logged, to find what kept it.
 */
@Mixin(ClientPacketListener.class)
abstract class StuckRideTeleportMixin {
	/** How far (blocks) a teleport has to go before a ride the client holds is taken for stale. */
	private static final double REACH = 8.0;

	@Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V"))
	private void jaymospowers$dropStaleRide(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.isPassenger()) {
			return;
		}
		Set<Relative> relatives = packet.relatives();
		Vec3 to = packet.change().position();
		Vec3 target = new Vec3(relatives.contains(Relative.X) ? player.getX() + to.x : to.x,
			relatives.contains(Relative.Y) ? player.getY() + to.y : to.y, relatives.contains(Relative.Z) ? player.getZ() + to.z : to.z);
		if (target.distanceToSqr(player.position()) <= REACH * REACH) {
			return;
		}
		Entity ride = player.getVehicle();
		JaymosPowers.LOGGER.warn("A teleport {} blocks away came while riding {} (id {}); getting off it so the teleport takes",
			(int)Math.sqrt(target.distanceToSqr(player.position())), ride == null ? "?" : BuiltInRegistries.ENTITY_TYPE.getKey(ride.getType()),
			ride == null ? -1 : ride.getId());
		player.removeVehicle();
	}
}

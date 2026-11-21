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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A teleport from the server isn't lost on a ride the server has already ended. Vanilla skips the move for a passenger
 * but still answers with where it stands, so a player whose client kept a vehicle the server had let go of answered every
 * teleport from where they were, the server refused it and sent it again, and they rubber-banded until they died
 * (Jungxxv, 2026-11-18 12:36 PM: 509 "moved too quickly" in 50 s after a /tp). A ride teleported with its rider
 * (a boat through an End gateway, /tp of a horse) also sends its rider one far teleport while they stay seated, so one
 * alone means nothing: only when the same far teleport (further than {@link #REACH}, to within {@link #SAME} of the last)
 * comes again within {@link #AGAIN_MS} while the client still rides, the server refusing its answers, does the player get
 * off first. The ride's type and id are logged, to find what kept it.
 */
@Mixin(ClientPacketListener.class)
abstract class StuckRideTeleportMixin {
	/** How far (blocks) a teleport has to go before a ride the client holds is taken for stale. */
	private static final double REACH = 8.0;
	/** How close (blocks) a repeated teleport's target is to the last, and how soon (ms) it comes again, to be the same. */
	private static final double SAME = 2.0;
	private static final long AGAIN_MS = 5000;

	/** The last far teleport that came while riding, and when (ms); null for none. */
	@Unique
	private static Vec3 jaymospowers$lastFar;
	@Unique
	private static long jaymospowers$lastFarAt;

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
		long now = System.currentTimeMillis();
		boolean again = jaymospowers$lastFar != null && now - jaymospowers$lastFarAt <= AGAIN_MS
			&& jaymospowers$lastFar.distanceToSqr(target) <= SAME * SAME;
		jaymospowers$lastFar = target;
		jaymospowers$lastFarAt = now;
		if (!again) {
			// (the first: maybe the ride itself is being teleported with them, so it's left to vanilla)
			return;
		}
		Entity ride = player.getVehicle();
		if (ride != null && ride.distanceToSqr(target) <= REACH * REACH) {
			// (the ride went there too: a ride teleported with its rider, its answer only slow)
			return;
		}
		jaymospowers$lastFar = null;
		JaymosPowers.LOGGER.warn("The same teleport {} blocks away came again while riding {} (id {}); getting off it so the teleport takes",
			(int)Math.sqrt(target.distanceToSqr(player.position())), ride == null ? "?" : BuiltInRegistries.ENTITY_TYPE.getKey(ride.getType()),
			ride == null ? -1 : ride.getId());
		player.removeVehicle();
	}
}

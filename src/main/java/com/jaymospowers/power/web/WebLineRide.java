package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Riding a webline (Legends patch 1, {@code ws_ride}): Andy's "ride, hang under and switch weblines". Sneak and use on a
 * webline ({@link WebBridges}) to stand on it or hang from it by the hands; forward and back slide you along it, left or
 * right at a crossing turns you onto the other line, and sneak again lets go. The slide is the slinger's own client's
 * ({@code WebLineRideClient}); the server only takes their word that they are on a line that is really there, lets them
 * hang in the air there without being kicked for floating, and shows everyone the pose ({@link #RIDE}).
 */
public final class WebLineRide {
	/** Standing on a webline, and hanging under one by the hands. */
	public static final int STAND = 1, HANG = 2;
	/** The server believes a rider only this far (blocks) from the line they say they are on. */
	static final double NEAR = 6.0;

	/** Client → server: riding in {@code mode} ({@link #STAND}, {@link #HANG}), or 0 for off the line. */
	public record Ride(int mode) implements CustomPacketPayload {
		public static final Type<Ride> TYPE = new Type<>(JaymosPowers.id("web_line_ride"));
		public static final StreamCodec<ByteBuf, Ride> CODEC = ByteBufCodecs.VAR_INT.map(Ride::new, Ride::mode);

		@Override
		public Type<Ride> type() {
			return TYPE;
		}
	}

	/** How a web slinger rides a webline right now ({@link #STAND}, {@link #HANG}), for everyone to draw. */
	public static final AttachmentType<Integer> RIDE = AttachmentRegistry.create(JaymosPowers.id("web_line_ride"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private WebLineRide() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Ride.TYPE, Ride.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ride.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			int mode = payload.mode();
			if ((mode == STAND || mode == HANG) && player.isAlive() && !player.isSpectator() && Powers.has(player, WebPower.INSTANCE) && onLine(player)) {
				player.setAttached(RIDE, mode);
				player.resetFallDistance();
			} else {
				player.removeAttached(RIDE);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.getAttached(RIDE) == null) {
					continue;
				}
				if (!player.isAlive() || !Powers.has(player, WebPower.INSTANCE) || !onLine(player)) {
					player.removeAttached(RIDE);
				} else {
					// riding a line isn't falling
					player.resetFallDistance();
				}
			}
		});
	}

	/** Whether {@code entity} is riding a webline right now. */
	public static boolean riding(Entity entity) {
		return entity.getAttached(RIDE) != null;
	}

	/** A webline of this level runs within {@link #NEAR} of the player. */
	private static boolean onLine(ServerPlayer player) {
		List<WebBridges.Span> spans = player.level().getAttached(WebBridges.SPANS);
		if (spans == null) {
			return false;
		}
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		for (WebBridges.Span span : spans) {
			if (WebCut.distanceToSegment(at, span.from(), span.to()) <= NEAR) {
				return true;
			}
		}
		return false;
	}
}

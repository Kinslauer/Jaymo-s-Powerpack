package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.jspecify.annotations.Nullable;
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
	/**
	 * The server believes a rider only this far (blocks) from the line they say they are on: the client reaches 7 from the
	 * eye with 1.1 of aim slack and then carries the body to the line, so this covers that with room to spare.
	 */
	static final double NEAR = 10.0;
	/** The point a rider names must lie this near (blocks) a real line. */
	static final double CLAIM_SLACK = 0.75;
	/** The line each rider says they are on. */
	private static final Map<UUID, WebBridges.Span> CLAIMED = new HashMap<>();

	/**
	 * Client → server: riding in {@code mode} ({@link #STAND}, {@link #HANG}), or 0 for off the line; {@code point} is a
	 * place on the line they are on, which names it.
	 */
	public record Ride(int mode, Vec3 point) implements CustomPacketPayload {
		public static final Type<Ride> TYPE = new Type<>(JaymosPowers.id("web_line_ride"));
		public static final StreamCodec<ByteBuf, Ride> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Ride::mode,
			Vec3.STREAM_CODEC, Ride::point,
			Ride::new);

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
			WebBridges.Span claimed = (mode == STAND || mode == HANG) && player.isAlive() && !player.isSpectator()
				&& Powers.has(player, WebPower.INSTANCE) ? spanAt(player, payload.point()) : null;
			if (claimed != null) {
				CLAIMED.put(player.getUUID(), claimed);
				player.setAttached(RIDE, mode);
				player.resetFallDistance();
			} else {
				CLAIMED.remove(player.getUUID());
				player.removeAttached(RIDE);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CLAIMED.remove(handler.getPlayer().getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.getAttached(RIDE) == null) {
					CLAIMED.remove(player.getUUID());
					continue;
				}
				if (!player.isAlive() || player.isSpectator() || !Powers.has(player, WebPower.INSTANCE) || !stillOn(player)) {
					CLAIMED.remove(player.getUUID());
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

	private static Vec3 middle(ServerPlayer player) {
		return player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
	}

	/** The webline that {@code point} lies on and that the rider is within {@link #NEAR} of, or null. */
	private static WebBridges.@Nullable Span spanAt(ServerPlayer player, Vec3 point) {
		List<WebBridges.Span> spans = player.level().getAttached(WebBridges.SPANS);
		if (spans == null) {
			return null;
		}
		Vec3 at = middle(player);
		WebBridges.Span best = null;
		double bestGap = CLAIM_SLACK;
		for (WebBridges.Span span : spans) {
			double gap = WebCut.distanceToSegment(point, span.from(), span.to());
			if (gap <= bestGap && WebCut.distanceToSegment(at, span.from(), span.to()) <= NEAR) {
				best = span;
				bestGap = gap;
			}
		}
		return best;
	}

	/** The line this rider named is still there and they are still near it. */
	private static boolean stillOn(ServerPlayer player) {
		WebBridges.Span claimed = CLAIMED.get(player.getUUID());
		List<WebBridges.Span> spans = player.level().getAttached(WebBridges.SPANS);
		return claimed != null && spans != null && spans.contains(claimed)
			&& WebCut.distanceToSegment(middle(player), claimed.from(), claimed.to()) <= NEAR;
	}
}

package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Anyone can punch a web line to break it: a swing line, a Yank, a hitch, a webline. The client finds the line under
 * the crosshair (anywhere along it, the shooter's end or the anchor) and says which; the server checks it's real and
 * in reach, and cuts it.
 */
public final class WebCut {
	public static final byte SWING = 0, HITCH = 1, BRIDGE = 2;
	/** How far from the eyes a line can be punched (blocks), with a little slack for lag. */
	public static final double REACH = 4.5;

	/** Client → server: punch the line {@code kind} (whose player or hitched creature is {@code entity}) between {@code a} and {@code b}. */
	public record Cut(byte kind, int entity, Vec3 a, Vec3 b) implements CustomPacketPayload {
		public static final Type<Cut> TYPE = new Type<>(JaymosPowers.id("web_cut"));
		public static final StreamCodec<ByteBuf, Cut> CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, Cut::kind, ByteBufCodecs.VAR_INT, Cut::entity, Vec3.STREAM_CODEC, Cut::a, Vec3.STREAM_CODEC, Cut::b, Cut::new);

		@Override
		public Type<Cut> type() {
			return TYPE;
		}
	}

	private WebCut() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Cut.TYPE, Cut.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Cut.TYPE, (payload, ctx) -> cut(ctx.player(), payload));
	}

	private static void cut(ServerPlayer player, Cut cut) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		if (distanceToSegment(eye, cut.a(), cut.b()) > REACH + 1.5) {
			return;
		}
		boolean done = switch (cut.kind()) {
			case SWING -> level.getEntity(cut.entity()) instanceof ServerPlayer owner && WebPower.cutLine(owner, cut.b());
			case HITCH -> {
				Entity tied = level.getEntity(cut.entity());
				yield tied != null && WebHitches.cut(level, tied, cut.b());
			}
			case BRIDGE -> WebBridges.cut(level, cut.a(), cut.b());
			default -> false;
		};
		if (done) {
			Vec3 at = closestPoint(eye, cut.a(), cut.b());
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.05);
		}
	}

	/** The point on segment a-b nearest to {@code p}. */
	public static Vec3 closestPoint(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double length = ab.lengthSqr();
		if (length < 1.0E-8) {
			return a;
		}
		double t = Math.clamp(p.subtract(a).dot(ab) / length, 0.0, 1.0);
		return a.add(ab.scale(t));
	}

	static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		return p.distanceTo(closestPoint(p, a, b));
	}
}

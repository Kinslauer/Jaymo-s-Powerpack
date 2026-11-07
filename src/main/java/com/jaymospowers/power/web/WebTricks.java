package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
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

/**
 * Swing tricks: flips and twirls off a released web, on the web, and on touching down (wings too). The slinger's own
 * client runs the swing, so it knows when a web lets go or the feet land, and asks for the trick; the server checks
 * it's a trick from a web slinger who isn't mid-dodge or ragdolled, and starts the {@link WebPose} everyone draws.
 */
public final class WebTricks {
	/** A new trick doesn't cut into one this fresh (ticks), unless it's a landing. */
	static final int MIN_GAP_TICKS = 4;

	/** Client → server: start trick {@code kind} ({@link WebPose#FIRST_TRICK}..{@link WebPose#LAST_TRICK}), turning to {@code side} (±1). */
	public record Trick(int kind, int side) implements CustomPacketPayload {
		public static final Type<Trick> TYPE = new Type<>(JaymosPowers.id("web_trick"));
		public static final StreamCodec<ByteBuf, Trick> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Trick::kind, ByteBufCodecs.VAR_INT, Trick::side, Trick::new);

		@Override
		public Type<Trick> type() {
			return TYPE;
		}
	}

	private WebTricks() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Trick.TYPE, Trick.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Trick.TYPE, (payload, ctx) -> trick(ctx.player(), payload));
	}

	private static void trick(ServerPlayer player, Trick trick) {
		if (!WebPose.trick(trick.kind()) || !player.isAlive() || player.isSpectator() || player.isPassenger()
			|| !Powers.has(player, WebPower.INSTANCE)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		WebPose current = player.getAttached(WebPower.POSE);
		if (current != null && now - current.start() < current.ticks()) {
			// a dodge or a ragdoll is never cut short; another trick only by a landing, or once it's under way
			if (!WebPose.trick(current.kind()) || !WebPose.landing(trick.kind()) && now - current.start() < MIN_GAP_TICKS) {
				return;
			}
		}
		WebPower.pose(player, trick.kind(), trick.side() < 0 ? -1 : 1);
		double x = player.getX(), y = player.getY(), z = player.getZ();
		switch (trick.kind()) {
			case WebPose.HERO_LAND -> {
				level.playSound(null, x, y, z, SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 0.6F, 0.7F);
				level.sendParticles(ParticleTypes.POOF, x, y + 0.1, z, 10, 0.5, 0.05, 0.5, 0.03);
				// (Motion patch 0) the ring of dust every hero landing kicks up
				com.jaymospowers.freerun.LandingRoll.dust(level, player);
			}
			case WebPose.LAND_ROLL, WebPose.WINGS_LAND ->
				level.playSound(null, x, y, z, SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, 0.5F, 1.2F);
			default -> level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3F, 1.9F);
		}
	}
}

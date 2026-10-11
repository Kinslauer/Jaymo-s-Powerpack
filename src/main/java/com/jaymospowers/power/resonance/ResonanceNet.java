package com.jaymospowers.power.resonance;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Resonance's own packets. */
public final class ResonanceNet {
	private ResonanceNet() {
	}

	/**
	 * Server → one client: these creatures can be heard (outlined through walls, on this client only) for {@code ticks}.
	 * {@code kind}: {@link #ECHO} (Echolocation, the Tuning Fork), {@link #HEARTBEAT} (Shriek's heartbeats).
	 */
	public record Sense(int[] ids, int ticks, int kind) implements CustomPacketPayload {
		public static final int ECHO = 0;
		public static final int HEARTBEAT = 1;
		public static final Type<Sense> TYPE = new Type<>(JaymosPowers.id("resonance_sense"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sense> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarIntArray(p.ids);
				buf.writeVarInt(p.ticks);
				buf.writeVarInt(p.kind);
			},
			buf -> new Sense(buf.readVarIntArray(4096), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Sense> type() {
			return TYPE;
		}
	}

	/** Server → one client: something you couldn't see just hit you, from over there (Heavy hearing). */
	public record Heard(double x, double y, double z) implements CustomPacketPayload {
		public static final Type<Heard> TYPE = new Type<>(JaymosPowers.id("resonance_heard"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Heard> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
			},
			buf -> new Heard(buf.readDouble(), buf.readDouble(), buf.readDouble()));

		@Override
		public Type<Heard> type() {
			return TYPE;
		}
	}

	/** Client → server: scrolled while holding Z (cycle the Throw Voice sound by {@code steps}). */
	public record Cycle(int steps) implements CustomPacketPayload {
		public static final Type<Cycle> TYPE = new Type<>(JaymosPowers.id("resonance_cycle"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cycle> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.steps),
			buf -> new Cycle(buf.readVarInt()));

		@Override
		public Type<Cycle> type() {
			return TYPE;
		}
	}

	/** Client → server: scrolled while holding X (Tune by {@code steps}; 0 = back to the middle). */
	public record Tune(int steps) implements CustomPacketPayload {
		public static final Type<Tune> TYPE = new Type<>(JaymosPowers.id("resonance_tune"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Tune> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.steps),
			buf -> new Tune(buf.readVarInt()));

		@Override
		public Type<Tune> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sense.TYPE, Sense.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Heard.TYPE, Heard.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Cycle.TYPE, Cycle.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Tune.TYPE, Tune.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Cycle.TYPE, (payload, ctx) -> ResonanceVoice.cycle(ctx.player(), payload.steps()));
		ServerPlayNetworking.registerGlobalReceiver(Tune.TYPE, (payload, ctx) -> ResonancePower.tune(ctx.player(), payload.steps()));
	}

	static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}

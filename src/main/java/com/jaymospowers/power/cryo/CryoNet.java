package com.jaymospowers.power.cryo;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** (0.14.0) Cryokinesis' packets. */
public final class CryoNet {
	private CryoNet() {
	}

	/** Client → server: X pressed (Ice Wall). X is the shared power skill key; the client takes a Cryokinesis user's presses. */
	public record Wall() implements CustomPacketPayload {
		public static final Type<Wall> TYPE = new Type<>(JaymosPowers.id("cryo_wall"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Wall> CODEC = StreamCodec.unit(new Wall());

		@Override
		public Type<Wall> type() {
			return TYPE;
		}
	}

	/**
	 * Client → server: skating on Ice Slide, the player hit a wall at {@code speed} blocks a tick (motion is the client's).
	 * The server turns it into damage ({@link CryoSlide#crash}).
	 */
	public record Crash(float speed) implements CustomPacketPayload {
		public static final Type<Crash> TYPE = new Type<>(JaymosPowers.id("cryo_crash"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Crash> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeFloat(p.speed),
			buf -> new Crash(buf.readFloat()));

		@Override
		public Type<Crash> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Wall.TYPE, Wall.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Crash.TYPE, Crash.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Wall.TYPE, (payload, ctx) -> CryoWall.cast(ctx.player()));
		ServerPlayNetworking.registerGlobalReceiver(Crash.TYPE, (payload, ctx) -> CryoSlide.crash(ctx.player(), payload.speed()));
	}
}

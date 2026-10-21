package com.jaymospowers.power.smoke;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** The Smoke power's packets. */
public final class SmokeNet {
	private SmokeNet() {
	}

	/**
	 * Server → one client: all the smoke near them, replacing what they had. {@code origin} is a packed block position;
	 * {@code cells} is {@link #STRIDE} bytes a block: x, y, z off the origin, density (0-255 of {@link SmokeField#CAP})
	 * and flags ({@link #YOURS}: you made it; the upper seven bits are its soot, 0-127), then more flags ({@link #SMOG}:
	 * it's smog; {@link #FROZEN}, 0.14.0: it's freezing fog). Empty: no smoke near you any more.
	 */
	public record Field(long origin, byte[] cells) implements CustomPacketPayload {
		public static final int STRIDE = 6;
		public static final int YOURS = 1;
		public static final int SMOG = 1;
		public static final int FROZEN = 2;
		public static final Type<Field> TYPE = new Type<>(JaymosPowers.id("smoke_field"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Field> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeLong(p.origin);
				buf.writeByteArray(p.cells);
			},
			buf -> new Field(buf.readLong(), buf.readByteArray(SmokeField.SYNC_MAX * STRIDE)));

		@Override
		public Type<Field> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Field.TYPE, Field.CODEC);
	}

	static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}

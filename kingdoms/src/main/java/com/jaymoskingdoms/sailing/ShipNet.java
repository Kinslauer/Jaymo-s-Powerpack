package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** (0.7.7) Client → server: use the block at (x, y, z) of a ship (in its own frame): take the helm, open a chest. */
public final class ShipNet {
	public record Use(int ship, int x, int y, int z) implements CustomPacketPayload {
		public static final Type<Use> TYPE = new Type<>(JaymosKingdoms.id("ship_use"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.ship);
				buf.writeVarInt(p.x);
				buf.writeVarInt(p.y);
				buf.writeVarInt(p.z);
			},
			buf -> new Use(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Use> type() {
			return TYPE;
		}
	}

	/** (0.7.12) Client → server: the one at the helm fires a broadside to the side they look. */
	public record Broadside(int ship) implements CustomPacketPayload {
		public static final Type<Broadside> TYPE = new Type<>(JaymosKingdoms.id("ship_broadside"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Broadside> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.ship), buf -> new Broadside(buf.readVarInt()));

		@Override
		public Type<Broadside> type() {
			return TYPE;
		}
	}

	private ShipNet() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, context) -> {
			if (context.player().level().getEntity(payload.ship()) instanceof Ship ship) {
				ship.use(context.player(), payload.x(), payload.y(), payload.z());
			}
		});
		PayloadTypeRegistry.serverboundPlay().register(Broadside.TYPE, Broadside.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Broadside.TYPE, (payload, context) -> {
			if (context.player().level().getEntity(payload.ship()) instanceof Ship ship) {
				ship.broadside(context.player());
			}
		});
	}
}

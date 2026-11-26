package com.jaymospowers.dino.network;

import com.jaymospowers.dino.Dino;
import com.jaymospowers.dino.entity.Dinosaur;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** The one packet: a rider's attack, special or roar key, sent to the dinosaur they're riding. */
public final class DinoNetwork {
	public static final int ATTACK = 0;
	public static final int SPECIAL = 1;
	public static final int ROAR = 2;

	private DinoNetwork() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(RiderAction.TYPE, RiderAction.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RiderAction.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.getVehicle() instanceof Dinosaur dino && dino.getControllingPassenger() == player) {
				switch (payload.action) {
					case ATTACK -> dino.riderAttack();
					case SPECIAL -> dino.trySpecial();
					case ROAR -> dino.riderRoar();
					default -> {
					}
				}
			}
		});
	}

	public record RiderAction(int action) implements CustomPacketPayload {
		public static final Type<RiderAction> TYPE = new Type<>(Dino.id("dino_rider_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RiderAction> CODEC = StreamCodec.of(
			(buf, msg) -> buf.writeByte(msg.action), buf -> new RiderAction(buf.readByte()));

		@Override
		public Type<RiderAction> type() {
			return TYPE;
		}
	}
}

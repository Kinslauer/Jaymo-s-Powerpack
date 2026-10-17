package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The bomb glider's packet. */
public final class BombGliderNet {
	/** Client → server: the rider of glider {@code glider} (entity id) left-clicked: drop a stick. */
	public record Drop(int glider) implements CustomPacketPayload {
		public static final Type<Drop> TYPE = new Type<>(JaymosPowers.id("bomb_glider_drop"));
		public static final StreamCodec<ByteBuf, Drop> CODEC = ByteBufCodecs.VAR_INT.map(Drop::new, Drop::glider);

		@Override
		public Type<Drop> type() {
			return TYPE;
		}
	}

	private BombGliderNet() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Drop.TYPE, Drop.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Drop.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof BombGlider glider && glider.getId() == payload.glider()) {
				glider.drop(context.player());
			}
		});
	}
}

package com.jaymospowers.gear;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.gear.arrow.QuiverCycle;
import com.jaymospowers.gear.belt.Belt;
import com.jaymospowers.gear.shield.ShieldMoves;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The gear keys' packets, client to server: the quiver, the belt, and the shield moves. */
public final class GearNet {
	/** The keys that act on worn gear. */
	public static final int QUIVER_CYCLE = 0, BELT_CYCLE = 1, BELT_THROW = 2;

	/** A gear key was pressed. */
	public record Key(int which) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosPowers.id("gear_key"));
		public static final StreamCodec<ByteBuf, Key> CODEC = ByteBufCodecs.VAR_INT.map(Key::new, Key::which);

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	/** A shield move was asked for ({@link ShieldMoves#THROW}, {@code SLAM}, {@code CHARGE}). */
	public record Shield(int move) implements CustomPacketPayload {
		public static final Type<Shield> TYPE = new Type<>(JaymosPowers.id("gear_shield"));
		public static final StreamCodec<ByteBuf, Shield> CODEC = ByteBufCodecs.VAR_INT.map(Shield::new, Shield::move);

		@Override
		public Type<Shield> type() {
			return TYPE;
		}
	}

	private GearNet() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Shield.TYPE, Shield.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, context) -> {
			switch (payload.which()) {
				case QUIVER_CYCLE -> QuiverCycle.cycle(context.player());
				case BELT_CYCLE -> Belt.cycle(context.player());
				case BELT_THROW -> Belt.throwPicked(context.player());
				default -> {
				}
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Shield.TYPE, (payload, context) -> ShieldMoves.move(context.player(), payload.move()));
	}
}

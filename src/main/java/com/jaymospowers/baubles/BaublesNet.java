package com.jaymospowers.baubles;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The bauble screen's packet. */
public final class BaublesNet {
	/** Client → server: open my bauble slots (the inventory tab or the Baubles key). */
	public record Open() implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosPowers.id("baubles_open"));
		public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.unit(new Open());

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	private BaublesNet() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Open.TYPE, Open.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Open.TYPE, (payload, context) -> Baubles.open(context.player()));
	}
}

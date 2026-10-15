package com.jaymospowers.backpack;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The backpack key's packet. */
public final class BackpackNet {
	/** Client → server: the backpack key was pressed (sneaking: take it off). */
	public record Key(boolean sneaking) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosPowers.id("backpack_key"));
		public static final StreamCodec<ByteBuf, Key> CODEC = ByteBufCodecs.BOOL.map(Key::new, Key::sneaking);

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	private BackpackNet() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, context) -> Backpack.useKey(context.player(), payload.sneaking()));
	}
}

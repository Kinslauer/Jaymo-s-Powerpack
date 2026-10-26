package com.jaymospowers.alchemy.codex;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The Codex's two packets: the server opening the book, and the book asking to unlock an entry. */
public final class CodexNet {
	/** Server → client: open the Codex screen (the item was used). */
	public record Open() implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosPowers.id("codex_open"));
		public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.unit(new Open());

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client → server: unlock this research entry (the server checks everything again). */
	public record Unlock(String id) implements CustomPacketPayload {
		public static final Type<Unlock> TYPE = new Type<>(JaymosPowers.id("codex_unlock"));
		public static final StreamCodec<ByteBuf, Unlock> CODEC = ByteBufCodecs.STRING_UTF8.map(Unlock::new, Unlock::id);

		@Override
		public Type<Unlock> type() {
			return TYPE;
		}
	}

	private CodexNet() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Unlock.TYPE, Unlock.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Unlock.TYPE, (payload, context) -> Codex.unlock(context.player(), payload.id()));
	}
}

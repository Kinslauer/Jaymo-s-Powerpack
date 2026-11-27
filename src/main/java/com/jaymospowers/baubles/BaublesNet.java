package com.jaymospowers.baubles;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The bauble screen's packets. */
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

	/**
	 * Client → server: in the creative inventory, a shift-click on {@code slot} (its index in the player's inventory menu)
	 * that should put a bauble on. The creative screen moves items on the client alone, so the server is told
	 * ({@link BaublesQuickEquip#creativeShiftClick}).
	 */
	public record QuickEquip(int slot) implements CustomPacketPayload {
		public static final Type<QuickEquip> TYPE = new Type<>(JaymosPowers.id("baubles_quick_equip"));
		public static final StreamCodec<ByteBuf, QuickEquip> CODEC = ByteBufCodecs.VAR_INT.map(QuickEquip::new, QuickEquip::slot);

		@Override
		public Type<QuickEquip> type() {
			return TYPE;
		}
	}

	private BaublesNet() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Open.TYPE, Open.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Open.TYPE, (payload, context) -> Baubles.open(context.player()));
		PayloadTypeRegistry.serverboundPlay().register(QuickEquip.TYPE, QuickEquip.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(QuickEquip.TYPE, (payload, context) ->
			context.server().execute(() -> BaublesQuickEquip.creativeShiftClick(context.player(), payload.slot())));
	}
}

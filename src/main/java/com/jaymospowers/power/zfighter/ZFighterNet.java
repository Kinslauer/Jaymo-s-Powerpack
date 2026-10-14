package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Z Fighter's own packet: the training screen's buttons and sneak+X. */
public final class ZFighterNet {
	public static final int BUY_STAT = 0;
	public static final int BUY_FORM = 1;
	public static final int BUY_LIMIT = 2;
	public static final int CYCLE_LIMIT = 3;

	private ZFighterNet() {
	}

	/** Client → server: {@code action} ({@link #BUY_STAT} etc.) on {@code index} (a stat, a form). */
	public record Train(int action, int index) implements CustomPacketPayload {
		public static final Type<Train> TYPE = new Type<>(JaymosPowers.id("zfighter_train"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Train> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.action);
				buf.writeVarInt(p.index);
			},
			buf -> new Train(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Train> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Train.TYPE, Train.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Train.TYPE, (payload, ctx) -> train(ctx.player(), payload));
	}

	private static void train(ServerPlayer player, Train train) {
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			return;
		}
		switch (train.action()) {
			case BUY_STAT -> ZFighterTraining.buyStat(player, train.index());
			case BUY_FORM -> ZFighterTraining.buyForm(player, train.index());
			case BUY_LIMIT -> ZFighterTraining.buyLimit(player);
			case CYCLE_LIMIT -> ZFighterPower.cycleLimit(player);
			default -> {
			}
		}
	}
}

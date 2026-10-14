package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Z Fighter's own packet: the training screen's buttons (stats, forms, Limit Break, techniques) and sneak+X. */
public final class ZFighterNet {
	public static final int BUY_STAT = 0;
	public static final int BUY_FORM = 1;
	public static final int BUY_LIMIT = 2;
	public static final int CYCLE_LIMIT = 3;
	public static final int BUY_TECHNIQUE = 4;
	public static final int EQUIP_TECHNIQUE = 5;

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

	/** Client → server: hold Z and scroll; take off into, or change to, flight pose {@code pose} (1-5), or land (0). */
	public record Fly(int pose) implements CustomPacketPayload {
		public static final Type<Fly> TYPE = new Type<>(JaymosPowers.id("zfighter_fly"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fly> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.pose),
			buf -> new Fly(buf.readVarInt()));

		@Override
		public Type<Fly> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Train.TYPE, Train.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Fly.TYPE, Fly.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Fly.TYPE, (payload, ctx) -> {
			if (Powers.has(ctx.player(), ZFighterPower.INSTANCE)) {
				ZFighterPower.fly(ctx.player(), payload.pose());
			}
		});
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
			case BUY_TECHNIQUE -> ZFighterTraining.buyTechnique(player, train.index());
			case EQUIP_TECHNIQUE -> ZFighterTraining.equipTechnique(player, train.index());
			default -> {
			}
		}
	}
}

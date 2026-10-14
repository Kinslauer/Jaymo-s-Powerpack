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
	public static final int PICK_LINEAGE = 6;

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

	/** Client → server: right click held bare-handed (the guard) or let go. */
	public record Guard(boolean up) implements CustomPacketPayload {
		public static final Type<Guard> TYPE = new Type<>(JaymosPowers.id("zfighter_guard"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Guard> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.up),
			buf -> new Guard(buf.readBoolean()));

		@Override
		public Type<Guard> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: a beam to draw (a Ki Wave and its kin, the Great Ape's): from, to, how thick, its colour, its
	 * style ({@code ZFighterKi.WAVE} etc.) and how long it lasts in ticks.
	 */
	public record BeamFx(double fx, double fy, double fz, double tx, double ty, double tz, float radius, int color, int style, int ticks)
		implements CustomPacketPayload {
		public static final Type<BeamFx> TYPE = new Type<>(JaymosPowers.id("zfighter_beam"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BeamFx> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeDouble(p.fx);
				buf.writeDouble(p.fy);
				buf.writeDouble(p.fz);
				buf.writeDouble(p.tx);
				buf.writeDouble(p.ty);
				buf.writeDouble(p.tz);
				buf.writeFloat(p.radius);
				buf.writeInt(p.color);
				buf.writeVarInt(p.style);
				buf.writeVarInt(p.ticks);
			},
			buf -> new BeamFx(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
				buf.readFloat(), buf.readInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<BeamFx> type() {
			return TYPE;
		}
	}

	/** Server → client: a Spirit Bomb (by its thrower's entity id) is here and this big; drawn while it keeps coming. */
	public record SpiritFx(int id, double x, double y, double z, float size, float glow) implements CustomPacketPayload {
		public static final Type<SpiritFx> TYPE = new Type<>(JaymosPowers.id("zfighter_spirit"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SpiritFx> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.id);
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeFloat(p.size);
				buf.writeFloat(p.glow);
			},
			buf -> new SpiritFx(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat()));

		@Override
		public Type<SpiritFx> type() {
			return TYPE;
		}
	}

	/** Server → client: the Eternal Dragon waits; open the wish screen. */
	public record OpenWish() implements CustomPacketPayload {
		public static final Type<OpenWish> TYPE = new Type<>(JaymosPowers.id("zfighter_open_wish"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenWish> CODEC = StreamCodec.unit(new OpenWish());

		@Override
		public Type<OpenWish> type() {
			return TYPE;
		}
	}

	/** Client → server: the wish picked on the wish screen ({@code ZFighterWishes.POWER} etc.). */
	public record Wish(int wish) implements CustomPacketPayload {
		public static final Type<Wish> TYPE = new Type<>(JaymosPowers.id("zfighter_wish"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Wish> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.wish),
			buf -> new Wish(buf.readVarInt()));

		@Override
		public Type<Wish> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenWish.TYPE, OpenWish.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Wish.TYPE, Wish.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Wish.TYPE, (payload, ctx) -> ZFighterWishes.wish(ctx.player(), payload.wish()));
		PayloadTypeRegistry.clientboundPlay().register(BeamFx.TYPE, BeamFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpiritFx.TYPE, SpiritFx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Guard.TYPE, Guard.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Guard.TYPE, (payload, ctx) -> {
			if (Powers.has(ctx.player(), ZFighterPower.INSTANCE)) {
				ZFighterGuard.set(ctx.player(), payload.up());
			}
		});
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
			case PICK_LINEAGE -> ZFighterLineage.pick(player, train.index());
			default -> {
			}
		}
	}
}

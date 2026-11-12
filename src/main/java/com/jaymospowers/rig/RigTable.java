package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The wrist table, server side: a table on the forearm (a screen opened from the Rig menu) with a small map of the
 * land the client has loaded, the enemy kinds the wearer has studied ({@link RigStudy}) and the orders for the drones
 * ({@link RigDrones}), the decoy ({@link RigDecoy}) and the Bank ({@link RigBank}). The screen is the client's; this is what
 * it is told and what it may ask.
 */
public final class RigTable {
	/** {@link Order} codes. */
	public static final int DEPLOY = 0, STOW = 1, SHELL = 2, MISSILE = 3, FORMATION = 4, FETCH = 5, MEND = 6, PATROL = 7, CLEAR_ROUTE = 8,
		DECOY_KIND = 9, BANK = 10, REFRESH = 11, DECOY = 12, FIELD = 13;
	/** The most points a list sent holds. */
	private static final int MAX_POINTS = 32;

	/** What the table shows: the drones, the settings and the studied kinds. */
	public record Data(boolean out, boolean shell, int formation, int flags, int count, boolean trio, boolean bank, int fieldBlows,
		List<Vec3> route, List<Vec3> drones, List<String> ids, List<Integer> kills, List<Boolean> lens) {
		public static final Data NONE = new Data(false, false, 0, 0, 0, false, false, 0, List.of(), List.of(), List.of(), List.of(), List.of());
		/** {@link #flags} bits. */
		public static final int FETCHING = 1, MENDING = 2, PATROLLING = 4;
		private static final StreamCodec<ByteBuf, List<Vec3>> POINTS = Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_POINTS));
		public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.of((buf, d) -> {
			buf.writeByte((d.out ? 1 : 0) | (d.shell ? 2 : 0) | (d.trio ? 4 : 0) | (d.bank ? 8 : 0));
			ByteBufCodecs.VAR_INT.encode(buf, d.formation);
			ByteBufCodecs.VAR_INT.encode(buf, d.flags);
			ByteBufCodecs.VAR_INT.encode(buf, d.count);
			ByteBufCodecs.VAR_INT.encode(buf, d.fieldBlows);
			POINTS.encode(buf, d.route);
			POINTS.encode(buf, d.drones);
			ByteBufCodecs.VAR_INT.encode(buf, d.ids.size());
			for (int i = 0; i < d.ids.size(); i++) {
				ByteBufCodecs.STRING_UTF8.encode(buf, d.ids.get(i));
				ByteBufCodecs.VAR_INT.encode(buf, d.kills.get(i));
				buf.writeBoolean(d.lens.get(i));
			}
		}, buf -> {
			int bits = buf.readByte();
			int formation = ByteBufCodecs.VAR_INT.decode(buf);
			int flags = ByteBufCodecs.VAR_INT.decode(buf);
			int count = ByteBufCodecs.VAR_INT.decode(buf);
			int field = ByteBufCodecs.VAR_INT.decode(buf);
			List<Vec3> route = POINTS.decode(buf);
			List<Vec3> drones = POINTS.decode(buf);
			int n = Math.min(ByteBufCodecs.VAR_INT.decode(buf), RigStudy.LIST_MAX);
			List<String> ids = new ArrayList<>();
			List<Integer> kills = new ArrayList<>();
			List<Boolean> lens = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				ids.add(ByteBufCodecs.STRING_UTF8.decode(buf));
				kills.add(ByteBufCodecs.VAR_INT.decode(buf));
				lens.add(buf.readBoolean());
			}
			return new Data((bits & 1) != 0, (bits & 2) != 0, formation, flags, count, (bits & 4) != 0, (bits & 8) != 0, field, route, drones, ids, kills, lens);
		});
	}

	/** Server → client: the table's contents; {@code open}: open the screen (else only refresh one that is open). */
	public record Show(boolean open, Data data) implements CustomPacketPayload {
		public static final Type<Show> TYPE = new Type<>(JaymosPowers.id("rig_table_show"));
		public static final StreamCodec<ByteBuf, Show> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Show::open, Data.CODEC, Show::data, Show::new);

		@Override
		public Type<Show> type() {
			return TYPE;
		}
	}

	/** Client → server: an order from the table. */
	public record Order(int code, int arg) implements CustomPacketPayload {
		public static final Type<Order> TYPE = new Type<>(JaymosPowers.id("rig_table_order"));
		public static final StreamCodec<ByteBuf, Order> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Order::code, ByteBufCodecs.VAR_INT, Order::arg, Order::new);

		@Override
		public Type<Order> type() {
			return TYPE;
		}
	}

	private RigTable() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Show.TYPE, Show.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Order.TYPE, Order.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Order.TYPE, (payload, context) -> order(context.player(), payload.code(), payload.arg()));
	}

	private static void order(ServerPlayer player, int code, int arg) {
		if (player.isSpectator() || !Rig.wearsAny(player)) {
			return;
		}
		switch (code) {
			case DEPLOY, STOW, SHELL, MISSILE, FORMATION, FETCH, MEND, PATROL, CLEAR_ROUTE -> RigDrones.order(player, code, arg);
			case DECOY_KIND -> RigDecoy.toggleKind(player);
			case BANK -> RigBank.toggle(player);
			case DECOY -> RigDecoy.use(player);
			case FIELD -> RigField.use(player);
			default -> {
			}
		}
		show(player, false);
	}

	/** Sends the table's contents to {@code player}; {@code open} opens the screen. */
	static void show(ServerPlayer player, boolean open) {
		boolean out = RigDrones.out(player);
		List<Vec3> drones = RigDrones.positions(player);
		List<String> ids = new ArrayList<>();
		List<Integer> kills = new ArrayList<>();
		List<Boolean> lens = new ArrayList<>();
		if (open) {
			for (RigStudy.Entry entry : RigStudy.studied(player)) {
				ids.add(entry.id());
				kills.add(entry.kills());
				lens.add(entry.lens());
			}
		}
		Data data = new Data(out, com.jaymospowers.power.xenoform.forms.HiveSwarm.shelled(RigDrones.KIT, player),
			com.jaymospowers.power.xenoform.forms.HiveSwarm.formation(RigDrones.KIT, player), RigDrones.flags(player), RigDrones.count(player),
			RigDecoy.trio(player), RigBank.on(player), RigField.blows(player), RigDrones.route(player), drones.size() > MAX_POINTS ? drones.subList(0, MAX_POINTS) : drones,
			ids, kills, lens);
		ServerPlayNetworking.send(player, new Show(open, data));
	}
}

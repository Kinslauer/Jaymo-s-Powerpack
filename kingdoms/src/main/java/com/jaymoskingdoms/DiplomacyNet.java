package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * (0.7.519) The four payloads of diplomacy ({@link Envoys}, {@link RulersDesk}): an envoy's offer to the sovereign who hears
 * it and the answer back, and the ruler's desk and what is done at it. Nothing is decided on the client: the server
 * checks every answer again.
 */
public final class DiplomacyNet {
	/** The answers to an envoy's offer. */
	public static final int NOT_YET = 0, SIGN = 1, REFUSE = 2;
	/** What is done at the desk. */
	public static final int SEND = 0, RENOUNCE = 1;

	/** Server to client: an envoy offers {@code kind} for {@code days}; their realm's creed and temper, and the relation and tension between the two realms. */
	public record EnvoyOffer(int entityId, int mission, String realm, String envoy, String kind, int days, boolean renew, String creed, String temper,
		String relation, int tension, boolean playerRealm) implements CustomPacketPayload {
		public static final Type<EnvoyOffer> TYPE = new Type<>(JaymosKingdoms.id("envoy_offer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, EnvoyOffer> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entityId);
				buf.writeVarInt(p.mission);
				buf.writeUtf(p.realm);
				buf.writeUtf(p.envoy);
				buf.writeUtf(p.kind);
				buf.writeVarInt(p.days);
				buf.writeBoolean(p.renew);
				buf.writeUtf(p.creed);
				buf.writeUtf(p.temper);
				buf.writeUtf(p.relation);
				buf.writeVarInt(p.tension);
				buf.writeBoolean(p.playerRealm);
			}, buf -> new EnvoyOffer(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(),
				buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean()));

		@Override
		public Type<EnvoyOffer> type() {
			return TYPE;
		}
	}

	/** Client to server: the sovereign's answer ({@link #NOT_YET}, {@link #SIGN}, {@link #REFUSE}) to the offer of {@code mission}. */
	public record EnvoyAnswer(int mission, int choice) implements CustomPacketPayload {
		public static final Type<EnvoyAnswer> TYPE = new Type<>(JaymosKingdoms.id("envoy_answer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, EnvoyAnswer> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.mission);
				buf.writeVarInt(p.choice);
			}, buf -> new EnvoyAnswer(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<EnvoyAnswer> type() {
			return TYPE;
		}
	}

	/**
	 * One realm on the desk: how it stands with this one, its creed, the treaties in force (kind keys and days left) and, for
	 * each of the four kinds, the odds ({@code -1} closed, {@code 0} unlikely, {@code 1} maybe, {@code 2} likely, {@code 3} its sovereign decides).
	 */
	public record Row(int id, String name, String relation, int tension, String creed, boolean playerRealm, boolean atWar, boolean envoyOut,
		List<String> kinds, List<Integer> daysLeft, List<Integer> odds) {
		static void write(RegistryFriendlyByteBuf buf, Row r) {
			buf.writeVarInt(r.id);
			buf.writeUtf(r.name);
			buf.writeUtf(r.relation);
			buf.writeVarInt(r.tension);
			buf.writeUtf(r.creed);
			buf.writeBoolean(r.playerRealm);
			buf.writeBoolean(r.atWar);
			buf.writeBoolean(r.envoyOut);
			buf.writeVarInt(r.kinds.size());
			for (int i = 0; i < r.kinds.size(); i++) {
				buf.writeUtf(r.kinds.get(i));
				buf.writeVarInt(r.daysLeft.get(i));
			}
			buf.writeVarInt(r.odds.size());
			for (int o : r.odds) {
				buf.writeVarInt(o + 1);
			}
		}

		static Row read(RegistryFriendlyByteBuf buf) {
			int id = buf.readVarInt();
			String name = buf.readUtf(), relation = buf.readUtf();
			int tension = buf.readVarInt();
			String creed = buf.readUtf();
			boolean player = buf.readBoolean(), war = buf.readBoolean(), out = buf.readBoolean();
			int n = buf.readVarInt();
			List<String> kinds = new ArrayList<>();
			List<Integer> days = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				kinds.add(buf.readUtf());
				days.add(buf.readVarInt());
			}
			int m = buf.readVarInt();
			List<Integer> odds = new ArrayList<>();
			for (int i = 0; i < m; i++) {
				odds.add(buf.readVarInt() - 1);
			}
			return new Row(id, name, relation, tension, creed, player, war, out, List.copyOf(kinds), List.copyOf(days), List.copyOf(odds));
		}
	}

	/** Server to client: the sovereign's desk: the realm, its treasury and what an envoy costs (already worded in its coin), a status line key, and the realms in reach. */
	public record Desk(String realm, Component treasury, Component cost, String status, List<Row> rows) implements CustomPacketPayload {
		public static final Type<Desk> TYPE = new Type<>(JaymosKingdoms.id("ruler_desk"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Desk> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeUtf(p.realm);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.treasury);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.cost);
				buf.writeUtf(p.status);
				buf.writeVarInt(p.rows.size());
				for (Row r : p.rows) {
					Row.write(buf, r);
				}
			}, buf -> {
				String realm = buf.readUtf();
				Component treasury = ComponentSerialization.STREAM_CODEC.decode(buf), cost = ComponentSerialization.STREAM_CODEC.decode(buf);
				String status = buf.readUtf();
				int n = buf.readVarInt();
				List<Row> rows = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					rows.add(Row.read(buf));
				}
				return new Desk(realm, treasury, cost, status, List.copyOf(rows));
			});

		@Override
		public Type<Desk> type() {
			return TYPE;
		}
	}

	/** Client to server: send an envoy ({@link #SEND}) or renounce ({@link #RENOUNCE}) {@code kind} (an ordinal of {@link Treaties.Kind}) with realm {@code realm}. */
	public record DeskAct(int action, int realm, int kind) implements CustomPacketPayload {
		public static final Type<DeskAct> TYPE = new Type<>(JaymosKingdoms.id("ruler_desk_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DeskAct> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.action);
				buf.writeVarInt(p.realm);
				buf.writeVarInt(p.kind);
			}, buf -> new DeskAct(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<DeskAct> type() {
			return TYPE;
		}
	}

	private DiplomacyNet() {
	}

	/** Payload types first, then the receivers. */
	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(EnvoyOffer.TYPE, EnvoyOffer.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(EnvoyAnswer.TYPE, EnvoyAnswer.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Desk.TYPE, Desk.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DeskAct.TYPE, DeskAct.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EnvoyAnswer.TYPE, (payload, context) -> Envoys.answered(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(DeskAct.TYPE, (payload, context) -> RulersDesk.act(context.player(), payload));
	}
}

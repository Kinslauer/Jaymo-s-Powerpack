package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.7.649) What Jaymo's Powers' character menu needs from the server that the client doesn't already hold (the client
 * half is {@code MenuCompat}, {@code FactionScreen}): the realm you are sworn to, your rank and standing in it, its
 * relations, and the settlements you know, for the Map tab's pins. Asked for when the Faction or Map tab opens.
 */
public final class MenuNet {
	/** Settlements this far off (blocks) go on the map; further, only those that know your name. The most sent. */
	static final double PLACES_RANGE = 3000.0;
	static final int MAX_PLACES = 48;
	/** The most relations sent. */
	static final int MAX_RELATIONS = 16;

	/** A settlement on the map: its name, colour (RGB) and seat. */
	public record Place(String name, int color, BlockPos seat) {
	}

	/** One stored relation: the other faction's name, colour and the relation's id ({@link Relation#id}). */
	public record Tie(String name, int color, String relation) {
	}

	/**
	 * Server → player. {@code sworn}: whether the rest of the faction fields mean anything. {@code kind}: its
	 * {@link FactionType#key}; {@code rank}: your {@link Rank#key}; {@code next}: the standing the next rank takes (-1 at
	 * the top); {@code overlord}: the realm it answers to ("" none); {@code here}: you stand in the settlements' world.
	 */
	public record Info(boolean sworn, String name, String kind, int color, String rank, int standing, int next, int members, int population,
		long treasury, String overlord, List<Tie> ties, List<Place> places, boolean here) implements CustomPacketPayload {
		public static final Type<Info> TYPE = new Type<>(JaymosKingdoms.id("menu_info"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Info> CODEC = CustomPacketPayload.codec(Info::write, Info::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeBoolean(sworn);
			buf.writeUtf(name);
			buf.writeUtf(kind);
			buf.writeInt(color);
			buf.writeUtf(rank);
			buf.writeVarInt(standing);
			buf.writeVarInt(next);
			buf.writeVarInt(members);
			buf.writeVarInt(population);
			buf.writeVarLong(treasury);
			buf.writeUtf(overlord);
			buf.writeVarInt(ties.size());
			for (Tie t : ties) {
				buf.writeUtf(t.name);
				buf.writeInt(t.color);
				buf.writeUtf(t.relation);
			}
			buf.writeVarInt(places.size());
			for (Place p : places) {
				buf.writeUtf(p.name);
				buf.writeInt(p.color);
				buf.writeBlockPos(p.seat);
			}
			buf.writeBoolean(here);
		}

		private static Info read(RegistryFriendlyByteBuf buf) {
			boolean sworn = buf.readBoolean();
			String name = buf.readUtf(), kind = buf.readUtf();
			int color = buf.readInt();
			String rank = buf.readUtf();
			int standing = buf.readVarInt(), next = buf.readVarInt(), members = buf.readVarInt(), population = buf.readVarInt();
			long treasury = buf.readVarLong();
			String overlord = buf.readUtf();
			int n = buf.readVarInt();
			List<Tie> ties = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				ties.add(new Tie(buf.readUtf(), buf.readInt(), buf.readUtf()));
			}
			n = buf.readVarInt();
			List<Place> places = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				places.add(new Place(buf.readUtf(), buf.readInt(), buf.readBlockPos()));
			}
			return new Info(sworn, name, kind, color, rank, standing, next, members, population, treasury, overlord, ties, places, buf.readBoolean());
		}

		@Override
		public Type<Info> type() {
			return TYPE;
		}
	}

	/** Player → server: the Faction or Map tab opened. */
	public record Ask() implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosKingdoms.id("menu_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = StreamCodec.unit(new Ask());

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	private MenuNet() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Info.TYPE, Info.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Ask.TYPE, Ask.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (payload, context) -> send(context.player()));
	}

	private static void send(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, Info.TYPE)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		boolean here = player.level() == Factions.level(server);
		List<Place> places = new ArrayList<>();
		if (here) {
			Map<String, Integer> known = player.getAttachedOrElse(Reputation.REPUTATION, Map.of());
			for (Faction f : Factions.all(server).stream()
				.filter(f -> !f.fallen() && (known.containsKey(Integer.toString(f.id())) || f.seat().distToCenterSqr(player.position()) < PLACES_RANGE * PLACES_RANGE))
				.sorted(Comparator.comparingDouble(f -> f.seat().distToCenterSqr(player.position())))
				.limit(MAX_PLACES).toList()) {
				places.add(new Place(f.name(), f.styleColor(server).getTextureDiffuseColor() & 0xFFFFFF, f.seat()));
			}
		}
		Faction f = Factions.of(server, player.getUUID());
		if (f == null) {
			ServerPlayNetworking.send(player, new Info(false, "", "", 0, "", 0, -1, 0, 0, 0L, "", List.of(), places, here));
			return;
		}
		Faction.Member me = f.members().get(player.getUUID());
		int standing = me == null ? 0 : me.standing();
		// earned by standing, or set higher (a crowned lord)
		Rank rank = Rank.forStanding(standing);
		if (me != null && me.rank() > rank.ordinal() && me.rank() < Rank.values().length) {
			rank = Rank.values()[me.rank()];
		}
		int next = rank.ordinal() + 1 < Rank.values().length ? Rank.values()[rank.ordinal() + 1].standing : -1;
		Faction over = f.overlord() >= 0 ? Factions.get(server, f.overlord()) : null;
		List<Tie> ties = new ArrayList<>();
		for (Map.Entry<String, Relation> e : f.relations().entrySet()) {
			if (ties.size() >= MAX_RELATIONS) {
				break;
			}
			int other;
			try {
				other = Integer.parseInt(e.getKey());
			} catch (NumberFormatException ex) {
				continue;
			}
			Faction o = Factions.get(server, other);
			if (o == null || o.fallen()) {
				continue;
			}
			ties.add(new Tie(o.name(), o.styleColor(server).getTextureDiffuseColor() & 0xFFFFFF, Relations.between(f, o).id()));
		}
		ServerPlayNetworking.send(player, new Info(true, f.name(), f.type().key(), f.styleColor(server).getTextureDiffuseColor() & 0xFFFFFF, rank.key(),
			standing, next, f.members().size(), f.holdings().population(), f.treasury(), over == null ? "" : over.name(), ties, places, here));
	}
}

package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who holds what, for the map (medieval pick gb-castles: "the map showing who holds what"): every standing realm's
 * name, its holder (itself, or the overlord a fiefdom's seat went to), the holder's colour, its seat and its land,
 * sent to each player as they join and whenever it changes. The client hands it to Xaero's Minimap when that's loaded
 * ({@code XaeroRealms}: each seat a waypoint in its holder's colour, the borders drawn on the map); without it nothing
 * is drawn. Villages and camps aren't on it.
 */
public final class RealmMap {
	/** How often the realms are looked at for a change (ticks). */
	static final int CHECK_TICKS = 600;

	/** One realm on the map: its id, name, holder's name and colour (a dye id), seat (packed) and land (packed chunks). */
	public record Realm(int id, String name, String holder, int dye, long seat, List<Long> claims) {
	}

	/** Server → client: the realms. */
	public record Realms(List<Realm> realms) implements CustomPacketPayload {
		public static final Type<Realms> TYPE = new Type<>(JaymosKingdoms.id("realm_map"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Realms> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeVarInt(p.realms.size());
			for (Realm r : p.realms) {
				buf.writeVarInt(r.id);
				buf.writeUtf(r.name);
				buf.writeUtf(r.holder);
				buf.writeVarInt(r.dye);
				buf.writeLong(r.seat);
				buf.writeVarInt(r.claims.size());
				for (long c : r.claims) {
					buf.writeLong(c);
				}
			}
		}, buf -> {
			int n = buf.readVarInt();
			List<Realm> realms = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				int id = buf.readVarInt();
				String name = buf.readUtf(), holder = buf.readUtf();
				int dye = buf.readVarInt();
				long seat = buf.readLong();
				int m = buf.readVarInt();
				List<Long> claims = new ArrayList<>(m);
				for (int j = 0; j < m; j++) {
					claims.add(buf.readLong());
				}
				realms.add(new Realm(id, name, holder, dye, seat, List.copyOf(claims)));
			}
			return new Realms(List.copyOf(realms));
		});

		@Override
		public Type<Realms> type() {
			return TYPE;
		}
	}

	private static int sent;

	private RealmMap() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Realms.TYPE, Realms.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, now(server)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS != 0) {
				return;
			}
			Realms realms = now(server);
			if (realms.hashCode() != sent) {
				sent = realms.hashCode();
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					send(player, realms);
				}
			}
		});
	}

	/** The realms as they stand. */
	static Realms now(MinecraftServer server) {
		List<Realm> out = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen()) {
				continue;
			}
			Faction holder = f.type() == FactionType.FIEFDOM ? Relations.overlord(server, f) : null;
			out.add(new Realm(f.id(), f.name(), holder == null ? f.name() : holder.name(), KeepColours.holder(server, f).getId(), f.seat().asLong(),
				List.copyOf(f.claims())));
		}
		return new Realms(List.copyOf(out));
	}

	private static void send(ServerPlayer player, Realms realms) {
		if (ServerPlayNetworking.canSend(player, Realms.TYPE)) {
			ServerPlayNetworking.send(player, realms);
		}
	}
}

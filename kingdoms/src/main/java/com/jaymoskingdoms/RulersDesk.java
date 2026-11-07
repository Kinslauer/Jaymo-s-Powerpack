package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jaymoskingdoms.Treaties.Kind;
import com.jaymoskingdoms.money.Money;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * (0.7.519) The sovereign's desk: seek an audience with your own steward and a screen lists the realms in reach (how they
 * stand with yours, their creed, the tension, the treaties in force and each kind's odds) from which you send an envoy or
 * renounce a treaty. Server side: it builds what the screen shows and checks every act again.
 */
final class RulersDesk {
	/** An act is accepted this long (ticks) after the desk was opened, and only this near (blocks) where it was. */
	static final long DESK_TICKS = 6000L;
	static final double DESK_RANGE = 16.0;
	/** The most realms listed (nearest first). */
	static final int ROWS_MOST = 24;

	/** Where and when a desk was opened, and for which realm. */
	private record Seat(int faction, ResourceKey<Level> dimension, BlockPos pos, long at) {
	}

	private static final Map<UUID, Seat> OPEN = new HashMap<>();

	private RulersDesk() {
	}

	/** Opens {@code f}'s desk for its sovereign {@code p}. False if their game can't show it (the old audience stays). */
	static boolean open(ServerPlayer p, Faction f) {
		if (!ServerPlayNetworking.canSend(p, DiplomacyNet.Desk.TYPE)) {
			return false;
		}
		OPEN.put(p.getUUID(), new Seat(f.id(), p.level().dimension(), p.blockPosition(), p.level().getGameTime()));
		send(p, f, "");
		return true;
	}

	private static void send(ServerPlayer p, Faction f, String status) {
		MinecraftServer server = p.level().getServer();
		Faction now = Factions.get(server, f.id());
		if (now == null) {
			return;
		}
		List<DiplomacyNet.Row> rows = new ArrayList<>();
		if (now.overlord() >= 0 || now.type() != FactionType.KINGDOM) {
			status = "gui.jaymoskingdoms.desk.vassal";
		} else {
			List<Faction> near = new ArrayList<>();
			for (Faction t : Wars.heads(server)) {
				if (t.id() != now.id() && Raids.horizontal(now.seat(), t.seat()) <= Envoys.RANGE * Envoys.RANGE) {
					near.add(t);
				}
			}
			near.sort(Comparator.comparingDouble(t -> Raids.horizontal(now.seat(), t.seat())));
			for (Faction t : near.subList(0, Math.min(ROWS_MOST, near.size()))) {
				rows.add(row(server, now, t));
			}
		}
		ServerPlayNetworking.send(p, new DiplomacyNet.Desk(now.name(), Money.text(now, Money.worth(now.treasury())), Money.text(now, Money.worth(Envoys.ENVOY_COST)), status, List.copyOf(rows)));
	}

	private static DiplomacyNet.Row row(MinecraftServer server, Faction f, Faction t) {
		List<String> kinds = new ArrayList<>();
		List<Integer> days = new ArrayList<>();
		List<Integer> odds = new ArrayList<>();
		for (Kind k : Kind.values()) {
			Treaties.Treaty held = Treaties.find(server, f, t, k);
			if (held != null && Treaties.has(server, f, t, k)) {
				kinds.add(k.key());
				days.add(Treaties.daysLeft(server, held));
			}
			odds.add(Envoys.odds(server, t, f, k));
		}
		return new DiplomacyNet.Row(t.id(), t.name(), f.relation(t.id()).id(), Math.round(GreatWars.tension(server, f.id(), t.id())),
			GreatWars.creed(server, t).key(), Crowns.sovereign(server, t) != null, Wars.between(server, f.id(), t.id()) != null,
			Envoys.between(server, f.id(), t.id()) != null, List.copyOf(kinds), List.copyOf(days), List.copyOf(odds));
	}

	/** {@code p} did something at the desk. */
	static void act(ServerPlayer p, DiplomacyNet.DeskAct act) {
		MinecraftServer server = p.level().getServer();
		Seat seat = OPEN.get(p.getUUID());
		if (seat == null || p.level().getGameTime() - seat.at() > DESK_TICKS || seat.dimension() != p.level().dimension()
			|| p.blockPosition().distSqr(seat.pos()) > DESK_RANGE * DESK_RANGE) {
			OPEN.remove(p.getUUID());
			return;
		}
		Faction f = Factions.get(server, seat.faction());
		Faction t = Factions.get(server, act.realm());
		if (f == null || t == null || f.overlord() >= 0 || !Crowns.rules(server, f, p.getUUID()) || act.kind() < 0 || act.kind() >= Kind.values().length) {
			return;
		}
		boolean inReach = false;
		for (Faction h : Wars.heads(server)) {
			if (h.id() == t.id() && h.id() != f.id() && Raids.horizontal(f.seat(), h.seat()) <= Envoys.RANGE * Envoys.RANGE) {
				inReach = true;
				break;
			}
		}
		if (!inReach) {
			return;
		}
		Kind kind = Kind.values()[act.kind()];
		String status = "";
		if (act.action() == DiplomacyNet.SEND) {
			status = sendEnvoy(p, server, f, t, kind);
		} else if (act.action() == DiplomacyNet.RENOUNCE) {
			if (Treaties.has(server, f, t, kind)) {
				Treaties.renounced(server, f, t, kind);
				status = "gui.jaymoskingdoms.desk.renounced";
			}
		}
		send(p, f, status);
	}

	/** Sends an envoy to {@code t}; the status line it leaves. */
	private static String sendEnvoy(ServerPlayer p, MinecraftServer server, Faction f, Faction t, Kind kind) {
		if (Envoys.odds(server, t, f, kind) == -1) {
			return "gui.jaymoskingdoms.desk.envoy_closed";
		}
		if (Envoys.between(server, f.id(), t.id()) != null || Envoys.outgoing(server, f.id()) != null) {
			return "gui.jaymoskingdoms.desk.envoy_out";
		}
		if (Envoys.full(server)) {
			return "gui.jaymoskingdoms.desk.envoy_full";
		}
		// (0.7.519) the treasury holds whole silver; only the shortfall is turned to milli for the purse
		long cost = Envoys.ENVOY_COST;
		long fromTreasury = Math.max(0L, Math.min(f.treasury(), cost));
		long short_ = cost - fromTreasury;
		if (short_ > 0L && !Money.charge(p, f, Money.worth(short_))) {
			return "gui.jaymoskingdoms.desk.poor";
		}
		if (fromTreasury > 0L) {
			Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() - fromTreasury));
		}
		Envoys.dispatch(server, f, t, kind, Treaties.find(server, f, t, kind) != null, p.getUUID());
		return "gui.jaymoskingdoms.desk.sent";
	}
}

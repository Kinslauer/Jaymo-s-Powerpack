package com.jaymoskingdoms;

import com.jaymoskingdoms.WarData.War;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.224) Andy (2026-10-26): "I like the idea of larger scale wars between wealthy kingdoms, differing ideologies cause
 * conflict making war inevitable. And the more money the more war." Ideas taken from Warring Nations (All Rights Reserved:
 * war goals that shape the peace, coalitions, rebellions, war betting), remade here; nothing copied.
 * <ul>
 * <li><b>Creeds.</b> Every realm holds to a creed: the Crown (the king's word is law), Chivalry, Conquest, the Faith,
 * Coin (trade above all) or Blood (the old blood). A ruled realm's is drawn mostly from its ruler's temper and kept by
 * the realm (an heir keeps his forebears' creed); a vassal holds its overlord's. Players' realms get one at random and
 * their lords may change it ({@code /kingdom war creed}).</li>
 * <li><b>Tension.</b> Each day, every pair of free kingdoms within {@link #TENSION_RANGE} of each other grows tenser by
 * how far their creeds clash ({@link #friction}), the more so the richer the two are put together; near borders, an old
 * rivalry and two rich trading realms add to it, shared creeds and alliances ease it, and a truce cools it. At
 * {@link #RIVAL_AT} they become rivals; at {@link #WAR_AT} war is inevitable: the richer, readier of the two declares
 * whatever the armies say.</li>
 * <li><b>The more money, the more war.</b> A king thinks of war more often the richer he is, and counts the
 * sellswords his treasury could buy as part of his army when he weighs a weaker rival ({@link Wars#considerWar}).</li>
 * <li><b>Great wars.</b> A war between two wealthy realms (each worth {@link #GREAT_WORTH}, or both
 * {@link #GREAT_TOGETHER}) is a great war: free kingdoms of a kindred creed (or allied, or tense with the other side)
 * pledge to a side and fight in it, their vassals with them ({@link #pledgedTo}); each principal empties a share of its
 * treasury into a war chest of sellswords ({@link #MERC_COST} a head), who count in its army and march in its parties
 * (larger, and sent twice as often); it lasts twice as long and needs a bigger score to win. Every day each side pays
 * its upkeep; a principal that runs out of money loses its sellswords and sues for peace.</li>
 * <li><b>War goals.</b> An attacker who wins makes the peace its creed wants ({@link Goal}): the Crown subjugates
 * sooner, Conquest takes twice the land, Coin takes more tribute from a smaller lead, Blood reclaims land, Chivalry
 * never takes land but keeps a long truce, and the Faith converts the loser. A vassal always takes its overlord's creed.
 * Winning allies share the loser's purse, losing allies pay reparations.</li>
 * <li><b>Rebellions.</b> In a great war, a fiefdom of another creed whose overlord is losing may rise, crown its lord
 * king and go over to the other side.</li>
 * <li><b>Wagers.</b> While a war is mustering or young, players may wager emeralds on a side
 * ({@code /kingdom war wager}): double back if it wins, the stake back on a white peace.</li>
 * </ul>
 */
final class GreatWars {
	enum Creed {
		CROWN, CHIVALRY, CONQUEST, FAITH, COIN, BLOOD;

		String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		Component named() {
			return Component.translatable("creed.jaymoskingdoms." + this.key());
		}

		static @Nullable Creed parse(String s) {
			for (Creed c : values()) {
				if (c.key().equalsIgnoreCase(s)) {
					return c;
				}
			}
			return null;
		}
	}

	/**
	 * What an attacker's creed wants from a won war: the leads from which it takes tribute, land and vassalage, the share of
	 * the loser's treasury it takes as tribute, how many chunks it takes, how many times the usual truce, and whether the
	 * loser takes its creed (on any terms past a white peace).
	 */
	record Goal(int tribute, int cede, int vassal, double share, int chunks, int truce, boolean converts) {
	}

	/** Goals by creed (CROWN, CHIVALRY, CONQUEST, FAITH, COIN, BLOOD); a defender who wins keeps to the usual terms. */
	static final Goal[] GOALS = {
		new Goal(15, 45, 60, 0.3, 4, 1, false),
		new Goal(10, Integer.MAX_VALUE, 90, 0.3, 0, 2, false),
		new Goal(15, 25, 90, 0.3, 8, 1, false),
		new Goal(15, 45, 90, 0.3, 4, 1, true),
		new Goal(5, 45, 90, 0.5, 4, 1, false),
		new Goal(15, 30, 90, 0.3, 6, 1, false)};
	static final Goal USUAL = new Goal(Wars.TRIBUTE_LEAD, Wars.CEDE_LEAD, Wars.VASSAL_LEAD, Wars.TRIBUTE_SHARE, Wars.CEDE_CHUNKS, 1, false);
	/** How far creeds clash, 0 (kindred) to 3 (irreconcilable), in {@link Creed} order; Blood against Blood is by whose blood. */
	static final int[][] FRICTION = {
		// CROWN CHIV CONQ FAITH COIN BLOOD
		{0, 1, 2, 2, 2, 1},
		{1, 0, 3, 1, 2, 2},
		{2, 3, 2, 2, 3, 2},
		{2, 1, 2, 0, 3, 2},
		{2, 2, 3, 3, 0, 2},
		{1, 2, 2, 2, 2, 0}};
	/** A ruled realm takes its ruler's temper's creed this often (percent), else any. Temper order: TYRANT, NOBLE, WARLORD, PARANOID, REVELER, PURIST. */
	static final int NATURAL_PERCENT = 65;
	static final Creed[] NATURAL = {Creed.CROWN, Creed.CHIVALRY, Creed.CONQUEST, Creed.FAITH, Creed.COIN, Creed.BLOOD};

	/** Tension: between free kingdoms this near (blocks), from 0 to {@link #TENSION_MAX}. */
	static final double TENSION_RANGE = 1600.0;
	static final float TENSION_MAX = 100.0F;
	/** A day's tension per point of friction, times (1 + their worth together / this, at most {@link #WEALTH_MOST}). */
	static final float FRICTION_GAIN = 2.5F;
	static final float WEALTH_STEP = 500.0F, WEALTH_MOST = 3.0F;
	/** More a day: seats this near (blocks), already rivals, two rich trading realms (both this worth, one of Coin). */
	static final double BORDER_RANGE = 450.0;
	static final float BORDER_GAIN = 2.0F, RIVAL_GAIN = 1.0F, TRADE_GAIN = 2.0F;
	static final long TRADE_WORTH = 250L;
	/** Less a day: kindred creeds, allies, a truce. */
	static final float KINDRED_EASE = 3.0F, ALLIED_EASE = 5.0F, TRUCE_EASE = 2.0F;
	/** Rivals from this tension; war from this; after a war is declared (or a peace made) it drops to this. */
	static final float RIVAL_AT = 35.0F, WAR_AT = 80.0F, AFTER_WAR = 30.0F;
	/** A reformed realm's edicts of tolerance ease Blood's friction by one. */
	static final boolean TOLERANCE_EASES_BLOOD = true;

	/** A great war: each principal worth this much, or both together this much. */
	static final long GREAT_WORTH = 260L, GREAT_TOGETHER = 750L;
	/** Coalitions: free kingdoms this near a side's principal pledge to it, at most this many a side. */
	static final double COALITION_RANGE = 2000.0;
	static final int COALITION_MOST = 3;
	/** Sellswords: each costs this much; a principal spends up to this share of its treasury, at most this many. */
	static final int MERC_COST = 10, MERC_MOST = 16;
	static final double MERC_SHARE = 0.3;
	/** A party in a great war: at most this many soldiers, and this many of the war chest's sellswords join it. */
	static final int PARTY_MAX = 16, MERCS_PER_PARTY = 4;
	/** A great war: its score to win and length, in multiples of the usual; and more unseen blows. */
	static final double SCORE_TIMES = 1.5;
	static final int LENGTH_TIMES = 2, BLOWS_TIMES = 2;
	/** A day's upkeep: a principal pays this much plus this share of its treasury plus this per sellsword; an ally pays this share. */
	static final int UPKEEP_BASE = 4, UPKEEP_PER_MERC = 1;
	static final double UPKEEP_SHARE = 0.03, ALLY_UPKEEP_SHARE = 0.01;
	/** Below this treasury a principal is broke: its sellswords leave and it sues for peace. */
	static final long BROKE = 15L;
	/** At peace: winning allies each get this share of the loser's treasury; losing allies each pay this share of theirs to the winner. */
	static final double ALLY_SPOILS = 0.1, ALLY_REPARATIONS = 0.1;
	/** Rebellion: a fiefdom of a creed this far from its overlord's, the overlord behind by this much, this chance a day. */
	static final int REBEL_FRICTION = 2, REBEL_BEHIND = 20;
	static final float REBEL_CHANCE = 0.15F;
	/** A rebel and its old overlord start from this tension. */
	static final float REBEL_TENSION = 60.0F;
	/** Wagers: open until the war is this old (ticks), at most this many emeralds a player a war; a win pays this many times. */
	static final long WAGER_OPEN_TICKS = 24000L;
	static final int WAGER_MOST = 128, WAGER_PAYS = 2;
	/** How often the daily round is looked at (ticks), and how often owed winnings are paid out (ticks). */
	static final int CHECK_TICKS = 1200, PAY_TICKS = 100;

	/** A realm pledged to a principal's side in a war. */
	record Pledge(int war, int principal) {
		static final Codec<Pledge> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("war").forGetter(Pledge::war),
			Codec.INT.fieldOf("principal").forGetter(Pledge::principal)).apply(i, Pledge::new));
	}

	/** A player's wager: who, on which war and side, how much. */
	record Wager(UUID player, int war, int side, int emeralds) {
		static final Codec<Wager> CODEC = RecordCodecBuilder.create(i -> i.group(
			net.minecraft.core.UUIDUtil.CODEC.fieldOf("player").forGetter(Wager::player),
			Codec.INT.fieldOf("war").forGetter(Wager::war),
			Codec.INT.fieldOf("side").forGetter(Wager::side),
			Codec.INT.fieldOf("emeralds").forGetter(Wager::emeralds)).apply(i, Wager::new));
	}

	/**
	 * Everything saved: creeds by realm id, tension by pair ("a|b"), pledges by realm id, war chests of sellswords by
	 * "war|principal", the great wars (by id), wagers, winnings owed by player, and the last day looked at.
	 */
	record Ledger(Map<String, String> creeds, Map<String, Float> tension, Map<String, Pledge> pledges, Map<String, Integer> mercs,
		List<Integer> great, List<Wager> wagers, Map<String, Integer> owed, long lastDay) {
		static final Ledger EMPTY = new Ledger(Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of(), Map.of(), -1L);
		static final Codec<Ledger> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("creeds", Map.of()).forGetter(Ledger::creeds),
			Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("tension", Map.of()).forGetter(Ledger::tension),
			Codec.unboundedMap(Codec.STRING, Pledge.CODEC).optionalFieldOf("pledges", Map.of()).forGetter(Ledger::pledges),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("mercs", Map.of()).forGetter(Ledger::mercs),
			Codec.INT.listOf().optionalFieldOf("great", List.of()).forGetter(Ledger::great),
			Wager.CODEC.listOf().optionalFieldOf("wagers", List.of()).forGetter(Ledger::wagers),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("owed", Map.of()).forGetter(Ledger::owed),
			Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(Ledger::lastDay)).apply(i, Ledger::new));
	}

	/** On the Overworld. */
	static final AttachmentType<Ledger> LEDGER = AttachmentRegistry.create(JaymosKingdoms.id("great_wars"), builder -> builder.persistent(Ledger.CODEC));

	private GreatWars() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GreatWars::tick);
	}

	// ---- the ledger ---------------------------------------------------------------------------------------------------

	static Ledger ledger(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(LEDGER, Ledger.EMPTY);
	}

	private static void edit(MinecraftServer server, UnaryOperator<Ledger> change) {
		Factions.level(server).setAttached(LEDGER, change.apply(ledger(server)));
	}

	private static <V> Map<String, V> with(Map<String, V> map, String key, @Nullable V value) {
		Map<String, V> next = new HashMap<>(map);
		if (value == null) {
			next.remove(key);
		} else {
			next.put(key, value);
		}
		return Map.copyOf(next);
	}

	private static Ledger withCreeds(Ledger l, Map<String, String> m) {
		return new Ledger(m, l.tension(), l.pledges(), l.mercs(), l.great(), l.wagers(), l.owed(), l.lastDay());
	}

	private static Ledger withTension(Ledger l, Map<String, Float> m) {
		return new Ledger(l.creeds(), m, l.pledges(), l.mercs(), l.great(), l.wagers(), l.owed(), l.lastDay());
	}

	private static Ledger withPledges(Ledger l, Map<String, Pledge> m) {
		return new Ledger(l.creeds(), l.tension(), m, l.mercs(), l.great(), l.wagers(), l.owed(), l.lastDay());
	}

	private static Ledger withMercs(Ledger l, Map<String, Integer> m) {
		return new Ledger(l.creeds(), l.tension(), l.pledges(), m, l.great(), l.wagers(), l.owed(), l.lastDay());
	}

	private static Ledger withGreat(Ledger l, List<Integer> great) {
		return new Ledger(l.creeds(), l.tension(), l.pledges(), l.mercs(), List.copyOf(great), l.wagers(), l.owed(), l.lastDay());
	}

	private static Ledger withWagers(Ledger l, List<Wager> wagers, Map<String, Integer> owed) {
		return new Ledger(l.creeds(), l.tension(), l.pledges(), l.mercs(), l.great(), List.copyOf(wagers), Map.copyOf(owed), l.lastDay());
	}

	private static Ledger withLastDay(Ledger l, long day) {
		return new Ledger(l.creeds(), l.tension(), l.pledges(), l.mercs(), l.great(), l.wagers(), l.owed(), day);
	}

	// ---- creeds -------------------------------------------------------------------------------------------------------

	/** {@code f}'s creed: a vassal holds its overlord's; a realm without one is given one now. */
	static Creed creed(MinecraftServer server, Faction f) {
		Faction top = Wars.top(server, f);
		String stored = ledger(server).creeds().get(Integer.toString(top.id()));
		Creed c = stored == null ? null : Creed.parse(stored);
		if (c != null) {
			return c;
		}
		RandomSource random = Factions.level(server).getRandom();
		Tempers.Temper t = Tempers.of(top);
		Creed chosen = t != null && random.nextInt(100) < NATURAL_PERCENT ? NATURAL[t.ordinal()] : Creed.values()[random.nextInt(Creed.values().length)];
		setCreed(server, top.id(), chosen);
		return chosen;
	}

	static void setCreed(MinecraftServer server, int faction, Creed creed) {
		edit(server, l -> withCreeds(l, with(l.creeds(), Integer.toString(faction), creed.key())));
	}

	/** How far {@code a}'s and {@code b}'s creeds clash (0 to 3). */
	static int friction(MinecraftServer server, Faction a, Faction b) {
		Creed ca = creed(server, a), cb = creed(server, b);
		int f;
		if (ca == Creed.BLOOD && cb == Creed.BLOOD) {
			Heritage.Kind ba = Purists.blood(Wars.top(server, a)), bb = Purists.blood(Wars.top(server, b));
			f = ba != null && bb != null && ba != bb ? 3 : 0;
		} else {
			f = FRICTION[ca.ordinal()][cb.ordinal()];
		}
		if (TOLERANCE_EASES_BLOOD && f > 0 && (ca == Creed.BLOOD && Reforms.tolerance(server, a) > 0 || cb == Creed.BLOOD && Reforms.tolerance(server, b) > 0)) {
			f--;
		}
		return f;
	}

	/** The peace {@code winner} makes in {@code war}: its creed's, if it declared the war; else the usual. */
	static Goal goal(MinecraftServer server, War war, int winner) {
		if (war.attacker().principal() != winner) {
			return USUAL;
		}
		Faction w = Factions.get(server, winner);
		return w == null ? USUAL : GOALS[creed(server, w).ordinal()];
	}

	// ---- tension ------------------------------------------------------------------------------------------------------

	static float tension(MinecraftServer server, int a, int b) {
		return ledger(server).tension().getOrDefault(WarData.pair(a, b), 0.0F);
	}

	static void setTension(MinecraftServer server, int a, int b, float value) {
		float v = Math.max(0.0F, Math.min(TENSION_MAX, value));
		edit(server, l -> withTension(l, with(l.tension(), WarData.pair(a, b), v <= 0.0F ? null : v)));
	}

	/** {@code king}'s appetite for war: 1, and more the richer he is (from {@link Wars#considerWar}). */
	static float appetite(MinecraftServer server, Faction king) {
		return 1.0F + Math.min(WEALTH_MOST, Wealth.worth(server, king) / WEALTH_STEP);
	}

	/** The sellswords {@code king}'s treasury could hire (counted in his army when he weighs a war). */
	static int hireable(Faction king) {
		return (int)Math.min(MERC_MOST, Math.round(king.treasury() * MERC_SHARE) / MERC_COST);
	}

	private static void daily(MinecraftServer server, ServerLevel level) {
		long day = Production.day(server);
		if (ledger(server).lastDay() == day) {
			return;
		}
		edit(server, l -> withLastDay(l, day));
		long now = level.getGameTime();
		forgetEnded(server);
		upkeep(server, level);
		rebellions(server, level);
		List<Faction> heads = Wars.heads(server);
		for (int i = 0; i < heads.size(); i++) {
			for (int j = i + 1; j < heads.size(); j++) {
				Faction a = heads.get(i), b = heads.get(j);
				if (Raids.horizontal(a.seat(), b.seat()) > TENSION_RANGE * TENSION_RANGE) {
					continue;
				}
				stir(server, level, a, b, now);
			}
		}
	}

	/** A day's tension between {@code a} and {@code b}, and what it comes to. */
	private static void stir(MinecraftServer server, ServerLevel level, Faction a, Faction b, long now) {
		int friction = friction(server, a, b);
		long worthA = Wealth.worth(server, a), worthB = Wealth.worth(server, b);
		float wealth = 1.0F + Math.min(WEALTH_MOST, (worthA + worthB) / WEALTH_STEP);
		float delta = friction * FRICTION_GAIN * wealth;
		Relation relation = a.relation(b.id());
		if (Raids.horizontal(a.seat(), b.seat()) <= BORDER_RANGE * BORDER_RANGE) {
			delta += BORDER_GAIN;
		}
		if (relation == Relation.RIVAL) {
			delta += RIVAL_GAIN;
		}
		Creed ca = creed(server, a), cb = creed(server, b);
		if (worthA >= TRADE_WORTH && worthB >= TRADE_WORTH && (ca == Creed.COIN || cb == Creed.COIN)) {
			delta += TRADE_GAIN;
		}
		if (friction == 0) {
			delta -= KINDRED_EASE;
		}
		if (relation == Relation.ALLIED) {
			delta = -ALLIED_EASE;
		}
		boolean truce = WarData.truce(server, a.id(), b.id(), now);
		if (truce) {
			delta = Math.min(0.0F, delta) - TRUCE_EASE;
		}
		float was = tension(server, a.id(), b.id());
		float t = Math.max(0.0F, Math.min(TENSION_MAX, was + delta));
		setTension(server, a.id(), b.id(), t);
		if (truce || relation == Relation.ALLIED || Wars.between(server, a.id(), b.id()) != null) {
			return;
		}
		if (relation == Relation.NEUTRAL && t >= RIVAL_AT) {
			Relations.set(server, a.id(), b.id(), Relation.RIVAL);
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.creed.rivals", a.name(), ca.named(), b.name(), cb.named())
				.withStyle(ChatFormatting.GOLD));
		}
		if (t >= WAR_AT && Wars.warsOf(server, a.id()).isEmpty() && Wars.warsOf(server, b.id()).isEmpty()) {
			// the richer and readier declares, whatever the armies say (a players' realm declares only by its lords' word)
			long readyA = worthA + (long)Wars.army(server, a.id()) * MERC_COST, readyB = worthB + (long)Wars.army(server, b.id()) * MERC_COST;
			boolean npcA = Tempers.of(a) != null, npcB = Tempers.of(b) != null;
			if (!npcA && !npcB) {
				return;
			}
			Faction from = npcA && (!npcB || readyA >= readyB) ? a : b, on = from == a ? b : a;
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.creed.inevitable", from.name(), creed(server, from).named(),
				on.name(), creed(server, on).named()).withStyle(ChatFormatting.RED));
			if (Wars.declare(server, from, on, false) == null) {
				setTension(server, a.id(), b.id(), AFTER_WAR);
			}
		}
	}

	// ---- great wars ---------------------------------------------------------------------------------------------------

	static boolean great(MinecraftServer server, int war) {
		return ledger(server).great().contains(war);
	}

	static boolean great(MinecraftServer server, War war) {
		return great(server, war.id());
	}

	/** The principal {@code faction} (a realm at the head of its own side) is pledged to, or -1. */
	static int pledgedTo(MinecraftServer server, int faction) {
		Pledge p = ledger(server).pledges().get(Integer.toString(faction));
		return p == null ? -1 : p.principal();
	}

	/** The realms pledged to {@code principal}'s side in {@code war}. */
	static List<Faction> allies(MinecraftServer server, int war, int principal) {
		List<Faction> out = new ArrayList<>();
		for (Map.Entry<String, Pledge> e : ledger(server).pledges().entrySet()) {
			if (e.getValue().war() == war && e.getValue().principal() == principal) {
				Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
				if (f != null && !f.fallen()) {
					out.add(f);
				}
			}
		}
		return out;
	}

	/** {@code principal}'s sellswords in its war chests. */
	static int mercs(MinecraftServer server, int principal) {
		int n = 0;
		for (Map.Entry<String, Integer> e : ledger(server).mercs().entrySet()) {
			if (e.getKey().endsWith("|" + principal)) {
				n += e.getValue();
			}
		}
		return n;
	}

	private static String chest(int war, int principal) {
		return war + "|" + principal;
	}

	/** A party of {@code side} in {@code war} takes up to {@code want} of its sellswords (from {@link WarParties}). */
	static int draftMercs(MinecraftServer server, War war, int side, int want) {
		String key = chest(war.id(), side);
		int have = ledger(server).mercs().getOrDefault(key, 0);
		int took = Math.min(have, want);
		if (took > 0) {
			edit(server, l -> withMercs(l, with(l.mercs(), key, have - took <= 0 ? null : have - took)));
		}
		return took;
	}

	/** The war has begun (from {@link Wars#begin}): a great war, if both sides are rich enough. */
	static void begun(MinecraftServer server, War war) {
		Faction a = Factions.get(server, war.attacker().principal()), d = Factions.get(server, war.defender().principal());
		if (a == null || d == null) {
			return;
		}
		long wa = Wealth.worth(server, a), wd = Wealth.worth(server, d);
		if (!(wa >= GREAT_WORTH && wd >= GREAT_WORTH) && wa + wd < GREAT_TOGETHER) {
			return;
		}
		edit(server, l -> {
			List<Integer> great = new ArrayList<>(l.great());
			great.add(war.id());
			return withGreat(l, great);
		});
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.great_war.begun", a.name(), creed(server, a).named(),
			d.name(), creed(server, d).named()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0F, 0.5F);
		}
		hire(server, war, a);
		hire(server, war, d);
		rally(server, war, a, d);
		Wars.syncRelations(server);
	}

	/** {@code principal} fills its war chest with sellswords. */
	private static void hire(MinecraftServer server, War war, Faction principal) {
		int n = hireable(principal);
		if (n <= 0) {
			return;
		}
		long cost = (long)n * MERC_COST;
		Factions.update(server, principal.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - cost)));
		edit(server, l -> withMercs(l, with(l.mercs(), chest(war.id(), principal.id()), n)));
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.great_war.mercs", principal.name(), n, cost).withStyle(ChatFormatting.GOLD));
	}

	/** Free kingdoms take sides: by kindred creed, alliance, or tension with the other side. */
	private static void rally(MinecraftServer server, War war, Faction a, Faction d) {
		long now = Factions.level(server).getGameTime();
		int[] joined = new int[2];
		for (Faction h : Wars.heads(server)) {
			// (players' realms choose their own wars)
			if (h.id() == a.id() || h.id() == d.id() || Tempers.of(h) == null || !Wars.warsOf(server, h.id()).isEmpty()) {
				continue;
			}
			double lean = lean(server, h, a, d, now), leanD = lean(server, h, d, a, now);
			Faction side = lean > leanD && lean > 0 ? a : leanD > lean && leanD > 0 ? d : null;
			if (side == null) {
				continue;
			}
			int idx = side == a ? 0 : 1;
			if (joined[idx] >= COALITION_MOST || Raids.horizontal(h.seat(), side.seat()) > COALITION_RANGE * COALITION_RANGE) {
				continue;
			}
			joined[idx]++;
			edit(server, l -> withPledges(l, with(l.pledges(), Integer.toString(h.id()), new Pledge(war.id(), side.id()))));
			Faction foe = side == a ? d : a;
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.great_war.pledge", h.name(), creed(server, h).named(),
				side.name(), foe.name()).withStyle(ChatFormatting.GOLD));
		}
	}

	/** How much {@code h} leans to {@code friend}'s side against {@code foe} (0 or less: not at all). */
	private static double lean(MinecraftServer server, Faction h, Faction friend, Faction foe, long now) {
		if (WarData.truce(server, h.id(), foe.id(), now) || h.relation(foe.id()) == Relation.ALLIED) {
			return 0.0;
		}
		double lean = friction(server, h, foe) - friction(server, h, friend);
		if (h.relation(friend.id()) == Relation.ALLIED) {
			lean += 3.0;
		}
		if (h.relation(foe.id()) == Relation.RIVAL) {
			lean += 1.0;
		}
		lean += tension(server, h.id(), foe.id()) / RIVAL_AT - tension(server, h.id(), friend.id()) / RIVAL_AT;
		return lean >= 2.0 ? lean : 0.0;
	}

	/** Drops pledges, war chests and great-war marks of wars that are gone (wagers on them are refunded). */
	private static void forgetEnded(MinecraftServer server) {
		java.util.Set<Integer> live = new java.util.HashSet<>();
		for (War war : WarData.wars(server)) {
			live.add(war.id());
		}
		Ledger l = ledger(server);
		boolean stale = l.great().stream().anyMatch(id -> !live.contains(id)) || l.pledges().values().stream().anyMatch(p -> !live.contains(p.war()))
			|| l.mercs().keySet().stream().anyMatch(k -> !live.contains(Integer.parseInt(k.substring(0, k.indexOf('|')))))
			|| l.wagers().stream().anyMatch(w -> !live.contains(w.war()));
		if (!stale) {
			return;
		}
		Map<String, Integer> owed = new HashMap<>(l.owed());
		for (Wager w : l.wagers()) {
			if (!live.contains(w.war())) {
				owed.merge(w.player().toString(), w.emeralds(), Integer::sum);
			}
		}
		Map<String, Pledge> pledges = new HashMap<>(l.pledges());
		pledges.values().removeIf(p -> !live.contains(p.war()));
		Map<String, Integer> mercs = new HashMap<>(l.mercs());
		mercs.keySet().removeIf(k -> !live.contains(Integer.parseInt(k.substring(0, k.indexOf('|')))));
		List<Integer> great = l.great().stream().filter(live::contains).toList();
		List<Wager> wagers = l.wagers().stream().filter(w -> live.contains(w.war())).toList();
		edit(server, x -> withWagers(withGreat(withMercs(withPledges(x, Map.copyOf(pledges)), Map.copyOf(mercs)), great), wagers, owed));
		Wars.syncRelations(server);
	}

	/** Each day of a great war: its upkeep; a principal out of money loses its sellswords and sues for peace. */
	private static void upkeep(MinecraftServer server, ServerLevel level) {
		for (War war : List.copyOf(WarData.wars(server))) {
			if (!war.active() || !great(server, war)) {
				continue;
			}
			for (int side : new int[] {war.attacker().principal(), war.defender().principal()}) {
				Faction p = Factions.get(server, side);
				if (p == null || p.fallen()) {
					continue;
				}
				int mercs = ledger(server).mercs().getOrDefault(chest(war.id(), side), 0);
				long cost = UPKEEP_BASE + Math.round(p.treasury() * UPKEEP_SHARE) + (long)mercs * UPKEEP_PER_MERC;
				Faction after = Factions.update(server, side, f -> f.withTreasury(Math.max(0L, f.treasury() - cost)));
				for (Faction ally : allies(server, war.id(), side)) {
					long share = Math.round(ally.treasury() * ALLY_UPKEEP_SHARE);
					Factions.update(server, ally.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - share)));
				}
				if (after != null && after.treasury() < BROKE) {
					edit(server, l -> withMercs(l, with(l.mercs(), chest(war.id(), side), null)));
					KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.great_war.broke", after.name()).withStyle(ChatFormatting.YELLOW));
					War current = WarData.war(server, war.id());
					if (current != null && current.offerFrom() < 0) {
						Wars.offer(server, current, side);
					}
				}
			}
		}
	}

	/** In a great war, a fiefdom of another creed whose overlord is losing may rise and go over to the other side. */
	private static void rebellions(MinecraftServer server, ServerLevel level) {
		for (War war : List.copyOf(WarData.wars(server))) {
			if (!war.active() || !great(server, war)) {
				continue;
			}
			for (WarData.Side side : new WarData.Side[] {war.attacker(), war.defender()}) {
				WarData.Side other = war.other(side.principal());
				if (other == null || other.score() - side.score() < REBEL_BEHIND) {
					continue;
				}
				Faction overlord = Factions.get(server, side.principal());
				if (overlord == null) {
					continue;
				}
				for (Faction fief : Factions.all(server, FactionType.FIEFDOM)) {
					if (fief.fallen() || fief.overlord() != overlord.id() || fief.leader().isEmpty()) {
						continue;
					}
					Tempers.Temper t = Tempers.of(fief);
					if (t == null) {
						continue;
					}
					Creed own = NATURAL[t.ordinal()];
					Creed theirs = creed(server, overlord);
					if (FRICTION[own.ordinal()][theirs.ordinal()] < REBEL_FRICTION || level.getRandom().nextFloat() >= REBEL_CHANCE) {
						continue;
					}
					rebel(server, war, fief, overlord, other.principal(), own);
				}
			}
		}
	}

	private static void rebel(MinecraftServer server, War war, Faction fief, Faction overlord, int joins, Creed creed) {
		Factions.update(server, fief.id(), f -> f.withType(FactionType.KINGDOM).withOverlord(-1));
		Relations.set(server, fief.id(), overlord.id(), Relation.RIVAL);
		setCreed(server, fief.id(), creed);
		setTension(server, fief.id(), overlord.id(), REBEL_TENSION);
		edit(server, l -> withPledges(l, with(l.pledges(), Integer.toString(fief.id()), new Pledge(war.id(), joins))));
		Wars.syncRelations(server);
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.great_war.rebellion", fief.name(), creed.named(), overlord.name(),
			Wars.name(server, joins)).withStyle(ChatFormatting.RED));
		JaymosKingdoms.LOGGER.info("{} rebelled against {} in war {}", fief.name(), overlord.name(), war.id());
	}

	// ---- the end ------------------------------------------------------------------------------------------------------

	/**
	 * {@code war} is over (from {@link Wars#conclude} and {@link Wars#end}): {@code winner} (-1 for none) won on
	 * {@code terms}. Allies share the spoils or pay; the Faith converts; a vassal takes its overlord's creed; pledges,
	 * war chests and wagers are settled; the tension between the two eases.
	 */
	static void ended(MinecraftServer server, War war, int winner, Wars.@Nullable Terms terms) {
		int loser = winner < 0 ? -1 : Wars.otherSide(war, winner);
		Faction w = winner < 0 ? null : Factions.get(server, winner), l = loser < 0 ? null : Factions.get(server, loser);
		if (w != null && l != null && terms != null && terms != Wars.Terms.WHITE) {
			for (Faction ally : allies(server, war.id(), winner)) {
				long share = Math.round(Factions.get(server, loser).treasury() * ALLY_SPOILS);
				if (share > 0) {
					Factions.update(server, loser, f -> f.withTreasury(Math.max(0L, f.treasury() - share)));
					Factions.update(server, ally.id(), f -> f.withTreasury(f.treasury() + share));
					KingdomEvents.announce(server, Component.literal("  ").append(Component.translatable("message.jaymoskingdoms.great_war.ally_spoils",
						ally.name(), share, l.name())).withStyle(ChatFormatting.YELLOW));
				}
			}
			for (Faction ally : allies(server, war.id(), loser)) {
				long share = Math.round(ally.treasury() * ALLY_REPARATIONS);
				if (share > 0) {
					Factions.update(server, ally.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - share)));
					Factions.update(server, winner, f -> f.withTreasury(f.treasury() + share));
					KingdomEvents.announce(server, Component.literal("  ").append(Component.translatable("message.jaymoskingdoms.great_war.reparations",
						ally.name(), share, w.name())).withStyle(ChatFormatting.YELLOW));
				}
			}
			Goal goal = goal(server, war, winner);
			Creed theirs = creed(server, w);
			if (terms == Wars.Terms.VASSAL || goal.converts()) {
				if (creed(server, l) != theirs) {
					setCreed(server, loser, theirs);
					KingdomEvents.announce(server, Component.literal("  ").append(Component.translatable("message.jaymoskingdoms.creed.converted",
						l.name(), theirs.named())).withStyle(ChatFormatting.YELLOW));
				}
			}
		}
		if (winner >= 0 && loser >= 0) {
			setTension(server, winner, loser, AFTER_WAR);
		}
		settleWagers(server, war, winner, terms);
		edit(server, led -> {
			Map<String, Pledge> pledges = new HashMap<>(led.pledges());
			pledges.values().removeIf(p -> p.war() == war.id());
			Map<String, Integer> mercs = new HashMap<>(led.mercs());
			mercs.keySet().removeIf(k -> k.startsWith(war.id() + "|"));
			List<Integer> great = new ArrayList<>(led.great());
			great.remove(Integer.valueOf(war.id()));
			return withGreat(withMercs(withPledges(led, Map.copyOf(pledges)), Map.copyOf(mercs)), great);
		});
	}

	// ---- wagers -------------------------------------------------------------------------------------------------------

	/** {@code player} wagers {@code emeralds} from their inventory on {@code side} of {@code war}. Returns what to tell them. */
	static Component wager(ServerPlayer player, War war, int side, int emeralds) {
		MinecraftServer server = player.level().getServer();
		long now = Factions.level(server).getGameTime();
		if (war.active() && now - war.startsAt() > WAGER_OPEN_TICKS) {
			return Component.translatable("message.jaymoskingdoms.wager.closed");
		}
		int already = 0;
		for (Wager w : ledger(server).wagers()) {
			if (w.player().equals(player.getUUID()) && w.war() == war.id()) {
				if (w.side() != side) {
					return Component.translatable("message.jaymoskingdoms.wager.other_side");
				}
				already += w.emeralds();
			}
		}
		int amount = Math.min(emeralds, WAGER_MOST - already);
		if (amount <= 0) {
			return Component.translatable("message.jaymoskingdoms.wager.most", WAGER_MOST);
		}
		int have = player.getInventory().countItem(Items.EMERALD);
		if (have < amount) {
			return Component.translatable("message.jaymoskingdoms.wager.short", amount);
		}
		int left = amount;
		for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(Items.EMERALD)) {
				int take = Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
			}
		}
		Wager made = new Wager(player.getUUID(), war.id(), side, amount);
		edit(server, l -> {
			List<Wager> wagers = new ArrayList<>(l.wagers());
			wagers.add(made);
			return withWagers(l, wagers, l.owed());
		});
		return Component.translatable("message.jaymoskingdoms.wager.made", amount, Wars.name(server, side), amount * WAGER_PAYS);
	}

	private static void settleWagers(MinecraftServer server, War war, int winner, Wars.@Nullable Terms terms) {
		Ledger l = ledger(server);
		Map<String, Integer> owed = new HashMap<>(l.owed());
		List<Wager> kept = new ArrayList<>();
		boolean any = false;
		for (Wager w : l.wagers()) {
			if (w.war() != war.id()) {
				kept.add(w);
				continue;
			}
			any = true;
			int pay = winner < 0 || terms == null || terms == Wars.Terms.WHITE ? w.emeralds() : w.side() == winner ? w.emeralds() * WAGER_PAYS : 0;
			if (pay > 0) {
				owed.merge(w.player().toString(), pay, Integer::sum);
			}
		}
		if (any) {
			edit(server, x -> withWagers(x, kept, owed));
		}
	}

	/** Winnings owed to players online are paid into their inventories (dropped at their feet when full). */
	private static void payOut(MinecraftServer server) {
		Map<String, Integer> owed = ledger(server).owed();
		if (owed.isEmpty()) {
			return;
		}
		Map<String, Integer> left = new HashMap<>(owed);
		for (Map.Entry<String, Integer> e : owed.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(e.getKey()));
			if (player == null) {
				continue;
			}
			int n = e.getValue();
			while (n > 0) {
				int stack = Math.min(64, n);
				ItemStack coins = new ItemStack(Items.EMERALD, stack);
				if (!player.getInventory().add(coins)) {
					player.drop(coins, false, Prediction.SERVER_ONLY);
				}
				n -= stack;
			}
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.wager.paid", e.getValue()).withStyle(ChatFormatting.GOLD));
			left.remove(e.getKey());
		}
		if (left.size() != owed.size()) {
			edit(server, l -> withWagers(l, l.wagers(), left));
		}
	}

	// ---- the clock ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % PAY_TICKS == 0) {
			payOut(server);
		}
		if (now % CHECK_TICKS == 0) {
			daily(server, level);
		}
	}

	/** For {@code /kingdom war tensions}: the tense pairs, tensest first, as lines. */
	static List<Component> describeTensions(MinecraftServer server, int most) {
		List<Map.Entry<String, Float>> pairs = new ArrayList<>(ledger(server).tension().entrySet());
		pairs.sort((x, y) -> Float.compare(y.getValue(), x.getValue()));
		List<Component> out = new ArrayList<>();
		for (Map.Entry<String, Float> e : pairs) {
			if (out.size() >= most) {
				break;
			}
			String[] ids = e.getKey().split("\\|");
			Faction a = Factions.get(server, Integer.parseInt(ids[0])), b = Factions.get(server, Integer.parseInt(ids[1]));
			if (a == null || b == null || a.fallen() || b.fallen()) {
				continue;
			}
			out.add(Component.translatable("message.jaymoskingdoms.creed.tension_line", a.name(), creed(server, a).named(), b.name(),
				creed(server, b).named(), Math.round(e.getValue()), Math.round(WAR_AT)));
		}
		return out;
	}
}

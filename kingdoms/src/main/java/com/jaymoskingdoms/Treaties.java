package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.jaymoskingdoms.WarData.War;
import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.519) Treaties between free realms (Otectus pick 10, our own version; plan: {@code docs/Diplomacy-Plan.md}). Four
 * kinds, each for a while and renewable: recognition (tension eases, no rivalry), safe passage (the guards let the
 * partner's people by), a trade charter (more caravans, better prices, half the gate toll) and aid (the partner joins
 * your wars). They are signed by envoys ({@link Envoys}) or from a sovereign's desk ({@link RulersDesk}), and torn up by
 * a war against the partner or by renouncing them. Vassals share their overlord's treaties: every check goes through
 * {@link Wars#top}.
 */
public final class Treaties {
	/** The kinds; the ordinal indexes the tables here and in {@link Envoys}. */
	public enum Kind {
		RECOGNITION, PASSAGE, CHARTER, AID;

		public String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public Component named() {
			return Component.translatable("treaty.jaymoskingdoms." + this.key());
		}

		public Component the() {
			return Component.translatable("treaty.jaymoskingdoms." + this.key() + ".the");
		}

		public static @Nullable Kind parse(String s) {
			for (Kind k : values()) {
				if (k.key().equalsIgnoreCase(s)) {
					return k;
				}
			}
			return null;
		}
	}

	/** A treaty: its kind, the two heads (a below b), when it was signed and runs out (game ticks), and who proposed it. */
	record Treaty(String kind, int a, int b, long signed, long until, int proposer) {
		static final Codec<Treaty> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(Treaty::kind),
			Codec.INT.fieldOf("a").forGetter(Treaty::a),
			Codec.INT.fieldOf("b").forGetter(Treaty::b),
			Codec.LONG.fieldOf("signed").forGetter(Treaty::signed),
			Codec.LONG.fieldOf("until").forGetter(Treaty::until),
			Codec.INT.optionalFieldOf("proposer", -1).forGetter(Treaty::proposer)).apply(i, Treaty::new));

		@Nullable Kind kindOf() {
			return Kind.parse(this.kind);
		}

		int other(int id) {
			return this.a == id ? this.b : this.a;
		}

		boolean has(int id) {
			return this.a == id || this.b == id;
		}
	}

	/** Every treaty by pair and kind ({@code a|b|kind}), and the last day the round was run. */
	record Book(Map<String, Treaty> treaties, long lastDay) {
		static final Book EMPTY = new Book(Map.of(), -1L);
		static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Treaty.CODEC).optionalFieldOf("treaties", Map.of()).forGetter(Book::treaties),
			Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(Book::lastDay)).apply(i, Book::new));
	}

	/** On the Overworld. Saved. */
	static final AttachmentType<Book> TREATIES = AttachmentRegistry.create(JaymosKingdoms.id("treaties"), b -> b.persistent(Book.CODEC));

	/** Length of each kind in days (recognition, passage, charter, aid), and of a day in ticks. */
	static final int[] DAYS = {30, 10, 15, 20};
	static final long DAY_TICKS = 24000L;
	/** This close to the end a treaty comes up for renewal; an NPC pair renews if both score this far under {@link Envoys#ACCEPT_AT}. */
	static final long RENEW_TICKS = 24000L * 2;
	static final int RENEW_EASE = 10;
	/** Passage, charter and aid need recognition first. */
	static final boolean NEEDS_RECOGNITION = true;
	/** Tension eased each day by each kind (added to the usual delta in {@link GreatWars#stir}); an aid pair eases like allies. */
	static final float RECOGNITION_EASE = 3.0F, PASSAGE_EASE = 1.0F, CHARTER_EASE = 1.0F;
	static final float AID_EASE = GreatWars.ALLIED_EASE;
	/** A charter: the share off a villager's prices for the partner's people, and the share of the gate toll they pay. */
	static final float CHARTER_PRICE = 0.10F;
	static final int CHARTER_TOLL_PERCENT = 50;
	/** A charter caravan's next send comes after this share of the usual wait; it takes this many more carts; its load home is this many points fuller. */
	static final int CHARTER_EVERY_PERCENT = 60, CHARTER_CARTS = 1, CHARTER_RETURN = 20;
	/** A charter partner counts as in reach up to this times the usual reach, and sorts as if this many times nearer. */
	static final double CHARTER_REACH = 1.5, CHARTER_PULL = 2.0;
	/** Added to the coalition lean toward an aid partner. */
	static final double AID_LEAN = 3.0;
	/** An ally who can't march sends this share of its treasury once per war, at most and at least this much (emeralds' worth). */
	static final double AID_COIN_SHARE = 0.10;
	static final long AID_COIN_MOST = 100L, AID_COIN_LEAST = 5L;
	/** Breaking faith: tension added between breaker and wronged, and between the breaker and each of its other partners. */
	static final float BREAK_TENSION = 25.0F, BREAK_TENSION_WITNESS = 8.0F;
	/** A player sovereign's reputation with the wronged realm and with each of the breaker's other partners. */
	static final int REP_BREAK = -150, REP_BREAK_WITNESS = -30;
	/** The tempers of NPC kings that may break a treaty for a war, and the chance a day once tension is at war pitch. */
	static final EnumSet<Tempers.Temper> BREAKERS = EnumSet.of(Tempers.Temper.TYRANT, Tempers.Temper.WARLORD);
	static final float BREAK_CHANCE = 0.25F;
	/** How often the daily round is looked at (ticks). */
	static final int CHECK_TICKS = 1200;

	private Treaties() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Treaties::tick);
	}

	// ---- the record ---------------------------------------------------------------------------------------------------

	static Book book(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(TREATIES, Book.EMPTY);
	}

	static void edit(MinecraftServer server, UnaryOperator<Book> change) {
		Factions.level(server).setAttached(TREATIES, change.apply(book(server)));
	}

	private static String key(int a, int b, Kind kind) {
		return WarData.pair(a, b) + "|" + kind.key();
	}

	/** The realm at the head of {@code f}'s side: the one that holds its treaties. */
	static Faction head(MinecraftServer server, Faction f) {
		return Wars.top(server, f);
	}

	private static @Nullable Treaty stored(MinecraftServer server, int headA, int headB, Kind kind) {
		return headA == headB ? null : book(server).treaties().get(key(headA, headB, kind));
	}

	/** {@code a} and {@code b} (or the realms they answer to) hold {@code kind} with each other. */
	static boolean has(MinecraftServer server, Faction a, Faction b, Kind kind) {
		Treaty t = stored(server, head(server, a).id(), head(server, b).id(), kind);
		return t != null && t.until() > Factions.level(server).getGameTime();
	}

	static boolean has(MinecraftServer server, int a, int b, Kind kind) {
		Faction fa = Factions.get(server, a), fb = Factions.get(server, b);
		return fa != null && fb != null && has(server, fa, fb, kind);
	}

	/** {@code a} and {@code b} hold some treaty with each other. */
	static boolean any(MinecraftServer server, Faction a, Faction b) {
		for (Kind k : Kind.values()) {
			if (has(server, a, b, k)) {
				return true;
			}
		}
		return false;
	}

	static boolean any(MinecraftServer server, int a, int b) {
		Faction fa = Factions.get(server, a), fb = Factions.get(server, b);
		return fa != null && fb != null && any(server, fa, fb);
	}

	/** The treaties {@code f}'s side holds. */
	static List<Treaty> of(MinecraftServer server, Faction f) {
		int id = head(server, f).id();
		List<Treaty> out = new ArrayList<>();
		long now = Factions.level(server).getGameTime();
		for (Treaty t : book(server).treaties().values()) {
			// (0.7.519) a treaty that has run out no longer counts, though the daily round has not dropped it yet
			if (t.has(id) && t.until() > now) {
				out.add(t);
			}
		}
		out.sort(java.util.Comparator.comparingInt((Treaty t) -> t.other(id)).thenComparing(Treaty::kind));
		return out;
	}

	/** The realms holding {@code kind} with {@code f}'s side. */
	static List<Faction> partners(MinecraftServer server, Faction f, Kind kind) {
		int id = head(server, f).id();
		List<Faction> out = new ArrayList<>();
		for (Treaty t : of(server, f)) {
			if (t.kindOf() == kind) {
				Faction p = Factions.get(server, t.other(id));
				if (p != null && !p.fallen()) {
					out.add(p);
				}
			}
		}
		return out;
	}

	static int daysLeft(MinecraftServer server, Treaty t) {
		long left = t.until() - Factions.level(server).getGameTime();
		return (int)Math.max(0L, (left + DAY_TICKS - 1L) / DAY_TICKS);
	}

	/** The treaty of {@code kind} between {@code a} and {@code b}'s sides, or null. */
	static @Nullable Treaty find(MinecraftServer server, Faction a, Faction b, Kind kind) {
		return stored(server, head(server, a).id(), head(server, b).id(), kind);
	}

	// ---- signing ---------------------------------------------------------------------------------------------------

	/** {@code a} and {@code b} sign {@code kind} (or renew it), {@code proposer} having asked. */
	static void sign(MinecraftServer server, Faction a, Faction b, Kind kind, int proposer) {
		Faction ha = head(server, a), hb = head(server, b);
		if (ha.id() == hb.id()) {
			return;
		}
		long now = Factions.level(server).getGameTime();
		boolean renewing = stored(server, ha.id(), hb.id(), kind) != null;
		Treaty t = new Treaty(kind.key(), Math.min(ha.id(), hb.id()), Math.max(ha.id(), hb.id()), now, now + DAYS[kind.ordinal()] * DAY_TICKS, proposer);
		edit(server, bk -> {
			Map<String, Treaty> map = new HashMap<>(bk.treaties());
			map.put(key(ha.id(), hb.id(), kind), t);
			return new Book(Map.copyOf(map), bk.lastDay());
		});
		if (kind == Kind.RECOGNITION && ha.relation(hb.id()) == Relation.RIVAL) {
			Relations.set(server, ha.id(), hb.id(), Relation.NEUTRAL);
		}
		if (renewing) {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.treaty.renewed", ha.name(), hb.name(), kind.named())
				.withStyle(ChatFormatting.GRAY));
		} else {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.treaty." + (big(server, ha, hb, kind) ? "great." : "signed.") + kind.key(),
				ha.name(), hb.name()).withStyle(ChatFormatting.GREEN));
		}
		JaymosKingdoms.LOGGER.info("{} and {} sign {}", ha.name(), hb.name(), kind.key());
		if (kind == Kind.AID && !renewing) {
			// (a partner already at war: it joins at once; a renewal doesn't charge the coin again)
			for (War war : Wars.warsOf(server, ha.id())) {
				joinWar(server, war, Wars.sideOf(server, war, ha.id()), hb);
			}
			for (War war : Wars.warsOf(server, hb.id())) {
				joinWar(server, war, Wars.sideOf(server, war, hb.id()), ha);
			}
		}
	}

	/** A treaty the Tidings carry: aid, any with a player's realm, or a charter between two great realms. */
	static boolean big(MinecraftServer server, Faction a, Faction b, Kind kind) {
		return kind == Kind.AID || Crowns.sovereign(server, a) != null || Crowns.sovereign(server, b) != null
			|| kind == Kind.CHARTER && Wealth.worth(server, a) >= GreatWars.GREAT_WORTH && Wealth.worth(server, b) >= GreatWars.GREAT_WORTH;
	}

	// ---- the day -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		long day = Production.day(server);
		if (book(server).lastDay() == day) {
			return;
		}
		edit(server, b -> new Book(b.treaties(), day));
		daily(server, Factions.level(server));
	}

	private static boolean standing(@Nullable Faction f) {
		return f != null && !f.fallen() && f.type() == FactionType.KINGDOM && f.overlord() < 0;
	}

	private static void daily(MinecraftServer server, ServerLevel level) {
		long now = level.getGameTime();
		for (Treaty t : List.copyOf(book(server).treaties().values())) {
			Kind kind = t.kindOf();
			Faction fa = Factions.get(server, t.a()), fb = Factions.get(server, t.b());
			if (kind == null || !standing(fa) || !standing(fb)) {
				// (a side fell, became a vassal, or stopped being a realm)
				drop(server, t, true);
				continue;
			}
			if (t.until() <= now) {
				drop(server, t, true);
				continue;
			}
			if (t.until() - now > RENEW_TICKS) {
				continue;
			}
			boolean sovA = Crowns.sovereign(server, fa) != null, sovB = Crowns.sovereign(server, fb) != null;
			if (sovA && sovB) {
				// (renewed from the desk)
				continue;
			}
			if (sovA || sovB) {
				Faction npc = sovA ? fb : fa, player = sovA ? fa : fb;
				if (Envoys.between(server, npc.id(), player.id()) == null && Envoys.outgoing(server, npc.id()) == null
					&& Envoys.score(server, npc, player, kind) >= Envoys.ACCEPT_AT - Envoys.PROPOSE_SLACK) {
					Envoys.dispatch(server, npc, player, kind, true, null);
				}
				continue;
			}
			if (Envoys.score(server, fa, fb, kind) >= Envoys.ACCEPT_AT - RENEW_EASE && Envoys.score(server, fb, fa, kind) >= Envoys.ACCEPT_AT - RENEW_EASE) {
				sign(server, fa, fb, kind, t.proposer());
			}
		}
	}

	private static void drop(MinecraftServer server, Treaty t, boolean announce) {
		Kind kind = t.kindOf();
		edit(server, b -> {
			Map<String, Treaty> map = new HashMap<>(b.treaties());
			map.values().removeIf(x -> x.a() == t.a() && x.b() == t.b() && x.kind().equals(t.kind()));
			return new Book(Map.copyOf(map), b.lastDay());
		});
		if (announce && kind != null) {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.treaty.lapsed", Wars.name(server, t.a()), Wars.name(server, t.b()), kind.named())
				.withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- breaking faith -------------------------------------------------------------------------------------------------

	/** {@code breaker} goes to war on {@code wronged} ({@code kind == null}: every treaty between them is torn up) or tears up one. */
	static void broken(MinecraftServer server, Faction breaker, Faction wronged, @Nullable Kind kind) {
		tearUp(server, head(server, breaker), head(server, wronged), kind, kind == null, kind == null);
	}

	/** (From the desk) {@code breaker} renounces {@code kind}; renouncing recognition tears up every treaty with {@code wronged}. */
	static void renounced(MinecraftServer server, Faction breaker, Faction wronged, Kind kind) {
		tearUp(server, head(server, breaker), head(server, wronged), kind, kind == Kind.RECOGNITION, false);
	}

	private static void tearUp(MinecraftServer server, Faction breaker, Faction wronged, @Nullable Kind kind, boolean all, boolean war) {
		if (breaker.id() == wronged.id()) {
			return;
		}
		Set<Integer> witnesses = new HashSet<>();
		for (Treaty t : of(server, breaker)) {
			int other = t.other(breaker.id());
			if (other != wronged.id()) {
				witnesses.add(other);
			}
		}
		boolean aid = kind == Kind.AID;
		boolean removed = false;
		for (Treaty t : List.copyOf(book(server).treaties().values())) {
			if (t.has(breaker.id()) && t.has(wronged.id()) && (all || kind == null || t.kind().equals(kind.key()))) {
				drop(server, t, false);
				removed = true;
				aid |= "aid".equals(t.kind());
			}
		}
		if (!removed) {
			return;
		}
		GreatWars.setTension(server, breaker.id(), wronged.id(), GreatWars.tension(server, breaker.id(), wronged.id()) + BREAK_TENSION);
		for (int w : witnesses) {
			GreatWars.setTension(server, breaker.id(), w, GreatWars.tension(server, breaker.id(), w) + BREAK_TENSION_WITNESS);
		}
		UUID sov = Crowns.sovereign(server, breaker);
		ServerPlayer player = sov == null ? null : server.getPlayerList().getPlayer(sov);
		if (player != null) {
			Reputation.change(player, wronged, REP_BREAK);
			for (int w : witnesses) {
				Faction wf = Factions.get(server, w);
				if (wf != null) {
					Reputation.change(player, wf, REP_BREAK_WITNESS);
				}
			}
		}
		boolean players = sov != null || Crowns.sovereign(server, wronged) != null;
		Component what = kind == null ? Component.empty() : kind.named();
		if (war) {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.treaty.great_war", breaker.name(), wronged.name()).withStyle(ChatFormatting.RED));
		} else if (aid || players) {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.treaty.great_broken", breaker.name(), what, wronged.name())
				.withStyle(ChatFormatting.RED));
		} else {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.treaty.broken", breaker.name(), what, wronged.name())
				.withStyle(ChatFormatting.RED));
		}
		JaymosKingdoms.LOGGER.info("{} breaks faith with {}", breaker.name(), wronged.name());
	}

	/** {@code a} has declared war on {@code d} (from {@link Wars#declare}): any treaty between them is torn up. */
	static void warDeclared(MinecraftServer server, int a, int d) {
		Faction fa = Factions.get(server, a), fd = Factions.get(server, d);
		if (fa != null && fd != null && any(server, fa, fd)) {
			broken(server, fa, fd, null);
		}
	}

	/** An NPC king bound to {@code on} by treaty may go to war on it only if he's the breaking sort and tension has boiled over (from {@link GreatWars#stir}). */
	static boolean mayBreakForWar(MinecraftServer server, Faction from, Faction on, ServerLevel level) {
		if (!any(server, from, on)) {
			return true;
		}
		if (GreatWars.tension(server, from.id(), on.id()) >= GreatWars.TENSION_MAX) {
			return true;
		}
		Tempers.Temper t = Tempers.of(from);
		return t != null && BREAKERS.contains(t) && level.getRandom().nextFloat() < BREAK_CHANCE;
	}

	// ---- aid -------------------------------------------------------------------------------------------------------

	/** A war has begun: every aid partner of each principal is called. */
	static void warBegun(MinecraftServer server, War war) {
		for (int principal : new int[] {war.attacker().principal(), war.defender().principal()}) {
			Faction p = Factions.get(server, principal);
			if (p == null) {
				continue;
			}
			for (Faction partner : partners(server, p, Kind.AID)) {
				joinWar(server, war, principal, partner);
			}
		}
	}

	/** {@code ally} is bound by aid to {@code principal}'s side of {@code war}: it marches if free, else sends coin. */
	private static void joinWar(MinecraftServer server, War war, int principal, Faction ally) {
		if (principal < 0 || ally.fallen() || ally.id() == principal) {
			return;
		}
		int foeId = Wars.otherSide(war, principal);
		Faction foe = Factions.get(server, foeId), side = Factions.get(server, principal);
		if (foe == null || side == null || ally.id() == foeId || foe.fallen() || Wars.onSide(server, ally.id(), principal)) {
			return;
		}
		if (any(server, ally, foe)) {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.treaty.aid_torn", ally.name(), side.name(), foe.name())
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		UUID sov = Crowns.sovereign(server, ally);
		if (Wars.warsOf(server, ally.id()).isEmpty() && GreatWars.pledgedTo(server, ally.id()) == -1 && ally.overlord() < 0) {
			GreatWars.pledge(server, ally.id(), war.id(), principal);
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.treaty.great_aid", ally.name(), side.name(), foe.name())
				.withStyle(ChatFormatting.GOLD));
			if (sov != null) {
				Post.report(sov, Component.translatable("message.jaymoskingdoms.treaty.aid_called", side.name(), foe.name(), ally.name()));
			}
			Wars.syncRelations(server);
			return;
		}
		// (0.7.519) the treasury holds whole silver, so the coin is in units; only the board line is in milli
		long coin = Math.min(AID_COIN_MOST, Math.round(ally.treasury() * AID_COIN_SHARE));
		if (coin < AID_COIN_LEAST) {
			return;
		}
		Factions.update(server, ally.id(), x -> x.withTreasury(x.treasury() - coin));
		Factions.update(server, side.id(), x -> x.withTreasury(x.treasury() + coin));
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.treaty.aid_coin", ally.name(), side.name(),
			Money.textPaid(side, Money.worth(coin)), foe.name()).withStyle(ChatFormatting.GRAY));
	}

	// ---- what a treaty does for people -----------------------------------------------------------------------------------

	/** {@code p}'s guards of {@code guardRealm} let them by: they are a stranger to none and wanted by none, and their realm holds passage with it. */
	static boolean passage(MinecraftServer server, Faction guardRealm, ServerPlayer p) {
		if (Identities.hidden(p, guardRealm.id()) || Theft.wanted(p, guardRealm.id())) {
			return false;
		}
		return holds(server, guardRealm, p, Kind.PASSAGE);
	}

	/** {@code p}'s realm holds a charter with the one that owns the land. */
	static boolean charterPlayer(MinecraftServer server, Faction landRealm, ServerPlayer p) {
		return holds(server, landRealm, p, Kind.CHARTER);
	}

	private static boolean holds(MinecraftServer server, Faction land, ServerPlayer p, Kind kind) {
		Faction mine = Factions.of(server, p.getUUID());
		if (mine == null || !mine.type().isRealm() || mine.fallen()) {
			return false;
		}
		Faction hm = head(server, mine), hl = head(server, land);
		return hm.id() != hl.id() && has(server, hm, hl, kind);
	}

	/** {@code x} and {@code y} hold a trade charter. */
	static boolean chartered(MinecraftServer server, Faction x, Faction y) {
		return has(server, x, y, Kind.CHARTER);
	}

	/** {@code villager}'s offers to {@code player} (from {@code VillagerPriceMixin}): cheaper for a charter partner's people. */
	public static void prices(AbstractVillager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) {
			return;
		}
		MinecraftServer server = level.getServer();
		Faction owner = Banners.owner(server, ChunkPos.containing(villager.blockPosition()));
		if (owner == null || owner.fallen() || !owner.type().isRealm() || !charterPlayer(server, owner, sp)) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			offer.addToSpecialPriceDiff(-Math.max(1, (int)Math.floor(offer.getBaseCostA().getCount() * CHARTER_PRICE)));
		}
	}

	// ---- the ruler's info --------------------------------------------------------------------------------------------

	/** The Standing line for {@code f}'s treaties, with what it says when hovered. */
	static Standing.Lines lines(MinecraftServer server, Faction f) {
		List<Treaty> all = of(server, f);
		int id = head(server, f).id();
		if (all.isEmpty()) {
			return new Standing.Lines(List.of(Component.translatable("standing.jaymoskingdoms.treaties.none").withStyle(ChatFormatting.WHITE)), List.of(Component.empty()));
		}
		net.minecraft.network.chat.MutableComponent hover = Component.empty();
		boolean first = true;
		for (Treaty t : all) {
			Kind kind = t.kindOf();
			if (kind == null) {
				continue;
			}
			hover.append(first ? Component.empty() : Component.literal("\n"));
			hover.append(Component.translatable("standing.jaymoskingdoms.treaties.line", kind.named(), Wars.name(server, t.other(id)), daysLeft(server, t)));
			first = false;
		}
		return new Standing.Lines(List.of(Component.translatable("standing.jaymoskingdoms.treaties", all.size()).withStyle(ChatFormatting.WHITE)), List.of(hover));
	}
}

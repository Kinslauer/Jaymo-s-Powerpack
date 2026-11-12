package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.523) Talks that matter: "Have a word" in MCA's talk menu (and now and then a Chat) opens a short talk on the talk screen
 * ({@link TalkNet}): how they look at you (the only hint of how they feel), what they say, and two or three replies. Each reply
 * has its own answer and quietly shifts how much they trust you, respect you and feel warmth for you ({@link Bearings}). Some make a
 * promise ({@link Promises}), earn a favour, get you a rumour or a secret, or start a quarrel. The talks are in {@link TalkBook}.
 * Our own take on the idea of MCA: Conversations (none of its code). Plan: docs/Talks-Plan.md.
 */
public final class Talks {
	/** MCA's main-menu answer: have a word. */
	public static final String ANSWER = "jaymoskingdoms_word";

	/** How long (ticks) a talk screen waits for an answer. */
	static final long OPEN_TICKS = 1200L;
	/** The chance a Chat turns into a talk. */
	static final float TALK_ON_CHAT_CHANCE = 0.3F;
	/** Talks a villager has with a player a day; days before one repeats. */
	static final int TALKS_PER_DAY = 1, TALK_REPEAT_DAYS = 24;
	/** Weights of the talks in the draw. */
	static final int WEIGHT_RECENT = 3, WEIGHT_LENDER = 2, WEIGHT_SECRET = 2, WEIGHT_LIFE = 1;
	/** MCA hearts in step with a warm or cold reply. */
	static final int TALK_HEARTS_AT = 3, TALK_WARM_HEARTS = 2, TALK_COLD_HEARTS = 2;
	/** A quarrel: days it lasts, hearts lost. */
	static final int QUARREL_DAYS = 1, QUARREL_HEARTS = 5;
	/** Days between favours from one villager. */
	static final int FAVOUR_GAP_DAYS = 3;
	/** A loan: emeralds' worth of coin, days to repay; you're short of coin below this. */
	static final int LOAN_EMERALDS = 6, LOAN_DAYS = 3, LOAN_POOR = 4;
	/** How far things are looked for. */
	static final double RUMOUR_RANGE = 64.0, CHILD_RANGE = 32.0, KIN_NEAR = 64.0, ESCORT_MIN_AWAY = 24.0, ESCORT_SEEK = 96.0, CHILD_IN_SIGHT = 48.0;
	/** Days a broken promise is still raised in a talk. */
	static final int BROKEN_TALK_DAYS = 7;
	/** Lines for having nothing to say, and for sulking. */
	static final int NONE_LINES = 3, SULK_LINES = 3;
	/** How many rumour fallback lines exist (no news, and a clammed-up villager). */
	static final int RUMOUR_NONE_LINES = 4, RUMOUR_CLAM_LINES = 2;

	private static final String KEY = "talk.jaymoskingdoms.word.";

	/** Everything a gate or an argument may need to know. */
	record Context(ServerLevel level, ServerPlayer player, Villager v, @Nullable Faction faction, String job, Heritage.@Nullable Kind blood,
		Heritage.@Nullable Kind mine, @Nullable Patron faith, @Nullable Patron sworn, List<Recollections.Recollection> memories, long today, long clock) {
	}

	/** A talk screen sent and waiting for an answer. */
	private record Open(int entityId, String talk, long until) {
	}

	private static final Map<UUID, Open> OPEN = new HashMap<>();

	private Talks() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> OPEN.clear());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OPEN.remove(handler.player.getUUID()));
	}

	// ---- who may talk, and what about ------------------------------------------------------------------------------------

	private static boolean eligible(ServerPlayer player, Villager v) {
		return player.level() instanceof ServerLevel && McaCompat.isMca(v) && v.isAlive() && Dialects.of(v) == null && !Approaches.talking(v)
			&& !Questioning.active(player, v) && v.distanceTo(player) <= Approaches.TALK_RANGE;
	}

	private static @Nullable Context context(ServerPlayer player, Villager v) {
		if (!(v.level() instanceof ServerLevel level) || level.getServer() == null) {
			return null;
		}
		String job = com.jaymoskingdoms.Downed.isGuard(v) ? "guard"
			: BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		Patrons.State state = Patrons.state(player);
		long clock = Promises.clockOf(level.getServer());
		return new Context(level, player, v, Bearings.realm(v), job, VillagerHeritage.of(v), Heritage.of(player), Faiths.of(v),
			state.sworn() ? Patrons.of(player) : null, Recollections.of(v, player), Math.floorDiv(clock, 24000L), clock);
	}

	private static long had(Recollections.Bearing b, String id) {
		return b.talks().getOrDefault(id, -1L);
	}

	/** The promise talk they'd have first, if one applies. */
	private static TalkBook.@Nullable Talk promiseTalk(Context c, Recollections.Bearing b) {
		if (Promises.owedTo(c.player(), c.v()) != null) {
			return TalkBook.DUE;
		}
		for (Recollections.Recollection r : c.memories()) {
			if (!r.heard() && r.kind().equals(Recollections.Kind.BROKE_WORD.key()) && c.today() - r.day() <= BROKEN_TALK_DAYS
				&& had(b, "#broken") < r.day()) {
				return TalkBook.BROKEN;
			}
		}
		return null;
	}

	/** Whether a talk may be had at all today (nothing else is drawn when this is false). */
	private static boolean mayTalk(Context c, Recollections.Bearing b) {
		return b.quarrel() < c.today() && had(b, "#day") != c.today() && !Bearings.cold(c.v(), c.player()) && !Recollections.refuses(c.v(), c.player());
	}

	private static boolean fresh(Context c, Recollections.Bearing b, TalkBook.Talk t) {
		long d = had(b, t.id());
		return d < 0 || !t.once() && c.today() - d >= TALK_REPEAT_DAYS;
	}

	private static TalkBook.@Nullable Talk pick(Context c) {
		Recollections.Bearing b = Recollections.bearingOf(c.v(), c.player());
		TalkBook.Talk promise = promiseTalk(c, b);
		if (promise != null) {
			return promise;
		}
		if (!mayTalk(c, b)) {
			return null;
		}
		List<TalkBook.Talk> fits = new ArrayList<>();
		int total = 0;
		for (TalkBook.Talk t : TalkBook.ALL) {
			if (fresh(c, b, t) && t.gate().test(c)) {
				fits.add(t);
				total += t.weight();
			}
		}
		if (fits.isEmpty()) {
			return null;
		}
		int roll = c.v().getRandom().nextInt(total);
		for (TalkBook.Talk t : fits) {
			roll -= t.weight();
			if (roll < 0) {
				return t;
			}
		}
		return fits.get(0);
	}

	/** "Have a word" shows: they have something to talk of (the same test as {@link #pick}, without the draw). */
	public static boolean canOffer(ServerPlayer player, Villager v) {
		if (!eligible(player, v)) {
			return false;
		}
		Context c = context(player, v);
		if (c == null) {
			return false;
		}
		Recollections.Bearing b = Recollections.bearingOf(v, player);
		if (promiseTalk(c, b) != null) {
			return true;
		}
		if (!mayTalk(c, b)) {
			return false;
		}
		for (TalkBook.Talk t : TalkBook.ALL) {
			if (fresh(c, b, t) && t.gate().test(c)) {
				return true;
			}
		}
		return false;
	}

	/** What they say when there's no talk: nothing much, or a sulk after a quarrel. */
	public static Component nothing(ServerPlayer player, Villager v) {
		Recollections.Bearing b = Recollections.bearingOf(v, player);
		long today = Math.floorDiv(Promises.clockOf(v.level().getServer()), 24000L);
		String kind = b.quarrel() >= today ? "sulk." + v.getRandom().nextInt(SULK_LINES) : "none." + v.getRandom().nextInt(NONE_LINES);
		return Component.translatable(KEY + kind, Recollections.nameTo(player, v));
	}

	/** A Chat turns into a talk, now and then. */
	public static boolean onChat(ServerPlayer player, Villager v) {
		return canChatTalk(player, v) && openFromChat(player, v);
	}

	/** The roll and the check for a chat turning into a talk; call before closing MCA's screen. */
	public static boolean canChatTalk(ServerPlayer player, Villager v) {
		return v.getRandom().nextFloat() < TALK_ON_CHAT_CHANCE && canOffer(player, v);
	}

	/** Opens the talk for a chat that passed {@link #canChatTalk}. */
	public static boolean openFromChat(ServerPlayer player, Villager v) {
		return open(player, v, true);
	}

	/** Opens a talk with {@code v}; false if they've none (or the screen can't be shown). */
	public static boolean open(ServerPlayer player, Villager v, boolean fromChat) {
		if (!eligible(player, v) || !ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			return false;
		}
		Context c = context(player, v);
		if (c == null) {
			return false;
		}
		TalkBook.Talk talk = pick(c);
		if (talk == null) {
			return false;
		}
		send(c, talk);
		return true;
	}

	// ---- the screen -----------------------------------------------------------------------------------------------------------

	private static Object[] args(Context c, TalkBook.Talk talk) {
		Object[] extra = talk.args().get(c);
		Object[] all = new Object[extra.length + 1];
		all[0] = Recollections.nameTo(c.player(), c.v());
		System.arraycopy(extra, 0, all, 1, extra.length);
		return all;
	}

	private static boolean needOk(Context c, TalkBook.Talk talk, TalkBook.Reply reply) {
		if (talk == TalkBook.DUE && reply.key().equals("a")) {
			Promises.Promise p = Promises.owedTo(c.player(), c.v());
			return p != null && Promises.carries(c.player(), p);
		}
		TalkBook.Need n = reply.need();
		return switch (n.type()) {
			case NONE -> true;
			case ITEM -> n.item() != null && Purse.count(c.player(), n.item()) >= n.count();
			case EMERALDS -> com.jaymoskingdoms.money.Money.canPay(c.player(), com.jaymoskingdoms.money.Money.worth(n.count()));
			case RULER -> c.faction() != null && ruledByOther(c);
			case MCA -> McaCompat.isMca(c.v());
			case CHILD_IN_SIGHT -> {
				Villager child = TalkBook.wandered(c);
				yield child != null && child.distanceTo(c.player()) <= CHILD_IN_SIGHT;
			}
		};
	}

	private static boolean doubted(Context c, TalkBook.Reply reply) {
		return reply.outcome().promises() && Bearings.trust(c.v(), c.player()) <= Bearings.DOUBT_TRUST;
	}

	private static boolean tooMany(Context c, TalkBook.Reply reply) {
		return reply.outcome().promises() && Promises.openCount(c.player()) >= Promises.MAX_OPEN;
	}

	private static void send(Context c, TalkBook.Talk talk) {
		ServerPlayer player = c.player();
		Villager v = c.v();
		Object[] args = args(c, talk);
		Component words = Component.translatable("language.jaymoskingdoms.gist", Bearings.mannerLine(v, player).copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
			Component.translatable(KEY + talk.id() + ".open", args));
		List<Component> labels = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (TalkBook.Reply reply : talk.replies()) {
			Component label = Component.translatable(KEY + talk.id() + "." + reply.key(), args);
			boolean ok = needOk(c, talk, reply);
			if (doubted(c, reply)) {
				label = Component.translatable(KEY + "doubt", label);
				ok = false;
			} else if (tooMany(c, reply)) {
				label = Component.translatable(KEY + "too_many", label);
				ok = false;
			}
			labels.add(label);
			enabled.add(ok);
		}
		ServerPlayNetworking.send(player, new TalkNet.Ask(v.getId(), v.getDisplayName(), words, labels, enabled));
		OPEN.put(player.getUUID(), new Open(v.getId(), talk.id(), player.level().getGameTime() + OPEN_TICKS));
		// (it counts as had when it's shown, so closing it doesn't let you fish for another)
		long today = c.today();
		boolean broken = talk == TalkBook.BROKEN;
		Recollections.bear(v, player, b -> {
			Map<String, Long> m = new HashMap<>(b.talks());
			m.put(talk.id(), today);
			m.put("#day", today);
			if (broken) {
				m.put("#broken", today);
			}
			return new Recollections.Bearing(b.trust(), b.respect(), b.warmth(), b.settled(), Map.copyOf(m), b.quarrel());
		});
	}

	// ---- answers ------------------------------------------------------------------------------------------------------------------

	private static TalkBook.@Nullable Talk byId(String id) {
		if (TalkBook.DUE.id().equals(id)) {
			return TalkBook.DUE;
		}
		if (TalkBook.BROKEN.id().equals(id)) {
			return TalkBook.BROKEN;
		}
		for (TalkBook.Talk t : TalkBook.ALL) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}

	/** (Approaches) {@code player} answered a talk screen; true if it was one of ours (and so nothing else should take it). */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Open open = OPEN.get(player.getUUID());
		if (open == null || open.entityId() != answer.entityId()) {
			return false;
		}
		OPEN.remove(player.getUUID());
		if (player.level().getGameTime() > open.until()) {
			return false;
		}
		if (answer.option() < 0) {
			return true;
		}
		TalkBook.Talk talk = byId(open.talk());
		if (talk == null || answer.option() >= talk.replies().size() || !(player.level().getEntity(answer.entityId()) instanceof Villager v)
			|| !v.isAlive() || v.distanceTo(player) > Approaches.TALK_RANGE) {
			return true;
		}
		Context c = context(player, v);
		TalkBook.Reply reply = talk.replies().get(answer.option());
		if (c == null || !needOk(c, talk, reply) || doubted(c, reply) || tooMany(c, reply)) {
			return true;
		}
		apply(c, talk, reply);
		return true;
	}

	private static void apply(Context c, TalkBook.Talk talk, TalkBook.Reply reply) {
		ServerLevel level = c.level();
		ServerPlayer player = c.player();
		Villager v = c.v();
		TalkBook.Outcome outcome = reply.outcome();
		int trust = reply.trust(), respect = reply.respect(), warmth = reply.warmth();
		String said = KEY + talk.id() + "." + reply.key() + ".said";
		if (talk.id().equals("faith_priest") && reply.key().equals("b") && Recollections.leaning(v, player) < 0) {
			said += "_wronged";
			trust = -1;
			respect = 0;
			warmth = -1;
		}
		boolean quarrel = outcome.has(TalkBook.Out.QUARREL);
		// (their thanks for what you hand over, and their answer to being let off, are said by the promise itself)
		boolean quiet = outcome.has(TalkBook.Out.KEEP) || outcome.has(TalkBook.Out.RELEASE);
		if (!quiet) {
			Speech.tell(level, v, player, Component.translatable(said, args(c, talk)), quarrel ? Speech.RED : Speech.WHITE);
		}
		Bearings.move(v, player, trust, respect, warmth);
		if (warmth >= TALK_HEARTS_AT) {
			McaCompat.hearts(v, player, TALK_WARM_HEARTS);
		} else if (warmth <= -TALK_HEARTS_AT) {
			McaCompat.hearts(v, player, -TALK_COLD_HEARTS);
		}
		for (TalkBook.Outcome o = outcome; o != null; o = o.next()) {
			run(c, talk, o);
		}
		level.playSound(null, v.getX(), v.getY(), v.getZ(), quarrel ? SoundEvents.VILLAGER_NO : SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.8F,
			v.isBaby() ? 1.5F : 1.0F);
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	private static void run(Context c, TalkBook.Talk talk, TalkBook.Outcome o) {
		ServerPlayer player = c.player();
		Villager v = c.v();
		ServerLevel level = c.level();
		var server = level.getServer();
		switch (o.type()) {
			case BRING -> Promises.make(player, v, Promises.Kind.BRING, new Promises.Terms(id(o.item()), o.count(), null, Promises.dueIn(server, o.days()), v.blockPosition()));
			case BRING_JOB -> {
				TalkBook.Goods g = TalkBook.goods(c.job());
				Promises.make(player, v, Promises.Kind.BRING, new Promises.Terms(id(g.want()), g.wantCount(), null, Promises.dueIn(server, o.days()), v.blockPosition()));
			}
			case COME_BACK -> Promises.make(player, v, Promises.Kind.COME_BACK, new Promises.Terms("", 0, null, Promises.dueIn(server, o.days()), v.blockPosition()));
			case WATCH_CHILD -> {
				Villager child = TalkBook.babyKin(c);
				if (child != null) {
					Promises.make(player, v, Promises.Kind.WATCH_CHILD, new Promises.Terms(child.getName().getString(), 0, child.getUUID(),
						Promises.dueIn(server, Promises.WATCH_DAYS), v.blockPosition()));
				}
			}
			case ESCORT_CHILD -> {
				Villager child = TalkBook.wandered(c);
				if (child != null && Promises.make(player, v, Promises.Kind.ESCORT_CHILD, new Promises.Terms(child.getName().getString(), 0, child.getUUID(),
					c.clock() + Promises.ESCORT_TICKS, v.blockPosition()))) {
					McaCompat.follow(child, player);
				}
			}
			case SPEAK_FOR -> {
				if (c.faction() != null && ruledByOther(c)) {
					Promises.make(player, v, Promises.Kind.SPEAK_FOR, new Promises.Terms("", 0, null, Promises.dueIn(server, o.days()), v.blockPosition()));
				}
			}
			case KEEP_PEACE -> Promises.make(player, v, Promises.Kind.KEEP_PEACE, new Promises.Terms("", 0, null, Promises.dueIn(server, o.days()), v.blockPosition()));
			case LOAN -> {
				if (Promises.make(player, v, Promises.Kind.REPAY, new Promises.Terms("", LOAN_EMERALDS, null, Promises.dueIn(server, LOAN_DAYS), v.blockPosition()))) {
					com.jaymoskingdoms.money.Money.pay(player, com.jaymoskingdoms.money.Money.worth(LOAN_EMERALDS));
				}
			}
			case FAVOUR -> favour(c);
			case RUMOUR -> rumour(c, false);
			case SECRET -> rumour(c, true);
			case RIDDLE -> Speech.tell(level, v, player, Heavens.riddle(v.getRandom()), Heavens.COLOR);
			case QUARREL -> {
				long until = c.today() + QUARREL_DAYS;
				Recollections.bear(v, player, b -> new Recollections.Bearing(b.trust(), b.respect(), b.warmth(), b.settled(), b.talks(), until));
				McaCompat.hearts(v, player, -QUARREL_HEARTS);
				Recollections.remember(v, player, Recollections.Kind.RUDE, Component.empty());
			}
			case GIVE -> {
				if (o.item() != null && Purse.count(player, o.item()) >= o.count()) {
					Purse.take(player, o.item(), o.count());
					Recollections.remember(v, player, Recollections.Kind.GIFT, new ItemStack(o.item()).getHoverName());
				}
			}
			case PAY -> {
				if (com.jaymoskingdoms.money.Money.charge(player, com.jaymoskingdoms.money.Money.worth(o.count()))) {
					Recollections.remember(v, player, Recollections.Kind.GIFT, com.jaymoskingdoms.money.Money.text(player, com.jaymoskingdoms.money.Money.worth(o.count())));
				}
			}
			case BRIBE -> {
				if (com.jaymoskingdoms.money.Money.charge(player, com.jaymoskingdoms.money.Money.worth(o.count()))) {
					Recollections.remember(v, player, Recollections.Kind.BRIBED, Component.empty());
				}
			}
			case LIE -> Recollections.remember(v, player, Recollections.Kind.LIED, Component.empty());
			case KIND -> Recollections.remember(v, player, Recollections.Kind.KIND, Component.empty());
			case KEEP -> {
				Promises.Promise p = Promises.owedTo(player, v);
				if (p != null && Promises.take(player, p)) {
					Promises.keep(player, p);
					Speech.tell(level, v, player, Promises.handedLine(player, v, p), Speech.WHITE);
				}
			}
			case RELEASE -> {
				Promises.Promise p = Promises.owedTo(player, v);
				if (p != null) {
					Promises.release(player, p);
				}
			}
			default -> {
			}
		}
	}

	// ---- favours, rumours --------------------------------------------------------------------------------------------------

	private static void favour(Context c) {
		ServerPlayer player = c.player();
		Villager v = c.v();
		Recollections.Bearing b = Recollections.bearingOf(v, player);
		boolean ready = Bearings.warmth(v, player) >= Bearings.FAVOUR_WARMTH && (!b.talks().containsKey("#favour") || c.today() - b.talks().get("#favour") > FAVOUR_GAP_DAYS);
		if (!ready) {
			Speech.tell(c.level(), v, player, Component.translatable(KEY + "favour.none"), Speech.WHITE);
			return;
		}
		TalkBook.Goods g = TalkBook.goods(c.job());
		ItemStack stack = new ItemStack(g.give(), g.giveCount());
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
		long today = c.today();
		Recollections.bear(v, player, x -> {
			Map<String, Long> m = new HashMap<>(x.talks());
			m.put("#favour", today);
			return new Recollections.Bearing(x.trust(), x.respect(), x.warmth(), x.settled(), Map.copyOf(m), x.quarrel());
		});
		Speech.tell(c.level(), v, player, Component.translatable(KEY + "favour.given"), Speech.WHITE);
	}

	/** The nearest other villager within {@link #RUMOUR_RANGE} who is a crook; or null. */
	static @Nullable Villager crookNear(Villager v) {
		Villager best = null;
		for (Villager e : v.level().getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(RUMOUR_RANGE), x -> x != v && x.isAlive() && Crooks.isCrook(x))) {
			if (best == null || e.distanceToSqr(v) < best.distanceToSqr(v)) {
				best = e;
			}
		}
		return best;
	}

	private static void rumour(Context c, boolean secret) {
		ServerPlayer player = c.player();
		Villager v = c.v();
		RandomSource random = v.getRandom();
		int trust = Bearings.trust(v, player);
		Component line = null;
		if (trust >= (secret ? Bearings.SECRET_TRUST : Bearings.RUMOUR_TRUST)) {
			Villager crook = trust >= Bearings.SECRET_TRUST ? crookNear(v) : null;
			if (crook != null) {
				line = Component.translatable(KEY + "rumour.crook", Recollections.nameTo(player, v), crook.getDisplayName());
			} else if (!secret) {
				line = Recollections.gossipLine(v, player, random);
				if (line == null && c.faction() != null) {
					List<String> war = Approaches.war(c.level().getServer(), c.faction());
					if (!war.isEmpty()) {
						line = Component.translatable(KEY + "rumour.war", Recollections.nameTo(player, v), war.get(0));
					}
				}
				if (line == null) {
					line = Component.translatable(KEY + "rumour.none." + random.nextInt(RUMOUR_NONE_LINES));
				}
			}
		}
		if (line == null) {
			line = Component.translatable(KEY + "rumour.clam." + random.nextInt(RUMOUR_CLAM_LINES));
		}
		Speech.tell(c.level(), v, player, line, Speech.WHITE);
	}

	/** (0.7.523) The villager's realm has a ruler and it isn't the player (a ruler can't speak to themself for someone). */
	static boolean ruledByOther(Context c) {
		return c.faction() != null && c.faction().leader().isPresent()
			&& !Crowns.rules(c.level().getServer(), c.faction(), c.player().getUUID());
	}

}

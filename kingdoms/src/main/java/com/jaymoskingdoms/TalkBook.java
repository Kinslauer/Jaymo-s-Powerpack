package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.523) The authored talks of {@link Talks}: data only. Each talk has a gate (who may start it), the arguments its lines take,
 * and two or three replies, each with its own deltas to trust, respect and warmth and perhaps an outcome. The lines are in the lang
 * file under {@code talk.jaymoskingdoms.word.<id>}. Plan: docs/Talks-Plan.md.
 */
final class TalkBook {
	/** A child farther than this from the parent counts as wandered. */
	static final float WANDER_FROM_PARENT = 16.0F;

	private TalkBook() {
	}

	// ---- the shapes ---------------------------------------------------------------------------------------------------------

	@FunctionalInterface
	interface Gate {
		boolean test(Talks.Context c);

		default Gate and(Gate other) {
			return c -> test(c) && other.test(c);
		}
	}

	@FunctionalInterface
	interface Args {
		Object[] get(Talks.Context c);
	}

	/** What a reply needs to be picked. */
	enum Needs {
		NONE, ITEM, EMERALDS, RULER, MCA, CHILD_IN_SIGHT
	}

	record Need(Needs type, @Nullable Item item, int count) {
		static final Need NONE = new Need(Needs.NONE, null, 0);
		static final Need RULER = new Need(Needs.RULER, null, 0);
		static final Need MCA = new Need(Needs.MCA, null, 0);
		static final Need CHILD_IN_SIGHT = new Need(Needs.CHILD_IN_SIGHT, null, 0);

		static Need item(Item item, int count) {
			return new Need(Needs.ITEM, item, count);
		}

		static Need emeralds(int n) {
			return new Need(Needs.EMERALDS, null, n);
		}
	}

	/** What a reply sets going. */
	enum Out {
		NONE, BRING, BRING_JOB, COME_BACK, WATCH_CHILD, ESCORT_CHILD, SPEAK_FOR, KEEP_PEACE, LOAN, FAVOUR, RUMOUR, SECRET, QUARREL, GIVE, PAY,
		BRIBE, LIE, KIND, KEEP, RELEASE, RIDDLE
	}

	/** An outcome, with another to follow it if there is one. */
	record Outcome(Out type, @Nullable Item item, int count, int days, @Nullable Outcome next) {
		static final Outcome NONE = new Outcome(Out.NONE, null, 0, 0, null);

		static Outcome of(Out type) {
			return new Outcome(type, null, 0, 0, null);
		}

		static Outcome days(Out type, int days) {
			return new Outcome(type, null, 0, days, null);
		}

		Outcome and(Outcome then) {
			return new Outcome(type, item, count, days, next == null ? then : next.and(then));
		}

		/** This reply is a promise: it's greyed if they won't take your word, or you've promised too much. */
		boolean promises() {
			for (Outcome o = this; o != null; o = o.next) {
				switch (o.type) {
					case BRING, BRING_JOB, COME_BACK, WATCH_CHILD, ESCORT_CHILD, SPEAK_FOR, KEEP_PEACE, LOAN -> {
						return true;
					}
					default -> {
					}
				}
			}
			return false;
		}

		boolean has(Out what) {
			for (Outcome o = this; o != null; o = o.next) {
				if (o.type == what) {
					return true;
				}
			}
			return false;
		}
	}

	record Reply(String key, int trust, int respect, int warmth, Outcome outcome, Need need) {
	}

	record Talk(String id, Gate gate, boolean recent, boolean once, Args args, List<Reply> replies, int weight) {
		boolean promises() {
			return replies.stream().anyMatch(r -> r.outcome().promises());
		}
	}

	/** What a job wants, and what it gives in a favour. */
	record Goods(Item want, int wantCount, Item give, int giveCount) {
	}

	static Goods goods(String job) {
		return switch (job) {
			case "farmer" -> new Goods(Items.BONE_MEAL, 8, Items.BREAD, 3);
			case "fisherman" -> new Goods(Items.STRING, 4, Items.COOKED_COD, 3);
			case "shepherd" -> new Goods(Items.WHEAT, 10, Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), 4);
			case "fletcher" -> new Goods(Items.FEATHER, 8, Items.ARROW, 8);
			case "librarian" -> new Goods(Items.INK_SAC, 3, Items.BOOK, 1);
			case "cartographer" -> new Goods(Items.PAPER, 6, Items.MAP, 1);
			case "cleric" -> new Goods(Items.CANDLE, 2, Items.EXPERIENCE_BOTTLE, 2);
			case "armorer" -> new Goods(Items.IRON_INGOT, 4, Items.SHIELD, 1);
			case "weaponsmith" -> new Goods(Items.COAL, 8, Items.IRON_NUGGET, 9);
			case "toolsmith" -> new Goods(Items.IRON_INGOT, 2, Items.IRON_SHOVEL, 1);
			case "butcher" -> new Goods(Items.OAK_LOG, 8, Items.COOKED_PORKCHOP, 3);
			case "leatherworker" -> new Goods(Items.RABBIT_HIDE, 4, Items.LEATHER, 3);
			case "mason" -> new Goods(Items.CLAY_BALL, 8, Items.BRICK, 8);
			case "guard" -> new Goods(Items.BREAD, 4, Items.TORCH, 8);
			case "nitwit", "none" -> new Goods(Items.APPLE, 3, Items.POPPY, 1);
			default -> new Goods(Items.BREAD, 4, Items.APPLE, 2);
		};
	}

	// ---- building ---------------------------------------------------------------------------------------------------------------

	private static Reply r(String key, int t, int r, int w) {
		return new Reply(key, t, r, w, Outcome.NONE, Need.NONE);
	}

	private static Reply r(String key, int t, int r, int w, Outcome o) {
		return new Reply(key, t, r, w, o, Need.NONE);
	}

	private static Reply r(String key, int t, int r, int w, Outcome o, Need n) {
		return new Reply(key, t, r, w, o, n);
	}

	private static Talk talk(String id, Gate gate, Args args, int weight, boolean recent, boolean once, Reply... replies) {
		Talk t = new Talk(id, gate, recent, once, args, List.of(replies), recent ? Talks.WEIGHT_RECENT : weight);
		// (a talk with a promise reply isn't drawn for a villager you already owe)
		return t.promises() ? new Talk(id, gate.and(NOT_OWED), recent, once, args, t.replies(), t.weight()) : t;
	}

	private static Talk life(String id, Gate gate, Args args, Reply... replies) {
		return talk(id, gate, args, Talks.WEIGHT_LIFE, false, false, replies);
	}

	private static Talk lifeOnce(String id, Gate gate, Args args, Reply... replies) {
		return talk(id, gate, args, Talks.WEIGHT_LIFE, false, true, replies);
	}

	private static Talk recent(String id, Gate gate, Args args, Reply... replies) {
		return talk(id, gate, args, Talks.WEIGHT_RECENT, true, false, replies);
	}

	private static final Args NO_ARGS = c -> new Object[0];

	// ---- the gates ------------------------------------------------------------------------------------------------------------------

	private static final Gate NOT_OWED = c -> Promises.open(c.player(), c.v().getUUID()).isEmpty();

	private static boolean grown(Talks.Context c) {
		return !c.v().isBaby();
	}

	private static String jobKey(Talks.Context c) {
		return c.job().equals("none") ? "nitwit" : c.job();
	}

	private static Gate job(String... jobs) {
		return c -> {
			if (!grown(c)) {
				return false;
			}
			String j = jobKey(c);
			for (String x : jobs) {
				if (x.equals(j)) {
					return true;
				}
			}
			return false;
		};
	}

	private static final Gate PARENT = c -> grown(c) && babyKin(c) != null;
	private static final Gate CHILD_AWAY = c -> McaCompat.LOADED && grown(c) && wandered(c) != null;
	private static final Gate PARTNER = c -> partner(c) != null;
	private static final Gate KIN_GROWN = c -> kinGrown(c) != null;
	private static final Gate GRIEVING = c -> grief(c) != null;
	private static final Gate CHILD = c -> c.v().isBaby();
	private static final Gate SAME_BLOOD = c -> c.blood() != null && c.blood() == c.mine() && !VillagerHeritage.common(c.blood());
	private static final Gate RARE_BLOOD = c -> c.blood() != null && !VillagerHeritage.common(c.blood()) && c.blood() != c.mine();
	private static final Gate PLAYER_RARE = c -> c.mine() != null && !VillagerHeritage.common(c.mine())
		&& (c.blood() == null || VillagerHeritage.common(c.blood()));
	private static final Gate SAME_FAITH = c -> c.faith() != null && c.faith() == c.sworn();
	private static final Gate OTHER_FAITH = c -> c.faith() != null && c.sworn() != null && c.faith() != c.sworn() && c.faith() != Patron.TRUTH;
	private static final Gate TRUTH_OLD = c -> c.faith() == Patron.TRUTH && c.sworn() != null && c.sworn().seven;
	private static final Gate UNSWORN_DEVOUT = c -> c.sworn() == null && Temperaments.of(c.v()) == Temperaments.Temperament.DEVOUT;
	private static final Gate PRIEST = c -> Accusations.priest(c.v());
	private static final Gate AT_WAR = c -> grown(c) && c.faction() != null && !Approaches.war(c.level().getServer(), c.faction()).isEmpty();
	private static final Gate RULED = c -> c.faction() != null && Talks.ruledByOther(c);
	private static final Gate STRANGER = c -> c.faction() != null && c.memories().isEmpty()
		&& !c.faction().members().containsKey(c.player().getUUID());
	private static final Gate MEMBER = c -> c.faction() != null && c.faction().members().containsKey(c.player().getUUID());
	private static final Gate TOWN = c -> c.faction() != null;
	private static final Gate RAIN = c -> c.level().isRaining() && !c.level().isThundering() && c.level().canSeeSky(c.v().blockPosition());
	private static final Gate STORM = c -> c.level().isThundering();
	private static final Gate NIGHT = c -> Vampires.night(c.clock());
	private static final Gate REGULAR = c -> c.memories().stream().anyMatch(r -> r.kind().equals(Recollections.Kind.TRADE.key())
		&& r.count() >= Recollections.REGULAR_TRADES);
	private static final Gate LENDER = c -> grown(c) && Bearings.trust(c.v(), c.player()) >= Bearings.LEND_TRUST
		&& Bearings.warmth(c.v(), c.player()) >= Bearings.LEND_WARMTH
		&& !com.jaymoskingdoms.money.Money.canPay(c.player(), com.jaymoskingdoms.money.Money.worth(Talks.LOAN_POOR))
		&& Promises.of(c.player()).stream().noneMatch(p -> p.open() && p.type() == Promises.Kind.REPAY);
	// (0.7.547) the heavens overhead (Heavens): a knower, a seeker, the Aether's stones in your pack
	private static final Gate KNOWS = c -> Heavens.knows(c.v());
	private static final Gate SEEKS = c -> Heavens.seeks(c.v());
	private static final Gate CLEAR_NIGHT = c -> Vampires.night(c.clock()) && !c.level().isRaining() && c.level().canSeeSky(c.v().blockPosition().above());
	private static final Gate POCKETS = c -> Heavens.carriesAether(c.player());
	private static final Gate CROOK_NEAR = c -> Bearings.trust(c.v(), c.player()) >= Bearings.SECRET_TRUST && Talks.crookNear(c.v()) != null;

	private static Gate mem(Recollections.Kind... kinds) {
		return c -> {
			for (Recollections.Recollection r : c.memories()) {
				if (r.heard()) {
					continue;
				}
				for (Recollections.Kind k : kinds) {
					if (r.kind().equals(k.key())) {
						return true;
					}
				}
			}
			return false;
		};
	}

	private static Gate blood(Heritage.Kind kind) {
		return c -> grown(c) && c.blood() == kind;
	}

	/** The nearest baby that's the villager's child (or kin), within {@link Talks#CHILD_RANGE}. */
	static @Nullable Villager babyKin(Talks.Context c) {
		Villager best = null;
		for (Villager b : c.level().getEntitiesOfClass(Villager.class, c.v().getBoundingBox().inflate(Talks.CHILD_RANGE),
			e -> e != c.v() && e.isAlive() && e.isBaby() && McaCompat.kin(c.v(), e) == McaCompat.KIN)) {
			if (best == null || b.distanceToSqr(c.v()) < best.distanceToSqr(c.v())) {
				best = b;
			}
		}
		return best;
	}

	/** A baby kin who has wandered far from home (and from the parent). */
	static @Nullable Villager wandered(Talks.Context c) {
		var home = McaCompat.home(c.v());
		if (home.isEmpty() || !home.get().dimension().equals(c.level().dimension())) {
			return null;
		}
		Villager best = null;
		for (Villager b : c.level().getEntitiesOfClass(Villager.class, c.v().getBoundingBox().inflate(Talks.ESCORT_SEEK),
			e -> e != c.v() && e.isAlive() && e.isBaby() && McaCompat.kin(c.v(), e) == McaCompat.KIN
				&& e.distanceTo(c.v()) > WANDER_FROM_PARENT && home.get().pos().distToCenterSqr(e.position()) > Talks.ESCORT_MIN_AWAY * Talks.ESCORT_MIN_AWAY)) {
			if (best == null || b.distanceToSqr(c.v()) < best.distanceToSqr(c.v())) {
				best = b;
			}
		}
		return best;
	}

	static @Nullable Villager partner(Talks.Context c) {
		for (Villager o : c.level().getEntitiesOfClass(Villager.class, c.v().getBoundingBox().inflate(Talks.KIN_NEAR),
			e -> e != c.v() && e.isAlive() && McaCompat.kin(c.v(), e) == McaCompat.PARTNER)) {
			return o;
		}
		return null;
	}

	static @Nullable Villager kinGrown(Talks.Context c) {
		if (!grown(c)) {
			return null;
		}
		for (Villager o : c.level().getEntitiesOfClass(Villager.class, c.v().getBoundingBox().inflate(Talks.KIN_NEAR),
			e -> e != c.v() && e.isAlive() && !e.isBaby() && McaCompat.kin(c.v(), e) == McaCompat.KIN)) {
			return o;
		}
		return null;
	}

	/** A memory of a kinsman killed by another player: {dead kin's name, the killer's name}; or null. */
	static String @Nullable [] grief(Talks.Context c) {
		UUID self = Recollections.key(c.player(), c.v());
		for (Map.Entry<UUID, Recollections.Person> e : c.v().getAttachedOrElse(Recollections.MEMORIES, Map.<UUID, Recollections.Person>of()).entrySet()) {
			if (e.getKey().equals(self) || e.getValue().name().isEmpty()) {
				continue;
			}
			for (Recollections.Recollection r : e.getValue().memories()) {
				if (r.kind().equals(Recollections.Kind.KILLED_KIN.key())) {
					return new String[] {r.detail().getString(), e.getValue().name()};
				}
			}
		}
		return null;
	}

	/** The crime they remember for the {@code recent_crime} talk. */
	private static Component crimeOf(Talks.Context c) {
		for (Recollections.Recollection r : c.memories()) {
			if (!r.heard() && (r.kind().equals(Recollections.Kind.CRIME.key()) || r.kind().equals(Recollections.Kind.ROBBED.key()))) {
				return r.detail().getString().isEmpty() ? Component.translatable("recall.jaymoskingdoms.crime.rummage") : r.detail();
			}
		}
		return Component.empty();
	}

	private static Component name(Item item) {
		return new ItemStack(item).getHoverName();
	}

	// ---- args -----------------------------------------------------------------------------------------------------------------------

	private static final Args GOODS_ARGS = c -> {
		Goods g = goods(c.job());
		return new Object[] {g.wantCount(), name(g.want())};
	};
	private static final Args CHILD_ARGS = c -> {
		Villager k = babyKin(c);
		return new Object[] {k == null ? "" : k.getName()};
	};
	private static final Args WANDERED_ARGS = c -> {
		Villager k = wandered(c);
		return new Object[] {k == null ? "" : k.getName()};
	};
	private static final Args PARTNER_ARGS = c -> {
		Villager k = partner(c);
		return new Object[] {k == null ? "" : k.getName()};
	};
	private static final Args KIN_ARGS = c -> {
		Villager k = kinGrown(c);
		return new Object[] {k == null ? "" : k.getName()};
	};
	private static final Args GRIEF_ARGS = c -> {
		String[] g = grief(c);
		return g == null ? new Object[] {"", ""} : new Object[] {g[0], g[1]};
	};

	/** The enemy of a war that may have ended since the talk was drawn. */
	private static Object[] warArgs(Talks.Context c) {
		List<?> war = c.faction() == null ? List.of() : Approaches.war(c.level().getServer(), c.faction());
		return new Object[] {war.isEmpty() ? "" : war.get(0)};
	}

	private static Args blood2(boolean mine) {
		return c -> {
			var h = mine ? c.mine() : c.blood();
			return new Object[] {h == null ? "" : Component.translatable("heritage.jaymoskingdoms." + h.key())};
		};
	}

	// ---- the talks --------------------------------------------------------------------------------------------------------------------

	/** Picked before any other: they hold you to something you brought or paid. */
	static final Talk DUE = new Talk("promise_due", c -> true, false, false, NO_ARGS, List.of(
		r("a", 0, 0, 0, Outcome.of(Out.KEEP), Need.NONE),
		r("b", 0, 0, 0),
		r("c", 0, 0, 0, Outcome.of(Out.RELEASE))), 1);

	/** Picked before any other: they hold a broken promise against you. */
	static final Talk BROKEN = new Talk("promise_broken", c -> true, false, false, NO_ARGS, List.of(
		r("a", 2, 0, 1),
		r("b", 0, 0, -1),
		r("c", -5, -2, -3, Outcome.of(Out.LIE).and(Outcome.of(Out.QUARREL)))), 1);

	static final List<Talk> ALL = build();

	/** True for a talk that must never be drawn again with the same villager. */
	static boolean isOnce(String id) {
		for (Talk t : ALL) {
			if (t.id().equals(id)) {
				return t.once();
			}
		}
		return false;
	}

	private static List<Talk> build() {
		List<Talk> t = new ArrayList<>();
		// ---- job
		t.add(life("farmer_crop", job("farmer"), GOODS_ARGS,
			r("a", 2, 0, 3, Outcome.days(Out.BRING_JOB, 2)), r("b", 0, 0, 1), r("c", -1, -1, -4)));
		t.add(life("fisher_nets", job("fisherman"), GOODS_ARGS,
			r("a", 2, 0, 3, Outcome.days(Out.BRING_JOB, 2)), r("b", 0, -1, -1), r("c", 0, 0, 1)));
		t.add(life("shepherd_wolves", job("shepherd"), NO_ARGS,
			r("a", 2, 1, 3, Outcome.days(Out.COME_BACK, 1)), r("b", 0, -1, -2),
			r("c", 2, 0, 5, payOf(2), Need.emeralds(2))));
		t.add(life("fletcher_feathers", job("fletcher"), GOODS_ARGS,
			r("a", 2, 0, 3, Outcome.days(Out.BRING_JOB, 2)), r("b", 0, -1, 0), r("c", 0, 0, 3)));
		t.add(life("librarian_pages", job("librarian"), NO_ARGS,
			r("a", 2, 0, 1), r("b", -2, 0, 0), r("c", -2, -2, -5, Outcome.of(Out.QUARREL))));
		t.add(life("cartographer_edge", job("cartographer"), NO_ARGS,
			r("a", 1, 1, 2, Outcome.days(Out.COME_BACK, 3)), r("b", 0, 0, 2), r("c", 0, 0, 0)));
		t.add(life("cleric_doubt", job("cleric"), NO_ARGS,
			r("a", 1, 0, 4, Outcome.of(Out.KIND)), r("b", 0, 1, 0), r("c", 0, -2, -2)));
		t.add(life("armorer_boast", job("armorer"), NO_ARGS,
			r("a", 0, 3, 1), r("b", 0, -1, -3), r("c", 0, 1, 2)));
		t.add(life("weaponsmith_coal", job("weaponsmith"), GOODS_ARGS,
			r("a", 2, 0, 2, Outcome.days(Out.BRING_JOB, 2)), r("b", 0, -1, 0), r("c", 0, 0, 1)));
		t.add(life("toolsmith_apprentice", job("toolsmith"), NO_ARGS,
			r("a", 0, 0, 2), r("b", 0, 0, -1), r("c", 2, 1, 2, Outcome.days(Out.SPEAK_FOR, 4), Need.RULER)));
		t.add(life("butcher_tracks", job("butcher"), NO_ARGS,
			r("a", 1, 0, 1, Outcome.of(Out.FAVOUR)), r("b", 0, -1, 0), r("c", 0, 0, 1)));
		t.add(life("leatherworker_hides", job("leatherworker"), GOODS_ARGS,
			r("a", 2, 0, 2, Outcome.days(Out.BRING_JOB, 3)), r("b", 0, 0, 0), r("c", 0, 0, 1)));
		t.add(life("mason_wall", job("mason"), GOODS_ARGS,
			r("a", 2, 1, 2, Outcome.days(Out.SPEAK_FOR, 4), Need.RULER), r("b", 2, 0, 2, Outcome.days(Out.BRING_JOB, 2)), r("c", 0, -1, -2)));
		t.add(life("nitwit_fly", job("nitwit"), NO_ARGS,
			r("a", 0, 0, 3), r("b", 0, 0, -1), r("c", 0, 0, 1)));
		t.add(life("guard_night", job("guard"), GOODS_ARGS,
			r("a", 2, 2, 2), r("b", 0, 0, 0), r("c", 1, 0, 2, Outcome.days(Out.BRING_JOB, 1))));
		t.add(life("guard_taxes", job("guard").and(RULED), NO_ARGS,
			r("a", 3, 0, 1), r("b", -6, 0, -4, Outcome.of(Out.QUARREL)), r("c", 0, 1, -1)));
		// ---- family
		t.add(life("child_watch", PARENT, CHILD_ARGS,
			r("a", 2, 0, 3, Outcome.of(Out.WATCH_CHILD)), r("b", 0, 0, -1), r("c", 0, 0, 1)));
		t.add(recent("child_wandered", CHILD_AWAY, WANDERED_ARGS,
			r("a", 3, 0, 4, Outcome.of(Out.ESCORT_CHILD), Need.MCA), r("b", 0, 0, -2), r("c", 1, 0, 2, Outcome.NONE, Need.CHILD_IN_SIGHT)));
		t.add(life("partner_words", PARTNER, PARTNER_ARGS,
			r("a", 1, 0, 3), r("b", 2, 1, 1), r("c", 0, -1, -3)));
		t.add(life("kin_visit", KIN_GROWN, KIN_ARGS,
			r("a", 0, 0, 2), r("b", 0, 0, 1), r("c", 1, 0, 2)));
		t.add(lifeOnce("grief_kin", GRIEVING, GRIEF_ARGS,
			r("a", 2, 0, 4, Outcome.of(Out.KIND)), r("b", 0, 0, -1), r("c", 2, 0, 0)));
		t.add(life("child_dreams", PARENT, NO_ARGS,
			r("a", 0, 0, 2), r("b", 0, 1, 2), r("c", -1, -1, -3)));
		t.add(life("kid_knight", CHILD, NO_ARGS,
			r("a", 0, 2, 2), r("b", 0, 0, 1), r("c", 2, 0, 3)));
		t.add(life("kid_dare", CHILD, NO_ARGS,
			r("a", 0, 2, 2), r("b", 0, -1, 0), r("c", 0, 0, -1)));
		// ---- blood and heritage
		t.add(lifeOnce("blood_same", SAME_BLOOD, blood2(false),
			r("a", 4, 0, 4), r("b", 0, 0, -2), r("c", 1, 0, 2)));
		t.add(life("blood_stares", RARE_BLOOD, blood2(false),
			r("a", 1, 0, 2), r("b", 3, 1, 1), r("c", -1, 0, -2)));
		t.add(lifeOnce("blood_yours", PLAYER_RARE, blood2(true),
			r("a", 0, 1, 1), r("b", 0, 1, -1), r("c", 2, 0, 2)));
		t.add(life("blood_ember_cold", blood(Heritage.Kind.EMBERBORN), c -> new Object[] {6, name(Items.COAL)},
			r("a", 1, 0, 3, bring(Items.COAL, 6, 2)), r("b", 0, 0, -1), r("c", 1, 0, 1)));
		t.add(life("blood_frost_heat", blood(Heritage.Kind.FROSTBORN), NO_ARGS,
			r("a", 0, 0, 3, give(Items.SNOWBALL, 4), Need.item(Items.SNOWBALL, 4)), r("b", 0, 0, 1), r("c", 0, 0, -3)));
		// ---- faith
		t.add(life("faith_same", SAME_FAITH, c -> new Object[] {c.faith().named()},
			r("a", 2, 0, 3), r("b", 0, -1, -1), r("c", 1, 0, 2)));
		t.add(life("faith_other", OTHER_FAITH, c -> new Object[] {c.sworn() == null ? "" : c.sworn().named(), c.faith().named()},
			r("a", 2, 2, 1), r("b", 0, -1, -3), r("c", 2, 1, 3)));
		t.add(life("faith_truth", TRUTH_OLD, c -> new Object[] {c.sworn() == null ? "" : c.sworn().named()},
			r("a", 0, 1, 0), r("b", 1, 0, 1), r("c", 0, 0, -2)));
		t.add(life("faith_unsworn", UNSWORN_DEVOUT, NO_ARGS,
			r("a", 0, 0, 1), r("b", 0, 1, 0), r("c", 1, 0, 3, Outcome.days(Out.COME_BACK, 1))));
		t.add(life("faith_priest", PRIEST, NO_ARGS,
			r("a", 2, 0, 1), r("b", 0, 0, 1), r("c", 0, 0, 0)));
		// ---- realm
		t.add(life("realm_war", AT_WAR, TalkBook::warArgs,
			r("a", 1, 0, 3), r("b", 0, 1, -2), r("c", 2, 1, 3, Outcome.days(Out.SPEAK_FOR, 4), Need.RULER)));
		t.add(life("realm_ruler", RULED, NO_ARGS,
			r("a", 1, 0, 1), r("b", 2, 0, -1), r("c", 0, 1, 0)));
		t.add(life("realm_stranger", STRANGER, NO_ARGS,
			r("a", 1, 0, 1), r("b", 0, 1, 1), r("c", -3, 0, -2)));
		t.add(lifeOnce("realm_sworn", MEMBER, NO_ARGS,
			r("a", 3, 0, 4), r("b", -2, -1, -2), r("c", 1, 0, 2)));
		t.add(life("realm_taxes", RULED.and(job("farmer", "nitwit", "shepherd", "fisherman")), c -> new Object[] {6, name(Items.BREAD)},
			r("a", 2, 0, 3, bring(Items.BREAD, 6, 2)), r("b", 0, 1, -3), r("c", 3, 0, 1)));
		t.add(life("realm_roads", TOWN, NO_ARGS,
			r("a", 1, 1, 2, Outcome.days(Out.COME_BACK, 2)), r("b", 0, 0, 0), r("c", 0, -1, -1)));
		// ---- recent events and your deeds
		t.add(recent("recent_rain", RAIN, NO_ARGS,
			r("a", 0, 0, 1), r("b", 0, 0, 2), r("c", 0, 0, 1)));
		t.add(recent("recent_storm", STORM, NO_ARGS,
			r("a", 0, 0, 1, Outcome.of(Out.RUMOUR)), r("b", 0, 0, 0), r("c", 0, 0, 2)));
		t.add(recent("recent_night", NIGHT, NO_ARGS,
			r("a", 0, 0, 1), r("b", -1, 1, -1), r("c", 2, 0, 3)));
		t.add(recent("recent_crime", mem(Recollections.Kind.CRIME, Recollections.Kind.ROBBED), c -> new Object[] {crimeOf(c)},
			r("a", 1, 0, 0, Outcome.days(Out.KEEP_PEACE, 5)), r("b", -3, -1, -2),
			r("c", 1, -2, 0, new Outcome(Out.BRIBE, null, 4, 0, null), Need.emeralds(4))));
		t.add(recent("recent_saved", mem(Recollections.Kind.SAVED, Recollections.Kind.CARRIED, Recollections.Kind.GOT_UP, Recollections.Kind.SHIELDED,
			Recollections.Kind.HOME, Recollections.Kind.FREED), NO_ARGS,
			r("a", 2, 1, 3), r("b", 0, 0, -1), r("c", 1, 0, 3)));
		t.add(recent("recent_struck", mem(Recollections.Kind.STRUCK), NO_ARGS,
			r("a", 3, 0, 2), r("b", -2, 0, -4, Outcome.of(Out.QUARREL)), r("c", 1, 0, 1, Outcome.days(Out.KEEP_PEACE, 7))));
		t.add(recent("recent_gift", mem(Recollections.Kind.GIFT), NO_ARGS,
			r("a", 1, 0, 3), r("b", -2, 0, -3), r("c", 1, 0, 2)));
		t.add(recent("recent_quest", mem(Recollections.Kind.QUEST_DONE), NO_ARGS,
			r("a", 2, 0, 3), r("b", 1, 1, -1), r("c", 0, 1, 0)));
		t.add(recent("recent_regular", REGULAR, NO_ARGS,
			r("a", 0, 0, 2, Outcome.of(Out.FAVOUR)), r("b", 0, 0, 1), r("c", 0, 0, -3)));
		t.add(recent("recent_rude", mem(Recollections.Kind.RUDE, Recollections.Kind.REBUFFED), NO_ARGS,
			r("a", 1, 0, 2), r("b", -1, 0, -4, Outcome.of(Out.QUARREL)), r("c", -1, 0, -2)));
		// ---- the heavens overhead (0.7.547, Heavens): hints and riddles, never said outright
		t.add(life("heavens_stars", KNOWS.and(CLEAR_NIGHT), NO_ARGS,
			r("a", 1, 0, 2, Outcome.of(Out.RIDDLE)), r("b", 0, 0, 0), r("c", 0, -1, -2)));
		t.add(lifeOnce("heavens_giants", KNOWS, NO_ARGS,
			r("a", 1, 0, 2, Outcome.of(Out.RIDDLE)), r("b", 0, 0, -1), r("c", 0, 1, 2)));
		t.add(life("heavens_rain", KNOWS.and(RAIN), NO_ARGS,
			r("a", 1, 0, 1, Outcome.of(Out.RIDDLE)), r("b", 0, 0, 1), r("c", 0, 0, -1)));
		t.add(recent("heavens_pockets", KNOWS.and(POCKETS), NO_ARGS,
			r("a", -1, 0, 0), r("b", 3, 1, 2, Outcome.of(Out.RIDDLE)), r("c", 1, 0, 1, Outcome.of(Out.RIDDLE))));
		t.add(life("heavens_tower", SEEKS, c -> new Object[] {8, name(Items.SCAFFOLDING)},
			r("a", 2, 0, 4, bring(Items.SCAFFOLDING, 8, 3)), r("b", 0, 0, 1, Outcome.of(Out.RIDDLE)), r("c", 0, -1, -3)));
		// ---- trust
		t.add(talk("money_loan", LENDER, c -> new Object[] {com.jaymoskingdoms.money.Money.text(c.player(), com.jaymoskingdoms.money.Money.worth(Talks.LOAN_EMERALDS))}, Talks.WEIGHT_LENDER, false, false,
			r("a", 1, 0, 1, Outcome.of(Out.LOAN)), r("b", 0, 1, 0), r("c", 0, 0, -2)));
		t.add(talk("secret_crook", CROOK_NEAR, NO_ARGS, Talks.WEIGHT_SECRET, false, false,
			r("a", 2, 0, 1, Outcome.of(Out.SECRET)), r("b", 0, 0, 0), r("c", 0, 0, -1)));
		return List.copyOf(t);
	}

	private static Outcome bring(Item item, int count, int days) {
		return new Outcome(Out.BRING, item, count, days, null);
	}

	private static Outcome give(Item item, int count) {
		return new Outcome(Out.GIVE, item, count, 0, null);
	}

	private static Outcome payOf(int n) {
		return new Outcome(Out.PAY, null, n, 0, null);
	}
}

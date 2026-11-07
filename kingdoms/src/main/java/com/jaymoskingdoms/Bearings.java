package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.523) Trust, respect and warmth: three hidden feelings every villager (and folk) holds about each player, kept on their
 * memory of you ({@link Recollections.Bearing}). Everything they remember moves them ({@link #DEEDS}), word passed on moves them
 * half as much, and they fade with the days. MCA's hearts and your realm reputation colour them on top. They're never shown:
 * you see them in how people act. Fond ones charge less, the trusting gossip and tell secrets, a guard who trusts and respects
 * you comes to your side in a fight, one who's fond of you looks away from your crimes, and one who can't stand you won't talk.
 * Our own take on the idea of MCA: Conversations (none of its code). Plan: docs/Talks-Plan.md.
 */
public final class Bearings {
	/** The least and most any of the three can be. */
	static final int LEAST = -100, MOST = 100;
	/** Chat AI wording: a feeling this strong gets the strong words, this mild the mild ones. */
	static final int WORD_STRONG = 50, WORD_MILD = 20;
	/** Days it takes each to move one point toward nothing. */
	static final int DRIFT_DAYS_TRUST = 4, DRIFT_DAYS_RESPECT = 4, DRIFT_DAYS_WARMTH = 2;
	/** What they hear of you from another counts for this much less. */
	static final int HEARSAY_DIV = 2;
	/** Reputation with their realm adds to respect: this much reputation per point, at most this many points. */
	static final int REP_PER_RESPECT = 40, REP_RESPECT_MOST = 20;
	/** MCA hearts add to warmth: this many hearts per point, at most this many points. */
	static final int HEARTS_PER_WARMTH = 5, HEARTS_WARMTH_MOST = 20;
	/** How they look at you (the manner line). */
	static final int FOND_WARMTH = 50, FOND_TRUST = 30, WARM_WARMTH = 20, TRUSTING_TRUST = 40, ADMIRING_RESPECT = 40, WARY_TRUST = -20,
		COLD_WARMTH = -20, SCORN_RESPECT = -30;
	/** Too cold to talk to you at all. */
	static final int REFUSE_WARMTH = -50, REFUSE_TRUST = -60;
	/** Gossip: no trust, no gossip; much trust, more of it. */
	static final int GUARDED_TRUST = -15, CONFIDE_TRUST = 30;
	static final float CONFIDE_GOSSIP = 2.0F;
	/** What a talk's outcomes need. */
	public static final int RUMOUR_TRUST = 15, SECRET_TRUST = 35, LEND_TRUST = 35, LEND_WARMTH = 20, DOUBT_TRUST = -25, FAVOUR_WARMTH = 10;
	/** One who is fond enough looks away from a crime. */
	static final int SHIELD_TRUST = 50, SHIELD_WARMTH = 40;
	/** Standing by you in a fight. */
	static final int STAND_TRUST = 20, STAND_RESPECT = 30;
	static final double STAND_RANGE = 16.0;
	static final long STAND_GAP = 40L, STAND_SAY_GAP = 600L;
	static final int STAND_LINES = 3, WARN_LINES = 3;
	/** Prices: at most this share of a price, once the sway is at least this much. */
	static final float PRICE_SHARE_MOST = 0.2F, PRICE_LEAST_SWAY = 0.15F;
	/** Temper scales the changes. */
	static final float PROUD_RESPECT = 1.5F, SUSPICIOUS_TRUST_GAIN = 0.5F, GENTLE_WARMTH_LOSS = 0.5F, SPITEFUL_LOSS = 1.5F,
		HOTHEADED_WARMTH_LOSS = 1.5F;
	/** Lines for each manner. */
	static final int MANNER_LINES = 3;

	/** What each kind of memory moves: {trust, respect, warmth}. A kind not here moves nothing. */
	static final Map<Recollections.Kind, int[]> DEEDS = new EnumMap<>(Recollections.Kind.class);

	static {
		deed(Recollections.Kind.GIFT, 0, 0, 3);
		deed(Recollections.Kind.TRADE, 1, 0, 1);
		deed(Recollections.Kind.FED, 2, 0, 5);
		deed(Recollections.Kind.KIND, 0, 0, 2);
		deed(Recollections.Kind.STOOD, 3, 4, 2);
		deed(Recollections.Kind.PLAYED, 1, 0, 3);
		deed(Recollections.Kind.CHATTED, 0, 0, 1);
		deed(Recollections.Kind.HUGGED, 0, 0, 3);
		deed(Recollections.Kind.KISSED, 1, 0, 5);
		deed(Recollections.Kind.REBUFFED, 0, -1, -2);
		deed(Recollections.Kind.RUDE, -1, -2, -4);
		deed(Recollections.Kind.LIED, -8, -3, -3);
		deed(Recollections.Kind.THREATENED, -6, 0, -8);
		deed(Recollections.Kind.CONFESSED, 3, 1, 0);
		deed(Recollections.Kind.BRIBED, -2, -3, 1);
		deed(Recollections.Kind.BEAT_YOU, 0, 5, 0);
		deed(Recollections.Kind.LOST_TO_YOU, 0, -2, 1);
		deed(Recollections.Kind.STRUCK, -5, -1, -6);
		deed(Recollections.Kind.PAID_OFF, 1, 0, 1);
		deed(Recollections.Kind.CRIME, -6, -3, -3);
		deed(Recollections.Kind.ROBBED, -10, -2, -6);
		deed(Recollections.Kind.KILLED_KIN, -40, 0, -40);
		deed(Recollections.Kind.SPARED, 4, 4, 2);
		deed(Recollections.Kind.SWORE, 2, 0, 0);
		deed(Recollections.Kind.KEPT_WORD, 15, 5, 10);
		deed(Recollections.Kind.PROMISED, 0, 0, 0);
		deed(Recollections.Kind.SAVED, 15, 8, 15);
		deed(Recollections.Kind.MENDED, 4, 2, 6);
		deed(Recollections.Kind.FREED, 10, 5, 10);
		deed(Recollections.Kind.HOME, 15, 6, 15);
		deed(Recollections.Kind.RETURNED_KIN, 12, 5, 12);
		deed(Recollections.Kind.SAW_DEED, 2, 4, 1);
		deed(Recollections.Kind.QUEST_DONE, 6, 3, 3);
		deed(Recollections.Kind.QUEST_FAILED, -5, -2, -1);
		deed(Recollections.Kind.LETTER, 2, 0, 1);
		deed(Recollections.Kind.REVIVED_YOU, 0, 0, 1);
		deed(Recollections.Kind.SHOVED_YOU, -1, 0, -2);
		deed(Recollections.Kind.SEARCHED_YOU, 0, 0, -1);
		deed(Recollections.Kind.THANKED, 1, 1, 2);
		deed(Recollections.Kind.APOLOGISED, 3, 1, 2);
		deed(Recollections.Kind.SPURNED, -1, -1, -1);
		deed(Recollections.Kind.AIRED, 1, 1, 0);
		deed(Recollections.Kind.ASKED_AFTER, 0, 0, 2);
		deed(Recollections.Kind.CARRIED, 8, 4, 8);
		deed(Recollections.Kind.GOT_UP, 8, 4, 8);
		deed(Recollections.Kind.TENDED, 3, 1, 4);
		deed(Recollections.Kind.SHIELDED, 6, 5, 5);
		deed(Recollections.Kind.THREW, -4, -2, -5);
		deed(Recollections.Kind.QUESTIONED, -12, 3, -12);
		deed(Recollections.Kind.ACCUSED, -6, -2, -6);
		deed(Recollections.Kind.BROKE_WORD, -25, -5, -12);
		deed(Recollections.Kind.TALKED, 0, 0, 0);
		deed(Recollections.Kind.LED_YOU, 0, 0, 0);
		deed(Recollections.Kind.HALTED_YOU, 0, 0, 0);
		for (Recollections.Kind k : Recollections.Kind.values()) {
			if (!DEEDS.containsKey(k)) {
				JaymosKingdoms.LOGGER.warn("Bearings: no row for memory kind {}", k);
			}
		}
	}

	private static void deed(Recollections.Kind kind, int trust, int respect, int warmth) {
		DEEDS.put(kind, new int[] {trust, respect, warmth});
	}

	/** player → the game time they were last stood by in a fight. */
	private static final Map<UUID, Long> STOOD_BY = new HashMap<>();
	/** speaker → the game time they last spoke up for a player in a fight. */
	private static final Map<UUID, Long> SPOKE = new HashMap<>();

	private Bearings() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STOOD_BY.clear();
			SPOKE.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (!blocked && damage > 0.0F && entity instanceof ServerPlayer player && player.isAlive() && source.getEntity() instanceof LivingEntity attacker
				&& attacker instanceof Enemy && attacker.isAlive() && player.level() instanceof ServerLevel level) {
				standBy(level, player, attacker);
			}
		});
	}

	// ---- reading -----------------------------------------------------------------------------------------------------

	private static long today(LivingEntity who) {
		return Deeds.today(who.level().getServer());
	}

	/** {@code value} moved {@code per} days' worth of one point toward nothing, for {@code days}. */
	private static int drift(int value, long today, long settled, int per) {
		long days = Math.floorDiv(today, (long)per) - Math.floorDiv(settled, (long)per);
		int steps = (int)Math.min(Math.max(0L, days), 400L);
		return value > 0 ? Math.max(0, value - steps) : Math.min(0, value + steps);
	}

	/** The stored bearing with the days since it was last settled drifted away. */
	private static Recollections.Bearing drifted(Recollections.Bearing b, long today) {
		return new Recollections.Bearing(drift(b.trust(), today, b.settled(), DRIFT_DAYS_TRUST), drift(b.respect(), today, b.settled(), DRIFT_DAYS_RESPECT),
			drift(b.warmth(), today, b.settled(), DRIFT_DAYS_WARMTH), today, b.talks(), b.quarrel());
	}

	private static int clamp(int v) {
		return Math.max(LEAST, Math.min(MOST, v));
	}

	/** {@code who}'s realm, for what your reputation there adds to their respect; or null. */
	static @Nullable Faction realm(LivingEntity who) {
		if (!(who.level() instanceof ServerLevel level)) {
			return null;
		}
		if (who instanceof Folk folk) {
			return Factions.get(level.getServer(), folk.factionId());
		}
		if (who instanceof Villager v && v.isBaby()) {
			return Theft.lawAt(level, v.blockPosition());
		}
		return Quests.settlement(level, who);
	}

	static int trust(LivingEntity who, ServerPlayer p) {
		return clamp(drifted(Recollections.bearingOf(who, p), today(who)).trust());
	}

	static int respect(LivingEntity who, ServerPlayer p) {
		int stored = drifted(Recollections.bearingOf(who, p), today(who)).respect();
		Faction f = realm(who);
		int rep = f == null ? 0 : Math.max(-REP_RESPECT_MOST, Math.min(REP_RESPECT_MOST, Reputation.get(p, f.id()) / REP_PER_RESPECT));
		return clamp(stored + rep);
	}

	static int warmth(LivingEntity who, ServerPlayer p) {
		int stored = drifted(Recollections.bearingOf(who, p), today(who)).warmth();
		int hearts = 0;
		if (who instanceof Villager v && !Identities.hiddenFrom(p, v)) {
			hearts = Math.max(-HEARTS_WARMTH_MOST, Math.min(HEARTS_WARMTH_MOST, McaCompat.heartsOf(v, p) / HEARTS_PER_WARMTH));
		}
		return clamp(stored + hearts);
	}

	// ---- moving ------------------------------------------------------------------------------------------------------

	/** Rounds half away from zero; a change that was something never rounds to nothing. */
	private static int scale(int change, float by) {
		if (change == 0 || by == 1.0F) {
			return change;
		}
		float v = change * by;
		int r = (int)(Math.signum(v) * Math.floor(Math.abs(v) + 0.5F));
		return r == 0 ? (change > 0 ? 1 : -1) : r;
	}

	/** {@code change} as {@code who}'s temper takes it: {trust, respect, warmth}. */
	private static int[] tempered(LivingEntity who, int trust, int respect, int warmth) {
		Temperaments.Temperament t = who instanceof Villager ? Temperaments.of(who) : null;
		if (t == null) {
			return new int[] {trust, respect, warmth};
		}
		switch (t) {
			case PROUD -> respect = scale(respect, PROUD_RESPECT);
			case SUSPICIOUS -> trust = trust > 0 ? scale(trust, SUSPICIOUS_TRUST_GAIN) : trust;
			case KINDHEARTED, CHEERFUL -> warmth = warmth < 0 ? scale(warmth, GENTLE_WARMTH_LOSS) : warmth;
			case SPITEFUL -> {
				trust = trust < 0 ? scale(trust, SPITEFUL_LOSS) : trust;
				respect = respect < 0 ? scale(respect, SPITEFUL_LOSS) : respect;
				warmth = warmth < 0 ? scale(warmth, SPITEFUL_LOSS) : warmth;
			}
			case HOTHEADED -> warmth = warmth < 0 ? scale(warmth, HOTHEADED_WARMTH_LOSS) : warmth;
			default -> {
			}
		}
		return new int[] {trust, respect, warmth};
	}

	/** {@code who}'s three feelings about {@code p} change by these (tempered), after drift. */
	static void move(LivingEntity who, ServerPlayer p, int trust, int respect, int warmth) {
		move(who, Recollections.key(p, who), "", trust, respect, warmth);
	}

	private static void move(LivingEntity who, UUID id, String name, int trust, int respect, int warmth) {
		if (trust == 0 && respect == 0 && warmth == 0) {
			return;
		}
		int[] c = tempered(who, trust, respect, warmth);
		long day = today(who);
		Recollections.bear(who, id, name, b -> {
			Recollections.Bearing d = drifted(b, day);
			return new Recollections.Bearing(clamp(d.trust() + c[0]), clamp(d.respect() + c[1]), clamp(d.warmth() + c[2]), day, d.talks(), d.quarrel());
		});
	}

	/** (Recollections) {@code who} just remembered {@code kind} of the player known as {@code id}: it moves them (hearsay, by half). */
	static void deed(LivingEntity who, UUID id, Recollections.Kind kind, boolean hearsay) {
		int[] row = DEEDS.get(kind);
		if (row == null || row[0] == 0 && row[1] == 0 && row[2] == 0) {
			return;
		}
		int div = hearsay ? HEARSAY_DIV : 1;
		move(who, id, "", row[0] / div, row[1] / div, row[2] / div);
	}

	// ---- what it changes -----------------------------------------------------------------------------------------------

	/** (VillagerPriceMixin) The fond charge less, the sour more: up to a fifth of each price. */
	public static void prices(Villager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel) || !(player instanceof ServerPlayer p)) {
			return;
		}
		float m = Math.max(-1.0F, Math.min(1.0F, (warmth(villager, p) + trust(villager, p) / 2) / 100.0F));
		if (Math.abs(m) < PRICE_LEAST_SWAY) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			int cost = offer.getBaseCostA().getCount();
			int by = Math.max(1, Math.round(cost * PRICE_SHARE_MOST * Math.abs(m)));
			offer.addToSpecialPriceDiff(m > 0 ? -by : by);
		}
	}

	/** {@code who} is fond of {@code p} (the manner "fond"). */
	static boolean fond(LivingEntity who, ServerPlayer p) {
		return warmth(who, p) >= FOND_WARMTH && trust(who, p) >= FOND_TRUST;
	}

	/** {@code who} is too cold to talk to {@code p} at all. */
	static boolean cold(LivingEntity who, ServerPlayer p) {
		return warmth(who, p) <= REFUSE_WARMTH || trust(who, p) <= REFUSE_TRUST;
	}

	/** (Theft) {@code who} would look away from {@code p}'s crime. */
	static boolean shields(Villager who, ServerPlayer p) {
		return trust(who, p) >= SHIELD_TRUST && warmth(who, p) >= SHIELD_WARMTH;
	}

	/** {@code who} would stand by {@code p} in a fight. */
	static boolean standsBy(LivingEntity who, ServerPlayer p) {
		return trust(who, p) >= STAND_TRUST && respect(who, p) >= STAND_RESPECT;
	}

	/** The manner state for how {@code who} looks at {@code p}. */
	static String manner(LivingEntity who, ServerPlayer p) {
		int warmth = warmth(who, p), trust = trust(who, p), respect = respect(who, p);
		if (warmth <= COLD_WARMTH) {
			return "cold";
		}
		if (trust <= WARY_TRUST) {
			return "wary";
		}
		if (respect <= SCORN_RESPECT) {
			return "scornful";
		}
		if (warmth >= FOND_WARMTH && trust >= FOND_TRUST) {
			return "fond";
		}
		if (respect >= ADMIRING_RESPECT) {
			return "admiring";
		}
		if (warmth >= WARM_WARMTH) {
			return "warm";
		}
		if (trust >= TRUSTING_TRUST) {
			return "trusting";
		}
		return "plain";
	}

	/** The line on how {@code who} looks at {@code p}: the only hint of how they feel. */
	static Component mannerLine(LivingEntity who, ServerPlayer p) {
		return Component.translatable("talk.jaymoskingdoms.word.manner." + manner(who, p) + "." + who.getRandom().nextInt(MANNER_LINES),
			who.getDisplayName());
	}

	/** For the chat AI: how {@code who} feels about {@code p}, in words (empty when none of it is strong). */
	static String prompt(LivingEntity who, ServerPlayer p) {
		int trust = trust(who, p), respect = respect(who, p), warmth = warmth(who, p);
		List<String> out = new ArrayList<>();
		if (trust >= WORD_STRONG) {
			out.add("You trust them a great deal.");
		} else if (trust >= WORD_MILD) {
			out.add("You trust them.");
		} else if (trust <= -WORD_STRONG) {
			out.add("You would not trust them with anything.");
		} else if (trust <= -WORD_MILD) {
			out.add("You don't trust them.");
		}
		if (respect >= WORD_STRONG) {
			out.add("You look up to them.");
		} else if (respect >= WORD_MILD) {
			out.add("You respect them.");
		} else if (respect <= -WORD_STRONG) {
			out.add("You hold them in contempt.");
		} else if (respect <= -WORD_MILD) {
			out.add("You think little of them.");
		}
		if (warmth >= WORD_STRONG) {
			out.add("You are fond of them.");
		} else if (warmth >= WORD_MILD) {
			out.add("You like them.");
		} else if (warmth <= -WORD_STRONG) {
			out.add("You can't stand them.");
		} else if (warmth <= -WORD_MILD) {
			out.add("You dislike them.");
		}
		return String.join(" ", out);
	}

	// ---- standing by you -------------------------------------------------------------------------------------------------

	/** A monster hurt {@code player}: the guards near who trust and respect them come to their side; others call out. */
	private static void standBy(ServerLevel level, ServerPlayer player, LivingEntity attacker) {
		long now = level.getGameTime();
		Long last = STOOD_BY.get(player.getUUID());
		if (last != null && now - last < STAND_GAP) {
			return;
		}
		if (com.jaymoskingdoms.forms.Duels.dueling(player, attacker)) {
			return;
		}
		boolean any = false;
		for (PathfinderMob e : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(STAND_RANGE),
			e -> e.isAlive() && (e instanceof Villager || e instanceof Folk) && e != attacker)) {
			boolean guard = Downed.isGuard(e);
			if (!guard && (!(e instanceof Villager v) || v.isBaby())) {
				continue;
			}
			if (guard && Rulers.target(e) != null) {
				continue;
			}
			if (!standsBy(e, player)) {
				continue;
			}
			Faction f = realm(e);
			if (f != null && Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
				continue;
			}
			any = true;
			if (guard) {
				Rulers.charge(e, attacker);
			}
			Long spoke = SPOKE.get(e.getUUID());
			if (spoke == null || now - spoke >= STAND_SAY_GAP) {
				SPOKE.put(e.getUUID(), now);
				String key = guard ? "stand." + e.getRandom().nextInt(STAND_LINES) : "warn." + e.getRandom().nextInt(WARN_LINES);
				Speech.tell(level, e, player, Component.translatable("talk.jaymoskingdoms.word." + key), Speech.YELLOW);
			}
		}
		if (any) {
			STOOD_BY.put(player.getUUID(), now);
			if (SPOKE.size() > 512) {
				SPOKE.values().removeIf(t -> now - t >= STAND_SAY_GAP);
			}
		}
	}
}

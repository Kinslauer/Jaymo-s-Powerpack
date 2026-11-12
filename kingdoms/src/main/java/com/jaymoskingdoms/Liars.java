package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;

/**
 * Villagers and merchants sometimes lie. Whether one lies about a thing today is fixed by who they are and the day
 * ({@link #wouldLie}), so asking twice gets the same answer. Three lies are told: a trader names a dearer price
 * ({@link #prices}), someone asked the way points the wrong way ({@link Directions}), and a guard asked where he was
 * claims his post ({@link Alibis}).
 * <p>A player with sharp Wits sees the tell ({@link #sharp}): a stage direction in a speech bubble, shown once a day per
 * liar. Whoever has taken the truth draught ({@link Poisons#truthful}) or stands in a zone of truth ({@link #zone}) cannot
 * lie at all, and a guard in either confesses what he hid.
 */
public final class Liars {
	/** What is lied about. */
	public enum Kind {
		PRICE, LEAD, ALIBI
	}

	/** The chance a villager would lie about a thing on a given day, before their nature leans it. */
	static final float LIE_CHANCE = 0.18F, GUARD_LIE_CHANCE = 0.4F;
	/** The chance a player with no Wits sees the tell, and what each Wits point adds. */
	static final float SEE_BASE = 0.15F, SEE_PER_WIT = 0.007F;
	/** A liar's price goes up by this share. */
	static final float PRICE_UP = 0.3F;
	/** A false lead points this far off true (degrees, at least) and is told this much nearer or farther. */
	static final double LEAD_OFF_MIN = 70.0, LEAD_OFF_SPREAD = 110.0;
	/** A zone of truth: the scoreboard tag on whoever stands in one (other mods may set it), and the default reach. */
	public static final String ZONE_TAG = "jaymoskingdoms_zone_of_truth";
	public static final double ZONE_RADIUS = 8.0;

	private record Zone(ServerLevel level, Vec3 centre, double radius, long until) {
	}

	private static final List<Zone> ZONES = new ArrayList<>();
	private static final List<Predicate<LivingEntity>> HONEST = new ArrayList<>();
	/** "villager/player/day/kind": the tell already shown. */
	private static final Set<String> TOLD = new HashSet<>();

	private Liars() {
	}

	/** Makes everyone {@code honest} accepts unable to lie (a spell that compels the truth plugs in here). */
	public static void addHonesty(Predicate<LivingEntity> honest) {
		HONEST.add(honest);
	}

	/** A zone of truth: no one within {@code radius} of {@code centre} can lie for {@code ticks}. */
	public static void zone(ServerLevel level, Vec3 centre, double radius, long ticks) {
		ZONES.add(new Zone(level, centre, radius, level.getGameTime() + ticks));
	}

	/** Whether {@code who} cannot lie now: the truth draught, a zone of truth, or a registered cause. */
	public static boolean honest(LivingEntity who) {
		if (Poisons.truthful(who) || who.entityTags().contains(ZONE_TAG)) {
			return true;
		}
		long now = who.level().getGameTime();
		ZONES.removeIf(z -> now >= z.until());
		for (Zone z : ZONES) {
			if (z.level() == who.level() && z.centre().distanceTo(who.position()) <= z.radius()) {
				return true;
			}
		}
		for (Predicate<LivingEntity> p : HONEST) {
			if (p.test(who)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code who} is the sort to lie about {@code kind} today, whatever else holds them back. */
	static boolean wouldLie(LivingEntity who, Kind kind, long day) {
		if (!(who instanceof Villager || Downed.isGuard(who)) || who instanceof Villager b && b.isBaby()) {
			return false;
		}
		long h = who.getUUID().getLeastSignificantBits() * 31L + who.getUUID().getMostSignificantBits() + day * 7919L + kind.ordinal() * 104729L;
		h ^= h >>> 29;
		h *= 0x9E3779B97F4A7C15L;
		h ^= h >>> 32;
		float roll = (h & 0xFFFFL) / 65536.0F;
		float chance = kind == Kind.ALIBI ? GUARD_LIE_CHANCE : LIE_CHANCE;
		String lean = who instanceof Villager v ? McaCompat.personality(v) : "";
		chance += switch (lean) {
			case "greedy", "crabby", "odd" -> 0.12F;
			case "friendly", "peaceful", "shy" -> -0.1F;
			default -> 0.0F;
		};
		return roll < chance;
	}

	/** Whether {@code who} lies to {@code player} about {@code kind} now. */
	public static boolean lies(LivingEntity who, Kind kind) {
		return who.level() instanceof ServerLevel level && wouldLie(who, kind, level.getGameTime() / 24000L) && !honest(who);
	}

	/** The chance {@code player} sees a liar's tell: sharper Wits see more. */
	public static float sharp(ServerPlayer player) {
		return Math.min(0.95F, SEE_BASE + SEE_PER_WIT * Stats.value(player, Stats.Stat.WITS));
	}

	/** If {@code player} is sharp enough, {@code liar} gives themselves away in a bubble (once a day per kind); true if shown. */
	static boolean tell(ServerPlayer player, LivingEntity liar, Kind kind) {
		if (!(liar.level() instanceof ServerLevel level)) {
			return false;
		}
		String key = liar.getUUID() + "/" + player.getUUID() + "/" + level.getGameTime() / 24000L + "/" + kind;
		if (TOLD.contains(key) || level.getRandom().nextFloat() >= sharp(player)) {
			return false;
		}
		if (TOLD.size() > 2000) {
			TOLD.clear();
		}
		TOLD.add(key);
		Speech.tell(level, liar, player, Component.translatable("liar.jaymoskingdoms.tell." + kind.name().toLowerCase(java.util.Locale.ROOT) + "." + level.getRandom().nextInt(3)), 0xB8B8B8);
		Skills.grant(player, Skills.Pillar.SCHOLAR, 1);
		return true;
	}

	/** A trader who lies names a dearer price; a sharp customer sees the tell. */
	public static void prices(Villager villager, net.minecraft.world.entity.player.Player player) {
		if (!(player instanceof ServerPlayer p) || villager.getOffers().isEmpty() || !lies(villager, Kind.PRICE)) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			offer.addToSpecialPriceDiff(Math.max(1, Math.round(offer.getBaseCostA().getCount() * PRICE_UP)));
		}
		tell(p, villager, Kind.PRICE);
	}

	/** {@code found} turned about: the lie told to someone asking the way. */
	static Directions.Found misdirect(Directions.Found found, Villager v) {
		double turn = Math.toRadians(LEAD_OFF_MIN + v.getRandom().nextDouble() * LEAD_OFF_SPREAD) * (v.getRandom().nextBoolean() ? 1 : -1);
		Vec3 from = v.position();
		Vec3 to = Vec3.atCenterOf(found.pos());
		double dx = to.x - from.x, dz = to.z - from.z;
		double cos = Math.cos(turn), sin = Math.sin(turn);
		BlockPos fake = BlockPos.containing(from.x + dx * cos - dz * sin, found.pos().getY(), from.z + dx * sin + dz * cos);
		return new Directions.Found(fake, null, found.name());
	}

	// ---- a guard's alibi ----------------------------------------------------------------------------------------------

	/** What a bribe costs (emeralds' worth), the reputation lost trying one on an honest guard, and how long a bought guard looks away. */
	static final int BRIBE_COST = 4, BRIBE_OFFENCE_REP = -5;
	static final long BRIBE_TICKS = 24000L;

	private static final java.util.Map<String, Long> BRIBED = new java.util.HashMap<>();

	/** Whether a guard asked where he was can be questioned (any guard of a realm that is not busy). */
	static boolean askable(LivingEntity who) {
		return who.isAlive() && Downed.isGuard(who) && !Downed.isDowned(who);
	}

	/** Whether {@code guard} has taken {@code player}'s coin and now looks away. */
	public static boolean bribed(LivingEntity guard, ServerPlayer player) {
		Long until = BRIBED.get(guard.getUUID() + "/" + player.getUUID());
		return until != null && guard.level().getGameTime() < until;
	}

	/** The menu: ask where he was, or slip him coin. */
	static void alibiMenu(ServerPlayer player, LivingEntity guard) {
		if (!askable(guard)) {
			return;
		}
		Menus.ask(player, guard, Component.translatable("talk.jaymoskingdoms.alibi.ask"), List.of(
			Menus.Choice.of(Component.translatable("talk.jaymoskingdoms.alibi.where"), p -> alibi(p, guard)),
			Menus.Choice.of(Component.translatable("talk.jaymoskingdoms.alibi.bribe", BRIBE_COST),
				com.jaymoskingdoms.money.Money.canPay(player, com.jaymoskingdoms.money.Money.worth(BRIBE_COST)), p -> bribe(p, guard))));
	}

	/** "Where were you last night?": the truth, a lie with a tell, or a confession under truth. */
	static void alibi(ServerPlayer player, LivingEntity guard) {
		if (!askable(guard) || !(guard.level() instanceof ServerLevel level)) {
			return;
		}
		long day = level.getGameTime() / 24000L;
		boolean corrupt = wouldLie(guard, Kind.ALIBI, day);
		String line;
		if (!corrupt) {
			line = "alibi.honest";
		} else if (honest(guard)) {
			line = "alibi.confess";
			Skills.grant(player, Skills.Pillar.SCHOLAR, 2);
		} else {
			line = "alibi.lie";
			tell(player, guard, Kind.ALIBI);
		}
		Speech.tell(level, guard, player, Component.translatable("liar.jaymoskingdoms." + line + "." + level.getRandom().nextInt(3)), corrupt && !honest(guard) ? Speech.WHITE : Speech.YELLOW);
	}

	/** Coin slipped to a guard: taken if he is the sort; an honest one is offended. */
	static void bribe(ServerPlayer player, LivingEntity guard) {
		if (!askable(guard) || !(guard.level() instanceof ServerLevel level)
			|| !com.jaymoskingdoms.money.Money.charge(player, com.jaymoskingdoms.money.Money.worth(BRIBE_COST))) {
			return;
		}
		long day = level.getGameTime() / 24000L;
		if (wouldLie(guard, Kind.ALIBI, day)) {
			BRIBED.put(guard.getUUID() + "/" + player.getUUID(), level.getGameTime() + BRIBE_TICKS);
			Speech.tell(level, guard, player, Component.translatable("liar.jaymoskingdoms.bribe.taken." + level.getRandom().nextInt(2)), Speech.YELLOW);
			Sparks.act(player, Sparks.Act.CHEATED, true);
		} else {
			Faction f = Theft.lawAt(level, guard.blockPosition());
			if (f != null) {
				Reputation.change(player, f, BRIBE_OFFENCE_REP);
			}
			com.jaymoskingdoms.money.Money.pay(player, com.jaymoskingdoms.money.Money.worth(BRIBE_COST));
			Speech.tell(level, guard, player, Component.translatable("liar.jaymoskingdoms.bribe.refused." + level.getRandom().nextInt(2)), Speech.RED);
		}
	}
}

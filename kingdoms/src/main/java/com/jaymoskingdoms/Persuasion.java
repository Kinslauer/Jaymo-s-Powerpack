package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Talk that can go wrong: on a villager's Interact page, <b>Talk Them Round</b> offers three moves, each tried once a day
 * per villager.
 * <ul>
 * <li><b>Persuade</b>: an honest case. Won, they warm to you and sell cheaper for the day.</li>
 * <li><b>Lie</b>: a made-up story. Won, they sell much cheaper and drop a rumour as thanks; lost, they know you lied.</li>
 * <li><b>Threaten</b>: won, they hand over coin and run in fear; lost (or won, if seen) it is assault, and they hold it
 * against you.</li>
 * </ul>
 * The chance of each is {@link #BASE} plus your Presence or Wits ({@link Presence#talk}), the villager's personality
 * ({@link #personality}), their mood, how they already feel about you, and any charm on them. A failure is remembered
 * ({@link Recollections}), and a villager who remembers you lying is harder to lie to again.
 */
public final class Persuasion {
	/** The three moves. */
	public enum Move {
		PERSUADE(0.4F, 6, -2, 0.12F), LIE(0.3F, 0, -8, 0.25F), THREATEN(0.25F, -12, -10, 0.0F);

		final float base;
		final int hearts, failHearts;
		/** The share off their prices for the rest of the day when it works. */
		final float discount;

		Move(float base, int hearts, int failHearts, float discount) {
			this.base = base;
			this.hearts = hearts;
			this.failHearts = failHearts;
			this.discount = discount;
		}

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Base chances are in {@link Move}; these lean them. Mood runs -15 to 15, hearts about -100 to 100. */
	static final float MOOD_LEAN = 0.01F, HEARTS_LEAN = 0.003F, GUARD_LEAN = -0.25F, MEMORY_LEAN = -0.2F, CHARM_LEAN = 1.0F;
	static final float MIN_CHANCE = 0.05F, MAX_CHANCE = 0.9F;
	/** What a threatened villager hands over (emeralds), and how long they run (ticks). */
	static final int PURSE_MIN = 2, PURSE_SPREAD = 4, FRIGHT_TICKS = 600;
	/** How near the villager must be (blocks). */
	static final double REACH = 6.0;

	private record Deal(UUID player, float share, long day) {
	}

	private static final Map<UUID, Deal> DEALS = new HashMap<>();
	private static final Map<String, Long> TRIED = new HashMap<>();

	private Persuasion() {
	}

	/** Whether {@code v} will hear {@code player} out: a grown villager who is not a ruler, a guard on duty or already tried today. */
	static boolean offers(ServerPlayer player, LivingEntity v) {
		return v instanceof Villager villager && !villager.isBaby() && villager.isAlive() && !Rulers.is(v) && !Downed.isDowned(v)
			&& !Charm.charmed(v) && Fear.of(v) == null && !Recollections.refuses(v, player);
	}

	/** The nature's lean on each move: what sort of person is hard or easy to sway this way. */
	static float personality(String nature, Move move) {
		return switch (nature) {
			case "friendly", "upbeat", "peaceful", "extroverted", "relaxed" -> move == Move.PERSUADE ? 0.15F : move == Move.THREATEN ? -0.05F : 0.05F;
			case "shy", "anxious" -> move == Move.THREATEN ? 0.2F : move == Move.LIE ? 0.1F : 0.0F;
			case "greedy" -> move == Move.PERSUADE ? -0.1F : move == Move.LIE ? 0.05F : 0.0F;
			case "crabby", "gloomy" -> move == Move.PERSUADE ? -0.15F : move == Move.THREATEN ? -0.1F : -0.05F;
			case "sensitive" -> move == Move.PERSUADE ? 0.1F : move == Move.THREATEN ? 0.1F : -0.1F;
			default -> 0.0F;
		};
	}

	/** The chance {@code player} wins {@code move} with {@code v} now. */
	static float chance(ServerPlayer player, Villager v, Move move) {
		float c = move.base + Presence.talk(player) + personality(McaCompat.personality(v), move) + McaCompat.mood(v) * MOOD_LEAN
			+ McaCompat.heartsOf(v, player) * HEARTS_LEAN * (move == Move.THREATEN ? -0.3F : 1.0F) + Charm.sway(v, player) * CHARM_LEAN;
		if (move == Move.LIE) {
			c += FeatsTrade.actorLie(player);
		}
		if (Downed.isGuard(v)) {
			c += GUARD_LEAN;
		}
		for (Recollections.Recollection r : Recollections.of(v, player)) {
			if (move == Move.LIE && "lied".equals(r.kind()) || move == Move.THREATEN && "threatened".equals(r.kind())) {
				c += MEMORY_LEAN;
			}
		}
		return Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, c));
	}

	/** The card: three moves. */
	static void ask(ServerPlayer player, Villager v) {
		if (!offers(player, v) || !(v.level() instanceof ServerLevel level)) {
			return;
		}
		long day = level.getGameTime() / 24000L;
		List<Menus.Choice> choices = new java.util.ArrayList<>();
		for (Move m : Move.values()) {
			boolean fresh = !TRIED.containsKey(key(player, v, m)) || TRIED.get(key(player, v, m)) < day;
			choices.add(Menus.Choice.of(Component.translatable("talk.jaymoskingdoms.round." + m.key()), fresh, p -> attempt(p, v, m)));
		}
		Menus.ask(player, v, Component.translatable("talk.jaymoskingdoms.round.ask"), choices);
	}

	private static String key(ServerPlayer player, Villager v, Move move) {
		return v.getUUID() + "/" + player.getUUID() + "/" + move;
	}

	/** {@code player} tries {@code move} on {@code v}: won or lost at the chance, with the world acting on it. */
	static void attempt(ServerPlayer player, Villager v, Move move) {
		if (!offers(player, v) || !(v.level() instanceof ServerLevel level)) {
			return;
		}
		long day = level.getGameTime() / 24000L;
		Long last = TRIED.get(key(player, v, move));
		if (last != null && last >= day) {
			return;
		}
		TRIED.put(key(player, v, move), day);
		boolean won = level.getRandom().nextFloat() < chance(player, v, move);
		String say = "talk.jaymoskingdoms.round." + move.key() + (won ? ".won." : ".lost.") + level.getRandom().nextInt(3);
		Speech.tell(level, v, player, Component.translatable(say), won ? Speech.GOLD : Speech.RED);
		Component detail = Component.translatable("talk.jaymoskingdoms.round." + move.key() + ".verb");
		if (won) {
			won(level, player, v, move, day, detail);
		} else {
			lost(level, player, v, move, detail);
		}
	}

	private static void won(ServerLevel level, ServerPlayer player, Villager v, Move move, long day, Component detail) {
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		if (move.discount > 0.0F) {
			DEALS.put(v.getUUID(), new Deal(player.getUUID(), move.discount, day));
		}
		switch (move) {
			case PERSUADE -> {
				McaCompat.hearts(v, player, move.hearts);
				Recollections.remember(v, player, Recollections.Kind.KIND, detail);
				Sparks.act(player, Sparks.Act.PERSUADED, false);
			}
			case LIE -> {
				Rumours.tell(level, v, player);
				Sparks.act(player, Sparks.Act.LIED, false);
			}
			case THREATEN -> {
				Fear.frighten(v, player, FRIGHT_TICKS);
				com.jaymoskingdoms.money.Money.pay(player, com.jaymoskingdoms.money.Money.worth(PURSE_MIN + level.getRandom().nextInt(PURSE_SPREAD + 1)));
				McaCompat.hearts(v, player, move.hearts);
				Recollections.remember(v, player, Recollections.Kind.THREATENED, detail);
				assault(level, player, v);
				Sparks.act(player, Sparks.Act.THREATENED, true);
			}
		}
		Skills.grant(player, Skills.Pillar.STEALTH, 1);
	}

	private static void lost(ServerLevel level, ServerPlayer player, Villager v, Move move, Component detail) {
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
		McaCompat.hearts(v, player, move.failHearts);
		switch (move) {
			case PERSUADE -> Recollections.remember(v, player, Recollections.Kind.REBUFFED, detail);
			case LIE -> {
				Recollections.remember(v, player, Recollections.Kind.LIED, detail);
				Sparks.act(player, Sparks.Act.LIED, true);
			}
			case THREATEN -> {
				Recollections.remember(v, player, Recollections.Kind.THREATENED, detail);
				assault(level, player, v);
				Sparks.act(player, Sparks.Act.THREATENED, true);
			}
		}
	}

	private static void assault(ServerLevel level, ServerPlayer player, Villager v) {
		Faction f = Theft.lawAt(level, v.blockPosition());
		if (f != null && f.type().isRealm()) {
			Theft.commit(player, level, f, Theft.Crime.ASSAULT, v.blockPosition(), null, true);
		}
	}

	/** A trader won over today sells cheaper (called as prices are worked out). */
	public static void prices(Villager villager, Player player) {
		Deal d = DEALS.get(villager.getUUID());
		if (d == null || !d.player().equals(player.getUUID()) || d.day() != villager.level().getGameTime() / 24000L) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			offer.addToSpecialPriceDiff(-Math.max(1, Math.round(offer.getBaseCostA().getCount() * d.share())));
		}
	}
}

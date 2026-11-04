package com.jaymoskingdoms;

import com.jaymoskingdoms.money.MoneyItems;
import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.money.PurseContents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.498) Andy, on the traders who still deal in emeralds ({@link Currencies#keeper}): "they must speak about this
 * and answer you if asked". (0.7.498) Coins are the culture coins now ("Combine: culture coins replace emeralds"): coin folk
 * say which coin their town uses and why (its land, or the trading hub whose coin it took), a foreign coin is sent to the
 * money changer, and the changer quotes the emerald in the town's coin. So:
 * <ul>
 * <li>Interact's Ask about Money ({@link InteractActions#MONEY}): one who keeps emeralds says so; the money changer gives his
 * rates; anyone else says it's coin and, if they know one, points you to a neighbour who still takes emeralds, or to the
 * money changer.</li>
 * <li>Ask the Way has Who Takes Emeralds and A Money Changer ({@link Directions}).</li>
 * <li>They bring it up themselves: one who keeps emeralds says so as you open their trades, and calls out when they
 * see emeralds in your hand; a coin trader tells you they won't take emeralds when you come to trade with emeralds and
 * no coin. Wandering traders too. Each says it at most once every {@link #REMARK_TICKS} to each player.</li>
 * <li>MCA's chat AI knows which they are ({@link #prompt}).</li>
 * <li>Andy: "people who use coin generally look down upon emerald traders as outdated luddites". Coin folk's lines sneer
 * at them, and the keepers know it.</li>
 * </ul>
 */
public final class MoneyTalk {
	/** How far around them they know their neighbours' trade (blocks). */
	static final double FIND_RANGE = 128.0;
	/** How near, and in sight, they must be to see emeralds in your hand (blocks); how often that's looked for (ticks). */
	static final double SPOT_RANGE = 6.0;
	static final int SPOT_EVERY = 40;
	/** The least time between remarks of theirs about money to the same player (ticks). */
	static final long REMARK_TICKS = 20L * 60 * 5;
	/** Lines per kind. */
	static final int LINES = 3;

	/** When each trader last spoke of money to each player (game time). */
	private static final Map<Entity, Map<UUID, Long>> REMARKED = new WeakHashMap<>();

	private MoneyTalk() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MoneyTalk::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> REMARKED.clear());
	}

	// ---- who deals in what ----------------------------------------------------------------------------------------------

	/** {@code e} is a grown trader who still deals in emeralds (never the money changer, who deals in both). */
	public static boolean keeper(@Nullable Entity e) {
		return e instanceof AbstractVillager t && t.isAlive() && Currencies.keeper(t);
	}

	/** {@code e} is a money changer. */
	public static boolean changer(@Nullable Entity e) {
		return e instanceof Villager v && Currencies.changer(v);
	}

	/** {@code t} has a trade to deal in (a wandering trader, or a villager with a job that trades). */
	static boolean trades(AbstractVillager t) {
		return Currencies.trades(t);
	}

	/** (0.7.498) The coin {@code t} trades in: its settlement's, or the land round it, else the realm silver (never the emerald or the bit). */
	static Currency coinOf(AbstractVillager t) {
		if (!(t.level() instanceof ServerLevel level)) {
			return Currency.SILVER;
		}
		Faction f = Quests.settlement(level, t);
		Currency c = f != null ? Currencies.of(level.getServer(), f) : Currencies.at(level, t.blockPosition());
		return c == Currency.EMERALD || c == Currency.BIT ? Currency.SILVER : c;
	}

	/** (0.7.498) The name of the trading hub whose coin {@code t}'s town took, if it took one; else null. */
	static @Nullable String hubOf(AbstractVillager t) {
		return t.level() instanceof ServerLevel level && Quests.settlement(level, t) instanceof Faction f ? Currencies.hubName(level.getServer(), f) : null;
	}

	/** The nearest grown trader to {@code v} (themselves first) that {@code which} picks, or null. */
	static @Nullable AbstractVillager nearest(ServerLevel level, Entity v, Predicate<Entity> which) {
		AABB box = v.getBoundingBox().inflate(FIND_RANGE, 32.0, FIND_RANGE);
		List<AbstractVillager> folk = level.getEntitiesOfClass(AbstractVillager.class, box,
			o -> o.isAlive() && !o.isBaby() && !Downed.isDowned(o) && which.test(o));
		AbstractVillager best = null;
		double bestD = Double.MAX_VALUE;
		for (AbstractVillager o : folk) {
			double d = o == v ? -1.0 : o.distanceToSqr(v);
			if (d < bestD) {
				best = o;
				bestD = d;
			}
		}
		return best;
	}

	// ---- asked ----------------------------------------------------------------------------------------------------------

	/** {@code player} pressed Ask about Money on {@code v}: what they say (in MCA's chat). */
	static Component answer(ServerPlayer player, Villager v) {
		RandomSource random = v.getRandom();
		if (McaBridge.heartsOf(v, player) < Directions.REFUSE_HEARTS) {
			return Component.translatable(line("refuse", random));
		}
		if (v.isBaby()) {
			return Component.translatable(line("child", random));
		}
		if (changer(v)) {
			return Component.translatable(line("changer", random), emeraldQuote(coinOf(v)));
		}
		if (keeper(v)) {
			return Component.translatable(line("keeper", random));
		}
		// (0.7.498) coin folk say which coin the town uses and why, then where to take emeralds or foreign coin
		Component here = here(v, random);
		if (v.level() instanceof ServerLevel level) {
			AbstractVillager other = nearest(level, v, o -> o != v && keeper(o));
			if (other != null) {
				return here.copy().append(" ").append(told(v, other, "coin_keeper", random));
			}
			AbstractVillager changer = nearest(level, v, o -> o != v && changer(o));
			if (changer != null) {
				return here.copy().append(" ").append(told(v, changer, "coin_changer", random));
			}
		}
		return here;
	}

	/** (0.7.498) "Realm Silver here, as is the custom of the land" or "... the coin of Kingdom X, whose merchants..." */
	private static Component here(Villager v, RandomSource random) {
		Component coin = Component.translatable(coinOf(v).key());
		String hub = hubOf(v);
		return hub == null ? Component.translatable(line("coin", random), coin) : Component.translatable(line("coin_hub", random), coin, hub);
	}

	/** (0.7.498) What a changer pays for an emerald in {@code local} coin, about, as shown ("1 Realm Silver, 4 Copper Bit"). */
	private static Component emeraldQuote(Currency local) {
		return Currencies.amount(Currencies.pay(local, (long)(Currency.EMERALD.base * Currencies.EMERALD_BUY)));
	}

	/** "... %1$s still takes emeralds, %2$s to the %3$s." (or the changer who changes them) */
	private static Component told(Villager v, AbstractVillager who, String kind, RandomSource random) {
		double far = Math.sqrt(who.distanceToSqr(v));
		return Component.translatable(line(kind, random), who.getName(), Directions.distance(far), Directions.heading(v.position(), who.position()));
	}

	/** (MCA chat AI) What {@code villager} knows of money, for their prompt; empty for a child. */
	static String prompt(Villager villager) {
		if (villager.isBaby()) {
			return "";
		}
		Currency local = coinOf(villager);
		String hub = hubOf(villager);
		StringBuilder sb = new StringBuilder("Money: coin is the money now, not emeralds. Your town uses ").append(words(local));
		sb.append(hub == null ? ", the coin of this land" : ", the coin of " + hub + ", the rich trading hub whose merchants your town follows");
		sb.append(". Copper bits are the small change every town takes. Other realms' coins are foreign here: only a money changer swaps them cleanly.");
		if (changer(villager)) {
			sb.append(" You are the money changer: you swap one realm's coin for another's at the going rate less a small cut, change copper bits for ")
				.append(words(local)).append(" at par, and buy emeralds (and diamonds) as gems for ").append(words(local))
				.append(", about ").append(quoteWords(local)).append(" for an emerald.");
		} else if (keeper(villager)) {
			sb.append(" But you still trade only in emeralds, as folk always did, and you distrust the new coin. Coin folk look down on you as an outdated fool; you know it and don't care. Say so if money comes up.");
		} else {
			sb.append(" You price your goods in coin and don't take emeralds; they're only gems now. Like most coin folk, you look down on the few who still trade in emeralds as backward, stubborn relics of the last age.");
			if (villager.level() instanceof ServerLevel level) {
				AbstractVillager other = nearest(level, villager, o -> o != villager && keeper(o));
				if (other != null) {
					sb.append(" ").append(other.getName().getString()).append(" nearby still trades in emeralds, the old fool.");
				}
				AbstractVillager changer = nearest(level, villager, o -> o != villager && changer(o));
				if (changer != null) {
					sb.append(" ").append(changer.getName().getString()).append(" nearby is the money changer.");
				}
			}
		}
		return sb.toString();
	}

	/** (0.7.498) {@code c} as plain English, for the chat prompt (the server has no mod lang to name an item with). */
	private static String words(Currency c) {
		return switch (c) {
			case SILVER -> "realm silver";
			case HACKSILVER -> "hacksilver";
			case DINAR -> "gold dinars";
			case SHELLS -> "strings of shells";
			case MON -> "mon";
			case BENT -> "bent copper";
			case GOLD -> "gold";
			case BIT -> "copper bits";
			case EMERALD -> "emeralds";
		};
	}

	/** (0.7.498) What a changer pays for an emerald in {@code local}, in plain English ("1 realm silver and 4 copper bits"). */
	private static String quoteWords(Currency local) {
		Currencies.Pay p = Currencies.pay(local, (long)(Currency.EMERALD.base * Currencies.EMERALD_BUY));
		String out = p.coins() > 0L ? p.coins() + " " + words(p.coin()) : "";
		return p.bits() <= 0L ? out : (out.isEmpty() ? "" : out + " and ") + p.bits() + " copper bits";
	}

	// ---- said unasked ---------------------------------------------------------------------------------------------------

	/** (mixin on AbstractVillager.setTradingPlayer) {@code player} opens {@code trader}'s trades. */
	public static void tradeOpened(AbstractVillager trader, ServerPlayer player) {
		if (!(trader.level() instanceof ServerLevel level) || trader.isBaby() || changer(trader)) {
			return;
		}
		if (keeper(trader)) {
			remark(level, trader, player, "trade");
		} else if (trades(trader) && player.getInventory().countItem(Items.EMERALD) > 0 && Money.count(player) <= 0) {
			remark(level, trader, player, "no_emeralds");
		} else if (trades(trader)) {
			foreign(level, trader, player);
		}
	}

	/**
	 * (0.7.498) A coin trader, opened to a player who carries another realm's coin and none of the town's (a trade screen
	 * takes the exact coin only): "that's not our coin, see the money changer", and where he is if there is one near.
	 */
	private static void foreign(ServerLevel level, AbstractVillager trader, ServerPlayer player) {
		Currency local = coinOf(trader);
		if (!local.coin() || carried(player, local) > 0) {
			return;
		}
		boolean foreign = false;
		for (Currency c : Currency.values()) {
			foreign |= c.coin() && c != local && carried(player, c) > 0;
		}
		if (!foreign) {
			return;
		}
		AbstractVillager changer = nearest(level, trader, o -> o != trader && changer(o));
		if (changer == null) {
			remark(level, trader, player, "foreign_far", Component.translatable(local.key()));
			return;
		}
		double far = Math.sqrt(changer.distanceToSqr(trader));
		remark(level, trader, player, "foreign", Component.translatable(local.key()), changer.getName(), Directions.distance(far),
			Directions.heading(trader.position(), changer.position()));
	}

	/** How many of {@code c} {@code player} has loose, in a purse in the pack, or in the worn one. */
	private static int carried(ServerPlayer player, Currency c) {
		Inventory inv = player.getInventory();
		ItemStack belt = PowersCompat.belt(player);
		int n = inv.countItem(c.item()) + (belt.is(MoneyItems.COIN_PURSE) ? PurseContents.of(belt).count(c) : 0);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			n += stack.is(MoneyItems.COIN_PURSE) && stack != belt ? PurseContents.of(stack).count(c) : 0;
		}
		return n;
	}

	/** Every {@link #SPOT_EVERY} ticks: one who keeps emeralds, near and in sight of emeralds in your hand, calls out. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SPOT_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !(player.level() instanceof ServerLevel level)
				|| !player.getMainHandItem().is(Items.EMERALD) && !player.getOffhandItem().is(Items.EMERALD)) {
				continue;
			}
			List<AbstractVillager> near = level.getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(SPOT_RANGE),
				t -> keeper(t) && t.getTradingPlayer() == null && !t.isSleeping() && !Downed.isDowned(t) && t.hasLineOfSight(player)
					&& t.distanceToSqr(player) <= SPOT_RANGE * SPOT_RANGE);
			for (AbstractVillager t : near) {
				if (remark(level, t, player, "spot")) {
					break;
				}
			}
		}
	}

	/** {@code t} says a {@code kind} line to {@code player} unless they spoke of money to them lately; whether they did. */
	private static boolean remark(ServerLevel level, AbstractVillager t, ServerPlayer player, String kind, Object... args) {
		long now = level.getGameTime();
		Map<UUID, Long> said = REMARKED.computeIfAbsent(t, k -> new HashMap<>());
		Long last = said.get(player.getUUID());
		if (last != null && now - last < REMARK_TICKS) {
			return false;
		}
		said.put(player.getUUID(), now);
		t.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Speech.sayTo(level, t, player, Component.translatable(line(kind, level.getRandom()), args), Speech.WHITE);
		return true;
	}

	private static String line(String kind, RandomSource random) {
		return "money.jaymoskingdoms." + kind + "." + random.nextInt(LINES);
	}
}

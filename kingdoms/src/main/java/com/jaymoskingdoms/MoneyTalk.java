package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Changer;
import com.jaymoskingdoms.money.Coinage;
import com.jaymoskingdoms.money.Money;
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
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.487) Andy, on the traders who still deal in emeralds ({@link Coinage#keepsEmeralds}): "they must speak about this
 * and answer you if asked". So:
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
		return e instanceof AbstractVillager t && t.isAlive() && !t.isBaby() && trades(t) && !changer(t) && Coinage.keepsEmeralds(t);
	}

	/** {@code e} is a money changer. */
	public static boolean changer(@Nullable Entity e) {
		return e instanceof Villager v && Changer.PROFESSION != null && Changer.is(v);
	}

	/** {@code t} has a trade to deal in (a wandering trader, or a villager with a job that trades). */
	static boolean trades(AbstractVillager t) {
		if (t instanceof WanderingTrader) {
			return true;
		}
		// (a pedlar's and Ignis Vael's wares are always priced in coin; MCA's guards, archers and our workers don't trade)
		return t instanceof Villager v && !v.getVillagerData().profession().is(VillagerProfession.NONE)
			&& !v.getVillagerData().profession().is(VillagerProfession.NITWIT) && !(McaCompat.LOADED && McaBridge.cannotTrade(v))
			&& !Pedlars.pedlar(v) && !com.jaymoskingdoms.nether.CinderForge.ignis(v);
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
			return Component.translatable(line("changer", random), Changer.EMERALD_CP);
		}
		if (keeper(v)) {
			return Component.translatable(line("keeper", random));
		}
		if (v.level() instanceof ServerLevel level) {
			AbstractVillager other = nearest(level, v, o -> o != v && keeper(o));
			if (other != null) {
				return told(v, other, "coin_keeper", random);
			}
			AbstractVillager changer = nearest(level, v, o -> o != v && changer(o));
			if (changer != null) {
				return told(v, changer, "coin_changer", random);
			}
		}
		return Component.translatable(line("coin", random));
	}

	/** "... %1$s still takes emeralds, %2$s to the %3$s." */
	private static Component told(Villager v, AbstractVillager who, String kind, RandomSource random) {
		double far = Math.sqrt(who.distanceToSqr(v));
		return Component.translatable(line(kind, random), who.getName(), Directions.distance(far), Directions.heading(v.position(), who.position()));
	}

	/** (MCA chat AI) What {@code villager} knows of money, for their prompt; empty for a child. */
	static String prompt(Villager villager) {
		if (villager.isBaby()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("Money: coins are the money now, copper pieces, silver (ten copper) and gold (ten silver).");
		if (changer(villager)) {
			sb.append(" You are the money changer: you change coins for free and buy emeralds at ").append(Changer.EMERALD_CP)
				.append(" copper each, as gems.");
		} else if (keeper(villager)) {
			sb.append(" But you still trade only in emeralds, as folk always did, and you distrust the new coin. Coin folk look down on you as an outdated fool; you know it and don't care. Say so if money comes up.");
		} else {
			sb.append(" You price your goods in coin and don't take emeralds; they're only gems now. Like most coin folk, you look down on the few who still trade in emeralds as backward, stubborn relics of the last age.");
			if (villager.level() instanceof ServerLevel level) {
				AbstractVillager other = nearest(level, villager, o -> o != villager && keeper(o));
				if (other != null) {
					sb.append(" ").append(other.getName().getString()).append(" nearby still trades in emeralds, the old fool.");
				}
			}
		}
		return sb.toString();
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
		}
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
	private static boolean remark(ServerLevel level, AbstractVillager t, ServerPlayer player, String kind) {
		long now = level.getGameTime();
		Map<UUID, Long> said = REMARKED.computeIfAbsent(t, k -> new HashMap<>());
		Long last = said.get(player.getUUID());
		if (last != null && now - last < REMARK_TICKS) {
			return false;
		}
		said.put(player.getUUID(), now);
		t.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Speech.sayTo(level, t, player, Component.translatable(line(kind, level.getRandom())), Speech.WHITE);
		return true;
	}

	private static String line(String kind, RandomSource random) {
		return "money.jaymoskingdoms." + kind + "." + random.nextInt(LINES);
	}
}

package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * Villagers who know the weather (docs/Climate-Plan.md, track F: vl-weather). The weather-wise are the <b>cartographers</b>: they
 * read the sky and the stars to draw their charts, so they know the turn of the year before anyone. They
 * <ul>
 * <li><b>sell</b> what the weather asks of you, through the villager price hook ({@link #offers}): Kitchen's thermometer and
 * waterskins (leather, iron), the almanac, and the land's own climate clothes (a linen headscarf in a hot land, a wool cap
 * in a cold one);</li>
 * <li><b>tell you which season is coming</b>: ask them about the weather (the Ask spoke of the talk wheel, {@link
 * InteractActions#WEATHER}), or, in a season's last days, one who sees you walks over and tells you unasked ({@link #tick}).</li>
 * </ul>
 * Said in a speech bubble, never chat; a few variants for each of the four seasons coming and for three tones (soft, plain, hard,
 * by temperament, {@link SharedHistory}). One who doesn't share your tongue ({@link Languages}) says nothing you'd follow. Needs
 * Kitchen's seasons; without them nobody has anything to tell.
 */
public final class WeatherWise {
	/** The last this-many days of a season are when a cartographer walks over unasked. */
	static final int WARN_DAYS = 3;
	/** The seasons' length in days (Kitchen's {@code Seasons.DAYS_PER_SEASON}). */
	static final int SEASON_DAYS = 21;
	/** Looked for this often (ticks), and how far a cartographer will come from (blocks). */
	static final int LOOK = 100;
	static final double RANGE = 16.0;
	/** Hearts below which they won't say. */
	static final int SOUR = -15;
	/** Variants of each line, and of the season-by-season lines. */
	static final int LINES = 3;
	/** Emeralds each wares sells for, with how many sales, the experience and the price rise. */
	static final int THERMOMETER = 4, SKIN = 3, IRON_SKIN = 6, ALMANAC = 8, SCARF = 4, CAP = 3, USES = 4, XP = 5;
	static final float PRICE_MULT = 0.05F;
	/** Day time (ticks) before which they walk about telling: not at night. */
	static final long NIGHT = 12000L;

	private static final String[] SEASONS = {"spring", "summer", "autumn", "winter"};

	/** Player → the season (year * 4 + season) whose end they've been told of. */
	private static final Map<UUID, Integer> TOLD = new HashMap<>();

	private WeatherWise() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WeatherWise::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TOLD.clear());
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOLD.remove(handler.getPlayer().getUUID()));
	}

	/** A cartographer: the weather-wise. */
	public static boolean is(net.minecraft.world.entity.LivingEntity e) {
		return e instanceof Villager v && !v.isBaby() && BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value())
			.getPath().equals("cartographer");
	}

	// ---- the wares -------------------------------------------------------------------------------------------------------

	/** (from {@code VillagerPriceMixin}) a cartographer lays out the weather's wares. */
	public static void offers(Villager v, Player player) {
		if (!is(v) || !(v.level() instanceof ServerLevel level)) {
			return;
		}
		MerchantOffers offers = v.getOffers();
		offer(offers, kitchen("thermometer"), THERMOMETER);
		offer(offers, kitchen("waterskin_leather"), SKIN);
		offer(offers, kitchen("waterskin_iron"), IRON_SKIN);
		offer(offers, com.jaymoskingdoms.disasters.Disasters.ALMANAC, ALMANAC);
		Faction f = Quests.settlement(level, v);
		Climate land = Climate.at(level, f != null ? f.seat() : v.blockPosition());
		if (ClimateWear.hot(level, f != null ? f.seat() : v.blockPosition())) {
			offer(offers, ClimateWear.LINEN_HEADSCARF, SCARF);
		} else if (land == Climate.SNOWY || land == Climate.TAIGA) {
			offer(offers, WoolClothes.CAP_ITEM, CAP);
		}
	}

	/** Kitchen's item by id, or null without it. */
	private static @Nullable Item kitchen(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(KitchenCompat.MOD, id)).orElse(null);
	}

	private static void offer(MerchantOffers offers, @Nullable Item item, int price) {
		if (item != null && item != Items.AIR && offers.stream().noneMatch(o -> o.getResult().is(item))) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), new ItemStack(item), USES, XP, PRICE_MULT));
		}
	}

	// ---- the season coming -----------------------------------------------------------------------------------------------

	/** The day of the season now (0 to 20), or -1 without Kitchen's seasons. */
	static int dayInSeason(ServerLevel level) {
		int day = KitchenCompat.dayOfYear(level);
		return day < 0 ? -1 : Math.floorMod(day, SEASON_DAYS);
	}

	/** The season after {@code now}'s ("spring" to "summer" ... "winter" to "spring"), or null. */
	static @Nullable String next(@Nullable String now) {
		for (int i = 0; i < SEASONS.length; i++) {
			if (SEASONS[i].equals(now)) {
				return SEASONS[(i + 1) % SEASONS.length];
			}
		}
		return null;
	}

	/** Server → what {@code mob} says of the season coming, for {@code kind} ("ask" or "warn"); null if there's nothing to say. */
	private static @Nullable Component line(ServerLevel level, Mob mob, ServerPlayer player, String kind) {
		String now = KitchenCompat.season(level);
		String next = next(now);
		int day = dayInSeason(level);
		if (next == null || day < 0) {
			return null;
		}
		int left = SEASON_DAYS - day;
		String key = "weather.jaymoskingdoms." + kind + "." + next + "." + SharedHistory.toneNoGreed(mob).key() + "." + mob.getRandom().nextInt(LINES);
		return Component.translatable(key, Recollections.nameTo(player, mob), left, Component.translatable("weather.jaymoskingdoms.season." + now),
			Component.translatable("weather.jaymoskingdoms.season." + next));
	}

	private static void say(ServerLevel level, Mob mob, ServerPlayer player, String kind) {
		Component words = Languages.understands(player, mob) ? line(level, mob, player, kind) : Component.translatable("weather.jaymoskingdoms.gesture");
		if (words == null) {
			return;
		}
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		if (Languages.understands(player, mob)) {
			Speech.tell(level, mob, player, words, Speech.WHITE);
		} else {
			Speech.sayPlainly(level, mob, player, words, Speech.WHITE);
		}
	}

	/**
	 * (the Ask About the Weather spoke, {@link InteractActions}) {@code v} tells {@code player} which season is coming and how
	 * many days it is off. One who bears you real ill will says only that they've nothing to tell you.
	 */
	static void ask(ServerPlayer player, Villager v) {
		if (!(v.level() instanceof ServerLevel level)) {
			return;
		}
		if (McaCompat.heartsOf(v, player) < SOUR) {
			Speech.tell(level, v, player, Component.translatable("weather.jaymoskingdoms.refuse." + v.getRandom().nextInt(LINES)), Speech.WHITE);
			return;
		}
		say(level, v, player, "ask");
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.7F, 1.0F);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || player.isSpectator() || Downed.isDowned(player) || ClimateVisits.busy(player.getUUID())
				|| Approaches.engaged(player.getUUID())) {
				continue;
			}
			int day = dayInSeason(level);
			int year = KitchenCompat.dayOfYear(level);
			if (day < SEASON_DAYS - WARN_DAYS || year < 0 || Math.floorMod(level.getOverworldClockTime(), 24000L) >= NIGHT) {
				continue;
			}
			// the season counted from the world's start (the year's own day wraps), by the day it began
			int season = (int)(Math.floorDiv(level.getOverworldClockTime(), 24000L) - day);
			if (TOLD.getOrDefault(player.getUUID(), Integer.MIN_VALUE) == season) {
				continue;
			}
			Mob best = null;
			double bestD = Double.MAX_VALUE;
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(RANGE), x -> is(x) && ClimateVisits.free(x)
				&& x.hasLineOfSight(player) && Languages.understands(player, x) && McaCompat.heartsOf(x, player) >= 0 && willing(x))) {
				double d = v.distanceToSqr(player);
				if (d < bestD) {
					bestD = d;
					best = v;
				}
			}
			if (best != null) {
				ClimateVisits.start(player, best, new ClimateVisits.Script() {
					@Override
					public @Nullable BlockPos reach(ServerLevel l, ServerPlayer p, Mob mob) {
						say(l, mob, p, "warn");
						TOLD.put(p.getUUID(), season);
						return null;
					}
				});
			}
		}
	}

	/** Whether {@code v} would bother: the spiteful and the hot-headed keep the news to themselves. */
	private static boolean willing(Villager v) {
		Temperaments.Temperament t = Temperaments.of(v);
		return t != Temperaments.Temperament.SPITEFUL && t != Temperaments.Temperament.HOTHEADED;
	}
}

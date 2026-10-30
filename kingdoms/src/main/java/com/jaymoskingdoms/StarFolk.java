package com.jaymoskingdoms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.382, Andy's SevTech picks: "Reading the sky") Star charts, in Kingdoms' hands. Jaymo's Powers' pod flies only to worlds
 * whose chart the pilot has read. The floating islands' hermits ({@link Hermits}) and the stargazers (about one villager in
 * {@link GiftedFolk#STARGAZER_ODDS}, tagged {@link #STARGAZER}) sell the charts a player hasn't read, for emeralds: "Ask about
 * the stars" in MCA's menu ({@link #ANSWER}) opens a talk screen ({@link ZFighterFolk#ask}) with a few words about the sky and
 * the charts for sale, cheapest trip first (a hermit sells {@link Hermits#CHARTS_OFFERED}, a stargazer {@link #STARGAZER_OFFERED}).
 * A stargazer's words also go into the player's next Tidings ({@link Post#tidings}).
 * <p>
 * <b>Lights in the sky</b> ({@link #lights}): before a player has flown a pod of his own, a band of raiders' pods or a patrolman's
 * pod coming down near a town he is near is written up in his Tidings as a light that fell, or a stranger seen at dusk. Powers is
 * read by reflection only ({@code SkyApi}); without it nobody sells a chart and no light is seen.
 */
public final class StarFolk {
	/** MCA's {@code main} menu answer (our dialogue data): "Ask about the stars". */
	public static final String ANSWER = "jaymoskingdoms_stars";
	/** A stargazing villager's tag (rolled once, in {@link GiftedFolk#stargaze}). */
	public static final String STARGAZER = "jaymoskingdoms_stargazer";
	/** How many charts a stargazer has for sale at once (a hermit's is {@link Hermits#CHARTS_OFFERED}). */
	public static final int STARGAZER_OFFERED = 2;
	/** A chart's price in emeralds: the base, plus this much for each place down the cost order (the Pale Moon is first). */
	static final int PRICE_BASE = 6, PRICE_STEP = 4;
	/** Variants of each line in the lang file ({@code talk.jaymoskingdoms.stars.<hermit|stargazer>.<n>}, {@code news.jaymoskingdoms.sky.*}). */
	static final int TALK_LINES = 4, NEWS_LINES = 4;
	/** A stargazer's words go into a player's Tidings at most once in this long (ticks: a day). */
	static final long TELL_TICKS = 24000L;
	/** How often (ticks) the sky is watched for pods coming down; how near a town (blocks, from its seat) a pod is "near", and how near the player. */
	static final int WATCH_TICKS = 100;
	static final double POD_NEAR = 96.0, TOWN_NEAR = 160.0;
	/** Pods seen per player are remembered up to this many. */
	static final int REMEMBERED = 64;
	/** Powers' tags on pods: a raiders' band, and a patrolman's visit. */
	static final String RAIDER_TAG = "jaymospowers.raider", PATROL_TAG = "jaymospowers.patrol_pod";
	/** A talk's colour (the stars' pale blue). */
	static final int COLOR = 0xB8C8FF;

	/** Who was told what, and when (game time): "villager/player" → when. */
	private static final Map<String, Long> TOLD = new HashMap<>();
	/** The pods each player has been told of. */
	private static final Map<UUID, Set<UUID>> SEEN = new HashMap<>();

	private static boolean looked;
	private static @Nullable MethodHandle unread, rank, sell, name, flights;

	private StarFolk() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % WATCH_TICKS == 0) {
				lights(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TOLD.clear();
			SEEN.clear();
		});
	}

	// ---- Powers, by reflection ----------------------------------------------------------------------------------------

	private static boolean ready() {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					Class<?> api = Class.forName("com.jaymospowers.power.zfighter.sky.SkyApi");
					MethodHandles.Lookup lookup = MethodHandles.publicLookup();
					unread = lookup.findStatic(api, "unread", MethodType.methodType(List.class, ServerPlayer.class, int.class));
					rank = lookup.findStatic(api, "rank", MethodType.methodType(int.class, int.class));
					sell = lookup.findStatic(api, "sell", MethodType.methodType(boolean.class, ServerPlayer.class, int.class));
					name = lookup.findStatic(api, "name", MethodType.methodType(Component.class, int.class));
					flights = lookup.findStatic(api, "flights", MethodType.methodType(int.class, ServerPlayer.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' star charts (older Powers?)", e);
					unread = null;
				}
			}
		}
		return unread != null;
	}

	@SuppressWarnings("unchecked")
	private static List<Integer> unread(ServerPlayer player, int count) {
		try {
			return ready() ? (List<Integer>)unread.invoke(player, count) : List.of();
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't read the star charts of {}", player.getName().getString(), e);
			return List.of();
		}
	}

	private static int rank(int world) {
		try {
			return ready() ? (int)rank.invoke(world) : 0;
		} catch (Throwable e) {
			return 0;
		}
	}

	private static boolean sell(ServerPlayer player, int world) {
		try {
			return ready() && (boolean)sell.invoke(player, world);
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't sell a star chart to {}", player.getName().getString(), e);
			return false;
		}
	}

	private static Component name(int world) {
		try {
			return ready() ? (Component)name.invoke(world) : Component.empty();
		} catch (Throwable e) {
			return Component.empty();
		}
	}

	private static int flights(ServerPlayer player) {
		try {
			return ready() ? (int)flights.invoke(player) : Integer.MAX_VALUE;
		} catch (Throwable e) {
			return Integer.MAX_VALUE;
		}
	}

	// ---- selling ------------------------------------------------------------------------------------------------------

	/** A chart's price in emeralds. */
	static int price(int world) {
		return PRICE_BASE + PRICE_STEP * rank(world);
	}

	/** Whether {@code e} is one who talks of the stars: a floating island's hermit, or a stargazer. */
	public static boolean is(@Nullable Entity e) {
		return e != null && (Hermits.is(e) || e.entityTags().contains(STARGAZER));
	}

	/** "Ask about the stars" shows only on one who talks of them, and only with Powers' charts to talk of. */
	public static boolean canAsk(@Nullable Entity e) {
		return is(e) && ready();
	}

	private static int emeralds(ServerPlayer player) {
		int count = 0;
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(Items.EMERALD)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void take(ServerPlayer player, int count) {
		int left = count;
		for (ItemStack stack : player.getInventory()) {
			if (left > 0 && stack.is(Items.EMERALD)) {
				int use = Math.min(left, stack.getCount());
				stack.shrink(use);
				left -= use;
			}
		}
	}

	/** "Ask about the stars": a few words about the sky, and the charts for sale on the talk screen. */
	public static void talk(ServerPlayer player, LivingEntity speaker) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		boolean hermit = Hermits.is(speaker);
		List<Integer> worlds = unread(player, hermit ? Hermits.CHARTS_OFFERED : STARGAZER_OFFERED);
		String kind = hermit ? "hermit" : "stargazer";
		Component words = Component.translatable("talk.jaymoskingdoms.stars." + kind + "." + level.getRandom().nextInt(TALK_LINES));
		if (!hermit) {
			tell(level, speaker, player);
		}
		if (worlds.isEmpty()) {
			ZFighterFolk.ask(player, speaker, Component.translatable("talk.jaymoskingdoms.stars.none"), List.of(Component.translatable("talk.jaymoskingdoms.stars.leave")),
				List.of(true), pick -> {
				});
			return;
		}
		int have = emeralds(player);
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (int world : worlds) {
			options.add(Component.translatable("talk.jaymoskingdoms.stars.buy", name(world), price(world)));
			enabled.add(have >= price(world));
		}
		options.add(Component.translatable("talk.jaymoskingdoms.stars.leave"));
		enabled.add(true);
		ZFighterFolk.ask(player, speaker, words, options, enabled, pick -> {
			if (pick < 0 || pick >= worlds.size()) {
				return;
			}
			int world = worlds.get(pick);
			if (emeralds(player) < price(world)) {
				Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.stars.short"), COLOR);
				return;
			}
			if (!sell(player, world)) {
				Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.stars.known"), COLOR);
				return;
			}
			take(player, price(world));
			level.playSound(null, speaker.getX(), speaker.getY(), speaker.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
			Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.stars.sold", name(world)), COLOR);
		});
	}

	/** A stargazer's words about the sky go into {@code player}'s next Tidings (once a day for each of them). */
	private static void tell(ServerLevel level, LivingEntity speaker, ServerPlayer player) {
		String key = speaker.getUUID() + "/" + player.getUUID();
		long now = level.getGameTime();
		Long told = TOLD.get(key);
		if (told != null && now - told < TELL_TICKS) {
			return;
		}
		TOLD.put(key, now);
		Post.tidings(player.getUUID(), Component.translatable("news.jaymoskingdoms.sky.gazer." + level.getRandom().nextInt(NEWS_LINES), speaker.getDisplayName()));
	}

	// ---- lights in the sky --------------------------------------------------------------------------------------------

	/**
	 * Every {@link #WATCH_TICKS}: a pod of raiders or of a patrolman that has come down within {@link #POD_NEAR} of the seat of a
	 * town a player is near ({@link #TOWN_NEAR}) is written into his Tidings, once for each pod, as long as he has never flown one.
	 */
	private static void lights(MinecraftServer server) {
		if (!ready()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && flights(player) == 0) {
				players.add(player);
			}
		}
		if (players.isEmpty()) {
			return;
		}
		for (Faction town : Factions.all(server)) {
			if (town.fallen() || town.type() == FactionType.BARBARIAN) {
				continue;
			}
			BlockPos seat = town.seat();
			List<ServerPlayer> near = new ArrayList<>();
			for (ServerPlayer player : players) {
				if (player.blockPosition().distSqr(seat) <= TOWN_NEAR * TOWN_NEAR) {
					near.add(player);
				}
			}
			if (near.isEmpty()) {
				continue;
			}
			for (Entity pod : level.getEntities((Entity)null, new AABB(seat).inflate(POD_NEAR, 64.0, POD_NEAR), e -> isPod(e))) {
				boolean raiders = pod.entityTags().contains(RAIDER_TAG);
				for (ServerPlayer player : near) {
					Set<UUID> seen = SEEN.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
					if (seen.size() >= REMEMBERED) {
						seen.clear();
					}
					if (seen.add(pod.getUUID())) {
						Post.tidings(player.getUUID(), Component.translatable("news.jaymoskingdoms.sky." + (raiders ? "raiders." : "patrol.")
							+ level.getRandom().nextInt(NEWS_LINES), town.name()));
					}
				}
			}
		}
	}

	/** One of Powers' space pods, of a raiders' band or a patrolman's visit. */
	private static boolean isPod(Entity e) {
		return (e.entityTags().contains(RAIDER_TAG) || e.entityTags().contains(PATROL_TAG))
			&& BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("space_pod");
	}
}

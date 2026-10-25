package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.200) The post: who's who by character name (so a letter can be addressed to someone who isn't on), and the
 * world's news. Andy: "this is so we get less information through chat and the world is more immersive". What
 * {@link KingdomEvents#announce} used to put in everyone's chat (wars, fallen rulers, cleared camps, assassinations) is
 * gathered for {@link #NEWS_DELAY} and written up as one "Tidings" letter for each player on; MCA's mail (letters from
 * family, condolences) is taken out of its inbox the same way. Each is brought ({@link #dispatch}): by a townsman who
 * runs it over when you're in a town ({@link Couriers}), else by a wild pigeon that comes down to you
 * ({@link PigeonPost#wild}). What can't be brought yet waits ({@link #later}) for the next try.
 */
public final class Post {
	/** How often the post is seen to (ticks); news is held this long before it's written up, at most this many lines a letter. */
	static final int EVERY = 100;
	static final long NEWS_DELAY = 400L;
	static final int NEWS_LINES = 10;
	/** One messenger a player at a time: the next comes this long after (ticks). */
	static final long GAP = 200L;
	/** In a town (a realm or village within this of its seat), a townsman within {@link #HERALD_RANGE} brings it. */
	static final double TOWN_RANGE = 128.0, HERALD_RANGE = 40.0;
	/** At most this many letters wait for anyone (the oldest go). */
	static final int WAITING_MAX = 12;

	/** On the Overworld: every player's character name as last seen, by their id. Saved. */
	static final AttachmentType<Map<String, String>> NAMES = AttachmentRegistry.create(JaymosKingdoms.id("post_names"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	/** On the Overworld: what waits to be brought to each player, by their id. Saved. */
	static final AttachmentType<Map<String, List<ItemStack>>> WAITING = AttachmentRegistry.create(JaymosKingdoms.id("post_waiting"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf())));

	/** The news not yet written up, and when the first of it came. */
	private static final List<Component> NEWS = new ArrayList<>();
	private static long newsSince = -1L;
	/** When each player's last messenger was sent. */
	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static @Nullable MinecraftServer current;

	private Post() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Post::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> current = server);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NEWS.clear();
			newsSince = -1L;
			LAST.clear();
			current = null;
		});
	}

	// ---- names ------------------------------------------------------------------------------------------------------

	/** What {@code player} goes by in a letter: their character name (else what they show as). */
	static Component nameOf(ServerPlayer player) {
		String name = CharacterNames.character(player);
		return Component.literal(name != null ? name : player.getDisplayName().getString());
	}

	/** What the player {@code id} went by when last seen, or "someone". */
	static Component nameOf(MinecraftServer server, UUID id) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p != null) {
			return nameOf(p);
		}
		String name = Factions.level(server).getAttachedOrElse(NAMES, Map.of()).get(id.toString());
		return name != null ? Component.literal(name) : Component.translatable("letter.jaymoskingdoms.someone");
	}

	/** The player a letter addressed to {@code name} is for (by character name, on or not; or their username), or null. */
	static @Nullable UUID resolve(MinecraftServer server, String name) {
		String want = plain(name);
		if (want.isEmpty()) {
			return null;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (plain(nameOf(p).getString()).equals(want)) {
				return p.getUUID();
			}
		}
		for (Map.Entry<String, String> e : Factions.level(server).getAttachedOrElse(NAMES, Map.of()).entrySet()) {
			if (plain(e.getValue()).equals(want)) {
				try {
					return UUID.fromString(e.getKey());
				} catch (IllegalArgumentException ignored) {
					// (a bad entry)
				}
			}
		}
		ServerPlayer byUsername = server.getPlayerList().getPlayerByName(name.strip());
		return byUsername != null ? byUsername.getUUID() : null;
	}

	private static String plain(String s) {
		return s.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	// ---- news and mail ------------------------------------------------------------------------------------------------

	/** A piece of the world's news (once everyone's chat): it goes out in the next Tidings. */
	public static void news(MinecraftServer server, Component headline) {
		JaymosKingdoms.LOGGER.info("[news] {}", headline.getString());
		if (NEWS.isEmpty()) {
			newsSince = Factions.level(server).getGameTime();
		}
		NEWS.add(headline);
		while (NEWS.size() > NEWS_LINES) {
			NEWS.removeFirst();
		}
	}

	/** {@code stack} (a letter) to be brought to {@code to} by the next messenger free. */
	static void later(UUID to, ItemStack stack) {
		if (current == null || stack.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(current);
		Map<String, List<ItemStack>> all = new HashMap<>(level.getAttachedOrElse(WAITING, Map.of()));
		List<ItemStack> mine = new ArrayList<>(all.getOrDefault(to.toString(), List.of()));
		mine.add(stack);
		while (mine.size() > WAITING_MAX) {
			mine.removeFirst();
		}
		all.put(to.toString(), List.copyOf(mine));
		level.setAttached(WAITING, Map.copyOf(all));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel overworld = Factions.level(server);
		long now = overworld.getGameTime();
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		// (who's who)
		Map<String, String> names = overworld.getAttachedOrElse(NAMES, Map.of());
		Map<String, String> fresh = null;
		for (ServerPlayer p : players) {
			String name = nameOf(p).getString();
			if (!name.equals(names.get(p.getUUID().toString()))) {
				if (fresh == null) {
					fresh = new HashMap<>(names);
				}
				fresh.put(p.getUUID().toString(), name);
			}
		}
		if (fresh != null) {
			overworld.setAttached(NAMES, Map.copyOf(fresh));
		}
		// (the news, written up for everyone on)
		if (!NEWS.isEmpty() && now - newsSince >= NEWS_DELAY) {
			List<Component> lines = List.copyOf(NEWS);
			NEWS.clear();
			newsSince = -1L;
			for (ServerPlayer p : players) {
				later(p.getUUID(), Letters.sealed(new Letters.Letter(nameOf(p).getString(), Component.translatable("letter.jaymoskingdoms.tidings.from"),
					lines, Optional.of(Component.translatable("letter.jaymoskingdoms.tidings")))));
			}
		}
		for (ServerPlayer p : players) {
			// (MCA's mail, out of its inbox)
			ItemStack mail = McaCompat.takeMail(p);
			if (!mail.isEmpty()) {
				later(p.getUUID(), mail);
			}
			Long last = LAST.get(p.getUUID());
			if (last != null && now - last < GAP || p.isSpectator() || !p.isAlive()) {
				continue;
			}
			ItemStack next = next(overworld, p.getUUID());
			if (next.isEmpty()) {
				continue;
			}
			if (dispatch(p, next)) {
				LAST.put(p.getUUID(), now);
			} else {
				later(p.getUUID(), next);
			}
		}
	}

	/** The first thing waiting for {@code id}, taken off the list (or empty). */
	private static ItemStack next(ServerLevel level, UUID id) {
		Map<String, List<ItemStack>> all = level.getAttachedOrElse(WAITING, Map.of());
		List<ItemStack> mine = all.get(id.toString());
		if (mine == null || mine.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Map<String, List<ItemStack>> rest = new HashMap<>(all);
		if (mine.size() == 1) {
			rest.remove(id.toString());
		} else {
			rest.put(id.toString(), List.copyOf(mine.subList(1, mine.size())));
		}
		level.setAttached(WAITING, Map.copyOf(rest));
		return mine.getFirst().copy();
	}

	/** A messenger with {@code stack} for {@code p}: a townsman in a town, else a wild pigeon; whether one went. */
	private static boolean dispatch(ServerPlayer p, ItemStack stack) {
		ServerLevel level = p.level();
		if (level == Factions.level(level.getServer()) && Factions.around(level.getServer(), level, p.blockPosition(), TOWN_RANGE) != null) {
			Villager herald = Couriers.nearestFree(level, p.position(), HERALD_RANGE);
			if (herald != null) {
				Couriers.start(level, herald, stack, null, p.getUUID());
				return true;
			}
		}
		if (level.dimensionType().hasSkyLight() && !level.canSeeSky(p.blockPosition().above())) {
			// (no bird finds you under the ground: it waits till you're out)
			return false;
		}
		return PigeonPost.wild(p, stack);
	}
}

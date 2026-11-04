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
 * (0.7.201) The post: who's who by character name (so a letter can be addressed to someone who isn't on), and the
 * world's news. Andy: "this is so we get less information through chat and the world is more immersive". What
 * {@link KingdomEvents#announce} used to put in everyone's chat (wars, fallen rulers, cleared camps, assassinations) is
 * gathered for {@link #NEWS_DELAY} and written up as one "Tidings" letter for each player on; MCA's mail (letters from
 * family, condolences) is taken out of its inbox the same way. Each is brought ({@link #dispatch}): by a townsman who
 * runs it over when you're in a town ({@link Couriers}), else by a wild pigeon that comes down to you
 * ({@link PigeonPost#wild}). What can't be brought yet waits ({@link #later}) for the next try.
 * <p>(0.7.424) Andy: "tidings can now be nailed this way and posted on boards in towns, they should be dropped to you by
 * bird less as those generally come from kingdoms. they would need a good reason to send it straight to you. you may just
 * not hear of some news." The world's news is kept ({@link #POSTED}) and pinned up on town boards by the townsfolk
 * ({@link #postable}), not every town hearing every piece ({@link #HEARD_CHANCE}); a bird or a courier brings a piece
 * straight to you only when it names you or your realm ({@link #concerns}).
 * <p>(0.7.494) Andy: "tidings should now only come to you if you have a roost built for recieving messenger pigeons, and
 * even then it will be rare, and of large happeneings, nothing small and insignificant." The town boards keep all the
 * news as before; a Tidings letter comes only to a player with a {@link Roost} the birds can find, carries only the world's
 * great happenings ({@link #great}: wars, fallen crowns and realms, hordes on the warpath, giants and kings felled) that
 * reach that far ({@link #REACH_CHANCE}, always when it names you or your realm), and comes at most once in
 * {@link #TIDINGS_GAP}. What someone sends you word of on purpose (a captain's spying, a parcel waylaid, a captive home)
 * comes as plain "Word" ({@link #report}), roost or not; the small lines that used to fill the Tidings are gone.
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
	/** (0.7.424) Pieces of news kept for the boards (the oldest go), how long each stays (ticks), and the chance a town hears of each. */
	static final int POSTED_MAX = 16;
	static final long POSTED_LIFE = 24000L * 3;
	static final float HEARD_CHANCE = 0.7F;
	/** (0.7.424) Characters to a line on a posted piece of news. */
	static final int POSTED_WIDTH = 38;
	/** (0.7.494) Tidings come at most this often (ticks: two days), with at most this many lines, the oldest going. */
	static final long TIDINGS_GAP = 24000L * 2;
	static final int TIDINGS_LINES = 5;
	/** (0.7.494) The chance a great happening that doesn't name you reaches your roost at all. */
	static final float REACH_CHANCE = 0.5F;
	/**
	 * (0.7.494) The world's great happenings: a headline whose translation key is one of these (or starts with one ending in
	 * '.') goes in the Tidings. Everything else stays on the town boards.
	 */
	static final java.util.Set<String> GREAT = java.util.Set.of(
		"message.jaymoskingdoms.war.declared", "message.jaymoskingdoms.war.begun", "message.jaymoskingdoms.war.peace",
		"message.jaymoskingdoms.war.ended_fallen", "message.jaymoskingdoms.war.ended_joined", "message.jaymoskingdoms.war.seat_changes",
		"message.jaymoskingdoms.great_war.begun", "message.jaymoskingdoms.great_war.rebellion", "message.jaymoskingdoms.great_war.broke",
		"message.jaymoskingdoms.king_fallen", "message.jaymoskingdoms.lord_fallen", "message.jaymoskingdoms.old_age.king",
		"message.jaymoskingdoms.fate.ruin", "message.jaymoskingdoms.fate.vassal", "message.jaymoskingdoms.emigrants.ruin",
		"message.jaymoskingdoms.crown.usurped", "message.jaymoskingdoms.crown.fell_in_war", "message.jaymoskingdoms.crown.conquered",
		"message.jaymoskingdoms.crown.founded",
		"message.jaymoskingdoms.horde.orc.4", "message.jaymoskingdoms.horde.goblin.4", "message.jaymoskingdoms.warpath.",
		"message.jaymoskingdoms.giant.first_fallen", "assassin.jaymoskingdoms.news.murdered.",
		"news.jaymoskingdoms.ogre_king.", "news.jaymoskingdoms.captain.betrayed.king",
		// (0.7.519) great treaties, broken faith, a treaty of aid joining a war, and a ruler's death where successors are chosen
		"message.jaymoskingdoms.treaty.great.", "message.jaymoskingdoms.treaty.great_broken", "message.jaymoskingdoms.treaty.great_war",
		"message.jaymoskingdoms.treaty.great_aid", "message.jaymoskingdoms.old_age.king.elect");

	/** On the Overworld: every player's character name as last seen, by their id. Saved. */
	static final AttachmentType<Map<String, String>> NAMES = AttachmentRegistry.create(JaymosKingdoms.id("post_names"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	/** On the Overworld: what waits to be brought to each player, by their id. Saved. */
	static final AttachmentType<Map<String, List<ItemStack>>> WAITING = AttachmentRegistry.create(JaymosKingdoms.id("post_waiting"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf())));

	/** (0.7.424) A piece of the world's news, kept for the town boards. */
	record Posted(String id, Component line, long at) {
		static final Codec<Posted> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Posted::id),
				net.minecraft.network.chat.ComponentSerialization.CODEC.fieldOf("line").forGetter(Posted::line),
				Codec.LONG.fieldOf("at").forGetter(Posted::at))
			.apply(i, Posted::new));
	}

	/** (0.7.424) On the Overworld: the news kept for the town boards, oldest first. Saved. */
	static final AttachmentType<List<Posted>> POSTED = AttachmentRegistry.create(JaymosKingdoms.id("post_posted"),
		builder -> builder.persistent(Posted.CODEC.listOf()));
	/** (0.7.494) On the Overworld: the lines waiting for each roost-keeper's next Tidings, by their id. Saved. */
	static final AttachmentType<Map<String, List<Component>>> PENDING = AttachmentRegistry.create(JaymosKingdoms.id("post_tidings_pending"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, net.minecraft.network.chat.ComponentSerialization.CODEC.listOf())));
	/** (0.7.494) On the Overworld: when each player's last Tidings was written, by their id. Saved. */
	static final AttachmentType<Map<String, Long>> LAST_TIDINGS = AttachmentRegistry.create(JaymosKingdoms.id("post_tidings_last"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	/** (0.7.256) Word for one player only, and when the first of it came. (0.7.494) Only what was sent them on purpose. */
	private static final Map<UUID, List<Component>> MINE = new HashMap<>();
	private static final Map<UUID, Long> MINE_SINCE = new HashMap<>();
	/** When each player's last messenger was sent. */
	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static @Nullable MinecraftServer current;

	private Post() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Post::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> current = server);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MINE.clear();
			MINE_SINCE.clear();
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

	/**
	 * A piece of the world's news (once everyone's chat): (0.7.424) kept for the town boards ({@link #POSTED}). (0.7.494) A
	 * great happening ({@link #great}) also goes in the next Tidings of each roost-keeper it reaches.
	 */
	public static void news(MinecraftServer server, Component headline) {
		JaymosKingdoms.LOGGER.info("[news] {}", headline.getString());
		ServerLevel overworld = Factions.level(server);
		long now = overworld.getGameTime();
		List<Posted> kept = new ArrayList<>(overworld.getAttachedOrElse(POSTED, List.of()));
		kept.removeIf(e -> now - e.at() > POSTED_LIFE);
		kept.add(new Posted(Long.toString(now, 36) + Integer.toString(overworld.getRandom().nextInt(1296), 36), headline, now));
		while (kept.size() > POSTED_MAX) {
			kept.removeFirst();
		}
		overworld.setAttached(POSTED, List.copyOf(kept));
		java.util.Set<UUID> told = new java.util.HashSet<>();
		for (String id : overworld.getAttachedOrElse(NAMES, Map.of()).keySet()) {
			try {
				told.add(UUID.fromString(id));
			} catch (IllegalArgumentException ignored) {
				// (a bad entry)
			}
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			told.add(p.getUUID());
		}
		if (!great(headline)) {
			return;
		}
		for (UUID id : told) {
			if (concerns(server, id, headline) || overworld.getRandom().nextFloat() < REACH_CHANCE) {
				tidings(id, headline);
			}
		}
	}

	/** (0.7.494) {@code headline} is one of the world's great happenings ({@link #GREAT}). */
	static boolean great(Component headline) {
		if (!(headline.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t)) {
			return false;
		}
		// (a queen's or a lady's line is the king's or lord's key with ".her")
		String key = t.getKey().endsWith(".her") ? t.getKey().substring(0, t.getKey().length() - 4) : t.getKey();
		if (GREAT.contains(key)) {
			return true;
		}
		for (String g : GREAT) {
			if (g.endsWith(".") && key.startsWith(g)) {
				return true;
			}
		}
		return false;
	}

	/** (0.7.424) {@code headline} names the player {@code id} (by the name they go by) or the realm they belong to. */
	static boolean concerns(MinecraftServer server, UUID id, Component headline) {
		String text = headline.getString();
		Faction f = Factions.of(server, id);
		return f != null && names(text, f.name()) || names(text, nameOf(server, id).getString());
	}

	/** {@code name} stands as words of its own in {@code text}. */
	private static boolean names(String text, String name) {
		String n = name.strip();
		return !n.isEmpty() && java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(n) + "(?![\\p{L}\\p{N}])",
			java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE).matcher(text).find();
	}

	/** (0.7.424) The kept piece of news {@code id}, if it's still about (null with no server running). */
	static @Nullable Posted posted(String id) {
		return current == null ? null : posted(current, id);
	}

	/** (0.7.424) The kept piece of news {@code id}, if it's still about. */
	static @Nullable Posted posted(MinecraftServer server, String id) {
		long now = Factions.level(server).getGameTime();
		for (Posted e : Factions.level(server).getAttachedOrElse(POSTED, List.of())) {
			if (e.id().equals(id) && now - e.at() <= POSTED_LIFE) {
				return e;
			}
		}
		return null;
	}

	/**
	 * (0.7.424) A piece of news the town board at {@code board} has heard of and hasn't up yet ({@code up}: every key
	 * pinned or on its way), the newest first; null if none.
	 */
	static @Nullable Posted postable(MinecraftServer server, long board, java.util.Set<String> up) {
		List<Posted> kept = Factions.level(server).getAttachedOrElse(POSTED, List.of());
		long now = Factions.level(server).getGameTime();
		for (int i = kept.size() - 1; i >= 0; i--) {
			Posted e = kept.get(i);
			String mark = "|tidings." + e.id() + "|" + board + "|";
			if (now - e.at() <= POSTED_LIFE && heard(e.id(), board) && up.stream().noneMatch(k -> k.contains(mark))) {
				return e;
			}
		}
		return null;
	}

	/** Whether word of {@code id} reached the town of the board at {@code board} (the same answer every time). */
	private static boolean heard(String id, long board) {
		long h = id.hashCode() * 0x9E3779B97F4A7C15L ^ board * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		return Math.floorMod(h, 1000L) < HEARD_CHANCE * 1000.0F;
	}

	/** (0.7.424) {@code line} broken up to {@link #POSTED_WIDTH} characters a line, for a notice's lore. */
	static List<Component> wrap(Component line) {
		List<Component> out = new ArrayList<>();
		StringBuilder at = new StringBuilder();
		for (String word : line.getString().split("\\s+")) {
			if (!at.isEmpty() && at.length() + 1 + word.length() > POSTED_WIDTH) {
				out.add(Component.literal(at.toString()));
				at.setLength(0);
			}
			if (!at.isEmpty()) {
				at.append(' ');
			}
			at.append(word);
		}
		if (!at.isEmpty()) {
			out.add(Component.literal(at.toString()));
		}
		return out;
	}

	/**
	 * (0.7.256) A line in {@code to}'s own next Tidings. (0.7.494) Only for something great, and only if they keep a
	 * {@link Roost}: without one it goes nowhere.
	 */
	public static void tidings(UUID to, Component line) {
		if (current == null || !Roost.has(current, to)) {
			return;
		}
		ServerLevel overworld = Factions.level(current);
		Map<String, List<Component>> all = new HashMap<>(overworld.getAttachedOrElse(PENDING, Map.of()));
		List<Component> mine = new ArrayList<>(all.getOrDefault(to.toString(), List.of()));
		mine.add(line);
		while (mine.size() > TIDINGS_LINES) {
			mine.removeFirst();
		}
		all.put(to.toString(), List.copyOf(mine));
		overworld.setAttached(PENDING, Map.copyOf(all));
	}

	/**
	 * (0.7.494) Something small that just happened to {@code to} (a drill, a duel, a round of a bout): once a line in the
	 * Tidings, now a line over the hotbar while they're on, and nothing if they're not.
	 */
	public static void aside(UUID to, Component line) {
		ServerPlayer p = current == null ? null : current.getPlayerList().getPlayer(to);
		if (p != null) {
			p.sendOverlayMessage(line);
		}
	}

	/**
	 * (0.7.494) Word sent to {@code to} on purpose (a captain's report, a parcel waylaid, a captive home): gathered for
	 * {@link #NEWS_DELAY} and brought as a letter, roost or not.
	 */
	public static void report(UUID to, Component line) {
		if (current == null) {
			return;
		}
		List<Component> mine = MINE.computeIfAbsent(to, id -> new ArrayList<>());
		if (mine.isEmpty()) {
			MINE_SINCE.put(to, Factions.level(current).getGameTime());
		}
		mine.add(line);
		while (mine.size() > NEWS_LINES) {
			mine.removeFirst();
		}
	}

	/** {@code stack} (a letter) to be brought to {@code to} by the next messenger free. */
	static void later(UUID to, ItemStack stack) {
		later(to, stack, false);
	}

	/** {@link #later}, at the front of the queue ({@code first}: it was next already). */
	private static void later(UUID to, ItemStack stack, boolean first) {
		if (current == null || stack.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(current);
		Map<String, List<ItemStack>> all = new HashMap<>(level.getAttachedOrElse(WAITING, Map.of()));
		List<ItemStack> mine = new ArrayList<>(all.getOrDefault(to.toString(), List.of()));
		if (first) {
			mine.addFirst(stack);
		} else {
			mine.add(stack);
		}
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
		// ((0.7.494) word sent you on purpose)
		for (ServerPlayer p : players) {
			List<Component> mine = MINE.get(p.getUUID());
			if (mine != null && !mine.isEmpty() && now - MINE_SINCE.getOrDefault(p.getUUID(), now) >= NEWS_DELAY) {
				MINE.remove(p.getUUID());
				MINE_SINCE.remove(p.getUUID());
				later(p.getUUID(), Letters.sealed(new Letters.Letter(nameOf(p).getString(), Component.translatable("letter.jaymoskingdoms.word.from"),
					List.copyOf(mine), Optional.of(Component.translatable("letter.jaymoskingdoms.word")))));
			}
		}
		// ((0.7.494) the Tidings: only to a roost-keeper, and rarely)
		Map<String, List<Component>> pending = overworld.getAttachedOrElse(PENDING, Map.of());
		if (!pending.isEmpty()) {
			Map<String, Long> lastTidings = overworld.getAttachedOrElse(LAST_TIDINGS, Map.of());
			for (ServerPlayer p : players) {
				String id = p.getUUID().toString();
				List<Component> lines = pending.get(id);
				Long last = lastTidings.get(id);
				if (lines == null || lines.isEmpty() || last != null && now - last < TIDINGS_GAP || !Roost.has(server, p.getUUID())) {
					continue;
				}
				Map<String, List<Component>> rest = new HashMap<>(overworld.getAttachedOrElse(PENDING, Map.of()));
				rest.remove(id);
				overworld.setAttached(PENDING, Map.copyOf(rest));
				Map<String, Long> stamped = new HashMap<>(overworld.getAttachedOrElse(LAST_TIDINGS, Map.of()));
				stamped.put(id, now);
				overworld.setAttached(LAST_TIDINGS, Map.copyOf(stamped));
				later(p.getUUID(), Letters.sealed(new Letters.Letter(nameOf(p).getString(), Component.translatable("letter.jaymoskingdoms.tidings.from"),
					List.copyOf(lines), Optional.of(Component.translatable("letter.jaymoskingdoms.tidings")))));
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
				later(p.getUUID(), next, true);
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

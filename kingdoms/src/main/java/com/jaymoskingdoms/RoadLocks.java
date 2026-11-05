package com.jaymoskingdoms;

import com.jaymoskingdoms.LifeStory.Question;
import com.jaymoskingdoms.LifeStory.Start;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.419) Roads earned by deeds. Andy: "The only available starts without this should be basic ones and req ones, like
 * verdant and divine born." Only {@link #OPEN} roads are there from the first day; every other road of the life story starts
 * locked and opens in one of two ways, both kept in {@link #LOCKS} (one row a road):
 * <ul>
 * <li><b>A deed</b> ({@link RoadDeeds}: freeing a captive, serving a sentence, a blood moon that has come and gone, a star
 * that fell, a whale taken, a world reached, ...). (0.7.466) Andy: "starts are now per group / player": it opens the road
 * for the one who did it and their group (the handshake party), not the whole world.</li>
 * <li><b>A secret past</b>: earlier answers of this player's own story (raised by outlaws and a cutpurse youth: the Hideout).</li>
 * </ul>
 * A road a player already took is never taken from them ({@link #TOOK}, and the road their story names). Ops can force a road
 * open or shut for testing with {@code /kingdom roads unlock|lock <road|all>} (recorded as the deeds {@code open.<road>} and
 * {@code shut.<road>}; shut wins). The server decides ({@link #allowed}); the client only dims the card and shows the hint line
 * ({@code story.jaymoskingdoms.road.<road>.hint}, always oblique, never what to do), using the roads the server said are open
 * and the same past table over the answers picked so far.
 */
public final class RoadLocks {
	/**
	 * The roads open from the start: the basic ones (a realm's capital, a village, the wild, sworn, banished, shipwrecked, fate) and the
	 * heritage-locked ones. (0.7.501, Andy: "these starts should be available from the start") rock bottom, the cells, the nobodies and
	 * the burned village too. The Bio-built's own road (cast out) is no longer here: it opens with the Bio-built blood
	 * ({@link #BIOBUILT_DEED}).
	 * <p>
	 * (0.7.605) Andy's list for day one: "Rock bottom, Heritage specifics, and Banished, Shipwreck, Let fate decide", with
	 * the Family farmhand (the village road), and "everything locked should now have its own unlock method and
	 * achievment". The capital, the wild, sworn, the cells, the nobodies and the burned village are locked again, each on
	 * a deed of its own; every locked road's deed earns its advancement ({@link #ADVANCEMENT}).
	 * <p>
	 * (0.7.606, Andy: "divine born start should be an unlock") the afterlife is locked too: it opens with the Divine-born
	 * blood ({@link #DIVINE_DEED}).
	 */
	static final Set<Start> OPEN = Collections.unmodifiableSet(EnumSet.of(Start.VILLAGE, Start.ROCK_BOTTOM, Start.BANISHED, Start.SHIPWRECK,
		Start.FATE, Start.VERDANT, Start.DEMON, Start.LAB));

	/**
	 * (0.7.501, Andy: "Biobuilt start and heritage should be locked ... only unlocked after meeting the tinkerer or becoming an
	 * android") The deed that opens the cast-out road, done with the Bio-built blood's waking ({@link Heritage#unlockChecks}).
	 */
	static final String BIOBUILT_DEED = "tinkerer.met";

	/**
	 * (0.7.606) The deed that opens the afterlife's road (the one that makes you Divine-born): the Divine-born blood woken,
	 * by dying again while one of the dead in the Other World ({@link Heritage#died}), as the blood itself is earned.
	 * Anyone who has that blood already counts ({@link #look}).
	 */
	static final String DIVINE_DEED = "afterlife.died";

	/** (0.7.605) The capital's deed: a journeyman's rank in any trade. The wild's: a night out far from any settlement. Sworn's: a raider slain. */
	static final String TRADE_DEED = "trade.ranked", WILD_DEED = "wild.night", RAIDER_DEED = "raider.slain";
	/** (0.7.605) How far from every settlement's seat a night counts as one in the wild (blocks). */
	static final double WILD_RANGE = 256.0;
	/** (0.7.605) How often players are looked at for the roads' deeds and advancements (ticks). */
	static final int LOOK_EVERY = 100;
	/** (0.7.605) Each locked road's advancement: {@code jaymoskingdoms:roads/<road>} (criterion {@code done}). */
	static final String ADVANCEMENT = "roads/";

	/** The prefix of the deed ids ops use to force a road open or shut. */
	static final String FORCE_OPEN = "open.", FORCE_SHUT = "shut.";

	/** The roads a player has taken (keys), kept for good: a road once taken is never locked again for them. Saved, kept through death. */
	static final AttachmentType<List<String>> TOOK = AttachmentRegistry.create(JaymosKingdoms.id("roads_taken"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/** A player's earlier answers, by question: the key of the answer, or null if none yet (or not asked). */
	@FunctionalInterface
	public interface Past {
		@Nullable String key(Question q);
	}

	/** One locked road: the deeds that open it (any one) and the secret past that opens it. (The hint is lang.) */
	record Lock(List<String> deeds, Predicate<Past> past) {
	}

	/** Every locked road, in one table. Hints: {@code story.jaymoskingdoms.road.<road>.hint}. */
	static final Map<Start, Lock> LOCKS = new EnumMap<>(Start.class);

	private static boolean is(Past p, Question q, String... keys) {
		String key = p.key(q);
		if (key == null) {
			return false;
		}
		for (String k : keys) {
			if (k.equals(key)) {
				return true;
			}
		}
		return false;
	}

	private static void lock(Start start, Predicate<Past> past, String... deeds) {
		LOCKS.put(start, new Lock(List.of(deeds), past));
	}

	static {
		// the plain ones
		lock(Start.CAVE, p -> is(p, Question.CHILDHOOD, "caves"), "kill.spider");
		lock(Start.MONOLITH, p -> is(p, Question.BORN, "scholars") && is(p, Question.CHILDHOOD, "books"), "aim.done");
		lock(Start.SKYFALL, p -> is(p, Question.CHILDHOOD, "storms") && is(p, Question.TURN, "storm"), "star.fell");
		lock(Start.TIMELOST, p -> is(p, Question.CHILDHOOD, "books") && is(p, Question.TURN, "clock"), "world.time_chamber");
		lock(Start.SNOWED, p -> is(p, Question.BORN, "north") && is(p, Question.CHILDHOOD, "snow") || is(p, Question.CHILDHOOD, "snow") && is(p, Question.YOUTH, "winter"), "kill.stray");
		lock(Start.WHALERS, p -> is(p, Question.RAISED, "fisherfolk") && is(p, Question.CHILDHOOD, "swimming"), "whalers.signed", "whale.taken");
		lock(Start.UNARMED, p -> is(p, Question.CHILDHOOD, "fights") && is(p, Question.YOUTH, "pits"), "pit.won");
		// (0.7.605) the roads Andy took off the first day, each on a deed of its own
		lock(Start.CITY, p -> is(p, Question.RAISED, "merchants") && is(p, Question.YOUTH, "apprentice"), TRADE_DEED);
		lock(Start.WILD, p -> is(p, Question.RAISED, "hunters") && is(p, Question.YOUTH, "hermit"), WILD_DEED);
		lock(Start.SWORN, p -> is(p, Question.RAISED, "soldiers"), RAIDER_DEED);
		lock(Start.SENTENCED, p -> is(p, Question.RAISED, "outlaws"), "cells.held");
		lock(Start.NOBODIES, p -> false, "orders.taken");
		lock(Start.BURNED, p -> false, "fire.doused");
		// the hard roads
		lock(Start.CAPTIVE, p -> is(p, Question.RAISED, "nobles") && is(p, Question.YOUTH, "hunted"), "captive.freed");
		lock(Start.CHAINED, p -> is(p, Question.RAISED, "soldiers") && is(p, Question.YOUTH, "levy"), "rise.captive");
		lock(Start.EVICTED, p -> is(p, Question.RAISED, "farmers") && is(p, Question.YOUTH, "winter"), "cells.served");
		// (0.7.443, Havens patch 7b) Andy: "unlockable by reaching good rep with the orcs" (TribePacts); no secret past opens it
		lock(Start.GREENSKIN, p -> false, TribePacts.FRIEND_DEED);
		// (0.7.501) the Bio-built's own road opens with their blood: the Mad Tinkerer met, or an android made; no secret past
		lock(Start.CAST_OUT, p -> false, BIOBUILT_DEED);
		lock(Start.HIDEOUT, p -> is(p, Question.RAISED, "outlaws") && is(p, Question.YOUTH, "cutpurses"), "market.black", "cells.broke");
		// the kit roads
		lock(Start.HEIRLOOM, p -> is(p, Question.RAISED, "nobles") && is(p, Question.YOUTH, "levy"), "assassin.killed");
		lock(Start.TRADER, p -> is(p, Question.RAISED, "merchants") && is(p, Question.YOUTH, "messenger"), "orders.sent");
		lock(Start.PUP, p -> is(p, Question.RAISED, "hunters") && is(p, Question.YOUTH, "winter"), "life.saved");
		// the powered roads
		lock(Start.ANDROID, p -> is(p, Question.RAISED, "smiths") && is(p, Question.YOUTH, "gunsmith"), "rise.lab");
		lock(Start.MOON_CURSED, p -> is(p, Question.BORN, "moors"), "moon.blood");
		// the other worlds
		lock(Start.MOON, p -> is(p, Question.CHILDHOOD, "roofs") && is(p, Question.TURN, "fall"), "world.moon");
		lock(Start.RUSTFALL, p -> is(p, Question.RAISED, "smiths") && is(p, Question.CHILDHOOD, "fire"), "world.rustfall");
		lock(Start.MAW, p -> is(p, Question.CHILDHOOD, "caves") && is(p, Question.TURN, "bite"), "world.maw");
		// (0.7.606) the afterlife's road opens with the Divine-born blood; no secret past
		lock(Start.AFTERLIFE, p -> false, DIVINE_DEED);
	}

	private RoadLocks() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("road lock attachment: {}", TOOK);
		RoadDeeds.register();
		// kills that count as deeds: a spider put down, a stray (the cold's bone archer)
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer) {
				if (entity instanceof Spider) {
					Deeds.done(killer, "kill.spider");
				} else if (entity instanceof Stray) {
					Deeds.done(killer, "kill.stray");
				}
				if (Raids.isRaider(entity)) {
					Deeds.done(killer, RAIDER_DEED);
				}
			}
		});
		// (0.7.605) the deeds looked for now and then, and every road a player's deeds have opened earns its advancement
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % LOOK_EVERY == 0) {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					look(server, p);
				}
			}
		});
		// a world of Jaymo's Powers reached (the moon, Rustfall, the Maw, the Cinder forge, the time chamber)
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
			if (to.dimension().identifier().getNamespace().equals("jaymospowers")) {
				Deeds.done(player, "world." + to.dimension().identifier().getPath());
			}
		});
	}

	/** (0.7.605) {@code player}'s deeds that are looked for, not done: a trade's rank, a night in the wild; then their roads' advancements. */
	private static void look(MinecraftServer server, ServerPlayer player) {
		for (Careers.Job job : Careers.Job.values()) {
			if (Careers.heldRank(player, job) >= Careers.JOURNEYMAN) {
				Deeds.done(player, TRADE_DEED);
				break;
			}
		}
		// (0.7.606) a Divine-born blood woken before the afterlife's road was locked still opens it
		if (Heritage.unlocked(player, Heritage.Kind.DIVINE)) {
			Deeds.done(player, DIVINE_DEED);
		}
		net.minecraft.server.level.ServerLevel level = player.level();
		long time = level.getOverworldClockTime() % 24000L;
		// (dawn, outdoors in the Overworld, alive, nowhere near a settlement: the night just gone was spent in the wild)
		if (time >= 0 && time < LOOK_EVERY && level.dimension() == net.minecraft.world.level.Level.OVERWORLD && player.isAlive()
			&& !player.isSpectator() && level.canSeeSky(player.blockPosition().above())
			&& Factions.around(server, level, player.blockPosition(), WILD_RANGE) == null) {
			Deeds.done(player, WILD_DEED);
		}
		Set<String> done = RoadDeeds.of(player);
		for (Map.Entry<Start, Lock> e : LOCKS.entrySet()) {
			for (String deed : e.getValue().deeds()) {
				if (done.contains(deed)) {
					PackAdvancements.awardOwn(player, ADVANCEMENT + key(e.getKey()), "done");
					break;
				}
			}
		}
	}

	// ---- queries --------------------------------------------------------------------------------------------------------

	/** The lowercase key of {@code start} (the road answer's key, and the lang key). */
	public static String key(Start start) {
		return start.name().toLowerCase(Locale.ROOT);
	}

	/** The road named {@code key}, or null. */
	static @Nullable Start byKey(String key) {
		for (Start s : Start.values()) {
			if (key(s).equals(key)) {
				return s;
			}
		}
		return null;
	}

	/** Whether {@code start} is open to every traveller from the start (and ops cannot shut it). */
	public static boolean basic(@Nullable Start start) {
		return start == null || OPEN.contains(start);
	}

	/** Whether ops have forced {@code start} shut (for testing). */
	static boolean shut(MinecraftServer server, Start start) {
		return RoadDeeds.has(server, FORCE_SHUT + key(start));
	}

	/**
	 * Whether ops have opened {@code start} for everyone, or (0.7.466) a deed done by {@code player} or their group has
	 * opened it for them ({@link RoadDeeds#of}).
	 */
	static boolean deedOpen(MinecraftServer server, @Nullable Player player, Start start) {
		if (RoadDeeds.has(server, FORCE_OPEN + key(start))) {
			return true;
		}
		Lock lock = LOCKS.get(start);
		if (lock == null || !(player instanceof ServerPlayer sp)) {
			return false;
		}
		Set<String> done = RoadDeeds.of(sp);
		for (String deed : lock.deeds()) {
			if (done.contains(deed)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code player} took {@code start} before (or their story names it): a road once taken stays theirs. */
	static boolean took(@Nullable Player player, Start start) {
		if (player == null) {
			return false;
		}
		List<String> taken = player.getAttached(TOOK);
		return taken != null && taken.contains(key(start)) || LifeStory.startedAs(player, start);
	}

	/** Whether {@code past} opens {@code start} (a secret past). Roads with no lock never need one. */
	public static boolean pastOpens(Start start, Past past) {
		Lock lock = LOCKS.get(start);
		return lock != null && lock.past().test(past);
	}

	/**
	 * Whether {@code player} may take {@code start} with {@code past}: a basic road always; any other unless ops shut it,
	 * and then if a deed opened it, they took it before, or their past does. The server checks this when the answer comes in.
	 */
	public static boolean allowed(MinecraftServer server, @Nullable Player player, @Nullable Start start, Past past) {
		if (basic(start)) {
			return true;
		}
		return !shut(server, start) && (deedOpen(server, player, start) || took(player, start) || pastOpens(start, past));
	}

	/** The keys of the locked roads {@code player} can take whatever their past (a deed, ops or a road they took), sent to their screen. */
	static List<String> opened(MinecraftServer server, @Nullable Player player) {
		List<String> out = new ArrayList<>();
		for (Start s : LOCKS.keySet()) {
			if (!shut(server, s) && (deedOpen(server, player, s) || took(player, s))) {
				out.add(key(s));
			}
		}
		return List.copyOf(out);
	}

	/** The keys of the roads ops have forced shut (even to a past that would open them), sent to the screen. */
	static List<String> shutKeys(MinecraftServer server) {
		List<String> out = new ArrayList<>();
		for (Start s : LOCKS.keySet()) {
			if (shut(server, s)) {
				out.add(key(s));
			}
		}
		return List.copyOf(out);
	}

	/** Whether fate may choose {@code start} for {@code player}: a road they can take with the story they have. */
	static boolean fateMay(MinecraftServer server, ServerPlayer player, Start start) {
		return allowed(server, player, start, q -> {
			LifeStory.Answer a = LifeStory.answer(player, q);
			return a == null ? null : a.key();
		});
	}

	/** Notes that {@code player} took {@code start}: it stays open to them for good. */
	static void note(ServerPlayer player, Start start) {
		if (basic(start)) {
			return;
		}
		List<String> taken = new ArrayList<>(player.getAttachedOrElse(TOOK, List.of()));
		if (!taken.contains(key(start))) {
			taken.add(key(start));
			player.setAttached(TOOK, List.copyOf(taken));
		}
	}

	// ---- the op command -------------------------------------------------------------------------------------------------

	/**
	 * {@code /kingdom roads unlock|lock <road|all>} (ops): force a locked road open or shut for everyone, for testing.
	 * (0.7.466) {@code /kingdom roads deeds <player>}: the deeds that player may count (their own and their group's).
	 */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("roads").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("unlock").then(Commands.argument("road", StringArgumentType.word()).suggests((c, b) -> suggest(b))
				.executes(c -> force(c.getSource(), StringArgumentType.getString(c, "road"), true))))
			.then(Commands.literal("lock").then(Commands.argument("road", StringArgumentType.word()).suggests((c, b) -> suggest(b))
				.executes(c -> force(c.getSource(), StringArgumentType.getString(c, "road"), false))))
			.then(Commands.literal("deeds").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
				.executes(c -> {
					ServerPlayer p = net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player");
					Set<String> done = RoadDeeds.of(p);
					c.getSource().sendSuccess(() -> Component.translatable("commands.jaymoskingdoms.roads.deeds", p.getDisplayName(), done.size(),
						done.isEmpty() ? "-" : String.join(", ", done)), false);
					return done.size();
				})));
	}

	private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder b) {
		b.suggest("all");
		for (Start s : LOCKS.keySet()) {
			b.suggest(key(s));
		}
		return b.buildFuture();
	}

	private static int force(CommandSourceStack source, String which, boolean open) {
		MinecraftServer server = source.getServer();
		List<Start> roads = new ArrayList<>();
		if (which.equalsIgnoreCase("all")) {
			roads.addAll(LOCKS.keySet());
		} else {
			Start s = byKey(which.toLowerCase(Locale.ROOT));
			if (s == null || !LOCKS.containsKey(s)) {
				source.sendFailure(Component.translatable("commands.jaymoskingdoms.roads.unknown", which));
				return 0;
			}
			roads.add(s);
		}
		for (Start s : roads) {
			if (open) {
				RoadDeeds.undo(server, FORCE_SHUT + key(s));
				RoadDeeds.done(server, FORCE_OPEN + key(s));
			} else {
				RoadDeeds.undo(server, FORCE_OPEN + key(s));
				RoadDeeds.done(server, FORCE_SHUT + key(s));
			}
		}
		int n = roads.size();
		String first = key(roads.get(0));
		source.sendSuccess(() -> n == 1 ? Component.translatable(open ? "commands.jaymoskingdoms.roads.unlocked" : "commands.jaymoskingdoms.roads.locked", first)
			: Component.translatable(open ? "commands.jaymoskingdoms.roads.unlocked_n" : "commands.jaymoskingdoms.roads.locked_n", n), true);
		return n;
	}
}

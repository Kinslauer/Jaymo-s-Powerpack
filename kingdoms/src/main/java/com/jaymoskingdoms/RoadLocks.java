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
 * <li><b>A deed</b> done by anyone in this world ({@link RoadDeeds}: freeing a captive, serving a sentence, a blood moon that
 * has come and gone, a star that fell, a whale taken, a world reached, ...). It opens the road for everyone.</li>
 * <li><b>A secret past</b>: earlier answers of this player's own story (raised by outlaws and a cutpurse youth: the Hideout).</li>
 * </ul>
 * A road a player already took is never taken from them ({@link #TOOK}, and the road their story names). Ops can force a road
 * open or shut for testing with {@code /kingdom roads unlock|lock <road|all>} (recorded as the deeds {@code open.<road>} and
 * {@code shut.<road>}; shut wins). The server decides ({@link #allowed}); the client only dims the card and shows the hint line
 * ({@code story.jaymoskingdoms.road.<road>.hint}, always oblique, never what to do), using the roads the server said are open
 * and the same past table over the answers picked so far.
 */
public final class RoadLocks {
	/** The roads open from the start: the basic ones (a realm's capital, a village, the wild, sworn, banished, shipwrecked, fate) and the heritage-locked ones. */
	static final Set<Start> OPEN = Collections.unmodifiableSet(EnumSet.of(Start.CITY, Start.VILLAGE, Start.WILD, Start.SWORN, Start.BANISHED,
		Start.SHIPWRECK, Start.FATE, Start.VERDANT, Start.AFTERLIFE, Start.DEMON, Start.LAB, Start.CAST_OUT));

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
		lock(Start.BURNED, p -> is(p, Question.RAISED, "farmers") && is(p, Question.CHILDHOOD, "fire"), "fire.doused");
		lock(Start.SNOWED, p -> is(p, Question.BORN, "north") && is(p, Question.CHILDHOOD, "snow") || is(p, Question.CHILDHOOD, "snow") && is(p, Question.YOUTH, "winter"), "kill.stray");
		lock(Start.WHALERS, p -> is(p, Question.RAISED, "fisherfolk") && is(p, Question.CHILDHOOD, "swimming"), "whalers.signed", "whale.taken");
		lock(Start.UNARMED, p -> is(p, Question.CHILDHOOD, "fights") && is(p, Question.YOUTH, "pits"), "pit.won");
		// the hard roads
		lock(Start.CAPTIVE, p -> is(p, Question.RAISED, "nobles") && is(p, Question.YOUTH, "hunted"), "captive.freed");
		lock(Start.CHAINED, p -> is(p, Question.RAISED, "soldiers") && is(p, Question.YOUTH, "levy"), "rise.captive");
		lock(Start.ROCK_BOTTOM, p -> is(p, Question.RAISED, "healers") && is(p, Question.CHILDHOOD, "caves"), "rise.chained");
		lock(Start.SENTENCED, p -> is(p, Question.RAISED, "merchants") && is(p, Question.YOUTH, "cutpurses"), "cells.held");
		lock(Start.EVICTED, p -> is(p, Question.RAISED, "farmers") && is(p, Question.YOUTH, "winter"), "cells.served");
		lock(Start.HIDEOUT, p -> is(p, Question.RAISED, "outlaws") && is(p, Question.YOUTH, "cutpurses"), "market.black", "cells.broke");
		// the kit roads
		lock(Start.HEIRLOOM, p -> is(p, Question.RAISED, "nobles") && is(p, Question.YOUTH, "levy"), "assassin.killed");
		lock(Start.TRADER, p -> is(p, Question.RAISED, "merchants") && is(p, Question.YOUTH, "messenger"), "orders.sent");
		lock(Start.PUP, p -> is(p, Question.RAISED, "hunters") && is(p, Question.YOUTH, "winter"), "life.saved");
		lock(Start.NOBODIES, p -> is(p, Question.RAISED, "foundling") && is(p, Question.YOUTH, "levy"), "orders.taken");
		// the powered roads
		lock(Start.ANDROID, p -> is(p, Question.RAISED, "smiths") && is(p, Question.YOUTH, "gunsmith"), "rise.lab", "world.rustfall");
		lock(Start.MOON_CURSED, p -> is(p, Question.BORN, "moors"), "moon.blood");
		// the other worlds
		lock(Start.MOON, p -> is(p, Question.CHILDHOOD, "roofs") && is(p, Question.TURN, "fall"), "world.moon");
		lock(Start.RUSTFALL, p -> is(p, Question.RAISED, "smiths") && is(p, Question.CHILDHOOD, "fire"), "world.rustfall");
		lock(Start.MAW, p -> is(p, Question.CHILDHOOD, "caves") && is(p, Question.TURN, "bite"), "world.maw");
	}

	private RoadLocks() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("road lock attachment: {}", TOOK);
		// kills that count as deeds: a spider put down, a stray (the cold's bone archer)
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer) {
				MinecraftServer server = killer.level().getServer();
				if (entity instanceof Spider) {
					Deeds.done(server, "kill.spider");
				} else if (entity instanceof Stray) {
					Deeds.done(server, "kill.stray");
				}
			}
		});
		// a world of Jaymo's Powers reached (the moon, Rustfall, the Maw, the Cinder forge, the time chamber)
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
			if (to.dimension().identifier().getNamespace().equals("jaymospowers")) {
				Deeds.done(to.getServer(), "world." + to.dimension().identifier().getPath());
			}
		});
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

	/** Whether a deed done in this world, or ops, has opened {@code start} for everyone. */
	static boolean deedOpen(MinecraftServer server, Start start) {
		if (RoadDeeds.has(server, FORCE_OPEN + key(start))) {
			return true;
		}
		Lock lock = LOCKS.get(start);
		if (lock != null) {
			for (String deed : lock.deeds()) {
				if (RoadDeeds.has(server, deed)) {
					return true;
				}
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
		return !shut(server, start) && (deedOpen(server, start) || took(player, start) || pastOpens(start, past));
	}

	/** The keys of the locked roads {@code player} can take whatever their past (a deed, ops or a road they took), sent to their screen. */
	static List<String> opened(MinecraftServer server, @Nullable Player player) {
		List<String> out = new ArrayList<>();
		for (Start s : LOCKS.keySet()) {
			if (!shut(server, s) && (deedOpen(server, s) || took(player, s))) {
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

	/** {@code /kingdom roads unlock|lock <road|all>} (ops): force a locked road open or shut for everyone, for testing. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("roads").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("unlock").then(Commands.argument("road", StringArgumentType.word()).suggests((c, b) -> suggest(b))
				.executes(c -> force(c.getSource(), StringArgumentType.getString(c, "road"), true))))
			.then(Commands.literal("lock").then(Commands.argument("road", StringArgumentType.word()).suggests((c, b) -> suggest(b))
				.executes(c -> force(c.getSource(), StringArgumentType.getString(c, "road"), false))));
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

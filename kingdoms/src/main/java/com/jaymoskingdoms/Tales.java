package com.jaymoskingdoms;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tales: quests told in steps over days and weeks, with choices that end them differently (docs/StoryQuests-Plan.md).
 * Andy picked every row of the pick list made from Mebahel's RPG Villager Quests and Companions (an All Rights Reserved
 * mod with no build for this game version: the ideas only, in our own code and words) and asked for them long and
 * layered, "witcher 3 style ... with multi day or even week involvements".
 *
 * <p>A tale is a data file ({@link Tale}). A villager with work may have a tale instead of an errand; a drunk's rumour,
 * a book out of a ruin, a letter a runner brings or a close companion can start one too. Each player keeps a {@link Run}
 * for every tale they're in: where it's got to, what was rolled, where its places lie, who its people are, what's been
 * written down. The open step is a {@link Quest} in their log (kind {@link Quest#TALE} or one of the job kinds), so the
 * tracker, the journal and the mark over whoever is next to speak with all work as they do for any quest.
 *
 * <p>A party shares a tale: its members near when it's taken get the same run, steps one of them finishes are finished
 * for all, and the ending one of them chooses is everyone's; each is paid their own reward.
 */
public final class Tales {
	// ================================================================================================================
	// Tuning
	/** Of the villagers with work to give, this share has a tale instead of an errand, when a tale suits their trade. */
	static final float TALE_SHARE = 0.4F;
	/** A tale's people are set out when a player comes this near their place; a place is reached this near it (unless the step says). */
	static final int SET_OUT_RANGE = 48, GO_RADIUS = 16;
	/** Clues lie within this many blocks of their scene, and are looked for again this far around it. */
	static final int CLUE_SPREAD = 5, CLUE_RANGE = 24;
	/** A tale offered and not taken is forgotten after this many days; a rumour never followed, after this many. */
	static final int UNTAKEN_DAYS = 1, RUMOUR_DAYS = 20;
	/** A teller of rumours has a tale's rumour rather than a treasure's this often, when one fits. */
	static final float RUMOUR_CHANCE = 0.35F;
	/** Each day, the chance a tale that starts by letter is sent to a player it fits, and that a close companion asks for help with theirs. */
	static final float LETTER_DAILY = 0.2F, COMPANION_DAILY = 0.15F;
	/** A companion who would ask must be within this many blocks. */
	static final double COMPANION_RANGE = 16.0;
	/** A tale's book lies in a ruin's chest this often (each chest opened), when one is written to be found there. */
	static final float BOOK_CHEST_CHANCE = 0.04F;
	/** A letter lost or never opened is sent again after this many days. */
	static final int LETTER_RESEND_DAYS = 2;
	/** Walking out on a tale costs this much of the asker's settlement's good name. */
	static final int ABANDON_REPUTATION = 10;
	/** How many finished tales the Stories page keeps, and the most lines one keeps. */
	static final int TOLD_MAX = 40, NOTES_MAX = 60;
	/** How often (ticks) each player's tales are looked over, clues re-laid and shown. */
	static final int WATCH_TICKS = 20, CLUE_TICKS = 100;
	/** The custom-data keys on a tale's book, a tale's letter, and a clue. */
	static final String KEY_BOOK = "jaymoskingdoms_tale_book", KEY_LETTER = "jaymoskingdoms_tale_letter", KEY_CLUE = "jaymoskingdoms_tale_clue";
	/** A tale's quest in the log: this, then the run's instance. */
	static final String QUEST = "tale:";

	// ================================================================================================================
	// What's kept

	/**
	 * One player's place in one tale. {@code instance} is shared by a party in it together. {@code giver} is who asked
	 * (their UUID, or empty for a tale that had no asker), {@code home} where it started, {@code vars} what was rolled,
	 * {@code places} where its places turned out to be, {@code cast} and {@code names} its people, {@code notes} what the
	 * journal says so far, {@code wake} the day a rest ends, {@code scratch} the open step's own bookkeeping.
	 */
	static final class Run {
		final String instance, tale;
		String step, giver, giverName, job;
		int faction;
		long home, wake;
		boolean taken;
		final Map<String, String> vars, cast, names, scratch;
		final Map<String, Long> places;
		final List<String> notes;

		Run(String instance, String tale, String step, String giver, String giverName, String job, int faction, long home, long wake, boolean taken,
			Map<String, String> vars, Map<String, String> cast, Map<String, String> names, Map<String, String> scratch, Map<String, Long> places,
			List<String> notes) {
			this.instance = instance;
			this.tale = tale;
			this.step = step;
			this.giver = giver;
			this.giverName = giverName;
			this.job = job;
			this.faction = faction;
			this.home = home;
			this.wake = wake;
			this.taken = taken;
			this.vars = new HashMap<>(vars);
			this.cast = new HashMap<>(cast);
			this.names = new HashMap<>(names);
			this.scratch = new HashMap<>(scratch);
			this.places = new HashMap<>(places);
			this.notes = new ArrayList<>(notes);
		}

		private static final Codec<Map<String, String>> WORDS = Codec.unboundedMap(Codec.STRING, Codec.STRING);
		static final Codec<Run> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("instance").forGetter(r -> r.instance),
				Codec.STRING.fieldOf("tale").forGetter(r -> r.tale),
				Codec.STRING.fieldOf("step").forGetter(r -> r.step),
				Codec.STRING.optionalFieldOf("giver", "").forGetter(r -> r.giver),
				Codec.STRING.optionalFieldOf("giver_name", "").forGetter(r -> r.giverName),
				Codec.STRING.optionalFieldOf("job", "").forGetter(r -> r.job),
				Codec.INT.optionalFieldOf("faction", -1).forGetter(r -> r.faction),
				Codec.LONG.fieldOf("home").forGetter(r -> r.home),
				Codec.LONG.optionalFieldOf("wake", -1L).forGetter(r -> r.wake),
				Codec.BOOL.optionalFieldOf("taken", false).forGetter(r -> r.taken),
				WORDS.optionalFieldOf("vars", Map.of()).forGetter(r -> r.vars),
				WORDS.optionalFieldOf("cast", Map.of()).forGetter(r -> r.cast),
				WORDS.optionalFieldOf("names", Map.of()).forGetter(r -> r.names),
				WORDS.optionalFieldOf("scratch", Map.of()).forGetter(r -> r.scratch),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("places", Map.of()).forGetter(r -> r.places),
				Codec.STRING.listOf().optionalFieldOf("notes", List.of()).forGetter(r -> r.notes))
			.apply(i, Run::new));

		String quest() {
			return QUEST + instance;
		}

		@Nullable UUID giverId() {
			return uuid(giver);
		}
	}

	/** A tale that's over, as the Stories page tells it: its name, how it ended, what was written along the way. */
	record Told(String tale, String title, String ending, List<String> notes, boolean failed, long day) {
		static final Codec<Told> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("tale").forGetter(Told::tale),
				Codec.STRING.fieldOf("title").forGetter(Told::title),
				Codec.STRING.optionalFieldOf("ending", "").forGetter(Told::ending),
				Codec.STRING.listOf().optionalFieldOf("notes", List.of()).forGetter(Told::notes),
				Codec.BOOL.optionalFieldOf("failed", false).forGetter(Told::failed),
				Codec.LONG.optionalFieldOf("day", 0L).forGetter(Told::day))
			.apply(i, Told::new));
	}

	/**
	 * Someone from a tale that's over who'll come back: {@code as} thanks, a warning, a recruit or revenge, on {@code day},
	 * saying {@code say}, with {@code gift}; {@code body} is their part of the tale's cast (as the file wrote it).
	 */
	record Return(String name, String body, long day, String as, String say, List<String> gift, int faction) {
		static final Codec<Return> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("name").forGetter(Return::name),
				Codec.STRING.optionalFieldOf("body", "{}").forGetter(Return::body),
				Codec.LONG.fieldOf("day").forGetter(Return::day),
				Codec.STRING.fieldOf("as").forGetter(Return::as),
				Codec.STRING.optionalFieldOf("say", "").forGetter(Return::say),
				Codec.STRING.listOf().optionalFieldOf("gift", List.of()).forGetter(Return::gift),
				Codec.INT.optionalFieldOf("faction", -1).forGetter(Return::faction))
			.apply(i, Return::new));
	}

	/**
	 * Everything of a player's tales: the runs open, the marks their choices left, which tales are done and how they
	 * ended, the ones told, who's still to come back, the letters due (tale to the day), and the companion who has
	 * something to ask ({@code tale|companion uuid|day}).
	 */
	static final class State {
		final List<Run> runs;
		final Set<String> marks;
		final Map<String, String> done;
		final List<Told> told;
		final List<Return> returns;
		final Map<String, Long> due;
		String ask;

		State(List<Run> runs, List<String> marks, Map<String, String> done, List<Told> told, List<Return> returns, Map<String, Long> due, String ask) {
			this.runs = new ArrayList<>(runs);
			this.marks = new HashSet<>(marks);
			this.done = new HashMap<>(done);
			this.told = new ArrayList<>(told);
			this.returns = new ArrayList<>(returns);
			this.due = new HashMap<>(due);
			this.ask = ask;
		}

		State() {
			this(List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of(), "");
		}

		static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Run.CODEC.listOf().optionalFieldOf("runs", List.of()).forGetter(s -> s.runs),
				Codec.STRING.listOf().optionalFieldOf("marks", List.of()).forGetter(s -> List.copyOf(s.marks)),
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("done", Map.of()).forGetter(s -> s.done),
				Told.CODEC.listOf().optionalFieldOf("told", List.of()).forGetter(s -> s.told),
				Return.CODEC.listOf().optionalFieldOf("returns", List.of()).forGetter(s -> s.returns),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("due", Map.of()).forGetter(s -> s.due),
				Codec.STRING.optionalFieldOf("ask", "").forGetter(s -> s.ask))
			.apply(i, State::new));
	}

	/** Per player: their tales. Saved, kept through death; wiped on reincarnation ({@link #reborn}). */
	static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosKingdoms.id("tales"),
		builder -> builder.persistent(State.CODEC).copyOnDeath());

	/** The day each player's tales were last looked over for the day's business. */
	private static final Map<UUID, Long> DAY = new HashMap<>();
	/** Players who asked their tale's giver about other work: the next talk goes to the ordinary quests. */
	private static final Set<UUID> OTHER_WORK = new HashSet<>();
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)\\}");

	private Tales() {
	}

	static void register() {
		Tale.register();
		TaleCast.register();
		ServerTickEvents.END_SERVER_TICK.register(Tales::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Tales::died);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(entity instanceof LivingEntity who)) {
				return InteractionResult.PASS;
			}
			// (the people of a tale you're in are spoken with by using them; anyone else keeps their own menu)
			return pending(sp, who) && talk(sp, who) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp) {
				ItemStack stack = player.getItemInHand(hand);
				if (stack.is(Items.WRITTEN_BOOK)) {
					read(sp, stack);
				}
			}
			return InteractionResult.PASS;
		});
		// (a tale's book among a ruin's things)
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (table.unwrapKey().filter(k -> RUIN_CHESTS.contains(k)).isPresent() && context.getRandom().nextFloat() < BOOK_CHEST_CHANCE) {
				ItemStack book = bookFor("ruin", null, context.getRandom());
				if (!book.isEmpty()) {
					drops.add(book);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			DAY.remove(handler.player.getUUID());
			OTHER_WORK.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DAY.clear();
			OTHER_WORK.clear();
		});
	}

	/** The chests a tale's book may lie in ("found": "ruin"). */
	private static final Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable>> RUIN_CHESTS = Set.of(
		BuiltInLootTables.SIMPLE_DUNGEON, BuiltInLootTables.ABANDONED_MINESHAFT, BuiltInLootTables.DESERT_PYRAMID, BuiltInLootTables.JUNGLE_TEMPLE,
		BuiltInLootTables.STRONGHOLD_LIBRARY, BuiltInLootTables.STRONGHOLD_CORRIDOR, BuiltInLootTables.SHIPWRECK_MAP, BuiltInLootTables.UNDERWATER_RUIN_BIG,
		BuiltInLootTables.PILLAGER_OUTPOST, BuiltInLootTables.WOODLAND_MANSION, BuiltInLootTables.RUINED_PORTAL, BuiltInLootTables.IGLOO_CHEST);

	// ---- a player's tales ---------------------------------------------------------------------------------------------

	static State state(ServerPlayer player) {
		State s = player.getAttached(STATE);
		if (s == null) {
			s = new State();
			player.setAttached(STATE, s);
		}
		return s;
	}

	private static @Nullable State stateOrNull(ServerPlayer player) {
		return player.getAttached(STATE);
	}

	static @Nullable Run run(ServerPlayer player, String instance) {
		State s = stateOrNull(player);
		if (s != null) {
			for (Run r : s.runs) {
				if (r.instance.equals(instance)) {
					return r;
				}
			}
		}
		return null;
	}

	/** The run {@code q} is the open step of, or null. */
	static @Nullable Run runOf(ServerPlayer player, Quest q) {
		return q.id().startsWith(QUEST) ? run(player, q.id().substring(QUEST.length())) : null;
	}

	/** Whether {@code q} belongs to a tale. */
	static boolean ofTale(Quest q) {
		return q.template().startsWith(QUEST);
	}

	/** {@code player} is in the tale {@code instance} (for its people: whom they'll fight, {@link TaleCast}). */
	static boolean in(ServerPlayer player, String instance) {
		return run(player, instance) != null;
	}

	/** A mark a choice left on {@code player}. */
	static boolean marked(ServerPlayer player, String mark) {
		State s = stateOrNull(player);
		return s != null && s.marks.contains(mark);
	}

	/** (Reincarnation) Everything of {@code player}'s tales is gone with the life that lived them. */
	static void reborn(ServerPlayer player) {
		State s = stateOrNull(player);
		if (s == null) {
			return;
		}
		if (player.level() instanceof ServerLevel level) {
			for (Run run : s.runs) {
				Quests.drop(player, run.quest());
				TaleCast.over(level, run, null);
			}
		}
		player.removeAttached(STATE);
		Quests.sync(player, true);
	}

	private static @Nullable UUID uuid(String s) {
		if (s == null || s.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static long day(ServerLevel level) {
		return Quests.day(level);
	}

	// ---- conditions and words --------------------------------------------------------------------------------------------

	/** Whether {@code cond} holds for {@code player} in {@code run} (null: before any run, for a tale's needs and bars). */
	static boolean holds(ServerPlayer player, @Nullable Run run, String cond) {
		boolean not = cond.startsWith("!");
		String c = not ? cond.substring(1) : cond;
		State s = stateOrNull(player);
		boolean v;
		if (c.equals("powers")) {
			v = FabricLoader.getInstance().isModLoaded("jaymospowers");
		} else if (c.startsWith("mark:")) {
			v = s != null && s.marks.contains(c.substring(5));
		} else if (c.startsWith("done:")) {
			String[] parts = c.split(":");
			String ended = s == null || parts.length < 2 ? null : s.done.get(parts[1]);
			v = ended != null && (parts.length < 3 || ended.equals(parts[2]));
		} else {
			int eq = c.indexOf('=');
			v = eq > 0 && run != null && c.substring(eq + 1).equals(run.vars.get(c.substring(0, eq)));
		}
		return v != not;
	}

	private static Predicate<String> facts(ServerPlayer player, @Nullable Run run) {
		return cond -> holds(player, run, cond);
	}

	private static @Nullable Faction faction(ServerPlayer player, Run run) {
		return run.faction < 0 ? null : Factions.get(player.level().getServer(), run.faction);
	}

	/** {@code raw} with its placeholders filled in for {@code player}'s {@code run} (docs/StoryQuests-Plan.md, "text"). */
	static MutableComponent text(ServerPlayer player, Run run, String raw) {
		Tale tale = Tale.get(run.tale);
		MutableComponent out = Component.empty();
		Matcher m = PLACEHOLDER.matcher(raw);
		int from = 0;
		while (m.find()) {
			if (m.start() > from) {
				out.append(raw.substring(from, m.start()));
			}
			out.append(fill(player, run, tale, m.group(1)));
			from = m.end();
		}
		if (from < raw.length()) {
			out.append(raw.substring(from));
		}
		return out;
	}

	private static Component fill(ServerPlayer player, Run run, @Nullable Tale tale, String name) {
		if (name.equals("player")) {
			return Post.nameOf(player);
		}
		if (name.equals("giver")) {
			return Component.literal(run.giverName);
		}
		if (name.equals("town")) {
			Faction f = faction(player, run);
			return f == null ? Component.translatable("tale.jaymoskingdoms.town") : Component.literal(f.name());
		}
		if (name.startsWith("coins:")) {
			return Money.text(faction(player, run), Money.worth(parse(name.substring(6), 1)));
		}
		if (name.startsWith("place:")) {
			return Component.literal(placeName(tale, name.substring(6)));
		}
		if (name.startsWith("way:") && tale != null && player.level() instanceof ServerLevel level) {
			return Rumours.words(player.position(), place(level, run, tale, name.substring(4)));
		}
		String rolled = run.vars.get(name);
		String role = rolled != null ? rolled : name;
		String known = run.names.get(role);
		return Component.literal(known != null ? known : role);
	}

	private static int parse(String s, int or) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return or;
		}
	}

	private static String placeName(@Nullable Tale tale, String key) {
		JsonObject p = tale == null ? null : tale.place(key);
		return p == null ? key : Tale.string(p, "name", key);
	}

	/** A step's (or a reply's) field as words: a text, or a switch to one; empty if it has none. */
	private static @Nullable MutableComponent words(ServerPlayer player, Run run, JsonObject in, String key) {
		JsonElement e = Tale.pick(in.get(key), facts(player, run));
		List<String> lines = Tale.strings(e);
		return lines.isEmpty() ? null : text(player, run, String.join(" ", lines));
	}

	private static void note(Run run, String line) {
		if (!line.isBlank() && run.notes.size() < NOTES_MAX) {
			run.notes.add(line);
		}
	}

	// ---- places ----------------------------------------------------------------------------------------------------------

	/** Where {@code run}'s place {@code key} is, worked out the first time it's asked for (and kept). */
	static BlockPos place(ServerLevel level, Run run, Tale tale, String key) {
		return place(level, run, tale, key, 0);
	}

	private static BlockPos place(ServerLevel level, Run run, Tale tale, String key, int depth) {
		Long known = run.places.get(key);
		if (known != null) {
			return BlockPos.of(known);
		}
		BlockPos home = BlockPos.of(run.home);
		JsonObject p = tale.place(key);
		if (p == null || depth > 4) {
			return home;
		}
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		String fromKey = Tale.string(p, "from", "home");
		BlockPos from = fromKey.equals("home") ? home : place(level, run, tale, fromKey, depth + 1);
		int dist = Tale.between(p.get("dist"), 60, random);
		Faction own = run.faction < 0 ? null : Factions.get(server, run.faction);
		BlockPos at = null;
		switch (Tale.string(p, "at", "near")) {
			case "home" -> at = home;
			case "town" -> at = own == null ? home : own.seat();
			case "other_town" -> {
				Faction other = otherTown(server, from, own);
				at = other == null ? null : other.seat();
			}
			case "ruin" -> at = Rumours.place(level, from);
			case "camp" -> {
				Faction camp = nearest(server, from, own, true);
				at = camp == null ? null : camp.seat();
			}
			case "road" -> {
				Faction other = otherTown(server, from, own);
				Vec3 on = other == null ? null : Roads.ahead(level, Vec3.atCenterOf(from), other.seat(), dist);
				at = on == null ? null : BlockPos.containing(on);
			}
			default -> {
			}
		}
		if (at == null) {
			at = wild(level, from, dist, random);
		}
		run.places.put(key, at.asLong());
		return at;
	}

	private static @Nullable Faction otherTown(MinecraftServer server, BlockPos from, @Nullable Faction own) {
		return nearest(server, from, own, false);
	}

	private static @Nullable Faction nearest(MinecraftServer server, BlockPos from, @Nullable Faction not, boolean camp) {
		Faction best = null;
		double bestD = 2000.0 * 2000.0;
		for (Faction f : Factions.all(server)) {
			if (f == not || f.fallen() || (f.type() == FactionType.BARBARIAN) != camp) {
				continue;
			}
			double d = f.seat().distSqr(from);
			if (d < bestD) {
				bestD = d;
				best = f;
			}
		}
		return best;
	}

	/** Open ground about {@code dist} blocks from {@code from}: dry land if any direction has it. Its height is the land's as the world would make it. */
	private static BlockPos wild(ServerLevel level, BlockPos from, int dist, RandomSource random) {
		net.minecraft.world.level.chunk.ChunkGenerator generator = level.getChunkSource().getGenerator();
		net.minecraft.world.level.levelgen.RandomState state = level.getChunkSource().randomState();
		int dry = generator.getSeaLevel() + 1;
		double start = random.nextDouble() * Math.PI * 2.0;
		BlockPos fallback = null;
		for (int i = 0; i < 12; i++) {
			double a = start + Math.PI * 2.0 * i / 12.0;
			int x = from.getX() + (int)Math.round(Math.cos(a) * dist), z = from.getZ() + (int)Math.round(Math.sin(a) * dist);
			int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, state);
			BlockPos at = new BlockPos(x, y, z);
			if (fallback == null) {
				fallback = at;
			}
			if (generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, state) >= dry
				&& Factions.around(level.getServer(), level, at, dist < 100 ? 0.0 : 48.0) == null) {
				return at;
			}
		}
		return fallback == null ? from : fallback;
	}

	/**
	 * The ground at {@code run}'s place {@code key}, once its chunks are loaded: a spot to stand on natural ground near
	 * it, kept from then on so everyone agrees where it is. Null while it isn't loaded.
	 */
	static @Nullable BlockPos ground(ServerLevel level, Run run, Tale tale, String key) {
		BlockPos at = place(level, run, tale, key);
		if (run.places.containsKey("~" + key)) {
			return at;
		}
		if (!level.isLoaded(at) || !level.isPositionEntityTicking(at)) {
			return null;
		}
		JsonObject p = tale.place(key);
		String kind = p == null ? "near" : Tale.string(p, "at", "near");
		BlockPos stand;
		if (kind.equals("home") || kind.equals("town") || kind.equals("other_town")) {
			stand = Construction.standable(level, at, 4);
		} else {
			stand = null;
			RandomSource random = level.getRandom();
			for (int i = 0; i < 16 && stand == null; i++) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					at.offset(i == 0 ? 0 : random.nextInt(21) - 10, 0, i == 0 ? 0 : random.nextInt(21) - 10));
				net.minecraft.world.level.block.state.BlockState below = level.getBlockState(top.below());
				if (level.getFluidState(top).isEmpty() && level.getFluidState(top.below()).isEmpty()
					&& (below.is(net.minecraft.tags.BlockTags.DIRT) || below.is(net.minecraft.tags.BlockTags.SAND)
						|| below.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || below.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)
						|| below.is(net.minecraft.world.level.block.Blocks.GRAVEL) || below.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK))) {
					stand = top;
				}
			}
			if (stand == null) {
				stand = Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at), 6);
			}
		}
		run.places.put(key, stand.asLong());
		run.places.put("~" + key, 1L);
		return stand;
	}

	private static double flat(ServerPlayer player, BlockPos at) {
		double dx = at.getX() + 0.5 - player.getX(), dz = at.getZ() + 0.5 - player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---- starting ----------------------------------------------------------------------------------------------------------

	/** Whether {@code tale} may start for {@code player}: not done, not under way, its needs met and none of its bars. */
	private static boolean fits(ServerPlayer player, Tale tale) {
		State s = stateOrNull(player);
		if (s != null) {
			if (s.done.containsKey(tale.id)) {
				return false;
			}
			for (Run r : s.runs) {
				if (r.tale.equals(tale.id)) {
					return false;
				}
			}
		}
		for (String need : Tale.strings(tale.start().get("needs"))) {
			if (!holds(player, null, need)) {
				return false;
			}
		}
		for (String bar : Tale.strings(tale.start().get("bars"))) {
			if (holds(player, null, bar)) {
				return false;
			}
		}
		return true;
	}

	/** One of the tales that start {@code by} this way and suit (a giver's trade {@code job}; a player, if given), by their weights; or null. */
	private static @Nullable Tale pick(String by, @Nullable String job, @Nullable ServerPlayer player, RandomSource random) {
		List<Tale> suit = new ArrayList<>();
		int total = 0;
		for (Tale tale : Tale.all()) {
			if (!tale.by().equals(by) || player != null && !fits(player, tale)) {
				continue;
			}
			List<String> jobs = Tale.strings(tale.start().get("jobs"));
			if (job != null && !jobs.isEmpty() && !jobs.contains(job)) {
				continue;
			}
			suit.add(tale);
			total += Math.max(1, Tale.number(tale.start(), "weight", 1));
		}
		if (suit.isEmpty()) {
			return null;
		}
		int lot = random.nextInt(total);
		for (Tale tale : suit) {
			lot -= Math.max(1, Tale.number(tale.start(), "weight", 1));
			if (lot < 0) {
				return tale;
			}
		}
		return suit.get(0);
	}

	private static String job(LivingEntity who) {
		return who instanceof Villager v ? BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath() : "";
	}

	/**
	 * A tale for {@code giver} to have instead of an errand ({@link Quests#offered}): the quest that stands for it on the
	 * giver until someone hears them out. Null when the lot says an errand, or no tale suits their trade.
	 */
	static @Nullable Quest offer(ServerLevel level, LivingEntity giver, Faction f) {
		if (!(giver instanceof Villager) || level.getRandom().nextFloat() >= TALE_SHARE) {
			return null;
		}
		String job = job(giver);
		Tale tale = pick("giver", job, null, level.getRandom());
		if (tale == null) {
			return null;
		}
		return new Quest("offer:" + tale.id, Quest.TALE, QUEST + tale.id, new Quest.Giver(giver.getUUID(), giver.getName().getString(), f.id(), false, job),
			new Quest.Objective("talk", 1, 0, Optional.empty(), Optional.empty(), "", "", false),
			new Quest.Reward(0, List.of(), Optional.empty(), Optional.empty(), 0, 0, 0), 0L);
	}

	/**
	 * {@code player} asks {@code giver} about the work they have, and it's a tale ({@code offer}): the giver starts
	 * telling it. False if this tale isn't for this player (done already, or barred to them): they've nothing for them.
	 */
	static boolean begin(ServerLevel level, ServerPlayer player, LivingEntity giver, Faction f, Quest offer) {
		Tale tale = Tale.get(offer.template().substring(QUEST.length()));
		if (tale == null || !fits(player, tale)) {
			return false;
		}
		start(level, player, tale, giver, f.id(), giver.blockPosition(), giver);
		return true;
	}

	/** A new run of {@code tale} for {@code player}, asked by {@code giver} (or nobody), from {@code home}; its first step is entered. */
	private static Run start(ServerLevel level, ServerPlayer player, Tale tale, @Nullable LivingEntity giver, int faction, BlockPos home,
		@Nullable LivingEntity speaker) {
		RandomSource random = level.getRandom();
		Map<String, String> vars = tale.roll(random);
		vars.put("~day", Long.toString(day(level)));
		Map<String, String> names = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : Tale.object(tale.json, "cast").entrySet()) {
			List<String> choices = e.getValue().isJsonObject() ? Tale.strings(e.getValue().getAsJsonObject().get("name")) : List.of();
			if (!choices.isEmpty()) {
				names.put(e.getKey(), choices.get(random.nextInt(choices.size())));
			}
		}
		Run run = new Run(UUID.randomUUID().toString(), tale.id, tale.first(), giver == null ? "" : giver.getUUID().toString(),
			giver == null ? "" : giver.getName().getString(), giver == null ? "" : job(giver), faction, home.asLong(), -1L, false, vars, Map.of(), names,
			Map.of(), Map.of(), List.of());
		state(player).runs.add(run);
		enter(player, run, speaker);
		Quests.sync(player, true);
		return run;
	}

	private static int factionAt(ServerLevel level, BlockPos at) {
		Faction f = Factions.around(level.getServer(), level, at, Villages.VILLAGE_RADIUS);
		if (f == null) {
			f = Factions.at(level.getServer(), level, at);
		}
		return f == null || f.fallen() ? -1 : f.id();
	}

	/**
	 * {@code teller} has a rumour for {@code player} ({@link Rumours#tell}): now and then it's a tale's. Said in a bubble;
	 * nothing is written down until the player goes and looks. True if one was told.
	 */
	static boolean rumour(ServerLevel level, Entity teller, ServerPlayer player) {
		if (level != Factions.level(level.getServer()) || level.getRandom().nextFloat() >= RUMOUR_CHANCE) {
			return false;
		}
		Tale tale = pick("rumour", null, player, level.getRandom());
		if (tale == null) {
			return false;
		}
		Run run = start(level, player, tale, teller instanceof LivingEntity l ? l : null, factionAt(level, teller.blockPosition()), teller.blockPosition(), null);
		MutableComponent said = words(player, run, tale.start(), "rumour");
		if (said != null) {
			Speech.tell(level, teller, player, said, Speech.GOLD);
		}
		return true;
	}

	// ---- books ---------------------------------------------------------------------------------------------------------------

	/** The book that starts {@code tale}. */
	static ItemStack book(Tale tale) {
		JsonObject b = Tale.object(tale.start(), "book");
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = new ArrayList<>();
		for (String page : Tale.strings(b.get("pages"))) {
			pages.add(Filterable.passThrough(Component.literal(page)));
		}
		String title = Tale.string(b, "title", tale.title());
		String shortTitle = net.minecraft.util.StringUtil.truncateStringIfNecessary(title, WrittenBookContent.TITLE_MAX_LENGTH, false);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(shortTitle), Tale.string(b, "author", ""), 0, pages, true));
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY_BOOK, tale.id);
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return book;
	}

	/** A tale's book found {@code where} ("ruin"), or on {@code mob}; empty if no tale writes one there. */
	static ItemStack bookFor(String where, @Nullable Entity mob, RandomSource random) {
		List<Tale> suit = new ArrayList<>();
		for (Tale tale : Tale.all()) {
			if (!tale.by().equals("book")) {
				continue;
			}
			JsonObject b = Tale.object(tale.start(), "book");
			for (String found : Tale.strings(b.get("found"))) {
				if (mob == null ? found.equals(where)
					: found.startsWith("mob:") && Quests.matches(found.substring(4), mob) && random.nextDouble() < Tale.decimal(b, "chance", 0.05)) {
					suit.add(tale);
					break;
				}
			}
		}
		return suit.isEmpty() ? ItemStack.EMPTY : book(suit.get(random.nextInt(suit.size())));
	}

	private static String tagged(ItemStack stack, String key) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(key, "");
	}

	/** {@code player} opens a book: a tale's book starts its tale, where they stand, the first time they read it. */
	private static void read(ServerPlayer player, ItemStack stack) {
		Tale tale = Tale.get(tagged(stack, KEY_BOOK));
		if (tale == null || !(player.level() instanceof ServerLevel level) || level != Factions.level(level.getServer()) || !fits(player, tale)) {
			return;
		}
		start(level, player, tale, null, factionAt(level, player.blockPosition()), player.blockPosition(), null);
	}

	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer && !(entity instanceof ServerPlayer)) {
			ItemStack book = bookFor("", entity, level.getRandom());
			if (!book.isEmpty()) {
				entity.spawnAtLocation(level, book);
			}
		}
		// (the one who asked is dead: what they asked dies with them)
		String id = entity.getUUID().toString();
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			State s = stateOrNull(p);
			if (s == null) {
				continue;
			}
			for (Run run : new ArrayList<>(s.runs)) {
				if (run.giver.equals(id) && TaleCast.cast(entity) == null) {
					close(p, run, "", Component.translatable("tale.jaymoskingdoms.giver_died", run.giverName).getString(), true, QuestPast.FAILED);
				}
			}
		}
	}

	// ---- letters -------------------------------------------------------------------------------------------------------------

	private static ItemStack letter(ServerPlayer player, Run run, JsonObject letter, String key) {
		List<Component> lines = new ArrayList<>();
		for (String line : Tale.strings(Tale.pick(letter.get("lines"), facts(player, run)))) {
			lines.add(text(player, run, line));
		}
		ItemStack stack = Letters.sealed(new Letters.Letter(Post.nameOf(player).getString(), text(player, run, Tale.string(letter, "from", "")), lines, Optional.empty()));
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY_LETTER, key);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static boolean holdsLetter(ServerPlayer player, String key) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (key.equals(tagged(inv.getItem(i), KEY_LETTER))) {
				return true;
			}
		}
		return false;
	}

	/** {@code player} breaks the seal on a letter ({@link Letters}): a tale's letter starts it, or wakes the tale that was resting on it. */
	static void opened(ServerPlayer player, ItemStack stack) {
		String key = tagged(stack, KEY_LETTER);
		if (key.isEmpty() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		State s = state(player);
		if (key.startsWith("start:")) {
			Tale tale = Tale.get(key.substring(6));
			if (tale != null && s.due.remove(tale.id) != null && fits(player, tale)) {
				start(level, player, tale, null, factionAt(level, player.blockPosition()), player.blockPosition(), null);
			}
			return;
		}
		Run run = run(player, key);
		Tale tale = run == null ? null : Tale.get(run.tale);
		JsonObject step = tale == null ? null : tale.step(run.step);
		if (step != null && Tale.string(step, "do", "").equals("wait") && Tale.string(step, "wake", "none").equals("letter") && run.scratch.containsKey("sent")) {
			go(player, run, step.get("then"), null);
		}
	}

	// ---- steps -----------------------------------------------------------------------------------------------------------------

	/** Everyone who's in {@code run} with {@code player} and at the same step: them, and their party's members online. */
	private static List<ServerPlayer> sharers(ServerPlayer player, Run run) {
		List<ServerPlayer> out = new ArrayList<>();
		out.add(player);
		for (ServerPlayer p : Parties.members(player)) {
			Run theirs = p == player ? null : run(p, run.instance);
			if (theirs != null && theirs.step.equals(run.step)) {
				out.add(p);
			}
		}
		return out;
	}

	/** {@code change} made to {@code run} for {@code player} and to the same run of everyone sharing it. */
	static void each(ServerPlayer player, Run run, Consumer<Run> change) {
		for (ServerPlayer p : sharers(player, run)) {
			Run theirs = p == player ? run : run(p, run.instance);
			if (theirs != null) {
				change.accept(theirs);
			}
		}
	}

	/**
	 * {@code run} moves on to {@code then} (a step's id, {@code end:<ending>}, or a switch to one), for {@code player} and
	 * everyone sharing it. {@code speaker}: whoever the player was just speaking with (a talk with them goes straight on).
	 */
	static void go(ServerPlayer player, Run run, @Nullable JsonElement then, @Nullable LivingEntity speaker) {
		JsonElement picked = Tale.pick(then, facts(player, run));
		String target = picked != null && picked.isJsonPrimitive() ? picked.getAsString() : "";
		if (target.isEmpty()) {
			JaymosKingdoms.LOGGER.warn("Tale {}: step {} leads nowhere", run.tale, run.step);
			return;
		}
		for (ServerPlayer p : sharers(player, run)) {
			Run theirs = p == player ? run : run(p, run.instance);
			if (theirs == null) {
				continue;
			}
			leave(p, theirs);
			if (target.startsWith("end:")) {
				end(p, theirs, target.substring(4));
			} else {
				theirs.step = target;
				enter(p, theirs, p == player ? speaker : null);
			}
			Quests.sync(p, true);
		}
	}

	/** The open step is finished: its line goes in the journal, its mark is set, its wages paid, its quest taken down. */
	private static void leave(ServerPlayer player, Run run) {
		Tale tale = Tale.get(run.tale);
		JsonObject step = tale == null ? null : tale.step(run.step);
		if (step != null && player.level() instanceof ServerLevel level) {
			MutableComponent line = words(player, run, step, "journal");
			if (line != null) {
				note(run, line.getString());
			}
			String mark = Tale.string(step, "mark", "");
			if (!mark.isEmpty()) {
				state(player).marks.add(mark);
			}
			int pay = Tale.pickNumber(step, "pay", facts(player, run), 0);
			if (pay > 0) {
				Money.pay(player, faction(player, run), Money.worth(pay));
			}
			Quest q = Quests.quest(player, run.quest());
			if (q != null && q.kind() >= Quest.PACK) {
				QuestJobs.over(level, player, q);
			}
		}
		Quests.drop(player, run.quest());
		run.scratch.clear();
		run.wake = -1L;
	}

	/** {@code run}'s step is begun: what it hands over is handed over, its rest is counted from today, its quest goes in the log. */
	private static void enter(ServerPlayer player, Run run, @Nullable LivingEntity speaker) {
		Tale tale = Tale.get(run.tale);
		JsonObject step = tale == null ? null : tale.step(run.step);
		if (step == null || !(player.level() instanceof ServerLevel level)) {
			JaymosKingdoms.LOGGER.warn("Tale {} has no step {}: the run is dropped", run.tale, run.step);
			state(player).runs.remove(run);
			return;
		}
		String kind = Tale.string(step, "do", "");
		boolean hidden = Tale.flag(step, "hidden");
		String who = Tale.pickString(step, "who", facts(player, run), "");
		boolean hearing = kind.equals("talk") && who.equals("giver") && (tale.by().equals("giver") || tale.by().equals("companion"));
		if (!run.taken && !hidden && !hearing) {
			take(level, player, run, tale);
		}
		for (JsonObject give : Tale.objects(step.get("give"))) {
			give(player, thing(player, run, give));
		}
		if (kind.equals("wait")) {
			run.wake = day(level) + Tale.between(step.get("days"), 1, level.getRandom());
		} else if (!hidden && run.taken) {
			Quests.set(player, quest(level, player, run, tale, step, kind));
		}
		if (kind.equals("talk") && speaker != null && is(run, who, speaker)) {
			say(player, run, step, speaker, 0);
		}
	}

	/** The tale is the player's now: the asker's offer is gone, and the party's members near are in it too. */
	private static void take(ServerLevel level, ServerPlayer player, Run run, Tale tale) {
		run.taken = true;
		LivingEntity giver = entity(level, run.giverId());
		if (giver != null && tale.by().equals("giver")) {
			Quests.withdraw(giver, QUEST);
		}
		player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.accepted", Component.literal(tale.title())).withStyle(ChatFormatting.YELLOW));
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		for (ServerPlayer p : Parties.near(player, Parties.SHARE_RANGE)) {
			if (run(p, run.instance) != null || !fits(p, tale)) {
				continue;
			}
			Run theirs = new Run(run.instance, run.tale, run.step, run.giver, run.giverName, run.job, run.faction, run.home, run.wake, true, run.vars, run.cast,
				run.names, Map.of(), run.places, run.notes);
			state(p).runs.add(theirs);
			p.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.accepted", Component.literal(tale.title())).withStyle(ChatFormatting.YELLOW));
			enter(p, theirs, null);
			Quests.sync(p, true);
		}
	}

	static @Nullable LivingEntity entity(ServerLevel level, @Nullable UUID id) {
		return id != null && level.getEntity(id) instanceof LivingEntity l && l.isAlive() ? l : null;
	}

	/** Whether {@code who} is {@code run}'s {@code role} (the giver, or one of its cast). */
	static boolean is(Run run, String role, Entity who) {
		String id = who.getUUID().toString();
		return role.equals("giver") ? id.equals(run.giver) : id.equals(run.cast.get(role));
	}

	private static ItemStack thing(ServerPlayer player, Run run, JsonObject spec) {
		Identifier id = Identifier.tryParse(Tale.string(spec, "item", "minecraft:paper"));
		Item item = id == null ? Items.PAPER : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PAPER);
		ItemStack stack = new ItemStack(item, Math.max(1, Tale.number(spec, "count", 1)));
		String name = Tale.string(spec, "name", "");
		if (!name.isEmpty()) {
			stack.set(DataComponents.CUSTOM_NAME, text(player, run, name).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
		}
		List<Component> lore = new ArrayList<>();
		for (String line : Tale.strings(spec.get("lore"))) {
			lore.add(text(player, run, line).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
		}
		if (!lore.isEmpty()) {
			stack.set(DataComponents.LORE, new ItemLore(lore));
		}
		return stack;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	/** The place a step happens at: the place it names, or where the one it's about waits. */
	private static @Nullable String placeKey(ServerPlayer player, Run run, Tale tale, JsonObject step, String kind) {
		Predicate<String> facts = facts(player, run);
		String key = switch (kind) {
			case "go", "escort" -> Tale.pickString(step, "to", facts, "");
			case "talk", "bring", "foe" -> {
				JsonObject role = tale.role(Tale.pickString(step, "who", facts, ""));
				yield role == null ? "" : Tale.string(role, "at", "");
			}
			default -> Tale.pickString(step, "at", facts, "");
		};
		return key.isEmpty() ? null : key;
	}

	/** The open step as a quest for the log. */
	private static Quest quest(ServerLevel level, ServerPlayer player, Run run, Tale tale, JsonObject step, String kind) {
		Predicate<String> facts = facts(player, run);
		String who = Tale.pickString(step, "who", facts, "");
		String key = placeKey(player, run, tale, step, kind);
		Optional<BlockPos> pos = key != null ? Optional.of(place(level, run, tale, key))
			: who.equals("giver") || kind.equals("defend") || kind.equals("lamps") ? Optional.of(BlockPos.of(run.home)) : Optional.empty();
		UUID giver = run.giverId();
		Optional<UUID> other = who.equals("giver") || kind.equals("defend") ? Optional.ofNullable(giver) : Optional.ofNullable(uuid(run.cast.get(who)));
		String otherName = who.equals("giver") ? run.giverName : run.names.getOrDefault(who, "");
		int count = Tale.pickNumber(step, kind.equals("defend") ? "waves" : "count", facts, kind.equals("defend") ? 3 : 1);
		int questKind = switch (kind) {
			case "slay" -> Quest.SLAY;
			case "defend" -> Quest.DEFEND;
			case "escort" -> Quest.TREK;
			case "pack" -> Quest.PACK;
			case "build" -> Quest.BUILD;
			case "blast" -> Quest.BLAST;
			case "stash" -> Quest.STASH;
			case "lamps" -> Quest.LAMPS;
			default -> Quest.TALE;
		};
		String target = switch (kind) {
			case "slay" -> Tale.pickString(step, "target", facts, "*monster");
			case "pack", "defend" -> Tale.pickString(step, "foes", facts, "minecraft:zombie");
			case "build" -> Tale.pickString(step, "blocks", facts, "#minecraft:fences");
			case "stash", "bring" -> Tale.pickString(step, "item", facts, "minecraft:bread");
			default -> kind;
		};
		return new Quest(run.quest(), questKind, QUEST + tale.id,
			new Quest.Giver(giver == null ? new UUID(0L, 0L) : giver, run.giverName, run.faction, false, run.job),
			new Quest.Objective(target, Math.max(1, count), 0, pos, other, otherName, key == null ? "" : placeName(tale, key), false),
			new Quest.Reward(0, List.of(), Optional.empty(), Optional.empty(), 0, 0, 0), level.getGameTime());
	}

	// ---- what the log shows (Quests asks) -----------------------------------------------------------------------------------

	static Component title(Quest q) {
		Tale tale = Tale.get(q.template().substring(QUEST.length()));
		return Component.literal(tale == null ? q.template() : tale.title());
	}

	/** What a tale's open step asks, for the tracker and the journal. */
	static Component goal(ServerPlayer player, Quest q) {
		Run run = runOf(player, q);
		Tale tale = run == null ? null : Tale.get(run.tale);
		JsonObject step = tale == null ? null : tale.step(run.step);
		if (step == null) {
			return Component.empty();
		}
		MutableComponent goal = words(player, run, step, "goal");
		Quest.Objective o = q.objective();
		if (goal == null) {
			String kind = Tale.string(step, "do", "");
			goal = Component.translatable("tale.jaymoskingdoms.goal." + kind, o.otherName().isEmpty() ? o.place() : o.otherName(), o.place());
		}
		if (o.count() > 1 && (q.kind() == Quest.SLAY || q.kind() >= Quest.PACK)) {
			goal.append(Component.literal(" (" + o.progress() + "/" + o.count() + ")"));
		}
		return goal;
	}

	/** Whose head the mark goes over for a tale's step: the one to speak with, if the step has one. */
	static UUID markOn(Quest q) {
		return q.kind() == Quest.TALE && q.objective().other().isPresent() ? q.objective().other().get() : q.giver().uuid();
	}

	/** Whether a tale's step is one to go and speak with someone about (their mark shows gold). */
	static boolean spoken(Quest q) {
		return q.kind() == Quest.TALE && (q.objective().target().equals("talk") || q.objective().target().equals("bring")) && q.objective().other().isPresent();
	}

	/** How hard a tale's job step is, one to five stars. */
	static int stars(ServerPlayer player, Quest q) {
		Run run = runOf(player, q);
		Tale tale = run == null ? null : Tale.get(run.tale);
		JsonObject step = tale == null ? null : tale.step(run.step);
		return step == null ? 2 : Math.clamp(Tale.pickNumber(step, "stars", facts(player, run), 2), 1, 5);
	}

	/** ({@link QuestJobs}) A tale's job step is done. */
	static void jobDone(ServerPlayer player, Quest q) {
		Run run = runOf(player, q);
		Tale tale = run == null ? null : Tale.get(run.tale);
		JsonObject step = tale == null ? null : tale.step(run.step);
		if (step != null) {
			go(player, run, step.get("then"), null);
		}
	}

	/** ({@link QuestJobs}) A tale's job step failed: the tale goes where the step says a failure leads, or the step is set again. */
	static void jobFailed(ServerPlayer player, Quest q) {
		Run run = runOf(player, q);
		Tale tale = run == null ? null : Tale.get(run.tale);
		JsonObject step = tale == null ? null : tale.step(run.step);
		if (step == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (step.has("failed")) {
			go(player, run, step.get("failed"), null);
			return;
		}
		for (ServerPlayer p : sharers(player, run)) {
			Run theirs = p == player ? run : run(p, run.instance);
			if (theirs != null) {
				Quests.set(p, quest(level, p, theirs, tale, step, Tale.string(step, "do", "")));
				Quests.sync(p, true);
			}
		}
	}

	// ---- talking ---------------------------------------------------------------------------------------------------------------

	private static boolean waking(ServerLevel level, Run run, JsonObject step, String kind) {
		return kind.equals("wait") && Tale.string(step, "wake", "none").equals("giver") && run.wake >= 0 && day(level) >= run.wake;
	}

	/** Whether {@code who} has a part in one of {@code player}'s tales to speak just now (so using them is for the tale). */
	static boolean pending(ServerPlayer player, LivingEntity who) {
		State s = stateOrNull(player);
		if (s == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		for (Run run : s.runs) {
			Tale tale = Tale.get(run.tale);
			JsonObject step = tale == null ? null : tale.step(run.step);
			if (step == null) {
				continue;
			}
			String kind = Tale.string(step, "do", "");
			String role = Tale.pickString(step, "who", facts(player, run), "");
			if (waking(level, run, step, kind) && is(run, "giver", who)
				|| (kind.equals("talk") || kind.equals("bring")) && run.taken && is(run, role, who)
				|| kind.equals("foe") && is(run, role, who) && TaleCast.yielded(who)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code giver} should wear the gold ! for a tale: a rest that's over and theirs to wake, or a companion with something to ask. */
	static boolean wants(LivingEntity giver) {
		if (!(giver.level() instanceof ServerLevel level)) {
			return false;
		}
		String id = giver.getUUID().toString();
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			State s = stateOrNull(p);
			if (s == null) {
				continue;
			}
			if (!s.ask.isEmpty() && s.ask.split("\\|")[1].equals(id)) {
				return true;
			}
			for (Run run : s.runs) {
				if (!run.giver.equals(id)) {
					continue;
				}
				Tale tale = Tale.get(run.tale);
				JsonObject step = tale == null ? null : tale.step(run.step);
				if (step != null && waking(level, run, step, Tale.string(step, "do", ""))) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * {@code player} speaks with {@code who}: if one of their tales has something for the two of them, it's said. True if
	 * it was the tale's talk (the caller does nothing more).
	 */
	static boolean talk(ServerPlayer player, LivingEntity who) {
		State s = stateOrNull(player);
		if (s == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		for (Run run : new ArrayList<>(s.runs)) {
			Tale tale = Tale.get(run.tale);
			JsonObject step = tale == null ? null : tale.step(run.step);
			if (step == null) {
				continue;
			}
			String kind = Tale.string(step, "do", "");
			String role = Tale.pickString(step, "who", facts(player, run), "");
			if (waking(level, run, step, kind) && is(run, "giver", who)) {
				go(player, run, step.get("then"), who);
				return true;
			}
			if (kind.equals("talk") && is(run, role, who)) {
				say(player, run, step, who, 0);
				return true;
			}
			if (kind.equals("bring") && is(run, role, who)) {
				bring(level, player, run, step, who);
				return true;
			}
			if (kind.equals("foe") && is(run, role, who) && TaleCast.yielded(who)) {
				plea(player, run, step, who);
				return true;
			}
		}
		// a companion with something to ask
		if (!s.ask.isEmpty()) {
			String[] ask = s.ask.split("\\|");
			Tale tale = Tale.get(ask[0]);
			if (ask[1].equals(who.getUUID().toString())) {
				s.ask = "";
				if (tale != null && fits(player, tale)) {
					start(level, player, tale, who, factionAt(level, who.blockPosition()), who.blockPosition(), who);
					return true;
				}
			}
		}
		// how it goes with what they asked
		if (!OTHER_WORK.remove(player.getUUID())) {
			for (Run run : s.runs) {
				if (run.taken && is(run, "giver", who)) {
					howGoes(player, run, who);
					return true;
				}
			}
		}
		return false;
	}

	private static boolean open(ServerPlayer player, Run run, String step) {
		State s = stateOrNull(player);
		return s != null && s.runs.contains(run) && run.step.equals(step);
	}

	/** {@code who} says page {@code page} of the talk; the last page offers the replies. */
	private static void say(ServerPlayer player, Run run, JsonObject step, LivingEntity who, int page) {
		Predicate<String> facts = facts(player, run);
		List<String> pages = Tale.strings(Tale.pick(step.get("say"), facts));
		if (pages.isEmpty()) {
			go(player, run, step.get("then"), who);
			return;
		}
		String at = run.step;
		int i = Math.min(page, pages.size() - 1);
		List<Menus.Choice> choices = new ArrayList<>();
		if (i < pages.size() - 1) {
			choices.add(Menus.Choice.of(Component.translatable("tale.jaymoskingdoms.go_on"), p -> {
				if (open(p, run, at)) {
					say(p, run, step, who, i + 1);
				}
			}));
		} else {
			for (JsonObject reply : Tale.objects(step.get("replies"))) {
				String cond = Tale.string(reply, "if", "");
				if (!cond.isEmpty() && !facts.test(cond)) {
					continue;
				}
				choices.add(Menus.Choice.of(text(player, run, Tale.string(reply, "say", "...")), affords(player, run, reply), p -> {
					if (open(p, run, at)) {
						reply(p, run, step, reply, who);
					}
				}));
			}
			if (choices.isEmpty()) {
				choices.add(Menus.Choice.of(Component.translatable("tale.jaymoskingdoms.go_on"), p -> {
					if (open(p, run, at)) {
						go(p, run, step.get("then"), who);
					}
				}));
			}
		}
		TaleCast.hold(who, player);
		Menus.ask(player, who, text(player, run, pages.get(i)), choices);
	}

	private static @Nullable Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
	}

	private static int count(ServerPlayer player, Item item) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item) && tagged(s, KEY_CLUE).isEmpty() && Quests.questOf(s) == null) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static void take(ServerPlayer player, Item item, int n) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item) && tagged(s, KEY_CLUE).isEmpty() && Quests.questOf(s) == null) {
				int m = Math.min(n, s.getCount());
				s.shrink(m);
				n -= m;
			}
		}
	}

	/** Whether {@code player} has what {@code reply} costs (an item, coins). */
	private static boolean affords(ServerPlayer player, Run run, JsonObject reply) {
		JsonObject needs = Tale.object(reply, "needs_item");
		Item item = needs.has("item") ? item(Tale.string(needs, "item", "")) : null;
		if (item != null && count(player, item) < Math.max(1, Tale.number(needs, "count", 1))) {
			return false;
		}
		int coins = Tale.number(reply, "needs_coins", 0);
		return coins <= 0 || Money.canPay(player, faction(player, run), Money.worth(coins));
	}

	private static void reply(ServerPlayer player, Run run, JsonObject step, JsonObject reply, LivingEntity who) {
		if (!affords(player, run, reply)) {
			return;
		}
		JsonObject needs = Tale.object(reply, "needs_item");
		Item item = needs.has("item") ? item(Tale.string(needs, "item", "")) : null;
		if (item != null) {
			take(player, item, Math.max(1, Tale.number(needs, "count", 1)));
		}
		int coins = Tale.number(reply, "needs_coins", 0);
		if (coins > 0) {
			Money.charge(player, faction(player, run), Money.worth(coins));
		}
		String mark = Tale.string(reply, "mark", "");
		MutableComponent line = words(player, run, reply, "journal");
		each(player, run, r -> {
			if (line != null) {
				note(r, line.getString());
			}
		});
		if (!mark.isEmpty()) {
			for (ServerPlayer p : sharers(player, run)) {
				state(p).marks.add(mark);
			}
		}
		go(player, run, reply.has("then") ? reply.get("then") : step.get("then"), who);
	}

	private static void bring(ServerLevel level, ServerPlayer player, Run run, JsonObject step, LivingEntity who) {
		Predicate<String> facts = facts(player, run);
		Item item = item(Tale.pickString(step, "item", facts, ""));
		int count = Math.max(1, Tale.pickNumber(step, "count", facts, 1));
		if (item == null || count(player, item) < count) {
			Speech.tell(level, who, player, Component.translatable("tale.jaymoskingdoms.bring.short", count,
				item == null ? Component.empty() : new ItemStack(item).getHoverName()), Speech.WHITE);
			return;
		}
		take(player, item, count);
		MutableComponent thanks = words(player, run, step, "say");
		if (thanks != null) {
			Speech.tell(level, who, player, thanks, Speech.GOLD);
		}
		go(player, run, step.get("then"), who);
	}

	/** A beaten foe asks for their life: the player grants it, or says nothing (and may finish it, or walk away). */
	private static void plea(ServerPlayer player, Run run, JsonObject step, LivingEntity who) {
		String at = run.step;
		MutableComponent plea = words(player, run, step, "plea");
		MutableComponent mercy = words(player, run, step, "mercy");
		List<Menus.Choice> choices = new ArrayList<>();
		choices.add(Menus.Choice.of(mercy != null ? mercy : Component.translatable("tale.jaymoskingdoms.mercy"), p -> {
			if (open(p, run, at)) {
				foeDone(p, run, who, false);
			}
		}));
		choices.add(Menus.Choice.of(Component.translatable("tale.jaymoskingdoms.silence"), p -> {
		}));
		Menus.ask(player, who, plea != null ? plea : Component.translatable("tale.jaymoskingdoms.plea"), choices);
	}

	/**
	 * ({@link TaleCast}) The foe of {@code run}'s open step is dead ({@code slain}) or was let go: the tale goes on the
	 * way the step says for each, and it's remembered (a foe spared remembers it; one cut down after yielding is seen).
	 */
	static void foeDone(ServerPlayer player, Run run, @Nullable LivingEntity foe, boolean slain) {
		Tale tale = Tale.get(run.tale);
		JsonObject step = tale == null ? null : tale.step(run.step);
		if (step == null || !Tale.string(step, "do", "").equals("foe") || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		String role = Tale.pickString(step, "who", facts(player, run), "");
		String name = run.names.getOrDefault(role, role);
		boolean yielded = foe != null && TaleCast.yielded(foe);
		if (!slain && foe != null) {
			MutableComponent thanks = words(player, run, step, "thanks");
			if (thanks != null) {
				Speech.tell(level, foe, player, thanks, Speech.WHITE);
			}
			Recollections.remember(foe, player, Recollections.Kind.SPARED, Component.empty());
			each(player, run, r -> r.vars.put("~spared", name));
		} else if (slain && yielded && foe != null) {
			// (cut down after they'd yielded: whoever saw it won't forget)
			for (LivingEntity seen : level.getEntitiesOfClass(LivingEntity.class, foe.getBoundingBox().inflate(TaleCast.WITNESS_RANGE),
				e -> e != foe && e.isAlive() && (e instanceof Villager || TaleCast.cast(e) != null) && Temperaments.humanoid(e))) {
				Recollections.remember(seen, player, Recollections.Kind.CUT_DOWN, Component.literal(name));
			}
			each(player, run, r -> r.vars.put("~cut_down", name));
		}
		JsonElement then = slain || !step.has("spared") ? step.get("slain") : step.get("spared");
		go(player, run, then, null);
	}

	private static void howGoes(ServerPlayer player, Run run, LivingEntity giver) {
		List<Menus.Choice> choices = new ArrayList<>();
		choices.add(Menus.Choice.of(Component.translatable("tale.jaymoskingdoms.howgoes.still"), p -> {
		}));
		choices.add(Menus.Choice.of(Component.translatable("tale.jaymoskingdoms.howgoes.other"), p -> {
			OTHER_WORK.add(p.getUUID());
			Quests.openLater(p, giver);
		}));
		choices.add(Menus.Choice.of(Component.translatable("tale.jaymoskingdoms.howgoes.give_up"), p -> {
			State s = stateOrNull(p);
			if (s != null && s.runs.contains(run) && p.level() instanceof ServerLevel level) {
				Speech.tell(level, giver, p, Component.translatable("tale.jaymoskingdoms.howgoes.given_up"), Speech.RED);
				giveUp(level, p, run);
			}
		}));
		Tale tale = Tale.get(run.tale);
		Menus.ask(player, giver, Component.translatable("tale.jaymoskingdoms.howgoes", Component.literal(tale == null ? "" : tale.title())), choices);
	}

	/** {@code player} walks out on {@code run}: it's written down as given up, and the asker's settlement thinks less of them. */
	static void giveUp(ServerLevel level, ServerPlayer player, Run run) {
		Faction f = faction(player, run);
		if (f != null) {
			Reputation.change(player, f, -ABANDON_REPUTATION);
		}
		close(player, run, "given_up", Component.translatable("tale.jaymoskingdoms.gave_up").getString(), true, QuestPast.GIVEN_UP);
	}

	// ---- the watch ---------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % WATCH_TICKS != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long today = day(level);
		for (ServerPlayer player : new ArrayList<>(level.players())) {
			State s = stateOrNull(player);
			Long last = DAY.put(player.getUUID(), today);
			if (last == null || last != today) {
				daily(level, player, today);
				s = stateOrNull(player);
			}
			if (s == null || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			for (Run run : new ArrayList<>(s.runs)) {
				if (s.runs.contains(run)) {
					watch(level, player, s, run, tick, today);
				}
			}
			visits(level, player, s, today);
		}
	}

	private static void watch(ServerLevel level, ServerPlayer player, State s, Run run, int tick, long today) {
		Tale tale = Tale.get(run.tale);
		JsonObject step = tale == null ? null : tale.step(run.step);
		if (step == null) {
			// (its file is gone, or changed under it)
			Quests.drop(player, run.quest());
			s.runs.remove(run);
			return;
		}
		String kind = Tale.string(step, "do", "");
		String before = run.step;
		switch (kind) {
			case "go" -> {
				BlockPos at = place(level, run, tale, Tale.pickString(step, "to", facts(player, run), ""));
				if (flat(player, at) <= Tale.number(step, "radius", GO_RADIUS)) {
					go(player, run, step.get("then"), null);
				}
			}
			case "search" -> search(level, player, run, tale, step, tick);
			case "slay" -> {
				Quest q = Quests.quest(player, run.quest());
				if (q != null && q.counted()) {
					go(player, run, step.get("then"), null);
				}
			}
			case "wait" -> rest(level, player, run, step, today);
			default -> {
			}
		}
		if (s.runs.contains(run) && run.step.equals(before)) {
			TaleCast.tend(level, player, run, tale, step, kind);
			// (an escort's charge is known once they've been set out)
			Quest q = Quests.quest(player, run.quest());
			if (q != null && q.kind() == Quest.TREK && q.objective().other().isEmpty()) {
				UUID charge = uuid(run.cast.get(Tale.pickString(step, "who", facts(player, run), "")));
				if (charge != null) {
					Quest.Objective o = q.objective();
					Quests.put(player, q.withObjective(new Quest.Objective(o.target(), o.count(), o.progress(), o.pos(), Optional.of(charge), o.otherName(), o.place(), o.spawned())));
				}
			}
		}
	}

	/** A rest: when its days are up it goes on by itself, or a runner sets out with its letter (the asker's ! is {@link #wants}). */
	private static void rest(ServerLevel level, ServerPlayer player, Run run, JsonObject step, long today) {
		if (run.wake < 0 || today < run.wake) {
			return;
		}
		String wake = Tale.string(step, "wake", "none");
		if (wake.equals("none")) {
			go(player, run, step.get("then"), null);
		} else if (wake.equals("letter")) {
			long sent = parse(run.scratch.getOrDefault("sent", "-1"), -1);
			boolean lost = sent >= 0 && today >= sent + LETTER_RESEND_DAYS && !holdsLetter(player, run.instance);
			if ((sent < 0 || lost) && TaleCast.canVisit(level, player)) {
				ItemStack letter = letter(player, run, Tale.object(step, "letter"), run.instance);
				TaleCast.runner(level, player, letter, () -> each(player, run, r -> r.scratch.put("sent", Long.toString(day(level)))));
			}
		}
	}

	/** A scene to search: its clues are laid when someone comes near, shown now and then, and counted as they're picked up. */
	private static void search(ServerLevel level, ServerPlayer player, Run run, Tale tale, JsonObject step, int tick) {
		Predicate<String> facts = facts(player, run);
		String key = Tale.pickString(step, "at", facts, "");
		BlockPos site = place(level, run, tale, key);
		if (flat(player, site) > SET_OUT_RANGE) {
			return;
		}
		site = ground(level, run, tale, key);
		if (site == null) {
			return;
		}
		List<JsonObject> clues = new ArrayList<>();
		for (JsonObject clue : Tale.objects(step.get("clues"))) {
			String cond = Tale.string(clue, "if", "");
			clues.add(cond.isEmpty() || facts.test(cond) ? clue : null);
		}
		Set<String> found = new HashSet<>(List.of(run.scratch.getOrDefault("found", "").split(",")));
		found.remove("");
		// what's in the pack now
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			String tag = tagged(inv.getItem(i), KEY_CLUE);
			int hash = tag.indexOf('#');
			if (hash < 0 || !tag.substring(0, hash).equals(run.instance + "/" + run.step)) {
				continue;
			}
			String n = tag.substring(hash + 1);
			int index = parse(n, -1);
			if (found.add(n) && index >= 0 && index < clues.size() && clues.get(index) != null) {
				JsonObject clue = clues.get(index);
				String line = Tale.string(clue, "journal", "");
				String all = String.join(",", found);
				each(player, run, r -> {
					r.scratch.put("found", all);
					if (!line.isEmpty()) {
						note(r, text(player, r, line).getString());
					}
				});
				level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.2F);
			}
		}
		int there = 0;
		for (JsonObject clue : clues) {
			there += clue == null ? 0 : 1;
		}
		int need = Math.min(there, Tale.pickNumber(step, "need", facts, there));
		if (found.size() >= need) {
			go(player, run, step.get("then"), null);
			return;
		}
		if (tick % CLUE_TICKS != 0) {
			return;
		}
		// the ones not yet found lie where they were left (laid again if something took them)
		String prefix = run.instance + "/" + run.step + "#";
		List<ItemEntity> lying = level.getEntitiesOfClass(ItemEntity.class, new AABB(site).inflate(CLUE_RANGE), e -> tagged(e.getItem(), KEY_CLUE).startsWith(prefix));
		for (int i = 0; i < clues.size(); i++) {
			JsonObject clue = clues.get(i);
			if (clue == null || found.contains(Integer.toString(i))) {
				continue;
			}
			String tag = prefix + i;
			ItemEntity lies = null;
			for (ItemEntity e : lying) {
				if (tagged(e.getItem(), KEY_CLUE).equals(tag)) {
					lies = e;
				}
			}
			if (lies == null) {
				ItemStack stack = thing(player, run, clue);
				stack.setCount(1);
				CompoundTag data = new CompoundTag();
				data.putString(KEY_CLUE, tag);
				stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
				stack.set(DataComponents.MAX_STACK_SIZE, 1);
				BlockPos at = Construction.standable(level, site.offset(level.getRandom().nextInt(CLUE_SPREAD * 2 + 1) - CLUE_SPREAD, 0,
					level.getRandom().nextInt(CLUE_SPREAD * 2 + 1) - CLUE_SPREAD), 3);
				lies = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, stack, 0.0, 0.0, 0.0);
				lies.setUnlimitedLifetime();
				lies.setNoPickUpDelay();
				level.addFreshEntity(lies);
			}
			level.sendParticles(player, ParticleTypes.END_ROD, true, false, lies.getX(), lies.getY() + 0.5, lies.getZ(), 2, 0.1, 0.25, 0.1, 0.0);
		}
	}

	// ---- each day ------------------------------------------------------------------------------------------------------------------

	private static void daily(ServerLevel level, ServerPlayer player, long today) {
		State s = stateOrNull(player);
		RandomSource random = level.getRandom();
		if (s != null) {
			// tales heard and never taken up are forgotten
			for (Run run : new ArrayList<>(s.runs)) {
				Tale tale = Tale.get(run.tale);
				long born = parse(run.vars.getOrDefault("~day", "0"), 0);
				if (!run.taken && today > born + (tale != null && tale.by().equals("rumour") ? RUMOUR_DAYS : UNTAKEN_DAYS)) {
					s.runs.remove(run);
				}
			}
			if (!s.ask.isEmpty() && today > parse(s.ask.split("\\|")[2], 0) + 2) {
				s.ask = "";
			}
		}
		// a letter that starts a tale is on its way
		if ((s == null || s.due.isEmpty()) && random.nextFloat() < LETTER_DAILY) {
			Tale tale = pick("letter", null, player, random);
			if (tale != null) {
				state(player).due.put(tale.id, today + Tale.between(Tale.object(tale.start(), "letter").get("days"), 2, random));
			}
		}
		// a close companion has something to ask
		s = stateOrNull(player);
		if ((s == null || s.ask.isEmpty()) && random.nextFloat() < COMPANION_DAILY) {
			List<Mob> close = Bonds.closeNear(player, COMPANION_RANGE);
			Tale tale = close.isEmpty() ? null : pick("companion", null, player, random);
			if (tale != null) {
				state(player).ask = tale.id + "|" + close.get(random.nextInt(close.size())).getUUID() + "|" + today;
			}
		}
	}

	/** Who's on their way to {@code player}: a runner with a letter that starts a tale, or someone come back from one that's over. */
	private static void visits(ServerLevel level, ServerPlayer player, State s, long today) {
		if (s.due.isEmpty() && s.returns.isEmpty() || !TaleCast.canVisit(level, player)) {
			return;
		}
		for (Map.Entry<String, Long> e : new ArrayList<>(s.due.entrySet())) {
			Tale tale = Tale.get(e.getKey());
			long due = e.getValue();
			if (tale == null) {
				s.due.remove(e.getKey());
				continue;
			}
			// (negative: delivered that day; sent again if it's lost)
			boolean lost = due < 0 && today >= -due + LETTER_RESEND_DAYS && !holdsLetter(player, "start:" + tale.id);
			if (due >= 0 && today >= due || lost) {
				Run blank = new Run("", tale.id, tale.first(), "", "", "", factionAt(level, player.blockPosition()), player.blockPosition().asLong(), -1L, false,
					Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());
				ItemStack letter = letter(player, blank, Tale.object(tale.start(), "letter"), "start:" + tale.id);
				TaleCast.runner(level, player, letter, () -> state(player).due.put(tale.id, -Math.max(1L, day(level))));
				return;
			}
		}
		for (Return back : new ArrayList<>(s.returns)) {
			if (today >= back.day() && TaleCast.comeBack(level, player, back)) {
				s.returns.remove(back);
				return;
			}
		}
	}

	// ---- endings -------------------------------------------------------------------------------------------------------------------

	private static boolean shared(ServerPlayer player, String instance) {
		for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
			if (p != player && in(p, instance)) {
				return true;
			}
		}
		return false;
	}

	/** {@code run} ends with the tale's ending {@code id}: paid, marked, remembered, told. */
	private static void end(ServerPlayer player, Run run, String id) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		Tale tale = Tale.get(run.tale);
		JsonObject ending = tale == null ? null : tale.ending(id);
		if (!run.taken) {
			// (turned down before it began: nothing's kept, and the asker has no more to say of it)
			state(player).runs.remove(run);
			LivingEntity giver = entity(level, run.giverId());
			if (giver != null) {
				Quests.withdraw(giver, QUEST);
			}
			return;
		}
		if (tale == null || ending == null) {
			close(player, run, id, "", false, QuestPast.DONE);
			return;
		}
		Predicate<String> facts = facts(player, run);
		State s = state(player);
		boolean failed = Tale.flag(ending, "failed");
		MutableComponent text = words(player, run, ending, "text");
		s.marks.addAll(Tale.strings(ending.get("marks")));
		JsonObject reward = Tale.object(ending, "reward");
		Quest q = new Quest(run.quest(), Quest.TALE, QUEST + tale.id,
			new Quest.Giver(run.giverId() == null ? new UUID(0L, 0L) : run.giverId(), run.giverName, run.faction, false, run.job),
			new Quest.Objective("end", 1, 1, Optional.empty(), Optional.empty(), "", "", false),
			new Quest.Reward(Tale.pickNumber(reward, "coins", facts, 0), Tale.strings(reward.get("items")),
				reward.has("book") ? Optional.of(Tale.string(reward, "book", "")) : Optional.empty(),
				reward.has("relic") ? Optional.of(Tale.string(reward, "relic", "")) : Optional.empty(),
				Tale.number(reward, "hearts", 0), Tale.number(reward, "reputation", 0), 0), level.getGameTime());
		Quests.payOut(level, player, q);
		LivingEntity giver = entity(level, run.giverId());
		if (giver != null) {
			if (q.reward().hearts() != 0) {
				McaCompat.hearts(giver, player, q.reward().hearts());
			}
			Recollections.remember(giver, player, failed ? Recollections.Kind.QUEST_FAILED : Recollections.Kind.QUEST_DONE, Component.literal(tale.title()));
			// (how it ended is the asker's to tell: who was let go)
			String spared = run.vars.get("~spared");
			if (spared != null) {
				Recollections.remember(giver, player, Recollections.Kind.MERCY, Component.literal(spared));
			}
		}
		// what the town says of it afterwards
		List<Component> town = new ArrayList<>();
		for (String line : Tale.strings(Tale.pick(ending.get("town"), facts))) {
			town.add(text(player, run, line));
		}
		if (!town.isEmpty() && run.faction >= 0) {
			Overheard.townTalk(player, run.faction, town);
		}
		// who comes back, and who stays
		String joins = Tale.string(ending, "joins", "");
		JsonObject returns = Tale.object(ending, "returns");
		String back = Tale.string(returns, "who", "");
		JsonObject body = back.isEmpty() ? null : tale.role(back);
		if (body != null) {
			MutableComponent say = words(player, run, returns, "say");
			s.returns.add(new Return(run.names.getOrDefault(back, back), body.toString(), day(level) + Tale.between(returns.get("days"), 5, level.getRandom()),
				Tale.string(returns, "as", "thanks"), say == null ? "" : say.getString(), Tale.strings(returns.get("gift")), run.faction));
		}
		if (!joins.isEmpty()) {
			TaleCast.join(level, player, run, joins);
		}
		level.playSound(null, player.blockPosition(), failed ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, failed ? 0.7F : 1.0F);
		if (!failed) {
			player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.complete", Component.literal(tale.title())).withStyle(ChatFormatting.GOLD));
		}
		close(player, run, id, text == null ? "" : text.getString(), failed, failed ? QuestPast.FAILED : QuestPast.DONE);
	}

	/** {@code run} is over, however it went: off the log, onto the Stories and Past pages, and its people go their ways. */
	private static void close(ServerPlayer player, Run run, String ending, String text, boolean failed, int how) {
		State s = state(player);
		if (!s.runs.remove(run)) {
			return;
		}
		Quest q = Quests.quest(player, run.quest());
		if (q != null && q.kind() >= Quest.PACK && player.level() instanceof ServerLevel level) {
			QuestJobs.over(level, player, q);
		}
		Quests.drop(player, run.quest());
		if (run.taken) {
			Tale tale = Tale.get(run.tale);
			String title = tale == null ? run.tale : tale.title();
			if (!ending.isEmpty()) {
				s.done.put(run.tale, ending);
			}
			s.told.add(0, new Told(run.tale, title, text, List.copyOf(run.notes), failed, player.level() instanceof ServerLevel level ? day(level) : 0L));
			while (s.told.size() > TOLD_MAX) {
				s.told.remove(s.told.size() - 1);
			}
			QuestPast.add(player, Component.literal(title), how, Component.literal(text));
		}
		if (player.level() instanceof ServerLevel level && !shared(player, run.instance)) {
			TaleCast.over(level, run, null);
		}
		Quests.sync(player, true);
	}

	// ---- the Stories page ------------------------------------------------------------------------------------------------------

	/** A tale as the journal's Stories page shows it: {@code state} 0 under way, 1 ended, 2 ended badly; {@code now} what's open (or how it ended). */
	public record Story(Component title, int state, Component now, List<Component> lines) {
	}

	/** {@code player}'s tales for the journal: the ones under way first, then the ones told, newest first. */
	static List<Story> stories(ServerPlayer player) {
		List<Story> out = new ArrayList<>();
		State s = stateOrNull(player);
		if (s == null || !(player.level() instanceof ServerLevel level)) {
			return out;
		}
		for (Run run : s.runs) {
			Tale tale = Tale.get(run.tale);
			JsonObject step = tale == null ? null : tale.step(run.step);
			if (step == null || !run.taken) {
				continue;
			}
			Quest q = Quests.quest(player, run.quest());
			Component now = q != null ? Quests.objective(level, player, q) : Component.translatable("tale.jaymoskingdoms.resting");
			List<Component> lines = new ArrayList<>();
			for (String note : run.notes) {
				lines.add(Component.literal(note));
			}
			out.add(new Story(Component.literal(tale.title()), 0, now, lines));
		}
		for (Told told : s.told) {
			List<Component> lines = new ArrayList<>();
			for (String note : told.notes()) {
				lines.add(Component.literal(note));
			}
			out.add(new Story(Component.literal(told.title()), told.failed() ? 2 : 1, Component.literal(told.ending()), lines));
		}
		return out;
	}
}

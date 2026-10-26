package com.jaymoskingdoms;

import com.jaymoskingdoms.remnants.Monoliths;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.216) Andy: "take inspiration from Bannerlord's intro asking about your life into adulthood and use the answers to
 * craft your character's heritage, spawn scenario, power affinity and class". A new player's first steps after MCA's
 * look and name: six questions about the life they led before the game ({@link Question}), every word our own. Where
 * they were born decides their heritage; who raised them and what they did as a child give a point in an attribute each
 * (and the child leans their heritage's look and, if fate is left to choose, their power); what they did growing up is
 * their class; "then one day..." is the power that will awaken in them (Jaymo's Powers' affinity, still after its usual
 * wait); and what sets them on the road now is where they wake ({@link Start}). The story page reads it all back
 * before it's sealed, for life, like the heritage and class it replaces at the start.
 *
 * <p>A start that echoes the power (a caving trip gone wrong and the spider bite, a shipwreck and the deep's song, a
 * monolith and the storm) counts as that power's calling: it wakes a day sooner. The heritage and class screens still
 * open for a Writ of Rebirth and for old players who never had a story. MCA's own "where do you start" page is skipped
 * ({@code client/mixin/mca/McaDestinySkipMixin}): the last question is where you start.
 */
public final class LifeStory {
	/** A capital start: how far from the realm's seat (blocks). */
	static final int CITY_MIN = 10, CITY_MAX = 28;
	/** A banished start: how far from the realm's seat you wake (blocks), and what they think of you after. */
	static final int BANISHED_MIN = 320, BANISHED_MAX = 480, BANISHED_REPUTATION = -60, RIVAL_REPUTATION = 40;
	/** A noble family's realm, and a sworn start's: their goodwill to begin with (at least this). */
	static final int NOBLE_REPUTATION = 50, SWORN_REPUTATION = 60;
	/** Alone in the wild: how far from the world's spawn (blocks). */
	static final int WILD_MIN = 600, WILD_MAX = 1200;
	/** Drawn to a monolith: how far from its foot you wake (blocks). */
	static final int MONOLITH_MIN = 36, MONOLITH_MAX = 56;
	/** How far a village or a wreck is looked for (chunks), and how close to it you wake (blocks). */
	static final int STRUCTURE_SEARCH = 100, STRUCTURE_NEAR = 14;
	/** A caving trip gone wrong: columns tried, how deep a hollow must lie (at most this Y), and the bite (ticks of Poison). */
	static final int CAVE_TRIES = 64, CAVE_MAX_Y = 40, CAVE_RADIUS = 96, BITE_TICKS = 140;
	/** (Andy: "poisoned, wounded, and barely breathing") the health you wake with, and how long the Slowness and Weakness last (ticks). */
	static final float CAVE_HEALTH = 4.0F;
	static final int CAVE_WOUND_TICKS = 1200;
	/** Spots tried for a dry, open place to stand before giving up on one. */
	static final int GROUND_TRIES = 16;
	/** "Let fate decide" on the power: how often the childhood's power gets the affinity anyway. */
	static final float FATE_LEANS = 0.5F;
	/** Every start's kit: bread; a capital's few coins; a merchant's child's purse; a caver's torches. */
	static final int KIT_BREAD = 4, CITY_EMERALDS = 2, MERCHANT_EMERALDS = 8, CAVE_TORCHES = 8;
	/** (Andy) the starts that come with their class's gear, armour worn ("it wouldn't make sense to start as an unarmed royal guardsman"). */
	static final Set<Start> GEARED = Set.of(Start.SWORN, Start.BANISHED, Start.CITY, Start.CAVE, Start.VILLAGE);
	/** The unarmed start's limb, one of these Jaymo's Powers part bits (right arm, left arm, right leg, left leg). */
	static final int[] LIMBS = {2, 4, 8, 16};
	/** A matching start takes this off the power's wait (ticks): a day. */
	static final int ECHO_TICKS = 24000;

	public static final String FATE = "fate";

	/** Where a story can start. {@code realm}: you name a realm for it. */
	public enum Start {
		CITY(false), BANISHED(true), CAVE(false), SHIPWRECK(false), SWORN(true), VILLAGE(false), MONOLITH(false), WILD(false), UNARMED(false), FATE(false);

		public final boolean realm;

		Start(boolean realm) {
			this.realm = realm;
		}
	}

	/**
	 * One answer: its key, its picture, and what it does. {@code heritage}: question 1's; {@code stat}: a point in it
	 * (null on a "points" answer: a free point instead, the foundling's); {@code cls}: question 4's; {@code power}: an id
	 * path of Jaymo's Powers (question 5's, or the childhood's lean); {@code looks}: the heritage looks it leans to (the
	 * first that is the born heritage's is pre-picked); {@code start}: question 6's; {@code realm}: you name a realm for it.
	 */
	public record Answer(String key, Item icon, Heritage.@Nullable Kind heritage, Stats.@Nullable Stat stat, boolean freePoint,
		Classes.@Nullable PlayerClass cls, String power, List<String> looks, @Nullable Start start, boolean realm) {

		static Answer born(Heritage.Kind kind, String key) {
			return new Answer(key, kind.icon, kind, null, false, null, "", List.of(), null, false);
		}

		static Answer raised(String key, Item icon, Stats.@Nullable Stat stat, boolean realm) {
			return new Answer(key, icon, null, stat, stat == null, null, "", List.of(), null, realm);
		}

		static Answer child(String key, Item icon, Stats.Stat stat, String power, String... looks) {
			return new Answer(key, icon, null, stat, false, null, power, List.of(looks), null, false);
		}

		static Answer youth(String key, Item icon, Classes.PlayerClass cls) {
			return new Answer(key, icon, null, null, false, cls, "", List.of(), null, false);
		}

		static Answer turn(String key, Item icon, String power) {
			return new Answer(key, icon, null, null, false, null, power, List.of(), null, false);
		}

		static Answer road(Start start, Item icon) {
			return new Answer(start.name().toLowerCase(Locale.ROOT), icon, null, null, false, null, "", List.of(), start, start.realm);
		}

		/** Whether it can be picked here: the unarmed start needs Jaymo's Powers (its lost limbs). */
		public boolean offered() {
			return this.start != Start.UNARMED || powers();
		}
	}

	/** The six questions, in order, and their answers. */
	public enum Question {
		BORN, RAISED, CHILDHOOD, YOUTH, TURN, ROAD;

		private final List<Answer> answers = new ArrayList<>();

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public List<Answer> answers() {
			return this.answers;
		}

		/** Question 5 is only asked with Jaymo's Powers there. */
		public boolean asked() {
			return this != TURN || powers();
		}

		static {
			BORN.answers.addAll(List.of(
				Answer.born(Heritage.Kind.HEARTLANDER, "plains"),
				Answer.born(Heritage.Kind.STONEBORN, "mountain"),
				Answer.born(Heritage.Kind.TIDEBORN, "coast"),
				Answer.born(Heritage.Kind.SYLVAN, "forest"),
				Answer.born(Heritage.Kind.VERDANT, "valley"),
				Answer.born(Heritage.Kind.EMBERBORN, "forge"),
				Answer.born(Heritage.Kind.FROSTBORN, "north"),
				Answer.born(Heritage.Kind.NIGHTBORN, "moors"),
				Answer.born(Heritage.Kind.WARRIORBORN, "warband"),
				Answer.born(Heritage.Kind.EARTHLING, "scholars"),
				Answer.born(Heritage.Kind.DAWNBORN, "temple")));
			RAISED.answers.addAll(List.of(
				Answer.raised("farmers", Items.WHEAT_SEEDS, Stats.Stat.ENDURANCE, false),
				Answer.raised("smiths", Items.ANVIL, Stats.Stat.MIGHT, false),
				Answer.raised("fisherfolk", Items.FISHING_ROD, Stats.Stat.AGILITY, false),
				Answer.raised("hunters", Items.BOW, Stats.Stat.FINESSE, false),
				Answer.raised("merchants", Items.EMERALD, Stats.Stat.WITS, false),
				Answer.raised("soldiers", Items.IRON_SWORD, Stats.Stat.MIGHT, false),
				Answer.raised("healers", Items.GLISTERING_MELON_SLICE, Stats.Stat.WITS, false),
				Answer.raised("wizard", Items.ENCHANTED_BOOK, Stats.Stat.WITS, false),
				Answer.raised("outlaws", Items.TRIPWIRE_HOOK, Stats.Stat.AGILITY, false),
				Answer.raised("nobles", Items.GOLDEN_HELMET, Stats.Stat.WITS, true),
				Answer.raised("foundling", Items.STRING, null, false)));
			CHILDHOOD.answers.addAll(List.of(
				Answer.child("roofs", Items.FEATHER, Stats.Stat.AGILITY, "gravity", "skymarked", "blossomkin", "moonpale"),
				Answer.child("caves", Items.COBWEB, Stats.Stat.ENDURANCE, "web", "obsidian", "hollow"),
				Answer.child("fights", Items.LEATHER, Stats.Stat.MIGHT, "zfighter", "battlescarred", "ironknuckle"),
				Answer.child("books", Items.WRITABLE_BOOK, Stats.Stat.WITS, "chrono", "inkmarked"),
				Answer.child("swimming", Items.KELP, Stats.Stat.AGILITY, "darktide", "abyssal", "reefcrowned"),
				Answer.child("storms", Items.LIGHTNING_ROD.weathering().unaffected(), Stats.Stat.FINESSE, "wind", "skymarked", "galeborn"),
				Answer.child("fire", Items.CAMPFIRE, Stats.Stat.GRIT, "smoke", "magmavein", "soulflame", "ashen"),
				Answer.child("snow", Items.SNOW_BLOCK, Stats.Stat.GRIT, "cryo", "rimeglass", "aurora"),
				Answer.child("bells", Items.BELL, Stats.Stat.WITS, "resonance", "umbral", "twilight"),
				Answer.child("blades", Items.BUNDLE, Stats.Stat.FINESSE, "arsenal", "warpainted")));
			YOUTH.answers.addAll(List.of(
				Answer.youth("cutpurses", Items.TRIPWIRE_HOOK, Classes.PlayerClass.THIEF),
				Answer.youth("levy", Items.IRON_AXE, Classes.PlayerClass.WARLORD),
				Answer.youth("apprentice", Items.BOOKSHELF, Classes.PlayerClass.MAGE),
				Answer.youth("hermit", Items.BLAZE_POWDER, Classes.PlayerClass.SORCERER),
				Answer.youth("hunted", Items.ARROW, Classes.PlayerClass.ARCHER),
				Answer.youth("winter", Items.STONE_AXE, Classes.PlayerClass.SURVIVALIST),
				Answer.youth("pits", Items.SHIELD, Classes.PlayerClass.BRUTE),
				Answer.youth("gunsmith", Items.GUNPOWDER, Classes.PlayerClass.MARKSMAN),
				Answer.youth("messenger", Items.SADDLE, Classes.PlayerClass.RIDER)));
			TURN.answers.addAll(List.of(
				Answer.turn("bite", Items.SPIDER_EYE, "web"),
				Answer.turn("weapon", Items.IRON_SWORD, "arsenal"),
				Answer.turn("fall", Items.FEATHER, "gravity"),
				Answer.turn("storm", Items.WIND_CHARGE, "wind"),
				Answer.turn("clock", Items.CLOCK, "chrono"),
				Answer.turn("bell", Items.BELL, "resonance"),
				Answer.turn("sea", Items.NAUTILUS_SHELL, "darktide"),
				Answer.turn("fists", Items.LEATHER, "zfighter"),
				Answer.turn("smoke", Items.CAMPFIRE, "smoke"),
				Answer.turn("blizzard", Items.POWDER_SNOW_BUCKET, "cryo"),
				Answer.turn(FATE, Items.ENDER_EYE, FATE)));
			ROAD.answers.addAll(List.of(
				Answer.road(Start.CITY, Items.GOLD_NUGGET),
				Answer.road(Start.BANISHED, Items.BARRIER),
				Answer.road(Start.CAVE, Items.COBWEB),
				Answer.road(Start.SHIPWRECK, Items.OAK_BOAT),
				Answer.road(Start.SWORN, Items.IRON_CHESTPLATE),
				Answer.road(Start.VILLAGE, Items.BELL),
				Answer.road(Start.MONOLITH, Items.CHISELED_STONE_BRICKS),
				Answer.road(Start.WILD, Items.COMPASS),
				Answer.road(Start.UNARMED, Items.BONE),
				Answer.road(Start.FATE, Items.ENDER_EYE)));
		}
	}

	/**
	 * Each player's story: the key of their answer to each question, in order ("" for one not asked), sealed once lived.
	 * Saved, kept through death, synced to its own player (the attribute points it gave show on the stats screen).
	 */
	public static final AttachmentType<List<String>> STORY = AttachmentRegistry.create(JaymosKingdoms.id("life_story"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/** A realm a player can name in their story: its id and name. */
	public record Realm(int id, String name) {
	}

	/** Server → player: tell your story (the screen opens; {@code forced}: it can't be shut), with the realms there are to name. */
	public record Open(boolean forced, List<Realm> realms) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("story_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBoolean(p.forced);
				buf.writeVarInt(p.realms.size());
				for (Realm r : p.realms) {
					buf.writeVarInt(r.id);
					buf.writeUtf(r.name);
				}
			}, buf -> {
				boolean forced = buf.readBoolean();
				int n = buf.readVarInt();
				List<Realm> realms = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					realms.add(new Realm(buf.readVarInt(), buf.readUtf()));
				}
				return new Open(forced, List.copyOf(realms));
			});

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/**
	 * Player → server: live this life. {@code answers}: an index per question (-1 for one not asked); {@code raisedRealm}
	 * and {@code roadRealm}: the realm named for a noble family and for a banished or sworn start (-1: none);
	 * {@code look}: the heritage's look (0: its first); {@code option}: a Mage's first spell or a Sorcerer's element (-1);
	 * {@code heritage} and {@code cls}: (Andy: "allow a player override AFTER the result") a heritage or class ordinal
	 * picked over the story's on its last page (-1: the story's).
	 */
	public record Live(List<Integer> answers, int raisedRealm, int roadRealm, int look, int option, int heritage, int cls) implements CustomPacketPayload {
		public static final Type<Live> TYPE = new Type<>(JaymosKingdoms.id("story_live"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Live> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.answers.size());
				for (int a : p.answers) {
					buf.writeVarInt(a + 1);
				}
				buf.writeVarInt(p.raisedRealm + 1);
				buf.writeVarInt(p.roadRealm + 1);
				buf.writeVarInt(p.look);
				buf.writeVarInt(p.option + 1);
				buf.writeVarInt(p.heritage + 1);
				buf.writeVarInt(p.cls + 1);
			}, buf -> {
				int n = Math.min(buf.readVarInt(), Question.values().length);
				List<Integer> answers = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					answers.add(buf.readVarInt() - 1);
				}
				return new Live(List.copyOf(answers), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt() - 1,
					buf.readVarInt() - 1, buf.readVarInt() - 1);
			});

		@Override
		public Type<Live> type() {
			return TYPE;
		}
	}

	private LifeStory() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Live.TYPE, Live.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Live.TYPE, (payload, context) -> live(context.player(), payload));
	}

	/** Whether Jaymo's Powers is here (question 5 is asked, and its answer is an affinity). */
	public static boolean powers() {
		return FabricLoader.getInstance().isModLoaded("jaymospowers");
	}

	public static @Nullable List<String> of(Player player) {
		return player.getAttached(STORY);
	}

	/** {@code player}'s answer to {@code q}, or null (none, or not asked). */
	public static @Nullable Answer answer(Player player, Question q) {
		List<String> story = of(player);
		if (story == null || story.size() <= q.ordinal()) {
			return null;
		}
		String key = story.get(q.ordinal());
		for (Answer a : q.answers) {
			if (a.key.equals(key)) {
				return a;
			}
		}
		return null;
	}

	/** Free points in {@code stat} {@code player}'s story gave (who raised them, what they did as a child). */
	public static int points(Player player, Stats.Stat stat) {
		int n = 0;
		for (Question q : List.of(Question.RAISED, Question.CHILDHOOD)) {
			Answer a = answer(player, q);
			if (a != null && a.stat == stat) {
				n++;
			}
		}
		return n;
	}

	/** Points to spend as they like that {@code player}'s story gave (a foundling's). */
	public static int freePoints(Player player) {
		Answer a = answer(player, Question.RAISED);
		return a != null && a.freePoint ? 1 : 0;
	}

	/**
	 * Whether {@code player} still owes their story: a newcomer (no heritage, no class, no story yet) whose client has
	 * this mod. Anyone part-made before stories came finishes on the heritage and class screens instead.
	 */
	static boolean owed(ServerPlayer player) {
		return of(player) == null && Heritage.of(player) == null && Classes.classOf(player) == null && ServerPlayNetworking.canSend(player, Open.TYPE);
	}

	/** Opens the story screen for {@code player}, with the realms of the world to name. */
	static void ask(ServerPlayer player, boolean forced) {
		if (!ServerPlayNetworking.canSend(player, Open.TYPE)) {
			return;
		}
		List<Realm> realms = new ArrayList<>();
		for (Faction f : Factions.all(player.level().getServer())) {
			if (f.type().isRealm() && !f.fallen()) {
				realms.add(new Realm(f.id(), f.name()));
			}
		}
		ServerPlayNetworking.send(player, new Open(forced, List.copyOf(realms)));
	}

	/** The answer at {@code index} of {@code q}, or null (out of range, or not asked). */
	private static @Nullable Answer at(Question q, List<Integer> answers) {
		if (!q.asked() || q.ordinal() >= answers.size()) {
			return null;
		}
		int i = answers.get(q.ordinal());
		return i >= 0 && i < q.answers.size() ? q.answers.get(i) : null;
	}

	/** A realm to name in a story: there, standing, and a realm. */
	private static @Nullable Faction realm(MinecraftServer server, int id) {
		Faction f = id < 0 ? null : Factions.get(server, id);
		return f != null && f.type().isRealm() && !f.fallen() ? f : null;
	}

	/** The story's look of {@code kind}: the first of the childhood's leanings that is one of its looks (0: its first). */
	public static int lookFor(Heritage.Kind kind, @Nullable Answer childhood) {
		if (childhood == null) {
			return 0;
		}
		for (String key : childhood.looks) {
			HeritageVariants.Variant v = HeritageVariants.Variant.byKey(key);
			if (v != null && v.heritage == kind) {
				return v.index();
			}
		}
		return 0;
	}

	/** The power a story's start echoes (its calling), or "". */
	public static String echo(Start start) {
		return switch (start) {
			case CAVE -> "web";
			case SHIPWRECK -> "darktide";
			case MONOLITH -> "wind";
			default -> "";
		};
	}

	/** From the story screen: live this life, all of it at once. */
	private static void live(ServerPlayer player, Live live) {
		if (!owed(player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		Answer born = at(Question.BORN, live.answers()), raised = at(Question.RAISED, live.answers()), child = at(Question.CHILDHOOD, live.answers()),
			youth = at(Question.YOUTH, live.answers()), turn = at(Question.TURN, live.answers()), road = at(Question.ROAD, live.answers());
		if (born == null || raised == null || child == null || youth == null || road == null || powers() && turn == null || !road.offered()) {
			return;
		}
		// the heritage and class picked over the story's, if any: a heritage open to them, any class
		Heritage.Kind heritage = born.heritage;
		if (live.heritage() >= 0) {
			Heritage.Kind[] kinds = Heritage.Kind.values();
			if (live.heritage() >= kinds.length || !Heritage.unlocked(player, kinds[live.heritage()])) {
				return;
			}
			heritage = kinds[live.heritage()];
		}
		Faction nobleRealm = raised.realm ? realm(server, live.raisedRealm()) : null;
		Faction roadRealm = road.realm ? realm(server, live.roadRealm()) : null;
		if (raised.realm && nobleRealm == null || road.realm && roadRealm == null) {
			return;
		}
		Classes.PlayerClass[] classes = Classes.PlayerClass.values();
		Classes.PlayerClass cls = live.cls() >= 0 && live.cls() < classes.length ? classes[live.cls()] : youth.cls;
		String option = cls != null && live.option() >= 0 && live.option() < cls.options.size() ? cls.options.get(live.option()) : "";
		if (cls == null || !cls.options.isEmpty() && option.isEmpty()) {
			return;
		}

		// the story first, so the heritage's settling counts its points
		player.setAttached(STORY, List.of(born.key, raised.key, child.key, youth.key, turn == null ? "" : turn.key, road.key));
		Heritage.set(player, heritage);
		HeritageVariants.choose(player, heritage, live.look());
		Classes.set(player, cls, option);
		Stats.attributes(player);

		RandomSource random = player.getRandom();
		String power = "";
		if (turn != null) {
			power = turn.power;
			if (FATE.equals(power) && random.nextFloat() < FATE_LEANS) {
				power = child.power;
			}
			PowersCompat.affinity(player, power);
		}
		if (nobleRealm != null) {
			atLeast(player, nobleRealm, NOBLE_REPUTATION);
		}

		Start start = road.start;
		if (start == Start.FATE) {
			start = fated(server, random);
		}
		Start landed = begin(player, start, roadRealm, heritage, random);
		if (landed == Start.UNARMED) {
			// an old loss, long healed over: one arm or one leg
			int limb = LIMBS[random.nextInt(LIMBS.length)];
			if (PowersCompat.bornWithout(player, limb)) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.story.limb",
					Component.translatable("story.jaymoskingdoms.limb." + limb)).withStyle(ChatFormatting.GRAY));
			}
		}
		kit(player, start, landed, raised, cls, option, random);
		if (!power.isEmpty() && power.equals(echo(landed))) {
			PowersCompat.call(player, power, ECHO_TICKS);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.story.begun",
			Component.translatable("story.jaymoskingdoms.road." + landed.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GOLD));
	}

	/** "Let fate decide" on the start: any start that needs no realm named (a capital only if there's one). */
	private static Start fated(MinecraftServer server, RandomSource random) {
		List<Start> open = new ArrayList<>(List.of(Start.CAVE, Start.SHIPWRECK, Start.VILLAGE, Start.WILD));
		if (capital(server, random) != null) {
			open.add(Start.CITY);
		}
		if (Monoliths.nearestFoot(server.overworld(), server.overworld().getRespawnData().pos()) != null) {
			open.add(Start.MONOLITH);
		}
		return open.get(random.nextInt(open.size()));
	}

	/** A realm's capital to wake in: a kingdom's if there is one, else any realm's (null with none). */
	private static @Nullable Faction capital(MinecraftServer server, RandomSource random) {
		List<Faction> kingdoms = new ArrayList<>(), realms = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen()) {
				realms.add(f);
				if (f.is(FactionType.KINGDOM)) {
					kingdoms.add(f);
				}
			}
		}
		List<Faction> from = kingdoms.isEmpty() ? realms : kingdoms;
		return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
	}

	/**
	 * Puts {@code player} where their story starts and does what it means to the realms; returns the start that came
	 * to pass (one that can't be found falls back to the wild, and that to where they stand).
	 */
	private static Start begin(ServerPlayer player, Start start, @Nullable Faction named, Heritage.Kind heritage, RandomSource random) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos to = null;
		switch (start) {
			case CITY -> {
				Faction city = capital(server, random);
				if (city != null) {
					to = ground(level, city.seat(), CITY_MIN, CITY_MAX, random);
				}
			}
			case BANISHED -> {
				if (named != null) {
					to = ground(level, named.seat(), BANISHED_MIN, BANISHED_MAX, random);
					if (to != null) {
						atMost(player, named, BANISHED_REPUTATION);
						for (Faction other : Factions.all(server)) {
							if (other.id() != named.id() && other.type().isRealm() && !other.fallen()
								&& (named.relation(other.id()) == Relation.RIVAL || named.relation(other.id()) == Relation.WAR)) {
								Reputation.change(player, other, RIVAL_REPUTATION);
							}
						}
					}
				}
			}
			case SWORN -> {
				if (named != null) {
					to = ground(level, named.seat(), CITY_MIN, CITY_MAX, random);
					if (to != null) {
						atLeast(player, named, SWORN_REPUTATION);
						if (Factions.of(server, player.getUUID()) == null) {
							Factions.update(server, named.id(), f -> f.withMember(player.getUUID(), new Faction.Member(Rank.PEASANT.ordinal(), 0)));
						}
					}
				}
			}
			case CAVE -> to = cave(level, spawn, random);
			case SHIPWRECK -> {
				BlockPos wreck = structure(level, BuiltinStructures.SHIPWRECK_BEACHED, spawn);
				if (wreck == null) {
					wreck = level.findNearestMapStructure(StructureTags.SHIPWRECK, spawn, STRUCTURE_SEARCH, false);
				}
				if (wreck != null) {
					to = ground(level, wreck, 0, STRUCTURE_NEAR, random);
				}
			}
			case VILLAGE -> {
				BlockPos village = level.findNearestMapStructure(villages(heritage), spawn, STRUCTURE_SEARCH, false);
				if (village == null) {
					village = level.findNearestMapStructure(StructureTags.VILLAGE, spawn, STRUCTURE_SEARCH, false);
				}
				if (village != null) {
					to = ground(level, village, 0, STRUCTURE_NEAR, random);
				}
			}
			case MONOLITH -> {
				BlockPos foot = Monoliths.nearestFoot(level, spawn);
				if (foot != null) {
					to = ground(level, foot, MONOLITH_MIN, MONOLITH_MAX, random);
				}
			}
			case WILD, UNARMED, FATE -> {
				// the unarmed start sets out from the wild too
				if (start == Start.UNARMED) {
					to = ground(level, spawn, WILD_MIN, WILD_MAX, random);
				}
			}
		}
		Start landed = start;
		if (to == null) {
			landed = Start.WILD;
			to = ground(level, spawn, WILD_MIN, WILD_MAX, random);
		}
		if (to == null) {
			return landed;
		}
		player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, Set.<net.minecraft.world.entity.Relative>of(), random.nextFloat() * 360.0F, 0.0F, false);
		if (landed == Start.CAVE) {
			// the bite you shrugged off, as you always do, and the fall that came after: poisoned, wounded, barely breathing
			player.setHealth(Math.min(player.getHealth(), CAVE_HEALTH));
			player.addEffect(new MobEffectInstance(MobEffects.POISON, BITE_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CAVE_WOUND_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CAVE_WOUND_TICKS, 0));
		}
		return landed;
	}

	/** The villages of the land a heritage comes from. */
	private static TagKey<Structure> villages(Heritage.Kind heritage) {
		return switch (heritage) {
			case STONEBORN, SYLVAN, NIGHTBORN -> StructureTags.ON_TAIGA_VILLAGE_MAPS;
			case FROSTBORN -> StructureTags.ON_SNOWY_VILLAGE_MAPS;
			case EMBERBORN, WARRIORBORN -> StructureTags.ON_SAVANNA_VILLAGE_MAPS;
			case DAWNBORN, TIDEBORN -> StructureTags.ON_DESERT_VILLAGE_MAPS;
			default -> StructureTags.ON_PLAINS_VILLAGE_MAPS;
		};
	}

	/** The nearest {@code key} structure to {@code from}, or null. */
	private static @Nullable BlockPos structure(ServerLevel level, ResourceKey<Structure> key, BlockPos from) {
		java.util.Optional<Holder.Reference<Structure>> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(key);
		return holder.isEmpty() ? null : level.findNearestMapStructure(HolderSet.direct(holder.get()), from, STRUCTURE_SEARCH, false);
	}

	/** A dry, open place to stand {@code min}-{@code max} blocks from {@code center} (horizontally), or null. */
	static @Nullable BlockPos ground(ServerLevel level, BlockPos center, int min, int max, RandomSource random) {
		for (int i = 0; i < GROUND_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int d = min + random.nextInt(Math.max(1, max - min + 1));
			int x = center.getX() + Mth.floor(Math.cos(angle) * d), z = center.getZ() + Mth.floor(Math.sin(angle) * d);
			level.getChunk(x >> 4, z >> 4);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (standable(level, top)) {
				return top;
			}
		}
		return null;
	}

	/** A hollow underground near {@code center}, deep and roofed, with room to stand; or null. */
	private static @Nullable BlockPos cave(ServerLevel level, BlockPos center, RandomSource random) {
		for (int i = 0; i < CAVE_TRIES; i++) {
			int x = center.getX() + random.nextInt(CAVE_RADIUS * 2 + 1) - CAVE_RADIUS, z = center.getZ() + random.nextInt(CAVE_RADIUS * 2 + 1) - CAVE_RADIUS;
			level.getChunk(x >> 4, z >> 4);
			int top = Math.min(CAVE_MAX_Y, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 12);
			for (int y = top; y > level.getMinY() + 8; y--) {
				BlockPos pos = new BlockPos(x, y, z);
				if (standable(level, pos) && !level.canSeeSky(pos)) {
					return pos;
				}
			}
		}
		return null;
	}

	/** Room to stand at {@code feet}: two clear blocks on a sturdy floor, no water or lava. */
	private static boolean standable(ServerLevel level, BlockPos feet) {
		BlockState floor = level.getBlockState(feet.below());
		BlockState body = level.getBlockState(feet), head = level.getBlockState(feet.above());
		return floor.isFaceSturdy(level, feet.below(), Direction.UP) && floor.getFluidState().isEmpty()
			&& body.isAir() && head.isAir();
	}

	/**
	 * Every start's small kit, and its own few things. {@code start} is the start the story chose (its gear comes even if
	 * the place couldn't be found), {@code landed} where they woke (its place's things).
	 */
	private static void kit(ServerPlayer player, Start start, Start landed, Answer raised, Classes.PlayerClass cls, String option, RandomSource random) {
		if (start == Start.SWORN) {
			// a realm's sworn guard: iron head to foot, a sword and a shield
			for (Item item : List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.IRON_SWORD, Items.SHIELD)) {
				equip(player, new ItemStack(item));
			}
		}
		if (GEARED.contains(start)) {
			for (ItemStack stack : Classes.gear(cls, option)) {
				equip(player, stack.copy());
			}
		}
		give(player, new ItemStack(Items.BREAD, KIT_BREAD));
		if (landed == Start.CITY) {
			give(player, new ItemStack(Items.EMERALD, CITY_EMERALDS));
		}
		if (landed == Start.CAVE) {
			give(player, new ItemStack(Items.IRON_PICKAXE));
			give(player, new ItemStack(Items.TORCH, CAVE_TORCHES));
		}
		if (landed == Start.VILLAGE) {
			// (0.7.218) a real job now ({@link Careers}), leaning to who raised you, taken up in the village you wake by
			String job = Careers.storyLean(raised.key, random);
			List<String> story = new ArrayList<>(of(player) == null ? List.of() : of(player));
			story.add(job);
			player.setAttached(STORY, List.copyOf(story));
			Careers.Job kind = Careers.Job.of(job);
			for (ItemStack stack : kind == null ? List.<ItemStack>of() : Careers.kit(kind)) {
				give(player, stack);
			}
			Careers.storyJob(player, job);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.story.job",
				Component.translatable("story.jaymoskingdoms.job." + job)).withStyle(ChatFormatting.GRAY));
		}
		if (raised.key.equals("merchants")) {
			give(player, new ItemStack(Items.EMERALD, MERCHANT_EMERALDS));
		}
	}

	/** Puts on armour (and a shield in the off hand) where that slot is free; anything else goes in the pack. */
	private static void equip(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		EquipmentSlot slot = equippable != null && equippable.slot().isArmor() ? equippable.slot() : stack.is(Items.SHIELD) ? EquipmentSlot.OFFHAND : null;
		if (slot != null && player.getItemBySlot(slot).isEmpty()) {
			player.setItemSlot(slot, stack);
		} else {
			give(player, stack);
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}

	/** Raises {@code player}'s reputation with {@code realm} to at least {@code rep}. */
	private static void atLeast(ServerPlayer player, Faction realm, int rep) {
		int now = Reputation.get(player, realm.id());
		if (now < rep) {
			Reputation.change(player, realm, rep - now);
		}
	}

	/** Lowers {@code player}'s reputation with {@code realm} to at most {@code rep}. */
	private static void atMost(ServerPlayer player, Faction realm, int rep) {
		int now = Reputation.get(player, realm.id());
		if (now > rep) {
			Reputation.change(player, realm, rep - now);
		}
	}

	/** {@code /kingdom story reset <players>} (ops): unmakes their heritage, class and story, and asks for their story again. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("story").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("reset").then(Commands.argument("players", EntityArgument.players()).executes(c -> {
				int n = 0;
				for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
					p.removeAttached(STORY);
					Classes.clear(p);
					Heritage.clear(p);
					ask(p, true);
					n++;
				}
				int count = n;
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.story.reset", count), true);
				return n;
			})));
	}
}

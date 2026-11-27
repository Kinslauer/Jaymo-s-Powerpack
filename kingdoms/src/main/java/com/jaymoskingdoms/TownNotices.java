package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.168) Town notices. Andy: "they can place quests or useless notices too, to add to immersion, (bring me some
 * leashed animals, or feed, etc,) add about 5 per village type". Besides wanted notices, a villager may pin up one of
 * their village's notices ({@link #NOTICES}, by the poster's villager type): an <b>errand</b> (bring so many of
 * something, or so many animals on a lead), which pulled off the board is a quest from the villager who posted it, turned
 * in to them like any other; or a bit of <b>news</b> (a lost cat, a dance, a warning), which pulled off is just a piece of
 * paper to keep, or to pin up again on any board. A few at a time ({@link #MOST}), and they come down after
 * {@link #LIFE}.
 *
 * <p>A pinned town notice is kept ({@link Bounties#PINNED}) as {@code town|template|poster's UUID|posted at}.
 * <p>(0.7.220) Or a <b>Help wanted</b> notice ({@code town|help.<job>|...}): a job the poster's town is short of hands
 * for ({@link Careers#shortOf}). Pulled off, it's the job, with them ({@link Careers#hire}); it stays up if they won't
 * have you.
 * <p>(0.7.424) Or a piece of the world's news ({@code town|tidings.<id>|<board>|...}, kept by {@link Post#postable}):
 * read like any other news, and it comes down once the news is old.
 */
final class TownNotices {
	// ---- tuning ----
	/** At most this many town notices on a board at once; the chance a board with no bounty to post gets one, each check. */
	static final int MOST = 3;
	static final float CHANCE = 0.35F;
	/** A town notice comes down after this long (ticks). */
	static final long LIFE = 24000L * 2;
	/** What an errand pays: MCA hearts with the poster, reputation with the village. */
	static final int HEARTS = 4, REPUTATION = 6;
	/**
	 * (village mods 4, fair pay) An errand pays what it asks for is worth ({@link Economy#worth}) and this share over, for the
	 * fetching; and this much (milli) for the trouble, and up to this much more besides.
	 */
	static final float ERRAND_SHARE = 1.5F;
	static final int ERRAND_LABOUR = 1000, ERRAND_JITTER = 1000;
	/** (0.7.220) The chance a notice is Help wanted, when the poster's town is short of hands. */
	static final float HELP_CHANCE = 0.3F;
	/** (0.7.385, Off-World Orders) The chance a notice asks for a far world's goods ({@link OffWorldGoods}), once a player has the tier for it. */
	static final float FAR_CHANCE = 0.25F;
	/** (0.7.424) The chance a notice is a piece of the world's news, when the board's town has heard some it hasn't up. */
	static final float TIDINGS_CHANCE = 0.6F;
	/** (0.7.220) A Help wanted notice's kind. */
	static final int HELP = -2;
	/** A notice of paid work ({@link BoardWork}): its kind. */
	static final int WORK = -3;
	/** A notice that starts a tale ({@code town|tale.<id>|<poster>|...}, {@link Tales#notice}): its kind. */
	static final int TALE = -4;

	static Item ITEM;

	/** One notice: its words ({@code quest.jaymoskingdoms.town.<key>}); for an errand, the quest kind, what, how many, pay. */
	private record N(String key, int kind, String target, int min, int max, int emeralds) {
		static N gather(String key, String item, int min, int max, int emeralds) {
			return new N(key, Quest.GATHER, item, min, max, emeralds);
		}

		static N lead(String key, String animal, int min, int max, int emeralds) {
			return new N(key, Quest.LEAD, animal, min, max, emeralds);
		}

		static N news(String key) {
			return new N(key, -1, "", 0, 0, 0);
		}

		boolean errand() {
			return kind >= 0;
		}

		boolean help() {
			return kind == HELP;
		}

		boolean work() {
			return kind == WORK;
		}

		/** The tale this notice starts, or null. */
		@Nullable Tale tale() {
			return kind == TALE ? Tale.get(key.substring(5)) : null;
		}

		Careers.@Nullable Job job() {
			return help() ? Careers.Job.of(key.substring(5)) : null;
		}
	}

	/** Each villager type's notices (vanilla's seven; any other reads as plains). */
	private static final Map<String, List<N>> NOTICES = Map.of(
		"plains", List.of(
			N.lead("plains.sheep", "minecraft:sheep", 2, 3, 6),
			N.gather("plains.hay", "minecraft:hay_block", 4, 8, 5),
			N.gather("plains.eggs", "minecraft:egg", 8, 16, 3),
			N.news("plains.cat"),
			N.news("plains.dance")),
		"desert", List.of(
			N.lead("desert.camel", "minecraft:camel", 1, 1, 8),
			N.gather("desert.water", "minecraft:water_bucket", 2, 4, 4),
			N.gather("desert.hides", "minecraft:rabbit_hide", 4, 8, 4),
			N.news("desert.well"),
			N.news("desert.sandal")),
		"savanna", List.of(
			N.lead("savanna.cattle", "minecraft:cow", 2, 3, 6),
			N.lead("savanna.llama", "minecraft:llama", 1, 2, 7),
			N.gather("savanna.acacia", "minecraft:acacia_log", 16, 32, 5),
			N.news("savanna.crossing"),
			N.news("savanna.tale")),
		"snow", List.of(
			N.lead("snow.goat", "minecraft:goat", 1, 2, 7),
			N.gather("snow.coal", "minecraft:coal", 8, 16, 4),
			N.gather("snow.stew", "minecraft:rabbit_stew", 2, 3, 5),
			N.news("snow.ice"),
			N.news("snow.mitten")),
		"taiga", List.of(
			N.lead("taiga.pigs", "minecraft:pig", 2, 3, 5),
			N.gather("taiga.berries", "minecraft:sweet_berries", 12, 24, 3),
			N.gather("taiga.firewood", "minecraft:spruce_log", 16, 32, 5),
			N.news("taiga.fox"),
			N.news("taiga.timber")),
		"jungle", List.of(
			N.lead("jungle.chickens", "minecraft:chicken", 3, 4, 4),
			N.gather("jungle.cocoa", "minecraft:cocoa_beans", 8, 16, 4),
			N.gather("jungle.melon", "minecraft:melon_slice", 12, 24, 3),
			N.news("jungle.parrots"),
			N.news("jungle.melon_thief")),
		"swamp", List.of(
			N.lead("swamp.frog", "minecraft:frog", 1, 2, 6),
			N.gather("swamp.slime", "minecraft:slime_ball", 2, 4, 5),
			N.gather("swamp.lilies", "minecraft:lily_pad", 4, 8, 3),
			N.news("swamp.hut"),
			N.news("swamp.boot")));

	/** (village mods 4) A land's own notices, posted on top of the poster's villager type's where the poster lives in it. */
	private record Land(java.util.function.Predicate<Holder<Biome>> in, List<N> notices) {
	}

	private static final List<Land> LANDS = List.of(
		new Land(b -> b.is(BiomeTags.IS_BEACH) || b.is(BiomeTags.IS_OCEAN), List.of(
			N.gather("land.shore.kelp", "minecraft:dried_kelp", 16, 32, 0),
			N.gather("land.shore.cod", "minecraft:cod", 4, 8, 0),
			N.news("land.shore.wreck"))),
		new Land(b -> b.is(BiomeTags.IS_RIVER), List.of(
			N.gather("land.river.clay", "minecraft:clay_ball", 8, 16, 0),
			N.gather("land.river.salmon", "minecraft:salmon", 3, 6, 0))),
		new Land(b -> b.is(BiomeTags.IS_MOUNTAIN), List.of(
			N.gather("land.mountain.iron", "minecraft:raw_iron", 4, 8, 0),
			N.news("land.mountain.echo"))),
		new Land(b -> b.is(BiomeTags.IS_BADLANDS), List.of(
			N.gather("land.badlands.gold", "minecraft:gold_nugget", 9, 18, 0),
			N.gather("land.badlands.clay", "minecraft:terracotta", 16, 32, 0),
			N.news("land.badlands.mine"))),
		new Land(b -> b.is(BiomeTags.IS_FOREST), List.of(
			N.gather("land.forest.mushrooms", "minecraft:brown_mushroom", 6, 12, 0),
			N.gather("land.forest.honey", "minecraft:honeycomb", 3, 6, 0))),
		new Land(b -> b.is(BiomeTags.IS_JUNGLE), List.of(
			N.gather("land.jungle.bamboo", "minecraft:bamboo", 16, 32, 0))),
		new Land(b -> b.is(Biomes.CHERRY_GROVE), List.of(
			N.gather("land.cherry.petals", "minecraft:pink_petals", 8, 16, 0),
			N.gather("land.cherry.honey", "minecraft:honey_bottle", 2, 4, 0),
			N.news("land.cherry.blossom"))),
		new Land(b -> b.is(Biomes.MANGROVE_SWAMP), List.of(
			N.gather("land.mangrove.mud", "minecraft:mud", 16, 32, 0),
			N.news("land.mangrove.roots"))),
		new Land(b -> b.is(Biomes.MUSHROOM_FIELDS), List.of(
			N.lead("land.mushroom.mooshroom", "minecraft:mooshroom", 1, 1, 0),
			N.gather("land.mushroom.red", "minecraft:red_mushroom", 8, 16, 0))),
		new Land(b -> b.is(Biomes.PALE_GARDEN), List.of(
			N.gather("land.pale.resin", "minecraft:resin_clump", 4, 8, 0),
			N.news("land.pale.quiet"))));

	private TownNotices() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("town_notice"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
		Decrees.register();
	}

	/** The notices {@code poster} may post: their villager type's (any other reads as plains), and those of the land they live in. */
	private static List<N> lists(LivingEntity poster) {
		String type = poster instanceof Villager v ? v.getVillagerData().type().unwrapKey().map(k -> k.identifier().getPath()).orElse("plains") : "plains";
		List<N> out = new ArrayList<>(NOTICES.getOrDefault(type, NOTICES.get("plains")));
		Holder<Biome> biome = poster.level().getBiome(poster.blockPosition());
		for (Land land : LANDS) {
			if (land.in().test(biome)) {
				out.addAll(land.notices());
			}
		}
		return out;
	}

	/** (village mods 4) The goods {@code poster}'s town might want besides, for a mixed ask: some of each of its errands' goods, as {@code id*n}. */
	static List<String> goodsPool(LivingEntity poster) {
		List<String> out = new ArrayList<>();
		for (N n : lists(poster)) {
			if (n.errand() && n.kind() == Quest.GATHER) {
				out.add(n.target() + "*" + Math.max(1, Math.round(n.min() * BoardTerms.ALSO_SHARE)));
			}
		}
		return out;
	}

	// ---- the key ---------------------------------------------------------------------------------------------------------

	static boolean is(String key) {
		return key.startsWith("town|");
	}

	private static String[] parts(String key) {
		return key.split("\\|");
	}

	private static @Nullable N notice(String key) {
		String[] p = parts(key);
		if (p.length < 2) {
			return null;
		}
		if (p[1].startsWith("help.")) {
			return Careers.Job.of(p[1].substring(5)) == null ? null : new N(p[1], HELP, "", 0, 0, 0);
		}
		if (p[1].startsWith("work.")) {
			return BoardWork.kind(p[1]) == null ? null : new N(p[1], WORK, "", 0, 0, 0);
		}
		if (p[1].startsWith("tale.")) {
			Tale tale = Tale.get(p[1].substring(5));
			return tale == null || !tale.by().equals("notice") ? null : new N(p[1], TALE, "", 0, 0, 0);
		}
		if (p[1].startsWith("tidings.")) {
			// (0.7.424) the world's news, while it's kept
			return Post.posted(p[1].substring(8)) == null ? null : N.news(p[1]);
		}
		if (p[1].startsWith("far.")) {
			// (0.7.385) a warrant for a far world's goods
			OffWorldGoods.Good g = OffWorldGoods.byKey(p[1].substring(4));
			return g == null ? null : N.gather(p[1], g.itemId(), g.min(), g.max(), g.emeralds());
		}
		for (List<N> list : NOTICES.values()) {
			for (N n : list) {
				if (n.key().equals(p[1])) {
					return n;
				}
			}
		}
		for (Land land : LANDS) {
			for (N n : land.notices()) {
				if (n.key().equals(p[1])) {
					return n;
				}
			}
		}
		return null;
	}

	/** (village mods 4) The paper {@code key}'s notice is on: board work's by its stars ({@link BoardTerms#paper}), anything else plain. */
	static String paper(String key) {
		String[] p = parts(key);
		return p.length >= 2 && BoardWork.kind(p[1]) != null ? BoardTerms.paper(BoardWork.stars(p[1])) : "plain";
	}

	/** It's been up longer than {@link #LIFE} (or it isn't one we know). */
	static boolean expired(String key, long now) {
		String[] p = parts(key);
		if (p.length < 4 || notice(key) == null) {
			return true;
		}
		try {
			return now - Long.parseLong(p[3]) > LIFE;
		} catch (NumberFormatException e) {
			return true;
		}
	}

	// ---- posting -----------------------------------------------------------------------------------------------------------

	/**
	 * A notice of {@code poster}'s village type that isn't up already ({@code up}: every key pinned or on its way), or null;
	 * (0.7.424) or a piece of news the town of the board at {@code board} has heard.
	 */
	static @Nullable String pick(Villager poster, Set<String> up, long now, RandomSource random, long board) {
		if (random.nextFloat() < TIDINGS_CHANCE && poster.level() instanceof ServerLevel heard) {
			Post.Posted news = Post.postable(heard.getServer(), board, up);
			if (news != null) {
				return "town|tidings." + news.id() + "|" + board + "|" + now;
			}
		}
		if (random.nextFloat() < Tales.NOTICE_CHANCE) {
			// someone's own trouble, nailed up in their own hand: a tale
			String tale = Tales.notice(poster, up, random);
			if (tale != null) {
				return "town|tale." + tale + "|" + poster.getStringUUID() + "|" + now;
			}
		}
		// (village mods 4) the decrees on this board steer what goes up on it
		java.util.Set<String> decrees = poster.level() instanceof ServerLevel sl ? Decrees.on(sl, BlockPos.of(board)) : java.util.Set.of();
		float workChance = BoardWork.CHANCE + Decrees.WORK_CHANCE * decrees.stream().filter(k -> !k.equals("farmers")).count();
		if (random.nextFloat() < workChance && poster.level() instanceof ServerLevel world) {
			// paid work of the newer kinds, rated in stars
			String work = BoardWork.pick(world, poster, up, now, random, decrees);
			if (work != null) {
				return BoardTerms.dated(work, BoardWork.stars(parts(work)[1]), random);
			}
		}
		List<N> list = lists(poster);
		if (random.nextFloat() < FAR_CHANCE && poster.level() instanceof ServerLevel far && OffWorldGoods.anyoneAsked(far.getServer())
			&& up.stream().noneMatch(k -> is(k) && parts(k)[1].startsWith("far."))) {
			List<OffWorldGoods.Good> goods = OffWorldGoods.available();
			if (!goods.isEmpty()) {
				return "town|far." + goods.get(random.nextInt(goods.size())).key() + "|" + poster.getStringUUID() + "|" + now;
			}
		}
		if (random.nextFloat() < HELP_CHANCE && poster.level() instanceof ServerLevel level && up.stream().noneMatch(k -> is(k) && parts(k)[1].startsWith("help."))) {
			// (0.7.220) Help wanted: a job their town is short of hands for
			Faction f = Quests.settlement(level, poster);
			List<Careers.Job> short_ = new ArrayList<>();
			for (Careers.Job job : Careers.Job.values()) {
				if (f != null && Careers.shortOf(level, f, job)) {
					short_.add(job);
				}
			}
			if (!short_.isEmpty()) {
				return "town|help." + short_.get(random.nextInt(short_.size())).key + "|" + poster.getStringUUID() + "|" + now;
			}
		}
		List<N> free = new ArrayList<>();
		for (N n : list) {
			if (up.stream().noneMatch(k -> is(k) && n.key().equals(parts(k)[1]))) {
				free.add(n);
			}
		}
		if (free.isEmpty()) {
			return null;
		}
		// (errands of a decree's trade are likelier while it's up)
		int total = 0;
		for (N n : free) {
			total += weight(n, decrees);
		}
		int roll = random.nextInt(total);
		N n = free.get(free.size() - 1);
		for (N each : free) {
			roll -= weight(each, decrees);
			if (roll < 0) {
				n = each;
				break;
			}
		}
		String key = "town|" + n.key() + "|" + (n.errand() ? poster.getStringUUID() : "-") + "|" + now;
		return n.errand() ? BoardTerms.dated(key, 1, random) : key;
	}

	private static int weight(N n, java.util.Set<String> decrees) {
		return n.errand() ? Decrees.weight(decrees, Decrees.ofGoods(n.target(), n.kind() == Quest.LEAD)) : 1;
	}

	private static Component title(N n) {
		if (n.help()) {
			return Component.translatable("quest.jaymoskingdoms.town.help.title", n.job() == null ? Component.empty() : n.job().title());
		}
		if (n.key().startsWith("tidings.")) {
			return Component.translatable("quest.jaymoskingdoms.town.tidings.title");
		}
		if (n.work()) {
			return BoardWork.title(n.key());
		}
		Tale tale = n.tale();
		if (tale != null) {
			return Component.literal(Tale.string(Tale.object(tale.start(), "notice"), "title", tale.title()));
		}
		return Component.translatable("quest.jaymoskingdoms.town." + n.key() + ".title");
	}

	/** The notice as a piece of paper: its title, and for news its words. */
	static ItemStack item(ServerLevel level, String key) {
		ItemStack stack = new ItemStack(ITEM);
		N n = notice(key);
		if (n == null) {
			return stack;
		}
		String paper = paper(key);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.town_notice.of", title(n))
			.withStyle(style -> style.withItalic(false).withColor(BoardTerms.colour(paper))));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(paper), List.of()));
		Post.Posted news = n.key().startsWith("tidings.") ? Post.posted(level.getServer(), n.key().substring(8)) : null;
		Component words = n.errand() ? Component.translatable("quest.jaymoskingdoms.town.errand_lore")
			: n.help() ? Component.translatable("quest.jaymoskingdoms.town.help.lore")
			: n.work() ? BoardWork.lore(n.key())
			: Component.translatable("quest.jaymoskingdoms.town." + n.key() + ".text");
		List<Component> lines = new ArrayList<>(news != null ? Post.wrap(news.line()) : List.of(words));
		Tale tale = n.tale();
		if (tale != null) {
			// (its own words, and whose hand they're in)
			lines.clear();
			for (String line : Tale.strings(Tale.object(tale.start(), "notice").get("lines"))) {
				lines.add(Component.literal(line));
			}
			String hand = Tale.string(Tale.object(tale.start(), "notice"), "hand", "");
			if (!hand.isEmpty()) {
				lines.add(Component.translatable("quest.jaymoskingdoms.town.tale.hand", hand));
			}
		}
		if (BoardTerms.days(key) > 0) {
			lines.add(BoardTerms.deadlineLine(BoardTerms.days(key)));
		}
		stack.set(DataComponents.LORE, new ItemLore(lines.stream()
			.map(line -> (Component) line.copy().withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))).toList()));
		CompoundTag tag = new CompoundTag();
		tag.putString(Bounties.NOTICE_KEY, key);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** What the poster calls out pinning it up. */
	static Component callOut(String key, RandomSource random) {
		N n = notice(key);
		return Component.translatable("talk.jaymoskingdoms.notice.posting." + random.nextInt(3), n == null ? Component.empty() : title(n));
	}

	// ---- pulled off --------------------------------------------------------------------------------------------------------

	/**
	 * {@code player} pulls the town notice {@code key} off a board: an errand is a quest from its poster (if they're still
	 * about and the player's log has room), news is a piece of paper, and so is a tale's notice (with its tale, if it's one
	 * for them). False if it stays up.
	 */
	/** Whether {@code player} already carries a copy of {@code paper}. */
	private static boolean holds(ServerPlayer player, ItemStack paper) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (ItemStack.isSameItemSameComponents(player.getInventory().getItem(i), paper)) {
				return true;
			}
		}
		return false;
	}

	static boolean pull(ServerLevel level, ServerPlayer player, String key) {
		N n = notice(key);
		if (n == null) {
			return true;
		}
		if (!n.errand() && !n.help() && !n.work() && n.tale() == null) {
			ItemStack paper = item(level, key);
			if (!player.getInventory().add(paper)) {
				player.drop(paper, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			return true;
		}
		String[] p = parts(key);
		LivingEntity giver = null;
		try {
			giver = Factions.level(level.getServer()).getEntity(UUID.fromString(p[2])) instanceof LivingEntity e && e.isAlive() ? e : null;
		} catch (IllegalArgumentException ignored) {
			// (not a UUID: no one to bring it to)
		}
		Faction f = giver == null ? null : Quests.settlement(level, giver);
		if (giver == null || f == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.stale").withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (n.help()) {
			// (0.7.220) the job, with whoever posted it
			Careers.Job wanted = n.job();
			return wanted != null && Careers.hire(level, player, f, wanted, giver);
		}
		if (n.work()) {
			return BoardWork.take(level, player, giver, f, key);
		}
		if (n.tale() != null) {
			// (the tale is theirs, and the paper with it; one they've had already, or that isn't for them, is only a copy of the
			// paper, the trouble on it in the asker's own hand, and the notice stays up for whoever it is for)
			boolean theirs = Tales.pulled(level, player, n.key().substring(5), giver, f);
			ItemStack paper = item(level, key);
			// (one copy is enough for someone it isn't for: the paper stays up for whoever it is for)
			if (theirs || !holds(player, paper)) {
				if (!player.getInventory().add(paper)) {
					player.drop(paper, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
			return theirs;
		}
		RandomSource random = level.getRandom();
		// (after many errands, this may be the rare one: the way into a ruin, in the giver's book)
		Boolean rare = ErrandBooks.rare(level, player, giver, f, random);
		if (rare != null) {
			return rare;
		}
		String job = giver instanceof Villager v
			? BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath() : "";
		int count = Mth.nextInt(random, n.min(), n.max());
		// (village mods 4) a second ask maybe, and pay to match what it all is worth, part of it in goods if it asks two things
		MinecraftServer server = level.getServer();
		String also = BoardTerms.also(goodsPool(giver), n.target(), random);
		long asked = (n.kind() == Quest.LEAD ? Economy.beastWorth(n.target()) : Economy.worth(server, n.target())) * count
			+ BoardTerms.alsoWorth(server, also);
		long milli = Math.round(asked * ERRAND_SHARE) + ERRAND_LABOUR + random.nextInt(ERRAND_JITTER);
		if (n.key().startsWith("far.")) {
			// (a far world's goods are priced by whoever asks for them: never under that)
			milli = Math.max(milli, Money.worth(n.emeralds()));
		}
		String goods = also == null ? null : BoardTerms.goods(server, milli);
		if (goods != null) {
			milli -= BoardTerms.alsoWorth(server, goods);
		}
		Quest q = new Quest(UUID.randomUUID().toString(), n.kind(), "town." + n.key(),
			new Quest.Giver(giver.getUUID(), giver.getName().getString(), f.id(), false, job),
			new Quest.Objective(n.target(), count, 0, Optional.empty(), Optional.empty(), "", f.name(), false),
			Economy.pay(milli, goods == null ? List.of() : List.of(goods), HEARTS, REPUTATION), 0L);
		if (!Quests.takeFromBoard(level, player, q)) {
			return false;
		}
		BoardTerms.taken(level, player, q, key, "plain", also);
		ErrandBooks.give(player, q, giver, f, false);
		return true;
	}
}

package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
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
 */
final class TownNotices {
	// ---- tuning ----
	/** At most this many town notices on a board at once; the chance a board with no bounty to post gets one, each check. */
	static final int MOST = 3;
	static final float CHANCE = 0.35F;
	/** A town notice comes down after this long (ticks). */
	static final long LIFE = 24000L * 2;
	/** What an errand pays: emeralds, MCA hearts with the poster, reputation with the village. */
	static final int HEARTS = 4, REPUTATION = 6;
	/** (0.7.220) The chance a notice is Help wanted, when the poster's town is short of hands. */
	static final float HELP_CHANCE = 0.3F;
	/** (0.7.385, Off-World Orders) The chance a notice asks for a far world's goods ({@link OffWorldGoods}), once a player has the tier for it. */
	static final float FAR_CHANCE = 0.25F;
	/** (0.7.220) A Help wanted notice's kind. */
	static final int HELP = -2;

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

	private TownNotices() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("town_notice"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
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
		return null;
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

	/** A notice of {@code poster}'s village type that isn't up already ({@code up}: every key pinned or on its way), or null. */
	static @Nullable String pick(Villager poster, Set<String> up, long now, RandomSource random) {
		String type = poster.getVillagerData().type().unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
		List<N> list = NOTICES.getOrDefault(type, NOTICES.get("plains"));
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
		N n = free.get(random.nextInt(free.size()));
		return "town|" + n.key() + "|" + (n.errand() ? poster.getStringUUID() : "-") + "|" + now;
	}

	private static Component title(N n) {
		if (n.help()) {
			return Component.translatable("quest.jaymoskingdoms.town.help.title", n.job() == null ? Component.empty() : n.job().title());
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
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.town_notice.of", title(n))
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.WHITE)));
		Component words = n.errand() ? Component.translatable("quest.jaymoskingdoms.town.errand_lore")
			: n.help() ? Component.translatable("quest.jaymoskingdoms.town.help.lore")
			: Component.translatable("quest.jaymoskingdoms.town." + n.key() + ".text");
		stack.set(DataComponents.LORE, new ItemLore(List.of(words.copy().withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))));
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
	 * about and the player's log has room), news is a piece of paper. False if it stays up.
	 */
	static boolean pull(ServerLevel level, ServerPlayer player, String key) {
		N n = notice(key);
		if (n == null) {
			return true;
		}
		if (!n.errand() && !n.help()) {
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
		RandomSource random = level.getRandom();
		String job = giver instanceof Villager v
			? BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath() : "";
		int count = Mth.nextInt(random, n.min(), n.max());
		Quest q = new Quest(UUID.randomUUID().toString(), n.kind(), "town." + n.key(),
			new Quest.Giver(giver.getUUID(), giver.getName().getString(), f.id(), false, job),
			new Quest.Objective(n.target(), count, 0, Optional.empty(), Optional.empty(), "", f.name(), false),
			new Quest.Reward(n.emeralds() + random.nextInt(3), List.of(), Optional.empty(), Optional.empty(), HEARTS, REPUTATION, 0), 0L);
		return Quests.takeFromBoard(level, player, q);
	}
}

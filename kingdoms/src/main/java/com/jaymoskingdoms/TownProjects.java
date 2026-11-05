package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.530) Shared village projects: a well, a granary, a wall, a watchtower, a bridge road and an infirmary. Each town board
 * carries a Town works sheet (like the merchant orders sheet) that lists what each project still needs. Anyone can give goods
 * or coin; progress is shared and saved per town. When a project is finished the town really gets something (see the
 * constants and the bonus reads below), the news goes round, and everyone who gave is paid coin and reputation by their
 * share, an absent giver when they next join.
 */
public final class TownProjects {
	/** How often a board gets its works sheet, once it has the orders sheet up. */
	static final float SHEET_CHANCE = 0.5F;
	/** How near a board a player must stay to give. */
	static final double READ_RANGE = 8.0;
	/** How often finished projects are looked at (ticks): their building handed to the builders, fallen towns dropped. */
	static final int CHECK_TICKS = 200;
	/** Coin given at once (emeralds' worth): a little, a lot; the share points each emerald's worth earns. */
	static final int COIN_SMALL = 1, COIN_BIG = 5, COIN_POINTS = 10;
	/** The share of the coin given that goes back to the givers (the rest goes to the treasury); the reputation each giver gets (least, and the pool shared by share). */
	static final float PAYBACK = 0.25F;
	static final int REP_LEAST = 10, REP_POOL = 90;
	/** Players this near a village's seat hear that a project is finished. */
	static final double LOCAL_NEWS = 300.0;
	/** A wall makes raids this much as likely; a watchtower this much; and warns players this near the seat. */
	static final float WALL_RAID = 0.75F, TOWER_RAID = 0.85F;
	static final double TOWER_WARN_RANGE = 200.0;
	/** A granary: the food reserve is this much as large, and this much bread is put into the stores. */
	static final float GRANARY_RESERVE = 0.5F;
	static final int GRANARY_FOOD = 48;
	/** A wall adds this much to a village's purse (for its next wall tier). */
	static final int WALL_PURSE = 60;
	/** A bridge: this many more road links. */
	static final int BRIDGE_LINKS = 1;
	/** Plague and wounds need this many more sick or hurt to start in a town with a well or an infirmary. */
	static final int WELL_SICK_MORE = 1, INFIRMARY_SICK_MORE = 2, INFIRMARY_WOUNDED_MORE = 2;

	static final String SHEET = "works|";

	/** One thing a project needs: an item id or {@code #tag}, how many, and the share points for each one given. */
	record Need(String what, int count, int points) {
	}

	/** A project: what it needs, the coin (emeralds' worth), the bonus the town adds to the payout (emeralds' worth), and a catalogue building its builders put up ("" for none). */
	record Project(String id, List<Need> needs, int coin, int bonus, String builds) {
	}

	private static final String STONE = "#minecraft:stone_crafting_materials", LOGS = "#minecraft:logs", PLANKS = "#minecraft:planks", WOOL = "#minecraft:wool";

	static final List<Project> PROJECTS_LIST = List.of(
		new Project("well", List.of(new Need(STONE, 96, 1), new Need("minecraft:bucket", 2, 8)), 10, 6, "well"),
		new Project("granary", List.of(new Need("minecraft:hay_block", 24, 4), new Need(LOGS, 48, 1)), 15, 8, ""),
		new Project("wall", List.of(new Need(STONE, 256, 1), new Need(LOGS, 64, 1), new Need("minecraft:iron_ingot", 8, 6)), 30, 12, ""),
		new Project("watchtower", List.of(new Need(LOGS, 64, 1), new Need(STONE, 64, 1), new Need("minecraft:torch", 32, 1), new Need("minecraft:spyglass", 1, 20)), 20, 10, ""),
		new Project("bridge", List.of(new Need(PLANKS, 128, 1), new Need(LOGS, 32, 1), new Need("minecraft:lantern", 4, 6)), 25, 10, ""),
		new Project("infirmary", List.of(new Need(WOOL, 16, 2), new Need(PLANKS, 64, 1), new Need("minecraft:glass", 16, 1)), 20, 8, "infirmary"));

	/**
	 * One project's progress in one town: given per need (by index), coin given (milli), share points by player, their names, the day it
	 * was finished (-1: open), and whether its building has been handed to the builders.
	 */
	record Progress(List<Integer> given, long coin, Map<String, Integer> points, Map<String, String> names, long done, boolean queued) {
		static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.listOf().optionalFieldOf("given", List.of()).forGetter(Progress::given),
				Codec.LONG.optionalFieldOf("coin", 0L).forGetter(Progress::coin),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("points", Map.of()).forGetter(Progress::points),
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("names", Map.of()).forGetter(Progress::names),
				Codec.LONG.optionalFieldOf("done", -1L).forGetter(Progress::done),
				Codec.BOOL.optionalFieldOf("queued", false).forGetter(Progress::queued))
			.apply(i, Progress::new));
		static final Progress NONE = new Progress(List.of(), 0L, Map.of(), Map.of(), -1L, false);

		int given(int need) {
			return need < given.size() ? given.get(need) : 0;
		}

		Progress withGiven(int need, int value) {
			List<Integer> next = new ArrayList<>(given);
			while (next.size() <= need) {
				next.add(0);
			}
			next.set(need, value);
			return new Progress(List.copyOf(next), coin, points, names, done, queued);
		}

		Progress withCoin(long coin) {
			return new Progress(given, coin, points, names, done, queued);
		}

		Progress withPoints(UUID who, String name, int add) {
			Map<String, Integer> p = new HashMap<>(points);
			p.merge(who.toString(), add, Integer::sum);
			Map<String, String> n = new HashMap<>(names);
			n.put(who.toString(), name);
			return new Progress(given, coin, Map.copyOf(p), Map.copyOf(n), done, queued);
		}

		Progress withDone(long done) {
			return new Progress(given, coin, points, names, done, queued);
		}

		Progress withQueued(boolean queued) {
			return new Progress(given, coin, points, names, done, queued);
		}
	}

	/** What is owed to a contributor who was not on: coin (milli) and reputation. */
	record Owed(long milli, int reputation) {
		static final Codec<Owed> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("milli", 0L).forGetter(Owed::milli),
				Codec.INT.optionalFieldOf("reputation", 0).forGetter(Owed::reputation))
			.apply(i, Owed::new));
	}

	/** A town's works: each project's progress by id, and what's owed to contributors who weren't on (by UUID string). */
	record TownWorks(Map<String, Progress> projects, Map<String, Owed> owed) {
		static final Codec<TownWorks> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Progress.CODEC).optionalFieldOf("projects", Map.of()).forGetter(TownWorks::projects),
				Codec.unboundedMap(Codec.STRING, Owed.CODEC).optionalFieldOf("owed", Map.of()).forGetter(TownWorks::owed))
			.apply(i, TownWorks::new));
		static final TownWorks NONE = new TownWorks(Map.of(), Map.of());

		Progress of(String id) {
			return projects.getOrDefault(id, Progress.NONE);
		}

		TownWorks with(String id, Progress progress) {
			Map<String, Progress> next = new HashMap<>(projects);
			next.put(id, progress);
			return new TownWorks(Map.copyOf(next), owed);
		}

		TownWorks owing(UUID who, long milli, int reputation) {
			Map<String, Owed> next = new HashMap<>(owed);
			next.merge(who.toString(), new Owed(milli, reputation), (a, b) -> new Owed(a.milli() + b.milli(), a.reputation() + b.reputation()));
			return new TownWorks(projects, Map.copyOf(next));
		}

		TownWorks without(UUID who) {
			Map<String, Owed> next = new HashMap<>(owed);
			next.remove(who.toString());
			return new TownWorks(projects, Map.copyOf(next));
		}
	}

	/** Faction id → its works. Kept on the Overworld. */
	static final AttachmentType<Map<String, TownWorks>> PROJECTS = AttachmentRegistry.create(JaymosKingdoms.id("town_projects"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, TownWorks.CODEC)));

	/** Player → the board whose works they're reading. */
	private static final Map<UUID, BlockPos> READING = new HashMap<>();

	private TownProjects() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				check(server);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> payOwed(server, handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READING.clear());
	}

	// ---- the sheet on the board ------------------------------------------------------------------------------------------

	static boolean is(String key) {
		return key.startsWith(SHEET);
	}

	static String sheetKey(BlockPos anchor) {
		return SHEET + anchor.asLong();
	}

	/** The board a sheet belongs to. */
	static @Nullable BlockPos boardOf(String key) {
		try {
			return BlockPos.of(Long.parseLong(key.substring(SHEET.length())));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** The sheet as the paper a villager carries to the board. */
	static ItemStack sheet(String key) {
		ItemStack stack = new ItemStack(TownNotices.ITEM);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("project.jaymoskingdoms.sheet")
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.WHITE)));
		CompoundTag tag = new CompoundTag();
		tag.putString(Bounties.NOTICE_KEY, key);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** What the villager calls out pinning the sheet up. */
	static Component callOut(RandomSource random) {
		return Component.translatable("talk.jaymoskingdoms.works.posting." + random.nextInt(3));
	}

	// ---- state -----------------------------------------------------------------------------------------------------------

	static TownWorks works(MinecraftServer server, int faction) {
		return Factions.level(server).getAttachedOrElse(PROJECTS, Map.<String, TownWorks>of()).getOrDefault(Integer.toString(faction), TownWorks.NONE);
	}

	private static void put(MinecraftServer server, int faction, TownWorks works) {
		Map<String, TownWorks> all = new HashMap<>(Factions.level(server).getAttachedOrElse(PROJECTS, Map.<String, TownWorks>of()));
		all.put(Integer.toString(faction), works);
		Factions.level(server).setAttached(PROJECTS, Map.copyOf(all));
	}

	static @Nullable Project project(String id) {
		for (Project p : PROJECTS_LIST) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return null;
	}

	private static boolean standing(@Nullable Faction f) {
		return f != null && !f.fallen() && f.type() != FactionType.BARBARIAN;
	}

	private static boolean finished(Project p, Progress pr) {
		for (int i = 0; i < p.needs().size(); i++) {
			if (pr.given(i) < p.needs().get(i).count()) {
				return false;
			}
		}
		return pr.coin() >= Money.worth(p.coin());
	}

	/** Share points the whole project is worth (every need met and the coin given). */
	private static long totalWeight(Project p) {
		long total = Money.worth(p.coin()) * COIN_POINTS / Money.UNIT;
		for (Need n : p.needs()) {
			total += (long)n.count() * n.points();
		}
		return Math.max(1L, total);
	}

	/** How far along it is, 0..1. */
	private static float fraction(Project p, Progress pr) {
		if (pr.done() >= 0) {
			return 1.0F;
		}
		long have = Math.min(pr.coin(), Money.worth(p.coin())) * COIN_POINTS / Money.UNIT;
		for (int i = 0; i < p.needs().size(); i++) {
			Need n = p.needs().get(i);
			have += (long)Math.min(pr.given(i), n.count()) * n.points();
		}
		return Mth.clamp(have / (float)totalWeight(p), 0.0F, 1.0F);
	}

	/**
	 * Whether the bridge could do anything for {@code f}: some town within {@link Roads#LINK_RANGE} is not already one of its nearest
	 * {@link Roads#LINKS}.
	 */
	static boolean bridgeable(MinecraftServer server, Faction f) {
		long near = Roads.towns(server).stream().filter(g -> g.id() != f.id() && g.seat().closerThan(f.seat(), Roads.LINK_RANGE)).count();
		return near > Roads.LINKS;
	}

	/** The projects {@code f} can show now. */
	private static List<Project> listed(MinecraftServer server, Faction f) {
		List<Project> out = new ArrayList<>();
		TownWorks w = works(server, f.id());
		for (Project p : PROJECTS_LIST) {
			if (p.id().equals("bridge") && w.of(p.id()).done() < 0 && !bridgeable(server, f)) {
				continue;
			}
			out.add(p);
		}
		return out;
	}

	// ---- the bonus reads ---------------------------------------------------------------------------------------------------

	static boolean done(MinecraftServer server, int faction, String id) {
		return works(server, faction).of(id).done() >= 0;
	}

	/** Raids: the chance on {@code target}, times {@link #WALL_RAID} and/or {@link #TOWER_RAID} (1 for null or none). */
	static float raidFactor(MinecraftServer server, @Nullable Faction target) {
		if (target == null) {
			return 1.0F;
		}
		TownWorks w = works(server, target.id());
		float factor = 1.0F;
		if (w.of("wall").done() >= 0) {
			factor *= WALL_RAID;
		}
		if (w.of("watchtower").done() >= 0) {
			factor *= TOWER_RAID;
		}
		return factor;
	}

	/** Economy: the food a town wants in store ({@link Economy#FOOD_RESERVE}, or times {@link #GRANARY_RESERVE} with a granary). */
	static int foodReserve(MinecraftServer server, Faction f) {
		return done(server, f.id(), "granary") ? Math.round(Economy.FOOD_RESERVE * GRANARY_RESERVE) : Economy.FOOD_RESERVE;
	}

	/** Roads: links on top of {@link Roads#LINKS} ({@link #BRIDGE_LINKS} with a bridge). */
	static int extraLinks(MinecraftServer server, Faction f) {
		return done(server, f.id(), "bridge") ? BRIDGE_LINKS : 0;
	}

	/** Raids.start: the watchtower's warning to players near {@code target}. */
	static void watch(ServerLevel level, Faction target, BlockPos muster) {
		if (!done(level.getServer(), target.id(), "watchtower")) {
			return;
		}
		Component warning = Component.translatable("project.jaymoskingdoms.watchtower.warn", target.name(), muster.getX(), muster.getZ()).withStyle(ChatFormatting.GOLD);
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(target.seat(), TOWER_WARN_RANGE)) {
				p.sendSystemMessage(warning);
				level.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
		}
	}

	/** TownTroubles: how many more sick it takes here for a plague (a well, an infirmary). */
	static int sickMore(MinecraftServer server, Faction f) {
		return (done(server, f.id(), "well") ? WELL_SICK_MORE : 0) + (done(server, f.id(), "infirmary") ? INFIRMARY_SICK_MORE : 0);
	}

	/** TownTroubles: how many more hurt it takes here for wounds to be a trouble (an infirmary). */
	static int woundedMore(MinecraftServer server, Faction f) {
		return done(server, f.id(), "infirmary") ? INFIRMARY_WOUNDED_MORE : 0;
	}

	// ---- the screen ------------------------------------------------------------------------------------------------------

	/** The town the board at {@code anchor} serves. */
	private static @Nullable Faction town(ServerLevel level, BlockPos anchor) {
		Faction f = Factions.around(level.getServer(), level, anchor, Couriers.REALM_RANGE);
		return standing(f) ? f : null;
	}

	private static void send(ServerPlayer player, QuestNet.Open open) {
		if (ServerPlayNetworking.canSend(player, QuestNet.Open.TYPE)) {
			ServerPlayNetworking.send(player, open);
		}
	}

	/** (Bounties) {@code player} reads the works sheet on the board at {@code anchor}. */
	static void open(ServerLevel level, ServerPlayer player, BlockPos anchor) {
		READING.put(player.getUUID(), anchor.immutable());
		Faction f = town(level, anchor);
		Component title = Component.translatable("project.jaymoskingdoms.screen", f == null ? Component.translatable("bounty.jaymoskingdoms.nowhere") : f.name());
		if (f == null) {
			send(player, new QuestNet.Open(QuestNet.PROJECTS, title, List.of(), Component.translatable("project.jaymoskingdoms.no_town")));
			return;
		}
		if (Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
			send(player, new QuestNet.Open(QuestNet.PROJECTS, title, List.of(), Component.translatable("project.jaymoskingdoms.refused", f.name())));
			return;
		}
		payOwed(level.getServer(), player);
		TownWorks w = works(level.getServer(), f.id());
		List<Project> shown = new ArrayList<>(listed(level.getServer(), f));
		// (open ones first, the most nearly finished first; then the finished)
		shown.sort(Comparator.comparingInt((Project p) -> w.of(p.id()).done() >= 0 ? 1 : 0).thenComparing(p -> -fraction(p, w.of(p.id()))));
		List<QuestNet.View> views = new ArrayList<>();
		for (Project p : shown) {
			views.add(view(level, player, f, p, w.of(p.id())));
		}
		send(player, new QuestNet.Open(QuestNet.PROJECTS, title, views, Component.translatable("project.jaymoskingdoms.none", f.name())));
	}

	private static QuestNet.View view(ServerLevel level, ServerPlayer player, Faction f, Project p, Progress pr) {
		boolean open = pr.done() < 0;
		int percent = Mth.floor(fraction(p, pr) * 100.0F);
		Component name = Component.translatable("project.jaymoskingdoms." + p.id() + ".title");
		Component title = Component.translatable("project.jaymoskingdoms.title", name, percent);
		Component text = open ? Component.translatable("project.jaymoskingdoms." + p.id() + ".text") : Component.translatable("project.jaymoskingdoms." + p.id() + ".done", f.name());
		net.minecraft.network.chat.MutableComponent objective = Component.empty();
		List<ItemStack> icons = new ArrayList<>();
		for (int i = 0; i < p.needs().size(); i++) {
			Need n = p.needs().get(i);
			if (i > 0) {
				objective.append(" · ");
			}
			objective.append(Component.translatable("project.jaymoskingdoms.need", needName(n), Math.min(pr.given(i), n.count()), n.count()));
			int left = n.count() - pr.given(i);
			if (left > 0) {
				ItemStack icon = icon(n);
				if (!icon.isEmpty()) {
					icon.setCount(Math.min(left, icon.getMaxStackSize()));
					icons.add(icon);
				}
			}
		}
		long total = Money.worth(p.coin());
		objective.append(" · ").append(Component.translatable("project.jaymoskingdoms.coin", Money.text(f, Math.min(pr.coin(), total)), Money.text(f, total)));
		if (pr.coin() < total) {
			List<ItemStack> coins = Money.stacks(f, total - pr.coin());
			if (!coins.isEmpty()) {
				icons.add(coins.get(0));
			}
		}
		List<Component> extras = new ArrayList<>();
		extras.add(Component.translatable("project.jaymoskingdoms.gives", Component.translatable("project.jaymoskingdoms." + p.id() + ".gives")));
		int mine = pr.points().getOrDefault(player.getUUID().toString(), 0);
		if (mine > 0) {
			int all = pr.points().values().stream().mapToInt(Integer::intValue).sum();
			extras.add(Component.translatable("project.jaymoskingdoms.share", Mth.floor(100.0F * mine / Math.max(1, all))));
		}
		if (!pr.points().isEmpty()) {
			extras.add(Component.translatable("project.jaymoskingdoms.givers", pr.points().size()));
		}
		return new QuestNet.View(p.id(), open ? QuestNet.OFFER : QuestNet.READY, false, title, text, objective, icons, extras);
	}

	private static @Nullable TagKey<Item> tag(Need n) {
		if (!n.what().startsWith("#")) {
			return null;
		}
		Identifier id = Identifier.tryParse(n.what().substring(1));
		return id == null ? null : TagKey.create(Registries.ITEM, id);
	}

	private static Component needName(Need n) {
		TagKey<Item> tag = tag(n);
		if (tag != null) {
			return Component.translatable("project.jaymoskingdoms.tag." + tag.location().getPath());
		}
		Identifier id = Identifier.tryParse(n.what());
		return id == null ? Component.literal(n.what()) : BuiltInRegistries.ITEM.getOptional(id).map(item -> new ItemStack(item).getHoverName()).orElse(Component.literal(n.what()));
	}

	private static ItemStack icon(Need n) {
		TagKey<Item> tag = tag(n);
		if (tag != null) {
			for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
				return new ItemStack(holder.value());
			}
			return ItemStack.EMPTY;
		}
		Identifier id = Identifier.tryParse(n.what());
		return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	// ---- giving ----------------------------------------------------------------------------------------------------------

	/** (Quests) A choice on the works screen: give goods or coin to project {@code act.questId()}. */
	static void act(ServerPlayer player, QuestNet.Act act) {
		BlockPos at = READING.get(player.getUUID());
		if (act.action() == QuestNet.CLOSE) {
			READING.remove(player.getUUID());
			return;
		}
		if (at == null || !(player.level() instanceof ServerLevel level) || level != Factions.level(level.getServer())
			|| player.distanceToSqr(Vec3.atCenterOf(at)) > READ_RANGE * READ_RANGE) {
			return;
		}
		Faction f = town(level, at);
		Project p = project(act.questId());
		if (f == null || p == null || Reputation.get(player, f.id()) <= Reputation.HOSTILE || !listed(level.getServer(), f).contains(p)) {
			return;
		}
		MinecraftServer server = level.getServer();
		Progress pr = works(server, f.id()).of(p.id());
		if (pr.done() >= 0) {
			return;
		}
		switch (act.action()) {
			case QuestNet.GIVE_GOODS -> pr = giveGoods(level, player, f, p, pr);
			case QuestNet.GIVE_COIN -> pr = giveCoin(player, f, p, pr, COIN_SMALL);
			case QuestNet.GIVE_COINS -> pr = giveCoin(player, f, p, pr, COIN_BIG);
			default -> {
				return;
			}
		}
		put(server, f.id(), works(server, f.id()).with(p.id(), pr));
		if (finished(p, pr)) {
			complete(level, f, p);
		}
		open(level, player, at);
	}

	private static Progress giveCoin(ServerPlayer player, Faction f, Project p, Progress pr, int emeralds) {
		long owed = Money.worth(p.coin()) - pr.coin();
		long milli = Math.min(Money.worth(emeralds), owed);
		if (milli <= 0) {
			return pr;
		}
		if (!Money.charge(player, f, milli)) {
			player.sendSystemMessage(Component.translatable("project.jaymoskingdoms.poor").withStyle(ChatFormatting.RED));
			return pr;
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_WORK_MASON, SoundSource.PLAYERS, 0.8F, 1.0F);
		return pr.withCoin(pr.coin() + milli).withPoints(player.getUUID(), player.getGameProfile().name(), (int)(milli * COIN_POINTS / Money.UNIT));
	}

	private static boolean matches(ItemStack stack, Need n) {
		TagKey<Item> tag = tag(n);
		if (tag != null) {
			return stack.is(tag);
		}
		Identifier id = Identifier.tryParse(n.what());
		return id != null && BuiltInRegistries.ITEM.getOptional(id).map(stack::is).orElse(false);
	}

	/** Hands over what {@code player} carries that {@code p} still needs, into the town's stores if it has any. */
	private static Progress giveGoods(ServerLevel level, ServerPlayer player, Faction f, Project p, Progress start) {
		Progress pr = start;
		boolean stores = !f.holdings().storage().isEmpty();
		boolean full = false;
		int total = 0;
		Inventory inv = player.getInventory();
		int[] slots = new int[Inventory.INVENTORY_SIZE + 1];
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			slots[i] = i;
		}
		slots[Inventory.INVENTORY_SIZE] = Inventory.SLOT_OFFHAND;
		for (int i = 0; i < p.needs().size(); i++) {
			Need n = p.needs().get(i);
			int left = n.count() - pr.given(i);
			for (int slot : slots) {
				if (left <= 0) {
					break;
				}
				ItemStack stack = inv.getItem(slot);
				// (letters, keepsakes, notices and named things are not goods)
				if (stack.isEmpty() || stack.has(DataComponents.CUSTOM_DATA) || stack.has(DataComponents.CUSTOM_NAME) || !matches(stack, n)) {
					continue;
				}
				ItemStack taken = stack.split(Math.min(stack.getCount(), left));
				int counted = taken.getCount();
				if (stores) {
					ItemStack over = Storage.put(level, f, taken);
					counted -= over.getCount();
					if (!over.isEmpty()) {
						full = true;
						if (!inv.add(over)) {
							player.drop(over, false, Prediction.SERVER_ONLY);
						}
					}
				}
				if (counted > 0) {
					pr = pr.withGiven(i, pr.given(i) + counted).withPoints(player.getUUID(), player.getGameProfile().name(), counted * n.points());
					left -= counted;
					total += counted;
				}
				if (full) {
					break;
				}
			}
			if (full) {
				break;
			}
		}
		if (total == 0) {
			player.sendSystemMessage(Component.translatable(full ? "project.jaymoskingdoms.store_full" : "project.jaymoskingdoms.nothing").withStyle(ChatFormatting.GRAY));
			return pr;
		}
		player.sendSystemMessage(Component.translatable("project.jaymoskingdoms.given", total, Component.translatable("project.jaymoskingdoms." + p.id() + ".title")).withStyle(ChatFormatting.GRAY));
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_WORK_MASON, SoundSource.PLAYERS, 0.8F, 1.0F);
		return pr;
	}

	// ---- finishing -------------------------------------------------------------------------------------------------------

	private static void complete(ServerLevel level, Faction f, Project p) {
		MinecraftServer server = level.getServer();
		Progress pr = works(server, f.id()).of(p.id()).withDone(Quests.day(level));
		// the structure, when there is one: handed to the builders (kept trying while their list is full)
		if (!p.builds().isEmpty()) {
			pr = pr.withQueued(handToBuilders(server, f, p.builds()));
		}
		put(server, f.id(), works(server, f.id()).with(p.id(), pr));
		// the effect
		switch (p.id()) {
			case "granary" -> {
				if (!f.holdings().storage().isEmpty()) {
					Storage.put(level, f, net.minecraft.world.item.Items.BREAD, GRANARY_FOOD);
				}
			}
			case "wall" -> WalledTowns.fund(server, f, WALL_PURSE);
			case "bridge" -> Roads.hurry(server);
			default -> {
			}
		}
		// the news
		Component news = Component.translatable("project.jaymoskingdoms.complete", f.name(), Component.translatable("project.jaymoskingdoms." + p.id() + ".title"),
			pr.points().size()).withStyle(ChatFormatting.GOLD);
		if (f.type().isRealm()) {
			KingdomEvents.announce(server, news);
		} else {
			for (ServerPlayer player : level.players()) {
				if (player.blockPosition().closerThan(f.seat(), LOCAL_NEWS)) {
					player.sendSystemMessage(news);
				}
			}
		}
		// the payout: a quarter of the coin and the bonus, shared by points; the rest of the coin to the treasury
		long back = Math.round(pr.coin() * PAYBACK);
		long purse = back + Money.worth(p.bonus());
		long rest = pr.coin() - back;
		if (rest >= Money.UNIT) {
			Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() + rest / Money.UNIT));
		}
		int all = Math.max(1, pr.points().values().stream().mapToInt(Integer::intValue).sum());
		for (Map.Entry<String, Integer> e : pr.points().entrySet()) {
			double share = e.getValue() / (double)all;
			long milli = Math.round(purse * share);
			int rep = REP_LEAST + (int)Math.round(REP_POOL * share);
			UUID who;
			try {
				who = UUID.fromString(e.getKey());
			} catch (IllegalArgumentException ex) {
				continue;
			}
			ServerPlayer online = server.getPlayerList().getPlayer(who);
			if (online != null) {
				Money.pay(online, f, milli);
				Reputation.change(online, f, rep);
				online.sendSystemMessage(Component.translatable("project.jaymoskingdoms.paid", f.name(), Money.text(f, milli),
					Component.translatable("project.jaymoskingdoms." + p.id() + ".title")).withStyle(ChatFormatting.GREEN));
			} else {
				put(server, f.id(), works(server, f.id()).owing(who, milli, rep));
			}
		}
		JaymosKingdoms.LOGGER.info("{} finished its {} with {} givers", f.name(), p.id(), pr.points().size());
	}

	/** Gives the town's builders catalogue building {@code id}. True when it is handed over, or when there is nothing to hand (no such building). */
	private static boolean handToBuilders(MinecraftServer server, Faction f, String id) {
		if (Blueprint.catalogue(f.type()).stream().noneMatch(e -> e.id().equals(id))) {
			JaymosKingdoms.LOGGER.debug("{} has no catalogue building {} for its project", f.name(), id);
			return true;
		}
		return Commissions.give(server, f, id);
	}

	/** Pays {@code player} what any town owes them from projects finished while they were away. */
	static void payOwed(MinecraftServer server, ServerPlayer player) {
		String key = player.getUUID().toString();
		for (Map.Entry<String, TownWorks> e : List.copyOf(Factions.level(server).getAttachedOrElse(PROJECTS, Map.<String, TownWorks>of()).entrySet())) {
			Owed owed = e.getValue().owed().get(key);
			if (owed == null) {
				continue;
			}
			int id;
			try {
				id = Integer.parseInt(e.getKey());
			} catch (NumberFormatException ex) {
				continue;
			}
			Faction f = Factions.get(server, id);
			put(server, id, e.getValue().without(player.getUUID()));
			if (standing(f)) {
				Money.pay(player, f, owed.milli());
				Reputation.change(player, f, owed.reputation());
			} else {
				Money.pay(player, null, owed.milli());
			}
			player.sendSystemMessage(Component.translatable("project.jaymoskingdoms.paid_late", f == null ? "?" : f.name(), Money.text(standing(f) ? f : null, owed.milli()))
				.withStyle(ChatFormatting.GREEN));
		}
	}

	/** Every so often: finished projects whose building the builders could not take yet are offered again; fallen towns are dropped. */
	private static void check(MinecraftServer server) {
		for (Map.Entry<String, TownWorks> e : List.copyOf(Factions.level(server).getAttachedOrElse(PROJECTS, Map.<String, TownWorks>of()).entrySet())) {
			int id;
			try {
				id = Integer.parseInt(e.getKey());
			} catch (NumberFormatException ex) {
				continue;
			}
			Faction f = Factions.get(server, id);
			TownWorks w = e.getValue();
			if (!standing(f)) {
				if (w.owed().isEmpty()) {
					Map<String, TownWorks> all = new HashMap<>(Factions.level(server).getAttachedOrElse(PROJECTS, Map.<String, TownWorks>of()));
					all.remove(e.getKey());
					Factions.level(server).setAttached(PROJECTS, Map.copyOf(all));
				}
				continue;
			}
			for (Project p : PROJECTS_LIST) {
				Progress pr = w.of(p.id());
				if (pr.done() >= 0 && !pr.queued() && !p.builds().isEmpty() && handToBuilders(server, f, p.builds())) {
					put(server, id, works(server, id).with(p.id(), pr.withQueued(true)));
				}
			}
		}
	}

	// ---- for ops ---------------------------------------------------------------------------------------------------------

	static List<String> ids() {
		return PROJECTS_LIST.stream().map(Project::id).toList();
	}

	/** (Testing) Fills project {@code id} of {@code f} with {@code player} as the only contributor, and completes it. False if unknown or already finished. */
	static boolean fill(ServerPlayer player, Faction f, String id) {
		Project p = project(id);
		if (p == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		MinecraftServer server = level.getServer();
		Progress pr = works(server, f.id()).of(id);
		if (pr.done() >= 0) {
			return false;
		}
		for (int i = 0; i < p.needs().size(); i++) {
			pr = pr.withGiven(i, p.needs().get(i).count());
		}
		pr = pr.withCoin(Money.worth(p.coin())).withPoints(player.getUUID(), player.getGameProfile().name(), (int)totalWeight(p));
		put(server, f.id(), works(server, f.id()).with(id, pr));
		complete(level, f, p);
		return true;
	}
}

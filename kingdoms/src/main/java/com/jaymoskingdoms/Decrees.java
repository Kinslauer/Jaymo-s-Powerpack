package com.jaymoskingdoms;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (village mods patch 4, ideas from Bountiful's decrees: our own code, names and art) A decree pinned on a town board
 * steers what the town posts: a <b>smiths'</b> decree (building, lamps, blasting, stores: timber, stone, ore), a
 * <b>hunters'</b> (packs, named killers, dens: hides and the like), a <b>farmers'</b> (food and animals on a lead) or a
 * <b>war</b> decree (holding the town, captives, the wanted). Up to {@link #MOST} on a board, one of a kind.
 *
 * <p>A town's ruler posts them by what the town is short of ({@link #wanted}): its food, the stores for its next
 * building or its builders' waiting repairs ({@link Upkeep}), a war, an attack on its people. A player earns one from
 * hard board work ({@link #earned}) and pins it on any board. Either comes down after {@link #LIFE}. A pinned decree is
 * {@code decree|<kind>|<r court id, or p player name>|<posted at>} ({@link Bounties#PINNED}); used, it's read, and stays up.
 */
final class Decrees {
	// ---- tuning ----
	/** At most this many decrees on one board, and this many of them a ruler's. */
	static final int MOST = 3, RULER_MOST = 2;
	/** A decree comes down after this long (ticks: three days). */
	static final long LIFE = 24000L * 3;
	/** How much likelier work and errands of a decree's trade are, while it's up; what each decree adds to the chance a notice is paid work. */
	static final int WEIGHT = 4;
	static final float WORK_CHANCE = 0.1F;
	/** The chance a board check that finds its town short posts a ruler's decree. */
	static final float POST_CHANCE = 0.25F;
	/** Board work of this many stars or more may earn a decree: the chance at four stars, at five. */
	static final int EARN_STARS = 4;
	static final float EARN_FOUR = 0.35F, EARN_FIVE = 1.0F;

	static final List<String> KINDS = List.of("smiths", "hunters", "farmers", "war");
	/** Errand goods of the hunters' trade (the rest that aren't food are the smiths'). */
	private static final Set<String> HUNTERS_GOODS = Set.of("minecraft:rabbit_hide", "minecraft:leather", "minecraft:slime_ball", "minecraft:bone",
		"minecraft:string", "minecraft:feather", "minecraft:ink_sac", "minecraft:spider_eye", "minecraft:gunpowder");
	/** Errand goods of the farmers' trade besides food. */
	private static final Set<String> FARMERS_GOODS = Set.of("minecraft:hay_block", "minecraft:egg", "minecraft:wheat", "minecraft:cocoa_beans",
		"minecraft:sweet_berries", "minecraft:honeycomb", "minecraft:pink_petals", "minecraft:lily_pad", "minecraft:water_bucket");
	private static final String TAG = "jaymoskingdoms_decree";

	static Item ITEM;

	private Decrees() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("decree"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
	}

	// ---- the key and the paper ---------------------------------------------------------------------------------------------

	static boolean is(String key) {
		return key.startsWith("decree|");
	}

	/** {@code key}'s kind, or null if it isn't a decree we know. */
	static @Nullable String kind(String key) {
		String[] p = key.split("\\|");
		return p.length >= 4 && p[0].equals("decree") && KINDS.contains(p[1]) ? p[1] : null;
	}

	static boolean expired(String key, long now) {
		String[] p = key.split("\\|");
		try {
			return kind(key) == null || now - Long.parseLong(p[3]) > LIFE;
		} catch (NumberFormatException e) {
			return true;
		}
	}

	static Component name(String kind) {
		return Component.translatable("item.jaymoskingdoms.decree." + kind);
	}

	/** A decree of {@code kind} as paper, for a herald to carry or a player to keep. */
	static ItemStack item(String kind) {
		ItemStack stack = new ItemStack(ITEM);
		stack.set(DataComponents.CUSTOM_NAME, name(kind).copy().withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("decree.jaymoskingdoms." + kind + ".words").withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)),
			Component.translatable("decree.jaymoskingdoms.pin").withStyle(style -> style.withItalic(false).withColor(ChatFormatting.DARK_GRAY)))));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(kind), List.of()));
		CompoundTag tag = new CompoundTag();
		tag.putString(TAG, kind);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** The kind of the decree {@code stack} is, or null. */
	static @Nullable String kindOf(ItemStack stack) {
		CustomData data = stack.is(ITEM) ? stack.get(DataComponents.CUSTOM_DATA) : null;
		String kind = data == null ? null : data.copyTag().getString(TAG).orElse(null);
		return kind != null && KINDS.contains(kind) ? kind : null;
	}

	// ---- on a board --------------------------------------------------------------------------------------------------------

	/** The keys of the decrees pinned on the board at {@code anchor}. */
	static List<String> pinned(ServerLevel level, BlockPos anchor) {
		BlockState state = level.getBlockState(anchor);
		if (!state.is(Bounties.BOARD)) {
			return List.of();
		}
		Direction facing = state.getValue(BountyBoard.FACING);
		java.util.ArrayList<String> out = new java.util.ArrayList<>();
		for (int c = 0; c < BountyBoard.WIDTH; c++) {
			for (int r = 0; r < BountyBoard.HEIGHT; r++) {
				String key = Bounties.pinned(level).get(Long.toString(BountyBoard.part(anchor, facing, c, r).asLong()));
				if (key != null && is(key)) {
					out.add(key);
				}
			}
		}
		return out;
	}

	/** The kinds of decree up on the board at {@code anchor}. */
	static Set<String> on(ServerLevel level, BlockPos anchor) {
		Set<String> out = new HashSet<>();
		for (String key : pinned(level, anchor)) {
			String kind = kind(key);
			if (kind != null) {
				out.add(kind);
			}
		}
		return out;
	}

	/** How much likelier something of {@code trade} is posted under {@code up}. */
	static int weight(Set<String> up, @Nullable String trade) {
		return trade != null && up.contains(trade) ? WEIGHT : 1;
	}

	/** The trade a kind of board work belongs to ({@link BoardWork}). */
	static String ofWork(String kind) {
		return switch (kind) {
			case "pack", "elite", "incursion" -> "hunters";
			case "build", "blast", "lamps", "stash" -> "smiths";
			case "defend", "capture", "hostage" -> "war";
			default -> "farmers";
		};
	}

	/** The trade an errand's goods belong to: animals and food the farmers', hides and the like the hunters', the rest the smiths'. */
	static String ofGoods(String target, boolean lead) {
		if (lead || FARMERS_GOODS.contains(target)) {
			return "farmers";
		}
		if (HUNTERS_GOODS.contains(target)) {
			return "hunters";
		}
		Identifier id = Identifier.tryParse(target);
		Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		return item != null && new ItemStack(item).has(DataComponents.FOOD) ? "farmers" : "smiths";
	}

	// ---- a ruler's decrees -------------------------------------------------------------------------------------------------

	/**
	 * A ruler's decree the board at {@code anchor} wants posted, or null: the first thing the town is short of that no decree
	 * on its board answers yet. Only a town under a ruler (its court has one in place) gets them.
	 */
	static @Nullable String wanted(ServerLevel level, BlockPos anchor, long now, RandomSource random) {
		if (random.nextFloat() >= POST_CHANCE) {
			return null;
		}
		MinecraftServer server = level.getServer();
		Faction f = Factions.around(server, level, anchor, Villages.VILLAGE_RADIUS);
		Faction court = Bounties.court(server, f);
		if (f == null || f.fallen() || court == null || court.leader().isEmpty()) {
			return null;
		}
		List<String> up = pinned(level, anchor);
		if (up.size() >= MOST || up.stream().filter(k -> k.split("\\|")[2].startsWith("r")).count() >= RULER_MOST) {
			return null;
		}
		Set<String> kinds = on(level, anchor);
		for (String kind : KINDS) {
			boolean short_ = switch (kind) {
				case "war" -> Economy.atWar(server, f) || Economy.atWar(server, court);
				case "hunters" -> Economy.underAttack(server, f);
				case "farmers" -> !Economy.hasFood(level, f);
				default -> !Storage.has(level, f, Economy.nextNeeds.apply(level, f)) || Upkeep.waiting(server, f.id()) >= Upkeep.BATCH;
			};
			if (short_ && !kinds.contains(kind)) {
				return "decree|" + kind + "|r" + court.id() + "|" + now;
			}
		}
		return null;
	}

	/** What the herald calls out pinning a decree up. */
	static Component callOut(String key, RandomSource random) {
		String kind = kind(key);
		return Component.translatable("talk.jaymoskingdoms.decree.posting." + random.nextInt(3), kind == null ? Component.empty() : name(kind));
	}

	/** {@code player} reads the decree {@code key} on a board: what it orders, whose it is, how long it stands. */
	static void read(ServerLevel level, ServerPlayer player, String key) {
		String kind = kind(key);
		if (kind == null) {
			return;
		}
		String[] p = key.split("\\|");
		Component by;
		if (p[2].startsWith("r")) {
			Faction court = null;
			try {
				court = Factions.get(level.getServer(), Integer.parseInt(p[2].substring(1)));
			} catch (NumberFormatException ignored) {
				// (no court to name)
			}
			by = Component.translatable("decree.jaymoskingdoms.by.ruler", court == null ? "" : court.name());
		} else {
			by = Component.translatable("decree.jaymoskingdoms.by.player", p[2].substring(1));
		}
		long left = 0;
		try {
			left = Math.max(0L, Long.parseLong(p[3]) + LIFE - level.getGameTime());
		} catch (NumberFormatException ignored) {
			// (read as come down already)
		}
		player.sendSystemMessage(name(kind).copy().withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("decree.jaymoskingdoms." + kind + ".words").withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.translatable("decree.jaymoskingdoms.stands", by, Math.max(1L, (left + 23999L) / 24000L)).withStyle(ChatFormatting.DARK_GRAY));
	}

	/**
	 * {@code player} pins the decree {@code stack} on a bare spot of the board at {@code anchor}: its key, or null (and why
	 * not, told them) if the board has its fill of decrees, or one of that kind.
	 */
	static @Nullable String pin(ServerLevel level, ServerPlayer player, BlockPos anchor, ItemStack stack) {
		String kind = kindOf(stack);
		if (kind == null) {
			return null;
		}
		if (pinned(level, anchor).size() >= MOST) {
			player.sendSystemMessage(Component.translatable("decree.jaymoskingdoms.full", MOST).withStyle(ChatFormatting.GRAY));
			return null;
		}
		if (on(level, anchor).contains(kind)) {
			player.sendSystemMessage(Component.translatable("decree.jaymoskingdoms.same", name(kind)).withStyle(ChatFormatting.GRAY));
			return null;
		}
		return "decree|" + kind + "|p" + player.getGameProfile().name() + "|" + level.getGameTime();
	}

	/** Hard board work of {@code kind} and {@code stars} brought back: maybe the town gives a decree of its trade. */
	static void earned(ServerLevel level, ServerPlayer player, Faction f, String kind, int stars) {
		if (stars < EARN_STARS || level.getRandom().nextFloat() >= (stars >= 5 ? EARN_FIVE : EARN_FOUR)) {
			return;
		}
		String trade = ofWork(kind);
		ItemStack decree = item(trade);
		if (!player.getInventory().add(decree)) {
			player.drop(decree, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		player.sendSystemMessage(Component.translatable("decree.jaymoskingdoms.earned", f.name(), name(trade)).withStyle(ChatFormatting.GOLD));
	}
}

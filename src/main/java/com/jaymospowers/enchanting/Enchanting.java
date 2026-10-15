package com.jaymospowers.enchanting;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.TinkerToolItem;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

/**
 * (0.11.47) Andy: Enchanting System Overhaul (CmdrJane, MIT; https://github.com/CmdrJane/enchanting-system-overhaul),
 * ported to 26.3 as our own. The enchanting table no longer rolls random enchantments for XP:
 * <ul>
 * <li>Reading an enchanted book (right-click) teaches you what's in it; the book keeps what you knew and burns up when
 * it taught you everything. Creative players know everything.</li>
 * <li>At the table you pick any enchantment you know that fits the item and pay its items ({@link EnchantRecipes}),
 * one level at a time, whenever you like. A plain book takes one too, to pass knowledge on.</li>
 * <li>Each item holds only so many enchantments, by what it's made of ({@link #limit}); a curse makes room for one
 * more.</li>
 * <li>The anvil can't push an item past that, and loot comes with fewer enchantments.</li>
 * </ul>
 * Tinkering tools aren't enchantable (their modifiers are their enchantments), so the table never takes them.
 */
public final class Enchanting {
	/** Enchantments an item takes when its material isn't listed in {@link #limit}. */
	public static final int DEFAULT_LIMIT = 4;
	/** Curses that each make room for {@link #CURSE_ROOM} more enchantments. */
	public static final int MAX_CURSES = 1, CURSE_ROOM = 1;
	/** Most enchantments a looted item or book keeps (the rest are dropped at random). */
	public static final int LOOT_ITEM_MAX = 3, LOOT_BOOK_MAX = 10;
	/** Per material (the start of the item's id), as ESO had them. */
	private static final Map<String, Integer> MATERIAL_LIMIT = Map.ofEntries(Map.entry("wooden_", 4), Map.entry("stone_", 2),
		Map.entry("copper_", 3), Map.entry("iron_", 3), Map.entry("golden_", 6), Map.entry("diamond_", 4), Map.entry("netherite_", 5),
		Map.entry("leather_", 5), Map.entry("chainmail_", 4), Map.entry("turtle_", 5));
	private static final Map<String, Integer> ITEM_LIMIT = Map.of("minecraft:fishing_rod", 5, "minecraft:bow", 4,
		"minecraft:crossbow", 4, "minecraft:trident", 4, "minecraft:mace", 4);

	/** What a player has learned: enchantment id to the highest level they've read. Kept through death. */
	public static final AttachmentType<Map<String, Integer>> LEARNED = AttachmentRegistry.create(JaymosPowers.id("enchanting_learned"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath()
			.syncWith(ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), AttachmentSyncPredicate.targetOnly()));

	public static MenuType<EnchantingMenu> MENU;

	private Enchanting() {
	}

	public static void init() {
		MENU = net.minecraft.core.Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("enchanting"),
			new MenuType<>(EnchantingMenu::new, FeatureFlags.VANILLA_SET));
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.ENCHANTED_BOOK)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer server) {
				read(server, stack);
			}
			return InteractionResult.SUCCESS;
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> drops.forEach(Enchanting::trimLoot));
	}

	/** Learns what's in the book: what's new (or a higher level) leaves the book; an empty book burns up. */
	private static void read(ServerPlayer player, ItemStack book) {
		ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		Map<String, Integer> learned = new HashMap<>(player.getAttachedOrElse(LEARNED, Map.of()));
		ItemEnchantments.Mutable left = new ItemEnchantments.Mutable(stored);
		boolean any = false;
		for (var entry : stored.entrySet()) {
			Holder<Enchantment> enchantment = entry.getKey();
			int level = entry.getIntValue();
			String id = enchantment.getRegisteredName();
			if (learned.getOrDefault(id, 0) >= level) {
				continue;
			}
			learned.put(id, level);
			left.set(enchantment, 0);
			any = true;
			player.sendSystemMessage(Component.translatable("message.jaymospowers.enchanting_learned",
				Enchantment.getFullname(enchantment, level).copy().withStyle(enchantment.is(EnchantmentTags.CURSE) ? ChatFormatting.RED : ChatFormatting.AQUA))
				.withStyle(ChatFormatting.GOLD));
		}
		Level level = player.level();
		if (!any) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.enchanting_known").withStyle(ChatFormatting.DARK_GREEN), true);
			return;
		}
		player.setAttached(LEARNED, learned);
		ItemEnchantments rest = left.toImmutable();
		if (rest.isEmpty()) {
			book.shrink(1);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.enchanting_ashes").withStyle(ChatFormatting.DARK_PURPLE), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
		} else {
			book.set(DataComponents.STORED_ENCHANTMENTS, rest);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	/** Loot keeps at most {@link #LOOT_ITEM_MAX} enchantments ({@link #LOOT_BOOK_MAX} on a book), picked at random. */
	private static void trimLoot(ItemStack stack) {
		boolean book = stack.is(Items.ENCHANTED_BOOK);
		ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		int max = book ? LOOT_BOOK_MAX : LOOT_ITEM_MAX;
		if (enchantments.size() <= max) {
			return;
		}
		List<Holder<Enchantment>> keys = new ArrayList<>(enchantments.keySet());
		Collections.shuffle(keys);
		ItemEnchantments.Mutable kept = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (int i = 0; i < max; i++) {
			kept.set(keys.get(i), enchantments.getLevel(keys.get(i)));
		}
		EnchantmentHelper.setEnchantments(stack, kept.toImmutable());
	}

	/** The level of this enchantment the player knows (0: not at all). Creative players know every level. */
	public static int known(Player player, Holder<Enchantment> enchantment) {
		if (player.hasInfiniteMaterials()) {
			return EnchantRecipes.maxLevel(enchantment);
		}
		return player.getAttachedOrElse(LEARNED, Map.of()).getOrDefault(enchantment.getRegisteredName(), 0);
	}

	/** Whether the table takes this item at all. */
	public static boolean enchantable(ItemStack stack) {
		return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK)
			|| stack.has(DataComponents.ENCHANTABLE) && !(stack.getItem() instanceof TinkerToolItem);
	}

	/** How many enchantments (curses aside) this item holds, with this many curses on it. */
	public static int limit(ItemStack stack, int curses) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		Integer base = ITEM_LIMIT.get(id.toString());
		if (base == null) {
			base = DEFAULT_LIMIT;
			for (var entry : MATERIAL_LIMIT.entrySet()) {
				if (id.getPath().startsWith(entry.getKey())) {
					base = entry.getValue();
					break;
				}
			}
		}
		return base + Math.min(curses, MAX_CURSES) * CURSE_ROOM;
	}

	public static int curses(ItemEnchantments enchantments) {
		int n = 0;
		for (Holder<Enchantment> e : enchantments.keySet()) {
			if (e.is(EnchantmentTags.CURSE)) {
				n++;
			}
		}
		return n;
	}

	/** Whether the anvil's result holds more than its item may (and more than went in, so old items still repair). */
	public static boolean overLimit(ItemStack result, ItemStack input) {
		if (result.is(Items.ENCHANTED_BOOK) || result.getItem() instanceof TinkerToolItem) {
			return false;
		}
		ItemEnchantments out = EnchantmentHelper.getEnchantmentsForCrafting(result);
		int curses = curses(out);
		int count = out.size() - curses;
		ItemEnchantments in = EnchantmentHelper.getEnchantmentsForCrafting(input);
		return count > limit(result, curses) && count > in.size() - curses(in);
	}

	/** Where an enchantment stands on an item at the table. */
	public enum State {
		/** Can be added or raised a level (if the items are in). */
		READY,
		/** Already at the most the table gives. */
		MAXED,
		/** Clashes with one already on the item. */
		CLASHES,
		/** The item has no room for another. */
		FULL,
		/** You only know lower levels (from the books you've read). */
		UNKNOWN_LEVEL
	}

	/** An enchantment the player knows that goes on the item, and the level it would go to. */
	public record Option(Holder.Reference<Enchantment> enchantment, int current, int target, State state) {
	}

	/** Every enchantment the player knows that goes on this item, in the registry's order (the table's list). */
	public static List<Option> options(Player player, ItemStack stack) {
		List<Option> out = new ArrayList<>();
		if (stack.isEmpty() || !enchantable(stack)) {
			return out;
		}
		Registry<Enchantment> registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		boolean book = stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
		ItemEnchantments on = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		int curses = curses(on);
		registry.listElements().forEach(enchantment -> {
			int known = known(player, enchantment);
			if (known <= 0 || !book && !enchantment.value().canEnchant(stack)) {
				return;
			}
			int current = on.getLevel(enchantment);
			int max = EnchantRecipes.maxLevel(enchantment);
			int target = current + 1;
			State state = State.READY;
			boolean curse = enchantment.is(EnchantmentTags.CURSE);
			if (current >= max) {
				state = State.MAXED;
				target = current;
			} else if (!book && clashes(on, enchantment)) {
				state = State.CLASHES;
			} else if (current == 0 && (curse ? curses >= MAX_CURSES : on.size() - curses >= limit(stack, curses))) {
				state = State.FULL;
			} else if (target > known && EnchantRecipes.maxLevel(enchantment) > 1 && !player.hasInfiniteMaterials() && LEVELS_NEED_BOOKS) {
				state = State.UNKNOWN_LEVEL;
			}
			out.add(new Option(enchantment, current, target, state));
		});
		return out;
	}

	/**
	 * ESO's "enchantment levelling" setting (off there by default): when on, raising a level needs a book of that level
	 * read first. Off: one book of any level teaches every level.
	 */
	public static final boolean LEVELS_NEED_BOOKS = false;

	private static boolean clashes(ItemEnchantments on, Holder<Enchantment> enchantment) {
		for (Holder<Enchantment> other : on.keySet()) {
			if (!other.equals(enchantment) && !Enchantment.areCompatible(other, enchantment)) {
				return true;
			}
		}
		return false;
	}
}

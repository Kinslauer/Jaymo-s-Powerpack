package com.jaymospowers.tinkering;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * Upgrades added at the Tool Station: a tool and the modifier's items in the other slots. Each level takes one of the
 * tool's modifier slots: {@link #BASE_SLOTS}, one more for each tool level past the first and each level of Enhanced
 * (rose gold). Most work as the vanilla enchantment of the same job; diamond, emerald and netherite raise stats.
 * Tinker tools can't be enchanted at a table (books on an anvil still work).
 */
public enum Modifier {
	HASTE("haste", ChatFormatting.RED, 5, Enchantments.EFFICIENCY, null, Items.REDSTONE_BLOCK),
	SHARPNESS("sharpness", ChatFormatting.WHITE, 5, Enchantments.SHARPNESS, null, Items.QUARTZ_BLOCK),
	LUCK("luck", ChatFormatting.BLUE, 3, Enchantments.FORTUNE, Enchantments.LOOTING, Items.LAPIS_BLOCK),
	SILKY("silky", ChatFormatting.WHITE, 1, Enchantments.SILK_TOUCH, null, Items.COBWEB, Items.GOLD_BLOCK),
	FIERY("fiery", ChatFormatting.GOLD, 2, Enchantments.FIRE_ASPECT, null, Items.BLAZE_ROD),
	KNOCKBACK("knockback", ChatFormatting.GRAY, 2, Enchantments.KNOCKBACK, null, Items.PISTON),
	REINFORCED("reinforced", ChatFormatting.DARK_PURPLE, 3, Enchantments.UNBREAKING, null, Items.OBSIDIAN),
	DIAMOND("diamond", ChatFormatting.AQUA, 1, null, null, Items.DIAMOND),
	EMERALD("emerald", ChatFormatting.GREEN, 1, null, null, Items.EMERALD),
	NETHERITE("netherite", ChatFormatting.DARK_GRAY, 1, null, null, Items.NETHERITE_INGOT);

	/** Modifier slots every tool starts with. */
	/** Whether this item goes into some modifier's recipe. */
	public static boolean ingredient(ItemStack stack) {
		for (Modifier modifier : values()) {
			if (modifier.recipe.contains(stack.getItem())) {
				return true;
			}
		}
		return false;
	}

	public static final int BASE_SLOTS = 3;
	/** Diamond: durability, attack and the tier it lifts the tool to. Emerald: durability share. Netherite: durability share. */
	static final int DIAMOND_DURABILITY = 500;
	static final float DIAMOND_ATTACK = 0.5F, EMERALD_DURABILITY = 0.5F, NETHERITE_DURABILITY = 0.2F, NETHERITE_ATTACK = 1.0F;
	/** Tool levels: xp to go from level n to n+1 is {@link #LEVEL_XP} times 2^(n-1); levels stop at {@link #MAX_LEVEL}. */
	public static final int LEVEL_XP = 100, MAX_LEVEL = 10;

	public final String id;
	public final ChatFormatting colour;
	public final int maxLevel;
	/** The enchantment it works as (on swords and the cleaver, {@link #swordEnchantment} if set), or null. */
	private final @Nullable ResourceKey<Enchantment> enchantment, swordEnchantment;
	/** The items one level takes, one each, in the station's other slots. */
	public final List<Item> recipe;

	Modifier(String id, ChatFormatting colour, int maxLevel, @Nullable ResourceKey<Enchantment> enchantment,
		@Nullable ResourceKey<Enchantment> swordEnchantment, Item... recipe) {
		this.id = id;
		this.colour = colour;
		this.maxLevel = maxLevel;
		this.enchantment = enchantment;
		this.swordEnchantment = swordEnchantment;
		this.recipe = List.of(recipe);
	}

	public Component name(int level) {
		Component name = Component.translatable("modifier.jaymospowers." + id);
		return maxLevel > 1 ? Component.empty().append(name).append(" ").append(Component.translatable("enchantment.level." + level)) : name;
	}

	public Component description() {
		return Component.translatable("modifier.jaymospowers." + id + ".desc");
	}

	public static @Nullable Modifier byId(String id) {
		for (Modifier modifier : values()) {
			if (modifier.id.equals(id)) {
				return modifier;
			}
		}
		return null;
	}

	/** A tool's modifiers and their levels, in the order they were added. */
	public static Map<Modifier, Integer> of(ItemStack stack) {
		Map<Modifier, Integer> out = new LinkedHashMap<>();
		Map<String, Integer> saved = stack.get(Tinkering.TOOL_MODIFIERS);
		if (saved != null) {
			saved.forEach((id, level) -> {
				Modifier modifier = byId(id);
				if (modifier != null && level > 0) {
					out.put(modifier, Math.min(level, modifier.maxLevel));
				}
			});
		}
		return out;
	}

	public static int level(ItemStack stack, Modifier modifier) {
		return of(stack).getOrDefault(modifier, 0);
	}

	// --- tool levels ---

	public static int xp(ItemStack stack) {
		return stack.getOrDefault(Tinkering.TOOL_XP, 0);
	}

	/** The level a tool has reached with this much xp (1 to start). */
	public static int toolLevel(int xp) {
		int level = 1;
		while (level < MAX_LEVEL && xp >= xpFor(level + 1)) {
			level++;
		}
		return level;
	}

	/** Total xp a tool needs to reach this level. */
	public static int xpFor(int level) {
		return LEVEL_XP * ((1 << Math.max(0, level - 1)) - 1);
	}

	/** Modifier slots: the base, one for each level past the first, and Enhanced. */
	public static int slots(ItemStack stack) {
		ToolStats.Stats stats = ToolStats.stats(stack);
		return BASE_SLOTS + toolLevel(xp(stack)) - 1 + (stats == null ? 0 : stats.level(Trait.ENHANCED));
	}

	public static int used(ItemStack stack) {
		return of(stack).values().stream().mapToInt(Integer::intValue).sum();
	}

	// --- adding one ---

	/** Why this modifier can't go on this tool now, or null if it can. */
	public static @Nullable Component cannotAdd(ItemStack tool, Modifier modifier) {
		Map<Modifier, Integer> has = of(tool);
		int level = has.getOrDefault(modifier, 0);
		if (level >= modifier.maxLevel) {
			return Component.translatable("gui.jaymospowers.tool_station.max", modifier.name(level));
		}
		if (used(tool) >= slots(tool)) {
			return Component.translatable("gui.jaymospowers.tool_station.no_slots");
		}
		if (modifier == LUCK && has.containsKey(SILKY) || modifier == SILKY && has.containsKey(LUCK)) {
			return Component.translatable("gui.jaymospowers.tool_station.clash", LUCK.name(1), SILKY.name(1));
		}
		if (modifier == NETHERITE && !has.containsKey(DIAMOND)) {
			return Component.translatable("gui.jaymospowers.tool_station.needs", DIAMOND.name(1));
		}
		return null;
	}

	/** The tool with one more level of this modifier (its stats rebuilt; enchantments come with {@link #enchant}). */
	public static ItemStack add(ItemStack tool, Modifier modifier) {
		ItemStack out = tool.copy();
		Map<String, Integer> saved = new LinkedHashMap<>(out.getOrDefault(Tinkering.TOOL_MODIFIERS, Map.of()));
		saved.merge(modifier.id, 1, Integer::sum);
		out.set(Tinkering.TOOL_MODIFIERS, saved);
		ToolStats.apply(out);
		return out;
	}

	/** Writes the enchantments the modifiers work as (other enchantments are kept), and Netherite's fireproofing. */
	public static void enchant(ItemStack stack, HolderLookup.Provider registries) {
		if (!(stack.getItem() instanceof TinkerToolItem tool)) {
			return;
		}
		HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		boolean sword = tool.type.style == ToolType.Style.SWORD;
		of(stack).forEach((modifier, level) -> {
			ResourceKey<Enchantment> key = sword && modifier.swordEnchantment != null ? modifier.swordEnchantment : modifier.enchantment;
			if (key != null) {
				lookup.get(key).ifPresent(holder -> enchantments.set(holder, Math.max(level, enchantments.getLevel(holder))));
			}
		});
		stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
		if (of(stack).containsKey(NETHERITE)) {
			stack.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(registries.lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(DamageTypeTags.IS_FIRE)));
		}
	}

	// --- stat upgrades, used by ToolStats ---

	static int durability(ItemStack stack, int durability) {
		Map<Modifier, Integer> has = of(stack);
		float out = durability + (has.containsKey(DIAMOND) ? DIAMOND_DURABILITY : 0);
		if (has.containsKey(EMERALD)) {
			out *= 1 + EMERALD_DURABILITY;
		}
		if (has.containsKey(NETHERITE)) {
			out *= 1 + NETHERITE_DURABILITY;
		}
		return Math.round(out);
	}

	static float attack(ItemStack stack, float attack) {
		Map<Modifier, Integer> has = of(stack);
		return attack + (has.containsKey(DIAMOND) ? DIAMOND_ATTACK : 0) + (has.containsKey(NETHERITE) ? NETHERITE_ATTACK : 0);
	}

	static TinkerMaterial.Tier tier(ItemStack stack, TinkerMaterial.Tier tier) {
		Map<Modifier, Integer> has = of(stack);
		TinkerMaterial.Tier lift = has.containsKey(NETHERITE) ? TinkerMaterial.Tier.NETHERITE
			: has.containsKey(DIAMOND) ? TinkerMaterial.Tier.DIAMOND : has.containsKey(EMERALD) ? TinkerMaterial.Tier.IRON : tier;
		return lift.ordinal() > tier.ordinal() ? lift : tier;
	}
}

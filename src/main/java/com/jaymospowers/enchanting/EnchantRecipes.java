package com.jaymospowers.enchanting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

/**
 * What each enchantment costs at the table, level by level: items instead of XP. The vanilla costs are Enchanting System
 * Overhaul's (CmdrJane, MIT, see {@link Enchanting}); density, breach, wind burst and lunge are ours. An enchantment
 * with no row here (another mod's) uses {@link #DEFAULT}.
 * <p>
 * A level is up to 4 ingredients (the table's 4 material slots), written {@code item*count}. {@code #tag} is any item in
 * the tag; {@code potion:<id>} is a potion of that kind; {@code a|b} is either. Potions leave their bottle and buckets
 * their bucket behind.
 */
public final class EnchantRecipes {
	/** Levels 1-3, 4-7 and 8-10 of an enchantment with no recipe of its own (ESO's default recipe). */
	private static final String[] DEFAULT = {
		"lapis_lazuli*12, amethyst_shard*3, gold_ingot*6, diamond*3",
		"lapis_lazuli*32, amethyst_shard*7, gold_ingot*24, diamond*7",
		"lapis_lazuli*48, amethyst_shard*16, gold_ingot*32, diamond*16"};

	private static final Map<String, String[]> RECIPES = new HashMap<>();

	static {
		add("aqua_affinity", "lapis_lazuli*16, glow_ink_sac*12, nautilus_shell*6, potion:water_breathing|potion:long_water_breathing*1");
		add("bane_of_arthropods", "lapis_lazuli*3, glistering_melon_slice*3, spider_eye*3", "lapis_lazuli*6, glistering_melon_slice*4, spider_eye*6",
			"lapis_lazuli*9, glistering_melon_slice*4, spider_eye*4", "lapis_lazuli*12, glistering_melon_slice*4, spider_eye*12",
			"lapis_lazuli*12, glistering_melon_slice*4, spider_eye*16");
		add("blast_protection", "redstone*12, heavy_weighted_pressure_plate*2, iron_ingot*6, gunpowder*6",
			"redstone*16, heavy_weighted_pressure_plate*4, iron_ingot*12, gunpowder*12", "redstone*32, heavy_weighted_pressure_plate*6, iron_ingot*12, gunpowder*24",
			"redstone*32, heavy_weighted_pressure_plate*6, iron_ingot*18, gunpowder*48");
		add("channeling", "lapis_lazuli*32, lightning_rod*1");
		add("binding_curse", "lapis_lazuli*24, redstone*24, cobweb*6, slime_ball*6");
		add("vanishing_curse", "lapis_lazuli*24, redstone*24, lava_bucket*1");
		add("depth_strider", "lapis_lazuli*24, nautilus_shell*3, kelp*12", "lapis_lazuli*24, nautilus_shell*3, kelp*24", "lapis_lazuli*48, nautilus_shell*6, kelp*24");
		add("efficiency", "redstone*12, gold_ingot*6, sugar*6", "redstone*24, gold_ingot*24, sugar*24",
			"redstone*24, gold_ingot*32, sugar*32, potion:swiftness|potion:long_swiftness*1",
			"redstone*32, gold_ingot*32, potion:strength|potion:long_strength*1, potion:swiftness|potion:long_swiftness*1",
			"redstone*32, gold_ingot*32, potion:strong_strength*1, potion:strong_swiftness*1");
		add("feather_falling", "feather*3, #wool*6, hay_block*2", "feather*9, #wool*12, hay_block*4", "feather*12, #wool*12, hay_block*6",
			"feather*12, #wool*24, hay_block*9");
		add("fire_aspect", "glowstone_dust*24, blaze_rod*12, blaze_powder*6, magma_cream*6", "glowstone_dust*64, blaze_rod*16, blaze_powder*18, magma_cream*12");
		add("fire_protection", "lapis_lazuli*12, blaze_powder*6, magma_cream*3", "lapis_lazuli*24, blaze_powder*12, magma_cream*12",
			"lapis_lazuli*32, blaze_powder*12, magma_cream*16", "lapis_lazuli*32, blaze_powder*24, magma_cream*16, potion:fire_resistance|potion:long_fire_resistance*1");
		add("flame", "glowstone_dust*48, blaze_powder*24, magma_cream*12");
		add("fortune", "lapis_lazuli*24, amethyst_shard*6, diamond*3, emerald*3", "lapis_lazuli*24, amethyst_shard*9, diamond*3, emerald*3",
			"lapis_lazuli*32, amethyst_shard*12, diamond*7, emerald*7");
		add("frost_walker", "lapis_lazuli*12, powder_snow_bucket*1", "lapis_lazuli*24, powder_snow_bucket*1, blue_ice*4");
		add("impaling", "glowstone_dust*12, quartz*6, amethyst_shard*3", "glowstone_dust*16, quartz*12, amethyst_shard*3", "glowstone_dust*24, quartz*12, amethyst_shard*6",
			"glowstone_dust*24, quartz*24, amethyst_shard*6", "glowstone_dust*24, quartz*32, amethyst_shard*7");
		add("infinity", "lapis_lazuli*32, ender_pearl*6, arrow*1, redstone*24");
		add("knockback", "redstone*24, piston*4", "redstone*32, piston*12");
		add("looting", "lapis_lazuli*16, emerald*3, raw_gold*12", "lapis_lazuli*32, emerald*10, raw_gold*24", "lapis_lazuli*48, emerald*16, raw_gold*32");
		add("loyalty", "lapis_lazuli*10, slime_ball*1, string*1, redstone*6", "lapis_lazuli*16, slime_ball*2, string*2, redstone*12",
			"lapis_lazuli*24, slime_ball*3, string*3, redstone*12");
		add("luck_of_the_sea", "lapis_lazuli*12, emerald*1, kelp*6", "lapis_lazuli*24, emerald*2, kelp*12", "lapis_lazuli*24, emerald*3, kelp*12");
		add("lure", "lapis_lazuli*12, glow_berries*1, wheat_seeds*3", "lapis_lazuli*12, glow_berries*1, wheat_seeds*6", "lapis_lazuli*12, glow_berries*1, wheat_seeds*12");
		add("mending", "redstone*48, amethyst_shard*12, potion:healing|potion:strong_healing*1,"
			+ " potion:regeneration|potion:long_regeneration|potion:strong_regeneration*1");
		add("multishot", "lapis_lazuli*24, ender_pearl*6, arrow*3");
		add("piercing", "redstone*12, diamond*1, firework_rocket*3", "redstone*24, diamond*1, firework_rocket*12", "redstone*32, diamond*1, firework_rocket*24",
			"redstone*64, diamond*1, firework_rocket*32");
		add("power", "redstone*12, diamond*1, slime_ball*3, string*6", "redstone*16, diamond*1, slime_ball*7, string*12",
			"redstone*24, diamond*1, slime_ball*12, string*12", "redstone*32, diamond*1, slime_ball*12, string*24", "redstone*32, diamond*1, firework_rocket*12");
		add("projectile_protection", "lapis_lazuli*12, #wool*4, heavy_weighted_pressure_plate*2, diamond*1",
			"lapis_lazuli*16, #wool*8, heavy_weighted_pressure_plate*4, diamond*2", "lapis_lazuli*24, #wool*12, heavy_weighted_pressure_plate*4, diamond*2",
			"lapis_lazuli*32, #wool*24, heavy_weighted_pressure_plate*6, diamond*4");
		add("protection", "lapis_lazuli*12, amethyst_shard*4, diamond*2, obsidian*4", "lapis_lazuli*24, amethyst_shard*6, diamond*6",
			"lapis_lazuli*24, amethyst_shard*6, diamond*12, obsidian*8", "glowstone_dust*32, diamond*16, amethyst_shard*12, obsidian*12");
		add("punch", "redstone*24, firework_rocket*3, piston*4", "redstone*32, firework_rocket*8, piston*6");
		add("quick_charge", "redstone*12, string*3, slime_ball*2", "redstone*12, lead*1, slime_ball*6",
			"redstone*24, potion:swiftness|potion:long_swiftness|potion:strong_swiftness*1");
		add("respiration", "lapis_lazuli*6, potion:water_breathing|potion:long_water_breathing*1, amethyst_shard*2",
			"lapis_lazuli*12, potion:water_breathing|potion:long_water_breathing*1, amethyst_shard*3",
			"lapis_lazuli*24, potion:water_breathing|potion:long_water_breathing*1, amethyst_shard*7");
		add("riptide", "lapis_lazuli*12, water_bucket*1, magma_block*2, redstone*12", "lapis_lazuli*24, water_bucket*1, magma_block*8, redstone*16",
			"lapis_lazuli*24, water_bucket*1, magma_block*12, potion:swiftness|potion:long_swiftness|potion:strong_swiftness*1");
		add("sharpness", "redstone*12, diamond*2, quartz*8, amethyst_shard*3", "redstone*16, diamond*3, quartz*14, amethyst_shard*6",
			"redstone*24, diamond*6, quartz*24, amethyst_shard*7", "redstone*32, diamond*9, quartz*32, amethyst_shard*7",
			"redstone*48, diamond*12, quartz*36, amethyst_shard*12");
		add("silk_touch", "lapis_lazuli*24, slime_ball*12, #wool*16");
		add("smite", "lapis_lazuli*6, glistering_melon_slice*2, amethyst_shard*2", "lapis_lazuli*12, glistering_melon_slice*4, amethyst_shard*2",
			"lapis_lazuli*12, glistering_melon_slice*6, amethyst_shard*2", "lapis_lazuli*16, glistering_melon_slice*6, amethyst_shard*2",
			"lapis_lazuli*24, glistering_melon_slice*7, amethyst_shard*2");
		add("soul_speed", "glowstone_dust*12, redstone*12, #soul_fire_base_blocks*12", "glowstone_dust*16, redstone*16, #soul_fire_base_blocks*12",
			"glowstone_dust*16, redstone*16, #soul_fire_base_blocks*24");
		add("sweeping_edge", "redstone*16, amethyst_shard*4, ender_pearl*2", "redstone*24, amethyst_shard*4, ender_pearl*4", "redstone*24, amethyst_shard*4, ender_pearl*6");
		add("swift_sneak", "redstone*16, rabbit_foot*1, sugar*6", "redstone*24, rabbit_foot*1, sugar*16", "redstone*24, rabbit_foot*2, sugar*32");
		add("thorns", "lapis_lazuli*12, pufferfish*3, quartz*12", "lapis_lazuli*16, pufferfish*4, quartz*24", "lapis_lazuli*24, pufferfish*7, quartz*24");
		add("unbreaking", "lapis_lazuli*24, obsidian*6, diamond*2", "lapis_lazuli*24, obsidian*12, diamond*3", "lapis_lazuli*32, obsidian*24, diamond*7");
		// ours, in the same spirit: the mace's weight and wind, the spear's reach
		add("density", "redstone*12, iron_ingot*6, heavy_weighted_pressure_plate*1", "redstone*16, iron_ingot*12, heavy_weighted_pressure_plate*2",
			"redstone*24, iron_block*2, heavy_weighted_pressure_plate*3", "redstone*32, iron_block*4, anvil*1", "redstone*48, iron_block*6, anvil*2");
		add("breach", "lapis_lazuli*12, quartz*8, breeze_rod*1", "lapis_lazuli*16, quartz*16, breeze_rod*2", "lapis_lazuli*24, quartz*24, breeze_rod*3",
			"lapis_lazuli*32, quartz*32, breeze_rod*4, diamond*2");
		add("wind_burst", "lapis_lazuli*12, breeze_rod*2, feather*6", "lapis_lazuli*24, breeze_rod*4, feather*12", "lapis_lazuli*32, breeze_rod*6, feather*24, phantom_membrane*2");
		add("lunge", "redstone*12, rabbit_foot*1, sugar*6", "redstone*16, rabbit_foot*1, sugar*12, string*6", "redstone*24, rabbit_foot*2, sugar*24, lead*1");
	}

	private EnchantRecipes() {
	}

	private static void add(String vanillaId, String... levels) {
		RECIPES.put("minecraft:" + vanillaId, levels);
	}

	/** The highest level the table can give: as many levels as the recipe has, or the enchantment's own most with the default. */
	public static int maxLevel(Holder<Enchantment> enchantment) {
		String[] levels = RECIPES.get(enchantment.getRegisteredName());
		return levels != null ? levels.length : Math.min(10, enchantment.value().getMaxLevel());
	}

	/** What this level costs. */
	public static List<Ing> cost(Holder<Enchantment> enchantment, int level) {
		String[] levels = RECIPES.get(enchantment.getRegisteredName());
		String row = levels != null ? levels[Math.min(level, levels.length) - 1] : DEFAULT[level <= 3 ? 0 : level <= 7 ? 1 : 2];
		return parse(row);
	}

	private static final Map<String, List<Ing>> PARSED = new HashMap<>();

	private static synchronized List<Ing> parse(String row) {
		return PARSED.computeIfAbsent(row, r -> {
			List<Ing> out = new ArrayList<>();
			for (String part : r.split(",")) {
				String p = part.trim();
				int star = p.lastIndexOf('*');
				out.add(new Ing(List.of(p.substring(0, star).split("\\|")), Integer.parseInt(p.substring(star + 1))));
			}
			return List.copyOf(out);
		});
	}

	/** One ingredient: any of {@code options}, {@code count} of them. */
	public record Ing(List<String> options, int count) {
		public boolean test(ItemStack stack) {
			if (stack.isEmpty() || stack.getCount() < count) {
				return false;
			}
			for (String option : options) {
				if (option.startsWith("#")) {
					if (stack.is(TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace(option.substring(1))))) {
						return true;
					}
				} else if (option.startsWith("potion:")) {
					PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
					if (stack.is(Items.POTION) && contents != null && contents.potion().isPresent()
						&& contents.potion().get().is(Identifier.withDefaultNamespace(option.substring(7)))) {
						return true;
					}
				} else if (stack.is(item(option))) {
					return true;
				}
			}
			return false;
		}

		/** What's left in the slot after paying: a potion's bottle, a bucket's bucket, or nothing. */
		public static ItemStack remainder(ItemStack stack) {
			if (stack.is(Items.POTION)) {
				return new ItemStack(Items.GLASS_BOTTLE);
			}
			if (stack.is(Items.LAVA_BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.POWDER_SNOW_BUCKET)) {
				return new ItemStack(Items.BUCKET);
			}
			return ItemStack.EMPTY;
		}

		/** An item to show for it in the screen. */
		public ItemStack icon() {
			String option = options.getFirst();
			if (option.startsWith("#")) {
				TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace(option.substring(1)));
				return BuiltInRegistries.ITEM.get(tag).flatMap(set -> set.stream().findFirst())
					.map(holder -> new ItemStack(holder, count)).orElse(new ItemStack(Items.BARRIER));
			}
			if (option.startsWith("potion:")) {
				ItemStack potion = new ItemStack(Items.POTION, count);
				BuiltInRegistries.POTION.get(Identifier.withDefaultNamespace(option.substring(7)))
					.ifPresent(holder -> potion.set(DataComponents.POTION_CONTENTS, new PotionContents(holder)));
				return potion;
			}
			return new ItemStack(item(option), count);
		}

		private static Item item(String id) {
			return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
		}

		/** The slot (of the table's material slots) that pays for this, or -1. */
		public int find(net.minecraft.world.Container materials, int from, int to, boolean[] taken) {
			for (int i = from; i < to; i++) {
				if (!taken[i] && test(materials.getItem(i))) {
					return i;
				}
			}
			return -1;
		}
	}

}

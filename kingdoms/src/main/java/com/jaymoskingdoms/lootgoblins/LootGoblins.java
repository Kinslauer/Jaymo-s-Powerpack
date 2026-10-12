package com.jaymoskingdoms.lootgoblins;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Loot goblins: very rarely, a mob that spawns naturally is a <b>loot goblin</b> instead (a little goblin lugging a treasure
 * chest, in one of eight looks, that runs from players and bursts into a pile of loot when one kills it). The original's
 * minor goblins (the ore mobs) and their items are left out.
 *
 * <p>A port to 26.3 of SkylorBeck's Loot Goblins (Fabric 1.19.2, LGPL-3.0, github.com/Skylorbeck/lootgoblins): its loot
 * tables, odds and art (the goblin's model and textures) are its own, used under its
 * licence ({@code licenses/lootgoblins/}); this package is LGPL-3.0 like it. The code is rewritten for 26.3 (no GeckoLib:
 * the model and its two animations are drawn by hand in {@code LootGoblinModel}).
 */
public final class LootGoblins {
	/** The chance a mob spawning naturally (a monster, an animal or a bat) is a goblin instead. */
	public static final float CHANCE = 0.0001F;
	/** A burst: up to this many of the goblin's own treasure; 5 to this many things off the general table. */
	static final int MOST_SPECIFIC = 5, MOST_GENERIC = 10;
	/** Up to this many enchanted books, each this likely; a music disc this likely. */
	static final int MOST_BOOKS = 1;
	static final float BOOK_CHANCE = 0.25F, DISC_CHANCE = 0.05F;
	/** Up to this many pieces of gear, each this likely; the chance any enchantable thing in the burst comes enchanted. */
	static final int MOST_GEAR = 2;
	static final float GEAR_CHANCE = 0.25F, ENCHANT_CHANCE = 0.25F;
	/** Experience for a loot goblin. */
	static final int GOBLIN_XP = 100;

	public static EntityType<LootGoblin> LOOT_GOBLIN;

	private LootGoblins() {
	}

	// ---- the loot tables (item ids, vanilla's unless named; any this game lacks are left out) --------------------------------------------------

	static final String[] GENERIC = {"granite", "andesite", "diorite", "blackstone", "tuff", "stone", "deepslate", "lapis_lazuli",
		"emerald", "saddle", "lead", "acacia_wood", "birch_wood", "dark_oak_wood", "jungle_wood", "oak_wood", "spruce_wood",
		"gold_nugget", "iron_nugget", "amethyst_shard", "apple", "potato", "beetroot", "bamboo", "honeycomb", "gunpowder", "ice",
		"glistering_melon_slice", "golden_carrot", "melon", "pumpkin", "fishing_rod", "coal", "charcoal", "fire_charge", "beef",
		"porkchop", "chicken", "cod", "salmon", "bread", "sponge", "glass", "tinted_glass", "white_wool", "obsidian",
		"crying_obsidian", "bone", "rotten_flesh", "spider_eye", "string", "blaze_rod", "nether_wart", "kelp", "end_rod",
		"red_mushroom", "brown_mushroom", "crimson_fungus", "warped_fungus", "torch", "redstone_torch", "soul_torch", "soul_sand",
		"soul_soil", "soul_campfire", "soul_lantern", "lantern", "campfire", "ender_eye", "ender_pearl", "diamond", "iron_ingot",
		"gold_ingot", "lily_pad", "shulker_shell", "egg", "scaffolding", "dragon_head", "dragon_breath", "dragon_egg",
		"creeper_head", "zombie_head", "honeycomb", "honey_bottle", "candle", "minecart", "tnt", "rail", "powered_rail",
		"activator_rail", "detector_rail", "stick", "piston", "sticky_piston", "carrot_on_a_stick", "warped_fungus_on_a_stick",
		"slime_ball", "bucket", "milk_bucket", "lava_bucket", "water_bucket", "brick", "feather", "turtle_scute", "clay_ball",
		"leather", "rabbit_foot", "rabbit_hide", "rabbit_stew", "mushroom_stew", "cocoa_beans", "glowstone_dust", "glow_berries",
		"item_frame", "glow_item_frame", "gunpowder", "iron_block", "gold_block", "copper_block", "diamond", "redstone_block",
		"cobweb", "redstone"};
	static final String[] GEAR = {"iron_sword", "iron_pickaxe", "iron_axe", "iron_shovel", "iron_horse_armor", "iron_boots",
		"iron_chestplate", "iron_leggings", "iron_helmet", "stone_sword", "stone_pickaxe", "stone_axe", "stone_shovel",
		"golden_sword", "golden_pickaxe", "golden_axe", "golden_shovel", "golden_horse_armor", "golden_boots", "golden_chestplate",
		"golden_leggings", "golden_helmet", "chainmail_boots", "chainmail_chestplate", "chainmail_leggings", "chainmail_helmet"};
	static final String[] DISCS = {"music_disc_13", "music_disc_11", "music_disc_blocks", "music_disc_cat", "music_disc_chirp",
		"music_disc_far", "music_disc_mall", "music_disc_mellohi", "music_disc_otherside", "music_disc_pigstep", "music_disc_stal",
		"music_disc_strad", "music_disc_wait", "music_disc_ward"};
	/** A loot goblin's own treasure, by its look ({@link LootGoblin#VARIANTS}). */
	static final String[][] GOBLIN_TREASURE = {
		{"diamond", "diamond", "iron_block", "iron_block", "redstone_block", "enchanted_golden_apple", "budding_amethyst"},
		{"netherite_scrap", "gold_block", "gold_block", "blaze_rod", "blaze_rod", "glowstone", "glowstone_dust", "quartz_block"},
		{"ender_pearl", "ender_pearl", "shulker_shell", "shulker_shell", "dragon_breath", "dragon_breath", "chorus_fruit",
			"chorus_fruit", "end_crystal"},
		GEAR,
		{"cake", "cookie", "wheat", "milk_bucket", "egg", "sugar", "cocoa_beans"},
		{"acacia_sapling", "birch_sapling", "spruce_sapling", "dark_oak_sapling", "jungle_sapling", "oak_sapling", "cactus",
			"dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
			"oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose", "sunflower", "lilac", "rose_bush", "peony",
			"grass_block", "bone_meal", "potato", "carrot", "fern", "large_fern", "stone_shovel", "stone_hoe", "wooden_shovel",
			"wooden_hoe", "shears", "flower_pot"},
		{"rabbit_foot", "rabbit_hide", "rabbit", "egg", "carrot", "carrot"},
		{"glass_bottle", "gunpowder", "redstone", "spider_eye", "emerald", "cauldron", "string", "glowstone_dust", "blaze_powder"}};

	// ---- registration ---------------------------------------------------------------------------------------------

	public static void register() {
		LOOT_GOBLIN = type("loot_goblin", EntityType.Builder.of(LootGoblin::new, MobCategory.MONSTER).sized(0.6F, 1.0F).clientTrackingRange(8));
		FabricDefaultAttributeRegistry.register(LOOT_GOBLIN, LootGoblin.createAttributes());
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	// ---- spawning ---------------------------------------------------------------------------------------------------

	/**
	 * What a mob vanilla's natural spawning is about to add becomes: now and then ({@link #CHANCE}) a goblin in its place
	 * (a loot goblin of any look); otherwise itself. Mobs riding or ridden (jockeys) are left be.
	 */
	public static Entity replace(ServerLevel level, Entity spawned) {
		if (!(spawned instanceof Mob mob) || LOOT_GOBLIN == null || level.getRandom().nextFloat() >= CHANCE
			|| mob.isVehicle() || mob.isPassenger()) {
			return spawned;
		}
		MobCategory category = mob.getType().getCategory();
		if (category != MobCategory.MONSTER && category != MobCategory.CREATURE && category != MobCategory.AMBIENT) {
			return spawned;
		}
		LootGoblin goblin = LOOT_GOBLIN.create(level, EntitySpawnReason.NATURAL);
		if (goblin == null) {
			return spawned;
		}
		goblin.snapTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), 0.0F);
		goblin.finalizeSpawn(level, level.getCurrentDifficultyAt(goblin.blockPosition()), EntitySpawnReason.NATURAL, null);
		goblin.setVariant(level.getRandom().nextInt(LootGoblin.VARIANTS));
		JaymosKingdoms.LOGGER.debug("a {} spawned at {} in place of a {}", goblin.getType().getDescriptionId(), goblin.blockPosition(),
			mob.getType().getDescriptionId());
		return goblin;
	}

	// ---- the burst of loot ---------------------------------------------------------------------------------------------

	/** Bursts {@code mob} into loot (a player killed it): some of its own treasure, a book, maybe a disc, gear, and a pile of anything. */
	static void burst(ServerLevel level, LivingEntity mob, String[] treasure) {
		RandomSource random = level.getRandom();
		List<Item> own = items(treasure);
		if (!own.isEmpty()) {
			for (int i = random.nextInt(MOST_SPECIFIC) + 1; i > 0; i--) {
				fling(level, mob, new ItemStack(own.get(random.nextInt(own.size()))));
			}
		}
		for (int i = 0; i < MOST_BOOKS; i++) {
			if (random.nextFloat() < BOOK_CHANCE) {
				fling(level, mob, enchant(level, new ItemStack(Items.BOOK)));
			}
		}
		List<Item> discs = items(DISCS);
		if (!discs.isEmpty() && random.nextFloat() < DISC_CHANCE) {
			fling(level, mob, new ItemStack(discs.get(random.nextInt(discs.size()))));
		}
		List<Item> gear = items(GEAR);
		for (int i = 0; i < MOST_GEAR; i++) {
			if (random.nextFloat() < GEAR_CHANCE) {
				fling(level, mob, maybeEnchanted(level, new ItemStack(gear.get(random.nextInt(gear.size())))));
			}
		}
		List<Item> generic = items(GENERIC);
		for (int i = random.nextInt(MOST_GENERIC - 4) + 5; i > 0; i--) {
			fling(level, mob, maybeEnchanted(level, new ItemStack(generic.get(random.nextInt(generic.size())))));
		}
	}

	private static List<Item> items(String[] ids) {
		List<Item> items = new ArrayList<>(ids.length);
		for (String id : ids) {
			BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).ifPresent(items::add);
		}
		return items;
	}

	private static ItemStack maybeEnchanted(ServerLevel level, ItemStack stack) {
		return stack.isEnchantable() && level.getRandom().nextFloat() < ENCHANT_CHANCE ? enchant(level, stack) : stack;
	}

	/** Enchants {@code stack} at a random level up to 30, half the time from any enchantment (treasures and curses too). */
	private static ItemStack enchant(ServerLevel level, ItemStack stack) {
		RandomSource random = level.getRandom();
		Optional<? extends HolderSet<Enchantment>> pool = random.nextBoolean() ? Optional.empty()
			: level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE);
		return EnchantmentHelper.enchantItem(random, stack, random.nextInt(30), level.registryAccess(), pool);
	}

	private static void fling(ServerLevel level, LivingEntity mob, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY() + 1.0, mob.getZ(), stack);
		item.setDeltaMovement(item.getDeltaMovement().add(0.0, level.getRandom().nextFloat() / 2.0F, 0.0));
		level.addFreshEntity(item);
	}
}

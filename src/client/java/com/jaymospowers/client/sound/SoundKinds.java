package com.jaymospowers.client.sound;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What things are made of, as our sounds hear it ({@link ImmersiveSounds}): the ground under a foot, a piece of armour,
 * an item. Blocks go by their own sound type (so a modded block that sounds like wood steps like wood), items by their
 * id, then by what they are.
 */
public final class SoundKinds {
	/** Ground, for footsteps: the names in sounds.json ({@code immersive.step.<ground>.<gait>}). */
	public enum Ground {
		GRASS, STONE, WOOD, SAND, GRAVEL, SNOW, METAL, CARPET, MUD;

		public final String id = name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Armour, for the sound it makes as you move ({@code immersive.armour.<kind>}). */
	public enum Armour {
		CLOTH(0.7F), LEATHER(0.8F), CHAIN(1.0F), SCALE(1.0F), PLATE(1.1F), HEAVY(1.25F), BONE(0.9F), CRYSTAL(0.8F);

		public final String id = name().toLowerCase(java.util.Locale.ROOT);
		/** How loud it is, beside the others. */
		public final float loud;

		Armour(float loud) {
			this.loud = loud;
		}
	}

	/** An item, for the sound it makes landing ({@code immersive.drop.<kind>}). */
	public enum Stuff {
		METAL, WOOD, COIN, STONE, GLASS, SOFT, GEM, PAPER, BONE;

		public final String id = name().toLowerCase(java.util.Locale.ROOT);
	}

	private static final Map<SoundType, Ground> GROUNDS = new IdentityHashMap<>();
	static {
		for (SoundType t : new SoundType[] {SoundType.GRASS, SoundType.WET_GRASS, SoundType.CROP, SoundType.HARD_CROP, SoundType.MOSS,
			SoundType.MOSS_CARPET, SoundType.LEAF_LITTER, SoundType.PINK_PETALS, SoundType.ROOTED_DIRT, SoundType.VINE, SoundType.ROOTS,
			SoundType.NYLIUM, SoundType.FUNGUS, SoundType.AZALEA, SoundType.FLOWERING_AZALEA, SoundType.AZALEA_LEAVES, SoundType.CHERRY_LEAVES,
			SoundType.POPLAR_LEAVES, SoundType.SWEET_BERRY_BUSH, SoundType.BIG_DRIPLEAF, SoundType.SMALL_DRIPLEAF, SoundType.NETHER_SPROUTS,
			SoundType.LILY_PAD, SoundType.BAMBOO_SAPLING, SoundType.CHERRY_SAPLING, SoundType.CAVE_VINES, SoundType.HANGING_ROOTS,
			SoundType.GLOW_LICHEN, SoundType.WART_BLOCK, SoundType.NETHER_WART, SoundType.STRAW_BED, SoundType.RED_SHRUB, SoundType.SHROOMLIGHT,
			SoundType.SPORE_BLOSSOM, SoundType.SCULK, SoundType.SCULK_VEIN}) {
			GROUNDS.put(t, Ground.GRASS);
		}
		for (SoundType t : new SoundType[] {SoundType.WOOD, SoundType.BAMBOO_WOOD, SoundType.NETHER_WOOD, SoundType.CHERRY_WOOD, SoundType.LADDER,
			SoundType.SCAFFOLDING, SoundType.BAMBOO, SoundType.CHISELED_BOOKSHELF, SoundType.SHELF, SoundType.STEM, SoundType.HANGING_SIGN,
			SoundType.NETHER_WOOD_HANGING_SIGN, SoundType.BAMBOO_WOOD_HANGING_SIGN, SoundType.CHERRY_WOOD_HANGING_SIGN, SoundType.CREAKING_HEART,
			SoundType.SHELF_MUSHROOM}) {
			GROUNDS.put(t, Ground.WOOD);
		}
		for (SoundType t : new SoundType[] {SoundType.GRAVEL, SoundType.SUSPICIOUS_GRAVEL}) {
			GROUNDS.put(t, Ground.GRAVEL);
		}
		for (SoundType t : new SoundType[] {SoundType.SAND, SoundType.SUSPICIOUS_SAND, SoundType.SOUL_SAND, SoundType.SOUL_SOIL}) {
			GROUNDS.put(t, Ground.SAND);
		}
		for (SoundType t : new SoundType[] {SoundType.SNOW, SoundType.POWDER_SNOW}) {
			GROUNDS.put(t, Ground.SNOW);
		}
		for (SoundType t : new SoundType[] {SoundType.METAL, SoundType.ANVIL, SoundType.CHAIN, SoundType.LANTERN, SoundType.NETHERITE_BLOCK,
			SoundType.COPPER, SoundType.COPPER_BULB, SoundType.COPPER_GRATE, SoundType.COPPER_GOLEM_STATUE, SoundType.IRON, SoundType.HEAVY_CORE,
			SoundType.LODESTONE, SoundType.VAULT, SoundType.TRIAL_SPAWNER, SoundType.SPAWNER}) {
			GROUNDS.put(t, Ground.METAL);
		}
		for (SoundType t : new SoundType[] {SoundType.WOOL, SoundType.SPONGE, SoundType.WET_SPONGE, SoundType.COBWEB}) {
			GROUNDS.put(t, Ground.CARPET);
		}
		for (SoundType t : new SoundType[] {SoundType.MUD, SoundType.MUDDY_MANGROVE_ROOTS, SoundType.SLIME_BLOCK, SoundType.HONEY_BLOCK,
			SoundType.FROGSPAWN, SoundType.MANGROVE_ROOTS}) {
			GROUNDS.put(t, Ground.MUD);
		}
	}

	private SoundKinds() {
	}

	/** The ground under {@code entity}'s feet: a thin layer it stands in (snow, a carpet) first, else the block it's on. */
	public static Ground ground(Level level, Entity entity) {
		BlockState feet = level.getBlockState(entity.blockPosition());
		if (thinLayer(feet)) {
			return ground(feet);
		}
		BlockPos on = entity.getOnPosLegacy();
		return ground(level.getBlockState(on));
	}

	private static boolean thinLayer(BlockState state) {
		return state.is(Blocks.SNOW) || state.is(BlockTags.WOOL_CARPETS) || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.PALE_MOSS_CARPET)
			|| state.is(Blocks.LEAF_LITTER) || state.is(Blocks.PINK_PETALS);
	}

	public static Ground ground(BlockState state) {
		SoundType type = state.getSoundType();
		// dirt sounds like gravel in vanilla, but underfoot it's soft earth
		if (type == SoundType.GRAVEL && state.is(BlockTags.DIRT)) {
			return Ground.SAND;
		}
		return GROUNDS.getOrDefault(type, Ground.STONE);
	}

	// ---- armour ------------------------------------------------------------------------------------------------------

	/** What a worn piece is made of: our own sets by name, then the piece's equip sound, then words in its id. */
	public static Armour armour(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		Armour named = armourByName(path);
		if (named != null) {
			return named;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			SoundEvent sound = equippable.equipSound().value();
			if (sound == SoundEvents.ARMOR_EQUIP_LEATHER.value()) {
				return Armour.LEATHER;
			}
			if (sound == SoundEvents.ARMOR_EQUIP_CHAIN.value()) {
				return Armour.CHAIN;
			}
			if (sound == SoundEvents.ARMOR_EQUIP_NETHERITE.value()) {
				return Armour.HEAVY;
			}
			if (sound == SoundEvents.ARMOR_EQUIP_TURTLE.value()) {
				return Armour.BONE;
			}
			if (sound == SoundEvents.ARMOR_EQUIP_ELYTRA.value() || sound == SoundEvents.ARMOR_EQUIP_GENERIC.value()) {
				return Armour.CLOTH;
			}
			if (sound == SoundEvents.ARMOR_EQUIP_IRON.value() || sound == SoundEvents.ARMOR_EQUIP_GOLD.value()
				|| sound == SoundEvents.ARMOR_EQUIP_DIAMOND.value() || sound == SoundEvents.ARMOR_EQUIP_COPPER.value()) {
				return Armour.PLATE;
			}
		}
		return Armour.CLOTH;
	}

	/**
	 * Our own armour, and other mods' by the words in their names. Tinkering's bog iron is plate; Arsenal's Void Crown
	 * rings like crystal; the Rig is heavy; Armory's caveman hide is leather; Alchemy's ichorcloth is cloth; Kingdoms'
	 * Ronin lamellar and Kintsugi are scale, the Warlord leather, Paladin, Gladiator and Heavenly plate, Reverent heavy,
	 * Primal bone; the Aether's zanite crystal, gravitite and obsidian heavy, neptune scale, valkyrie and phoenix plate.
	 */
	static Armour armourByName(String path) {
		if (path.startsWith("rig_") || has(path, "reverent", "gravitite", "obsidian", "netherite", "heavy", "dwarven")) {
			return Armour.HEAVY;
		}
		if (has(path, "void_crown", "zanite", "crystal", "amethyst", "glass", "gem")) {
			return Armour.CRYSTAL;
		}
		if (has(path, "ronin", "kintsugi", "neptune", "scale", "lamellar", "brigandine")) {
			return Armour.SCALE;
		}
		if (has(path, "primal", "bone", "shell", "chitin", "skull")) {
			return Armour.BONE;
		}
		if (has(path, "caveman", "warlord", "leather", "hide", "fur", "pelt")) {
			return Armour.LEATHER;
		}
		if (has(path, "chain", "mail")) {
			return Armour.CHAIN;
		}
		if (has(path, "ichorcloth", "cloth", "robe", "wool", "silk", "tunic", "gambeson", "padded", "cloak", "hood", "costume", "shirt")) {
			return Armour.CLOTH;
		}
		if (has(path, "bog_iron", "paladin", "gladiator", "heavenly", "valkyrie", "phoenix", "plate", "steel", "bronze", "knight")) {
			return Armour.PLATE;
		}
		return null;
	}

	// ---- items -------------------------------------------------------------------------------------------------------

	/** What a dropped item is made of. */
	public static Stuff stuff(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (has(path, "coin", "nugget", "crown_piece", "mark")) {
			return Stuff.COIN;
		}
		if (has(path, "leather", "hide", "wool", "string", "cloth", "feather", "flesh", "fur", "pelt", "carpet", "bed", "banner", "silk",
			"rope", "seed", "wheat", "meat", "beef", "pork", "mutton", "chicken", "rabbit", "fish", "cod", "salmon", "slime", "dough", "bread")) {
			return Stuff.SOFT;
		}
		if (has(path, "wooden", "wood", "log", "plank", "stick", "bowl", "barrel", "fence", "door", "sign", "boat", "bow", "arrow",
			"shield", "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped", "club", "staff")) {
			return Stuff.WOOD;
		}
		if (path.startsWith("stone_") || has(path, "cobble", "flint", "rock", "brick", "coal", "charcoal", "_ore", "raw_", "pebble", "deepslate",
			"granite", "diorite", "andesite", "basalt", "tuff", "knapped")) {
			return Stuff.STONE;
		}
		if (has(path, "glass", "bottle", "potion", "vial", "flask", "lens", "spyglass", "jar")) {
			return Stuff.GLASS;
		}
		if (has(path, "diamond", "emerald", "amethyst", "quartz", "gem", "ruby", "sapphire", "crystal", "lapis", "zanite", "pearl")) {
			return Stuff.GEM;
		}
		if (has(path, "paper", "book", "map", "scroll", "letter", "writ", "page", "note", "schematic", "deed")) {
			return Stuff.PAPER;
		}
		if (has(path, "bone", "skull", "tooth", "horn", "shell", "scute", "fang", "claw", "antler")) {
			return Stuff.BONE;
		}
		if (has(path, "iron", "gold", "copper", "steel", "bronze", "netherite", "chain", "sword", "dagger", "blade", "axe", "pick", "shovel",
			"hoe", "ingot", "bucket", "shears", "anvil", "lantern", "helmet", "chestplate", "leggings", "boots", "musket", "pistol", "gun", "key",
			"hammer", "mace", "spear", "knife", "rapier", "saber", "katana", "halberd", "plate", "tin", "silver")) {
			return Stuff.METAL;
		}
		if (stack.getItem() instanceof BlockItem block) {
			return switch (ground(block.getBlock().defaultBlockState())) {
				case WOOD -> Stuff.WOOD;
				case METAL -> Stuff.METAL;
				case STONE -> block.getBlock().defaultBlockState().getSoundType() == SoundType.GLASS ? Stuff.GLASS : Stuff.STONE;
				default -> Stuff.SOFT;
			};
		}
		if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)) {
			return Stuff.METAL;
		}
		return Stuff.SOFT;
	}

	private static boolean has(String path, String... words) {
		for (String w : words) {
			if (path.contains(w)) {
				return true;
			}
		}
		return false;
	}
}

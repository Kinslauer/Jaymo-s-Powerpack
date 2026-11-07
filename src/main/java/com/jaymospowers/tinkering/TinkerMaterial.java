package com.jaymospowers.tinkering;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A tool material, with Tinkers' Construct's stats for it (1.20.1 data). The id is also its texture suffix and the
 * string the item models select on. Variants (crimson wood, granite) share their family's stats and only look different.
 * <ul>
 * <li>Head: durability, mining speed, mining tier and attack, averaged over a tool's head parts.</li>
 * <li>Handle: fractions added to the tool's durability, mining speed, attack and attack speed (0.1 = +10%).</li>
 * <li>Binding-only materials (leather, string, vine) give just their trait.</li>
 * <li>Metals ({@link #cast}) are cast into parts in a smeltery; their ingots still repair tools.</li>
 * </ul>
 */
public enum TinkerMaterial {
	WOOD("wood", Stats.WOOD, Trait.CULTIVATED),
	WOOD_CRIMSON("wood_crimson", Stats.WOOD, Trait.CULTIVATED),
	WOOD_WARPED("wood_warped", Stats.WOOD, Trait.CULTIVATED),
	ROCK_STONE("rock_stone", Stats.ROCK, Trait.STONEBOUND),
	ROCK_GRANITE("rock_granite", Stats.ROCK, Trait.STONEBOUND),
	ROCK_DIORITE("rock_diorite", Stats.ROCK, Trait.STONEBOUND),
	ROCK_BLACKSTONE("rock_blackstone", Stats.ROCK, Trait.STONEBOUND),
	FLINT("flint", Stats.FLINT, Trait.JAGGED),
	BONE("bone", Stats.BONE, Trait.PIERCE),
	SEARED_STONE("seared_stone", Stats.SEARED_STONE, Trait.SEARING),
	COPPER("copper", Stats.COPPER, Trait.DWARVEN, true),
	IRON("iron", Stats.IRON, Trait.MAGNETIC, true),
	SILVER("silver", Stats.SILVER, Trait.SMITE, true),
	ROSE_GOLD("rose_gold", Stats.ROSE_GOLD, Trait.ENHANCED, true),
	AMETHYST_BRONZE("amethyst_bronze", Stats.AMETHYST_BRONZE, Trait.CRUMBLING, true),
	PIG_IRON("pig_iron", Stats.PIG_IRON, Trait.TASTY, true),
	SLIMESTEEL("slimesteel", Stats.SLIMESTEEL, Trait.OVERSLIME, true),
	/** (Smithing) Iron and pig iron alloyed, or crucible steel fired in a kiln: netherite-tier, Tempered. */
	STEEL("steel", Stats.STEEL, Trait.TEMPERED, true),
	/**
	 * (0.18.55) The far worlds' first metals, one each (Moon, Rustfall, Glacia, Cinder, Maw, Verdant): knapped from the world's own
	 * rocks at a field anvil, then cast and forged like any metal. Only a tool whose head is a world's first metal answers that
	 * world's ore ({@code power/zfighter/farworld}).
	 */
	PALE_ALLOY("pale_alloy", Stats.PALE_ALLOY, Trait.ENHANCED, true),
	RUST_IRON("rust_iron", Stats.RUST_IRON, Trait.MAGNETIC, true),
	ICE_IRON("ice_iron", Stats.ICE_IRON, Trait.STONEBOUND, true),
	CINDER_BRONZE("cinder_bronze", Stats.CINDER_BRONZE, Trait.SEARING, true),
	VOID_SHARD("void_shard", Stats.VOID_SHARD, Trait.PIERCE, true),
	AMBER_BRONZE("amber_bronze", Stats.AMBER_BRONZE, Trait.SOLAR_POWERED, true),
	LEATHER("leather", null, Trait.TANNED),
	STRING("string", null, Trait.STRINGY),
	VINE("vine", null, Trait.SOLAR_POWERED),
	// (0.15.x) arrow parts only: fletching (feather, leaves) and a blaze rod shaft; they have no head, handle or binding stats
	FEATHER("feather", null, Trait.AERODYNAMIC),
	LEAVES("leaves", null, Trait.SOLAR_POWERED),
	BLAZE_ROD("blaze_rod", null, Trait.SEARING),
	// ---- (0.18.56) back home: star metal, from fallen stars (its own block: keep it apart when merging far-world metals) ----
	/** Melted from star metal ore (1 ore = 1 ingot), cast and forged only: light, quick, Starlit. */
	STAR_METAL("star_metal", Stats.STAR_METAL, Trait.STARLIT, true),
	// ---- (0.18.102) bog iron (Jaymo's Kingdoms: swamp ore held by the Ogre Kings) ----
	/** Melted from Kingdoms' raw bog iron or its ore, cast and forged only: heavy, tough, iron-tier, Jagged. */
	BOG_IRON("bog_iron", Stats.BOG_IRON, Trait.JAGGED, true);

	/** Mining tiers, weakest first: what each can't harvest. */
	public enum Tier {
		WOOD("wood", BlockTags.INCORRECT_FOR_WOODEN_TOOL), STONE("stone", BlockTags.INCORRECT_FOR_STONE_TOOL),
		IRON("iron", BlockTags.INCORRECT_FOR_IRON_TOOL), DIAMOND("diamond", BlockTags.INCORRECT_FOR_DIAMOND_TOOL),
		NETHERITE("netherite", BlockTags.INCORRECT_FOR_NETHERITE_TOOL);

		public final String id;
		public final TagKey<Block> incorrect;

		Tier(String id, TagKey<Block> incorrect) {
			this.id = id;
			this.incorrect = incorrect;
		}

		public Component displayName() {
			return Component.translatable("tier.jaymospowers." + id);
		}
	}

	/** A family's stats: head (durability, speed, attack, tier) and handle bonuses. */
	public record Stats(int durability, float miningSpeed, float attack, Tier tier,
		float handleDurability, float handleSpeed, float handleAttack, float handleAttackSpeed) {
		static final Stats WOOD = new Stats(60, 2.0F, 0.0F, Tier.WOOD, 0.0F, 0.0F, 0.0F, 0.0F);
		static final Stats ROCK = new Stats(130, 4.0F, 1.0F, Tier.STONE, -0.1F, 0.05F, 0.0F, 0.0F);
		static final Stats FLINT = new Stats(85, 3.5F, 1.25F, Tier.STONE, -0.15F, 0.0F, 0.1F, 0.0F);
		static final Stats BONE = new Stats(100, 2.5F, 1.25F, Tier.STONE, -0.25F, 0.0F, 0.0F, 0.1F);
		static final Stats COPPER = new Stats(210, 5.0F, 0.5F, Tier.IRON, -0.2F, 0.1F, 0.05F, 0.0F);
		static final Stats IRON = new Stats(250, 6.0F, 2.0F, Tier.IRON, 0.1F, 0.0F, 0.0F, 0.0F);
		static final Stats SILVER = new Stats(300, 5.5F, 2.25F, Tier.IRON, -0.1F, 0.05F, 0.0F, 0.1F);
		static final Stats SEARED_STONE = new Stats(225, 6.5F, 1.5F, Tier.IRON, -0.15F, 0.1F, 0.05F, 0.0F);
		static final Stats ROSE_GOLD = new Stats(175, 9.0F, 1.0F, Tier.WOOD, -0.3F, 0.1F, 0.0F, 0.1F);
		static final Stats AMETHYST_BRONZE = new Stats(720, 7.0F, 1.5F, Tier.DIAMOND, 0.0F, 0.1F, 0.0F, 0.05F);
		static final Stats PIG_IRON = new Stats(580, 6.0F, 2.5F, Tier.DIAMOND, 0.1F, -0.15F, 0.15F, 0.0F);
		static final Stats SLIMESTEEL = new Stats(1040, 6.0F, 2.5F, Tier.DIAMOND, 0.2F, 0.0F, 0.0F, -0.05F);
		static final Stats STEEL = new Stats(1500, 7.0F, 3.0F, Tier.NETHERITE, 0.15F, 0.05F, 0.1F, 0.0F);
		/** (0.18.56) Star metal: diamond-tier, middling durability, light in the hand (a faster handle, no weight in the blow). */
		static final Stats STAR_METAL = new Stats(640, 7.5F, 2.0F, Tier.DIAMOND, 0.0F, 0.1F, 0.0F, 0.1F);
		/** (0.18.102) Bog iron: iron-tier, more durable and harder hitting than iron, slower in the hand. */
		static final Stats BOG_IRON = new Stats(340, 5.5F, 2.5F, Tier.IRON, 0.15F, -0.05F, 0.05F, -0.05F);
		/** (0.18.55) The first metals: iron-tier (the Cinder and Maw ones diamond-tier, for ancient debris), none beyond steel. */
		static final Stats PALE_ALLOY = new Stats(300, 6.5F, 2.0F, Tier.IRON, 0.05F, 0.05F, 0.0F, 0.05F);
		static final Stats RUST_IRON = new Stats(420, 6.0F, 2.5F, Tier.IRON, 0.1F, 0.0F, 0.05F, 0.0F);
		static final Stats ICE_IRON = new Stats(380, 7.0F, 2.0F, Tier.IRON, 0.05F, 0.1F, 0.0F, 0.0F);
		static final Stats CINDER_BRONZE = new Stats(640, 7.0F, 2.5F, Tier.DIAMOND, 0.1F, 0.05F, 0.05F, 0.0F);
		static final Stats VOID_SHARD = new Stats(500, 8.0F, 3.0F, Tier.DIAMOND, -0.1F, 0.1F, 0.1F, 0.1F);
		static final Stats AMBER_BRONZE = new Stats(460, 6.5F, 2.0F, Tier.IRON, 0.15F, 0.0F, 0.0F, 0.05F);
	}

	/** (0.15.x) A bow limb's stats (Tinkers'): durability, and draw speed, arrow velocity and accuracy it adds (0.1 = +10%). */
	public record Limb(int durability, float drawSpeed, float velocity, float accuracy) {
	}

	/** (0.15.x) A bow grip's stats: durability share, accuracy added, and melee damage added (a bonk with the bow). */
	public record Grip(float durability, float accuracy, float meleeDamage) {
	}

	private static final java.util.IdentityHashMap<Stats, Limb> LIMBS = new java.util.IdentityHashMap<>();
	private static final java.util.IdentityHashMap<Stats, Grip> GRIPS = new java.util.IdentityHashMap<>();

	static {
		limb(Stats.WOOD, new Limb(60, 0.0F, 0.0F, 0.0F), new Grip(0.0F, 0.0F, 0.0F));
		limb(Stats.BONE, new Limb(100, 0.05F, -0.05F, 0.05F), new Grip(-0.25F, 0.05F, 1.25F));
		limb(Stats.COPPER, new Limb(210, -0.1F, 0.05F, 0.0F), new Grip(-0.2F, 0.0F, 0.5F));
		limb(Stats.IRON, new Limb(250, -0.2F, 0.1F, 0.0F), new Grip(0.1F, 0.0F, 2.0F));
		limb(Stats.SILVER, new Limb(300, -0.05F, 0.0F, 0.1F), new Grip(-0.1F, -0.05F, 2.25F));
		limb(Stats.ROSE_GOLD, new Limb(175, 0.15F, -0.25F, 0.15F), new Grip(-0.3F, 0.25F, 1.0F));
		limb(Stats.AMETHYST_BRONZE, new Limb(720, -0.15F, 0.15F, -0.05F), new Grip(0.0F, 0.1F, 1.5F));
		limb(Stats.PIG_IRON, new Limb(580, 0.0F, 0.0F, 0.1F), new Grip(0.1F, -0.15F, 2.5F));
		limb(Stats.SLIMESTEEL, new Limb(1040, -0.05F, -0.05F, 0.15F), new Grip(0.2F, -0.1F, 2.5F));
		limb(Stats.STEEL, new Limb(775, -0.2F, 0.2F, -0.1F), new Grip(0.05F, -0.05F, 2.75F));
		limb(Stats.BOG_IRON, new Limb(340, -0.25F, 0.1F, -0.05F), new Grip(0.15F, -0.05F, 2.5F));
		limb(Stats.STAR_METAL, new Limb(640, 0.1F, 0.05F, 0.1F), new Grip(0.0F, 0.1F, 2.0F));
	}

	private static void limb(Stats family, Limb limb, Grip grip) {
		LIMBS.put(family, limb);
		GRIPS.put(family, grip);
	}

	/** Material units per item: a plank or ingot is 1, a log 4, a block of metal 9. */
	private static final Map<Item, TinkerMaterial> ITEMS = new LinkedHashMap<>();
	private static final Map<Item, Integer> UNITS = new LinkedHashMap<>();

	static {
		units(WOOD_CRIMSON, 1, Items.CRIMSON_PLANKS);
		units(WOOD_CRIMSON, 4, Items.CRIMSON_STEM, Items.CRIMSON_HYPHAE, Items.STRIPPED_CRIMSON_STEM, Items.STRIPPED_CRIMSON_HYPHAE);
		units(WOOD_WARPED, 1, Items.WARPED_PLANKS);
		units(WOOD_WARPED, 4, Items.WARPED_STEM, Items.WARPED_HYPHAE, Items.STRIPPED_WARPED_STEM, Items.STRIPPED_WARPED_HYPHAE);
		units(ROCK_STONE, 1, Items.COBBLESTONE, Items.STONE, Items.COBBLED_DEEPSLATE, Items.ANDESITE, Items.TUFF);
		units(ROCK_GRANITE, 1, Items.GRANITE);
		units(ROCK_DIORITE, 1, Items.DIORITE);
		units(ROCK_BLACKSTONE, 1, Items.BLACKSTONE);
		units(FLINT, 1, Items.FLINT);
		units(BONE, 1, Items.BONE);
		units(BONE, 3, Items.BONE_BLOCK);
		units(COPPER, 1, Items.COPPER_INGOT);
		units(COPPER, 9, Items.COPPER_BLOCK.weathering().unaffected(), Items.COPPER_BLOCK.waxed().unaffected());
		units(IRON, 1, Items.IRON_INGOT);
		units(IRON, 9, Items.IRON_BLOCK);
		units(LEATHER, 1, Items.LEATHER);
		units(STRING, 1, Items.STRING);
		units(VINE, 1, Items.VINE);
		units(FEATHER, 1, Items.FEATHER);
		units(BLAZE_ROD, 1, Items.BLAZE_ROD);
	}

	public final String id;
	public final @Nullable Stats stats;
	public final Trait trait;
	/** A metal: its parts are cast in a smeltery, not shaped at the Part Builder. */
	public final boolean cast;

	TinkerMaterial(String id, @Nullable Stats stats, Trait trait) {
		this(id, stats, trait, false);
	}

	TinkerMaterial(String id, @Nullable Stats stats, Trait trait, boolean cast) {
		this.id = id;
		this.stats = stats;
		this.trait = trait;
		this.cast = cast;
	}

	/** Our own items (seared bricks, alloy ingots), added once they're registered. */
	static void own(TinkerMaterial material, int units, Item item) {
		units(material, units, item);
	}

	private static void units(TinkerMaterial material, int units, Item... items) {
		for (Item item : items) {
			ITEMS.put(item, material);
			UNITS.put(item, units);
		}
	}

	/** (0.15.x) Its bow limb stats, or null when it makes no limb (stone, flint, seared stone and the like). */
	public @Nullable Limb limb() {
		return stats == null ? null : LIMBS.get(stats);
	}

	public @Nullable Grip grip() {
		return stats == null ? null : GRIPS.get(stats);
	}

	/** (0.15.x) Arrow velocity a shaft of this adds (a share: 0.05 = +5%). */
	public float shaftVelocity() {
		return this == BONE ? 0.05F : this == BLAZE_ROD ? 0.1F : 0.0F;
	}

	/** (0.16.0) The chance (in %) an arrow with a shaft of this comes out of a corpse whole (Jaymo's Kitchen); plain arrows 60. */
	public int shaftStrength() {
		return switch (this) {
			case BONE -> 80;
			case BLAZE_ROD -> 90;
			case WOOD_CRIMSON, WOOD_WARPED -> 65;
			default -> 60;
		};
	}

	/** Whether this material can make a part of that kind. */
	public boolean makes(PartType.Kind kind) {
		return switch (kind) {
			case HEAD, HANDLE, ARROW_HEAD -> stats != null;
			case BINDING -> stats != null || this == LEATHER || this == STRING || this == VINE;
			case LIMB -> limb() != null;
			case GRIP -> grip() != null;
			case BOWSTRING -> this == STRING || this == VINE || this == LEATHER;
			case ARROW_SHAFT -> this == WOOD || this == WOOD_CRIMSON || this == WOOD_WARPED || this == BONE || this == BLAZE_ROD;
			case FLETCHING -> this == FEATHER || this == LEAVES;
		};
	}

	public Component displayName() {
		return Component.translatable("material.jaymospowers." + id);
	}

	/** The material an item counts as, or null: listed items first, then any planks and logs as wood. */
	public static @Nullable TinkerMaterial of(ItemStack stack) {
		TinkerMaterial listed = ITEMS.get(stack.getItem());
		if (listed != null) {
			return listed;
		}
		return stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS) ? WOOD : stack.is(ItemTags.LEAVES) ? LEAVES : null;
	}

	/** How many material units one of this item is worth (0: not a material). */
	public static int units(ItemStack stack) {
		Integer listed = UNITS.get(stack.getItem());
		if (listed != null) {
			return listed;
		}
		return stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LEAVES) ? 1 : stack.is(ItemTags.LOGS) ? 4 : 0;
	}

	public static @Nullable TinkerMaterial byId(String id) {
		for (TinkerMaterial material : values()) {
			if (material.id.equals(id)) {
				return material;
			}
		}
		return null;
	}

	/** Head materials, in creative tab order. */
	public static List<TinkerMaterial> heads() {
		return java.util.Arrays.stream(values()).filter(m -> m.stats != null).toList();
	}
}

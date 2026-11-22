package com.jaymospowers.boxing;

import com.jaymospowers.JaymosPowers;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * (Jung) Fist gear: what a boxer wraps or straps on. A boxing style ({@link Boxing}) only works with fist gear in the
 * main hand; a truly empty hand is not boxing (Z Fighter's bare-hand fighting is untouched).
 * <ul>
 * <li><b>Hand Wraps</b>: rags or cloth wound round the fist, made in the crafting grid from two of the
 * {@code jaymospowers:wrap_cloth} tag (a Clean Rag, Kingdoms' Torn Cloth, the Kitchen's Hemp Cloth). The plainest fist
 * gear: nothing but the style.</li>
 * <li>The <b>cestus</b>, for damage: a styled punch does {@link #punchBonus} more with one on (leather, iron, steel).</li>
 * <li>The <b>bracers</b>, for blocking: no harder a punch than wraps, but they stop the most of a blow.</li>
 * </ul>
 * The leather cestus and bracers are sewn round Hand Wraps in the crafting grid. The iron and steel ones are forged
 * like every metal weapon ({@code tinkering/WeaponParts}: the cestus's studs or the bracers' plates, and a binding, at
 * the Tool Station). No gold or diamond. Without a style, all of it is a plain weak weapon.
 *
 * <p><b>Blocking.</b> Fist gear is a weapon, so right click raises the pack's weapon guard with it ({@code combat/Guard}:
 * the block, and the parry in its first ticks). What it stops is the gear's own ({@link #blockShare}): each step up,
 * wraps to leather to iron to steel, stops more and costs a boxer less ({@link #blockCostShare}); bracers most of all.
 *
 * <p>Fist gear is whatever is in the {@code jaymospowers:boxing_gear} item tag. It is deliberately not in Kingdoms'
 * {@code form_fists} tag: with fist gear in hand, right click has to stay free for the boxer's block. The art comes
 * from {@code tools/boxing_assets.py}.
 */
public final class BoxingGear {
	// ---- tuning ----
	/** Hand wraps without a style: damage on top of a bare hand's 1, and the attack speed modifier (4 is a bare hand). */
	static final float WRAPS_DAMAGE = 0.5F, WRAPS_SPEED = -2.4F;
	/** How many punches a pair of hand wraps lasts. */
	static final int WRAPS_DURABILITY = 250;
	/** What a cestus adds to a styled punch: leather, iron, steel. */
	static final float LEATHER_CESTUS_PUNCH = 0.5F, IRON_CESTUS_PUNCH = 1.0F, STEEL_CESTUS_PUNCH = 1.5F;
	/** Without a style, on top of the material's own bonus (iron 2, steel {@link #STEEL_BONUS}): a cestus, and bracers. */
	static final float CESTUS_DAMAGE = 1.0F, IRON_CESTUS_DAMAGE = 0.0F, BRACERS_DAMAGE = 0.5F, METAL_BRACERS_DAMAGE = -1.0F;
	/**
	 * Gallant Gauntlets' idea ({@link OffhandPunch}): each piece, in either hand, gives the armour of a pair of boots of
	 * what guards the hand: none for the wraps' cloth, leather's 1, iron's 2; steel a step past iron's. The cestus's
	 * studs are only on the knuckles, so a cestus is leather whatever its studs; a bracer is its metal.
	 */
	static final float WRAPS_ARMOUR = 0.0F, LEATHER_ARMOUR = 1.0F, IRON_ARMOUR = 2.0F, STEEL_ARMOUR = 3.0F;
	/** How many punches leather gear lasts (iron and steel: their material's). */
	static final int LEATHER_DURABILITY = 400;
	/** Steel as a material: durability and the damage it adds (iron: 250 and 2). */
	static final int STEEL_DURABILITY = 600;
	static final float STEEL_BONUS = 2.5F;

	/**
	 * The share of a blow from the front each piece stops when raised (right click; the pack's weapon guard,
	 * {@code combat/Guard}, which stops 0.6): Hand Wraps, then the cestus and the bracers in leather, iron, steel.
	 */
	static final float WRAPS_BLOCK = 0.5F;
	static final float[] CESTUS_BLOCK = {0.55F, 0.6F, 0.65F}, BRACERS_BLOCK = {0.65F, 0.75F, 0.85F};
	/** What a boxer's block costs with each piece, as a share of the full cost: the same order. */
	static final float WRAPS_BLOCK_COST = 1.0F;
	static final float[] CESTUS_BLOCK_COST = {0.95F, 0.9F, 0.85F}, BRACERS_BLOCK_COST = {0.9F, 0.8F, 0.7F};

	/** Everything a boxer can box with. */
	public static final TagKey<Item> GEAR = TagKey.create(Registries.ITEM, JaymosPowers.id("boxing_gear"));

	private static final ToolMaterial STEEL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, STEEL_DURABILITY, 6.0F, STEEL_BONUS, 14,
		TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/steel")));

	public static Item HAND_WRAPS, LEATHER_CESTUS, IRON_CESTUS, STEEL_CESTUS, LEATHER_BRACERS, IRON_BRACERS, STEEL_BRACERS;
	/** What each cestus adds to a styled punch. */
	private static final Map<Item, Float> PUNCH = new LinkedHashMap<>();
	/** Each piece's block: the share of a blow it stops, and the share of the block's cost it leaves. */
	private static final Map<Item, float[]> BLOCK = new LinkedHashMap<>();

	private BoxingGear() {
	}

	static void init() {
		HAND_WRAPS = item("hand_wraps", gauntlet(new Item.Properties(), ToolMaterial.WOOD, WRAPS_DAMAGE, WRAPS_SPEED, WRAPS_ARMOUR)
			.durability(WRAPS_DURABILITY).repairable(Items.STRING));
		LEATHER_CESTUS = item("leather_cestus", gauntlet(new Item.Properties(), ToolMaterial.WOOD, CESTUS_DAMAGE, WRAPS_SPEED, LEATHER_ARMOUR)
			.durability(LEATHER_DURABILITY).repairable(Items.LEATHER));
		IRON_CESTUS = item("iron_cestus", gauntlet(new Item.Properties(), ToolMaterial.IRON, IRON_CESTUS_DAMAGE, WRAPS_SPEED, LEATHER_ARMOUR));
		STEEL_CESTUS = item("steel_cestus", gauntlet(new Item.Properties(), STEEL, IRON_CESTUS_DAMAGE, WRAPS_SPEED, LEATHER_ARMOUR));
		LEATHER_BRACERS = item("leather_bracers", gauntlet(new Item.Properties(), ToolMaterial.WOOD, BRACERS_DAMAGE, WRAPS_SPEED, LEATHER_ARMOUR)
			.durability(LEATHER_DURABILITY).repairable(Items.LEATHER));
		IRON_BRACERS = item("iron_bracers", gauntlet(new Item.Properties(), ToolMaterial.IRON, METAL_BRACERS_DAMAGE, WRAPS_SPEED, IRON_ARMOUR));
		STEEL_BRACERS = item("steel_bracers", gauntlet(new Item.Properties(), STEEL, METAL_BRACERS_DAMAGE, WRAPS_SPEED, STEEL_ARMOUR));
		PUNCH.put(LEATHER_CESTUS, LEATHER_CESTUS_PUNCH);
		PUNCH.put(IRON_CESTUS, IRON_CESTUS_PUNCH);
		PUNCH.put(STEEL_CESTUS, STEEL_CESTUS_PUNCH);
		BLOCK.put(HAND_WRAPS, new float[] {WRAPS_BLOCK, WRAPS_BLOCK_COST});
		Item[] cestus = {LEATHER_CESTUS, IRON_CESTUS, STEEL_CESTUS}, bracers = {LEATHER_BRACERS, IRON_BRACERS, STEEL_BRACERS};
		for (int tier = 0; tier < 3; tier++) {
			BLOCK.put(cestus[tier], new float[] {CESTUS_BLOCK[tier], CESTUS_BLOCK_COST[tier]});
			BLOCK.put(bracers[tier], new float[] {BRACERS_BLOCK[tier], BRACERS_BLOCK_COST[tier]});
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(HAND_WRAPS);
			output.accept(LEATHER_CESTUS);
			output.accept(IRON_CESTUS);
			output.accept(STEEL_CESTUS);
			output.accept(LEATHER_BRACERS);
			output.accept(IRON_BRACERS);
			output.accept(STEEL_BRACERS);
		});
	}

	/**
	 * A gauntlet's numbers: a sword's damage and speed in the main hand ({@code ToolMaterial#applySwordProperties}, whose
	 * attributes this replaces with the same two), and {@code armour} in each hand it's held in, the main and the off
	 * (each its own modifier, as one id can't be applied twice). The Fang Gauntlets use it too.
	 */
	public static Item.Properties gauntlet(Item.Properties props, ToolMaterial material, float damage, float speed, float armour) {
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage + material.attackDamageBonus(),
				AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND);
		if (armour > 0.0F) {
			attributes.add(Attributes.ARMOR, new AttributeModifier(JaymosPowers.id("gauntlet_armour_mainhand"), armour, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND);
			attributes.add(Attributes.ARMOR, new AttributeModifier(JaymosPowers.id("gauntlet_armour_offhand"), armour, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.OFFHAND);
		}
		return props.sword(material, damage, speed).attributes(attributes.build());
	}

	private static Item item(String id, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(props.setId(key)));
	}

	/** The gear worn on the hands when held (the client draws it there), by item id; its place here is its {@link #wornIndex}. */
	public static final String[] WORN = {"hand_wraps", "leather_cestus", "iron_cestus", "steel_cestus", "leather_bracers", "iron_bracers",
		"steel_bracers"};

	/** {@code stack}'s place in {@link #WORN}, or -1 if it isn't boxing's gear. */
	public static int wornIndex(ItemStack stack) {
		if (stack.isEmpty() || HAND_WRAPS == null) {
			return -1;
		}
		Item item = stack.getItem();
		return item == HAND_WRAPS ? 0 : item == LEATHER_CESTUS ? 1 : item == IRON_CESTUS ? 2 : item == STEEL_CESTUS ? 3
			: item == LEATHER_BRACERS ? 4 : item == IRON_BRACERS ? 5 : item == STEEL_BRACERS ? 6 : -1;
	}

	/** Whether {@code stack} is fist gear. */
	public static boolean isGear(ItemStack stack) {
		return !stack.isEmpty() && stack.is(GEAR);
	}

	/** The share of a blow {@code item} stops when raised: its own if it is fist gear, else {@code otherwise}. */
	public static float blockShare(Item item, float otherwise) {
		float[] block = BLOCK.get(item);
		return block == null ? otherwise : block[0];
	}

	/** The share of a boxer's block cost left with {@code stack} raised (1 for anything that isn't fist gear). */
	public static float blockCostShare(ItemStack stack) {
		float[] block = BLOCK.get(stack.getItem());
		return block == null ? 1.0F : block[1];
	}

	/** What {@code stack} adds to a styled punch (a cestus; 0 for anything else). */
	public static float punchBonus(ItemStack stack) {
		return PUNCH.getOrDefault(stack.getItem(), 0.0F);
	}
}

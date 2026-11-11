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
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;

/**
 * (Jung) Fist gear: what a boxer wraps or straps on. A boxing style ({@link Boxing}) only works with fist gear in the
 * main hand; a truly empty hand is not boxing (Z Fighter's bare-hand fighting is untouched).
 * <ul>
 * <li><b>Hand Wraps</b>: rags or cloth wound round the fist, made in the crafting grid from two of the
 * {@code jaymospowers:wrap_cloth} tag (a Clean Rag, Kingdoms' Torn Cloth, the Kitchen's Hemp Cloth). The plainest fist
 * gear: nothing but the style.</li>
 * <li>The <b>cestus</b>, for damage: a styled punch does {@link #punchBonus} more with one on (leather, iron, steel).</li>
 * <li>The <b>bracers</b>, for blocking: no harder a punch than wraps; what they do for a block comes with the blocks.</li>
 * </ul>
 * The leather cestus and bracers are sewn round Hand Wraps in the crafting grid. The iron and steel ones are forged
 * like every metal weapon ({@code tinkering/WeaponParts}: the cestus's studs or the bracers' plates, and a binding, at
 * the Tool Station). No gold or diamond. Without a style, all of it is a plain weak weapon.
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
	/** How many punches leather gear lasts (iron and steel: their material's). */
	static final int LEATHER_DURABILITY = 400;
	/** Steel as a material: durability and the damage it adds (iron: 250 and 2). */
	static final int STEEL_DURABILITY = 600;
	static final float STEEL_BONUS = 2.5F;

	/** Everything a boxer can box with. */
	public static final TagKey<Item> GEAR = TagKey.create(Registries.ITEM, JaymosPowers.id("boxing_gear"));

	private static final ToolMaterial STEEL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, STEEL_DURABILITY, 6.0F, STEEL_BONUS, 14,
		TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/steel")));

	public static Item HAND_WRAPS, LEATHER_CESTUS, IRON_CESTUS, STEEL_CESTUS, LEATHER_BRACERS, IRON_BRACERS, STEEL_BRACERS;
	/** What each cestus adds to a styled punch. */
	private static final Map<Item, Float> PUNCH = new LinkedHashMap<>();

	private BoxingGear() {
	}

	static void init() {
		HAND_WRAPS = item("hand_wraps", new Item.Properties().sword(ToolMaterial.WOOD, WRAPS_DAMAGE, WRAPS_SPEED)
			.durability(WRAPS_DURABILITY).repairable(Items.STRING));
		LEATHER_CESTUS = item("leather_cestus", new Item.Properties().sword(ToolMaterial.WOOD, CESTUS_DAMAGE, WRAPS_SPEED)
			.durability(LEATHER_DURABILITY).repairable(Items.LEATHER));
		IRON_CESTUS = item("iron_cestus", new Item.Properties().sword(ToolMaterial.IRON, IRON_CESTUS_DAMAGE, WRAPS_SPEED));
		STEEL_CESTUS = item("steel_cestus", new Item.Properties().sword(STEEL, IRON_CESTUS_DAMAGE, WRAPS_SPEED));
		LEATHER_BRACERS = item("leather_bracers", new Item.Properties().sword(ToolMaterial.WOOD, BRACERS_DAMAGE, WRAPS_SPEED)
			.durability(LEATHER_DURABILITY).repairable(Items.LEATHER));
		IRON_BRACERS = item("iron_bracers", new Item.Properties().sword(ToolMaterial.IRON, METAL_BRACERS_DAMAGE, WRAPS_SPEED));
		STEEL_BRACERS = item("steel_bracers", new Item.Properties().sword(STEEL, METAL_BRACERS_DAMAGE, WRAPS_SPEED));
		PUNCH.put(LEATHER_CESTUS, LEATHER_CESTUS_PUNCH);
		PUNCH.put(IRON_CESTUS, IRON_CESTUS_PUNCH);
		PUNCH.put(STEEL_CESTUS, STEEL_CESTUS_PUNCH);
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

	/** What {@code stack} adds to a styled punch (a cestus; 0 for anything else). */
	public static float punchBonus(ItemStack stack) {
		return PUNCH.getOrDefault(stack.getItem(), 0.0F);
	}
}

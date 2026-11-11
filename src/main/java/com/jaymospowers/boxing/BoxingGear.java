package com.jaymospowers.boxing;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;

/**
 * (Jung) Fist gear: what a boxer wraps or straps on. A boxing style ({@link Boxing}) only works with fist gear in the
 * main hand; a truly empty hand is not boxing (Z Fighter's bare-hand fighting is untouched).
 *
 * <p>So far the <b>Hand Wraps</b> only: rags or cloth wound round the fist, made in the crafting grid from two of the
 * {@code jaymospowers:wrap_cloth} tag (a Clean Rag, Kingdoms' Torn Cloth, the Kitchen's Hemp Cloth). Without a style
 * they are a weak weapon like any other. The cestus and the bracers come in their own patch (they are forged).
 *
 * <p>Fist gear is whatever is in the {@code jaymospowers:boxing_gear} item tag. It is deliberately not in Kingdoms'
 * {@code form_fists} tag: with fist gear in hand, right click has to stay free for the boxer's block.
 */
public final class BoxingGear {
	// ---- tuning ----
	/** Hand wraps without a style: damage on top of a bare hand's 1, and the attack speed modifier (4 is a bare hand). */
	static final float WRAPS_DAMAGE = 0.5F, WRAPS_SPEED = -2.4F;
	/** How many punches a pair of hand wraps lasts. */
	static final int WRAPS_DURABILITY = 250;

	/** Everything a boxer can box with. */
	public static final TagKey<Item> GEAR = TagKey.create(Registries.ITEM, JaymosPowers.id("boxing_gear"));

	public static Item HAND_WRAPS;

	private BoxingGear() {
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("hand_wraps"));
		HAND_WRAPS = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)
			.sword(ToolMaterial.WOOD, WRAPS_DAMAGE, WRAPS_SPEED).durability(WRAPS_DURABILITY).repairable(Items.STRING)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(HAND_WRAPS));
	}

	/** Whether {@code stack} is fist gear. */
	public static boolean isGear(ItemStack stack) {
		return !stack.isEmpty() && stack.is(GEAR);
	}
}

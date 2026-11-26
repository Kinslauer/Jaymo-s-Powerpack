package com.jaymoskingdoms.glowwood.creatures;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Jaymo's Kitchen's forager's things (optional), looked up by id when they are asked for. Without Kitchen the truffle is a
 * brown mushroom, the chanterelle too, and there is no truffle soil.
 */
public final class GlowFoods {
	private static final Identifier TRUFFLE = Identifier.fromNamespaceAndPath("jaymoskitchen", "truffle");
	private static final Identifier CHANTERELLE = Identifier.fromNamespaceAndPath("jaymoskitchen", "chanterelle");
	private static final Identifier TRUFFLE_SOIL = Identifier.fromNamespaceAndPath("jaymoskitchen", "truffle_soil");

	private GlowFoods() {
	}

	/** Kitchen's truffle, or the brown mushroom without it. */
	public static Item truffle() {
		return BuiltInRegistries.ITEM.get(TRUFFLE).map(Holder::value).orElse(Items.BROWN_MUSHROOM);
	}

	public static boolean isTruffle(ItemStack stack) {
		return stack.is(truffle());
	}

	public static Item chanterelle() {
		return BuiltInRegistries.ITEM.get(CHANTERELLE).map(Holder::value).orElse(Items.BROWN_MUSHROOM);
	}

	/** Kitchen's Truffle Soil, or null without Kitchen. */
	public static @Nullable Block truffleSoil() {
		return BuiltInRegistries.BLOCK.get(TRUFFLE_SOIL).map(Holder::value).orElse(null);
	}
}

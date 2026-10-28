package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (Smithing) Armour is hammered out on the Smithing Anvil: iron, gold and copper helmets, chestplates, leggings and
 * boots, for the ingots the grid recipe took (5, 8, 7, 4; the grid recipes are switched off in data,
 * tools/tinkering_assets.py), with blows to match ({@link Forging#hitsFor}). The piece takes its grade like any head
 * ({@link Grades#bake} adds or takes away armour points and durability).
 */
public final class ArmourForging {
	/** Ingots each piece takes: helmet, chestplate, leggings, boots (vanilla's). */
	public static final int[] INGOTS = {5, 8, 7, 4};
	private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

	private ArmourForging() {
	}

	static void register() {
		Forging.PROVIDERS.add(ArmourForging::options);
	}

	private static List<Forging.Option> options() {
		List<Forging.Option> out = new ArrayList<>();
		add(out, Molten.IRON, new Item[] {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS});
		add(out, Molten.GOLD, new Item[] {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS});
		add(out, Molten.COPPER, new Item[] {Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS});
		return out;
	}

	private static void add(List<Forging.Option> out, Molten metal, Item[] items) {
		for (int i = 0; i < items.length; i++) {
			out.add(new Forging.Option("armour/" + metal.id + "/" + PIECES[i], new ItemStack(items[i]), metal, INGOTS[i], Forging.hitsFor(INGOTS[i])));
		}
	}
}

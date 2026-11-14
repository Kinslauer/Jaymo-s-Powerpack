package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * (Smithing) The armour sets Jaymo's Kingdoms brings from Frontier Armaments, hammered out here when Kingdoms is in
 * (its pieces are found by id; without it there is nothing to forge). A set is forged from its <b>schematic</b>: a
 * blueprint of the whole set ({@code frontier/templar}), which its people's armourers sell. Without the schematic on
 * you the anvil won't start the piece; with it, it grades and levels like any blueprint. The iron sets are iron; their
 * blackened twins, and the ashen Hedge garb, are steel (Andy: "these can be made from steel").
 */
public final class FrontierForging {
	/** A forged look: the set whose schematic it needs, its item ids' stem, its metal, and the ingots its helmet, chestplate, leggings and boots take. */
	private record Look(String set, String name, Molten metal, int[] ingots) {
	}

	/** The settler's cheap gear takes less iron than a knight's. */
	private static final int[] LIGHT = {3, 5, 4, 2};
	private static final List<Look> LOOKS = List.of(
		new Look("templar", "templar", Molten.IRON, ArmourForging.INGOTS),
		new Look("templar", "templar_steel", Molten.STEEL, ArmourForging.INGOTS),
		new Look("ranger", "ranger", Molten.IRON, ArmourForging.INGOTS),
		new Look("ranger", "ranger_steel", Molten.STEEL, ArmourForging.INGOTS),
		new Look("settler", "settler", Molten.IRON, LIGHT),
		new Look("hedge", "hedge_ashen", Molten.STEEL, LIGHT));
	private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};
	private static final String PREFIX = "frontier/";

	private FrontierForging() {
	}

	static void register() {
		Forging.PROVIDERS.add(FrontierForging::options);
		Forging.CHECKS.add((smith, anvil, option) -> option.id().startsWith(PREFIX) && Blueprints.find(smith, option) == null
			? Component.translatable("message.jaymospowers.smithing.needs_schematic", Blueprints.shapeName(Blueprints.shape(option))) : null);
	}

	/** {@code option} is one of these sets' pieces. */
	static boolean is(Forging.Option option) {
		return option.id().startsWith(PREFIX);
	}

	private static List<Forging.Option> options() {
		List<Forging.Option> out = new ArrayList<>();
		for (Look look : LOOKS) {
			for (int i = 0; i < PIECES.length; i++) {
				Item item = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymoskingdoms", look.name() + "_" + PIECES[i])).orElse(null);
				if (item != null) {
					// (the id's last part is the piece, so the set is the blueprint's shape: Blueprints.shape)
					out.add(new Forging.Option(PREFIX + look.set() + "/" + look.name() + "_" + PIECES[i], new ItemStack(item), look.metal(), look.ingots()[i],
						Forging.hitsFor(look.ingots()[i])));
				}
			}
		}
		return out;
	}
}

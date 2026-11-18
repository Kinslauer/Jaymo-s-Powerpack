package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * (Smithing) Knight Armor and Weapons' metal pieces, hammered out here when it is in (its pieces are found by id;
 * without it there is nothing to forge). Its grid recipes are switched off in data (data/knightarmorweapons/recipe).
 * The armour grid wanted an iron piece and 5 to 7 more ingots, more than one anvil holds, so the pieces take what an
 * anvil can hold; the leather is no longer needed.
 */
public final class KnightArmorForging {
	/** Item ids (in the mod's namespace) and the iron ingots each takes: helmet, chestplate, leggings, boots, then the weapons. */
	private static final String[] PIECES = {"knight_helmet", "knight_chestplate", "knight_leggings", "knight_boots", "knight_long_sword",
		"knight_spiked_mace", "knight_war_sickle", "knight_throwing_axe"};
	private static final int[] INGOTS = {7, 9, 8, 6, 6, 6, 3, 2};
	private static final String PREFIX = "knight/";

	private KnightArmorForging() {
	}

	static void register() {
		Forging.PROVIDERS.add(KnightArmorForging::options);
	}

	private static List<Forging.Option> options() {
		List<Forging.Option> out = new ArrayList<>();
		for (int i = 0; i < PIECES.length; i++) {
			Item item = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("knightarmorweapons", PIECES[i])).orElse(null);
			if (item != null) {
				// (the metal is the id's last part, so the piece is the blueprint's shape: Blueprints.shape)
				out.add(new Forging.Option(PREFIX + PIECES[i] + "/iron", new ItemStack(item), Molten.IRON, INGOTS[i], Forging.hitsFor(INGOTS[i])));
			}
		}
		return out;
	}
}

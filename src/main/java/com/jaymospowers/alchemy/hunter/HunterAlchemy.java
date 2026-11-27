package com.jaymospowers.alchemy.hunter;

import com.jaymospowers.alchemy.Alchemy;
import net.minecraft.world.item.Item;

/**
 * Hunter's Road, patch 6, part B ("the hunter's alchemy"): registers the draughts and monster brews, the leans, the refill
 * items and bench, the formulas, the paste ladder, the testing needle and the hunter's flasks.
 */
public final class HunterAlchemy {
	public static Item NEEDLE, FLASK, NETHERITE_FLASK;

	private HunterAlchemy() {
	}

	public static void init() {
		HuntersFlask.register();
		Draughts.init();
		Leans.init();
		Refills.init();
		Formulas.init();
		Pastes.init();
		LabFurniture.init();
		NEEDLE = Alchemy.item("testing_needle", p -> new NeedleItem(p.stacksTo(1)));
		FLASK = Alchemy.item("hunters_flask", p -> new HuntersFlask(p.stacksTo(1), HuntersFlask.LONGER));
		NETHERITE_FLASK = Alchemy.item("netherite_hunters_flask", p -> new HuntersFlask(p.stacksTo(1).fireResistant(), HuntersFlask.LONGER_NETHERITE));
	}
}

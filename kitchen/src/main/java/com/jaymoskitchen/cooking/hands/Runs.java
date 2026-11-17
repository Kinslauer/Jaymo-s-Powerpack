package com.jaymoskitchen.cooking.hands;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/** Which kinds of cooking are done by hand (each registers its run here), and what has to be true to begin one. */
final class Runs {
	private Runs() {
	}

	static void register() {
		HandsOn.factory(Pick.Kind.CHOP, (table, pick, starter, now) -> new ChopRun(table, pick, now));
		HandsOn.factory(Pick.Kind.STIR, (table, pick, starter, now) -> new StirRun(table, pick, now));
		HandsOn.factory(Pick.Kind.DOUGH, (table, pick, starter, now) -> {
			if (!starter.hasInfiniteMaterials()) {
				// the mixing bowl is only borrowed
				com.jaymoskitchen.cooking.Kitchen.give(starter, new ItemStack(Items.BOWL));
			}
			return new DoughRun(table, pick, now);
		});
		HandsOn.factory(Pick.Kind.JOINT, (table, pick, starter, now) -> new JointRun(table, pick, now));
	}

	/** Whether {@code pick} can begin on this table (the spit is up for a roast...); says so to the cook if not. */
	static boolean ready(Table table, Pick pick, ServerPlayer cook) {
		return true;
	}
}

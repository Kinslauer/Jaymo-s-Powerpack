package com.jaymoskitchen.cooking.hands;

import net.minecraft.network.chat.Component;
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
		HandsOn.factory(Pick.Kind.SEQUENCE, (table, pick, starter, now) -> new SequenceRun(table, pick, now));
		HandsOn.factory(Pick.Kind.SANDWICH, (table, pick, starter, now) -> new SandwichRun(table, pick, now));
		HandsOn.factory(Pick.Kind.KEG, (table, pick, starter, now) -> new PourRuns.Keg(table, pick, now));
		HandsOn.factory(Pick.Kind.TEA, (table, pick, starter, now) -> new PourRuns.Tea(table, pick, now));
		HandsOn.factory(Pick.Kind.SUSHI, (table, pick, starter, now) -> new PourRuns.Sushi(table, pick, now));
		HandsOn.factory(Pick.Kind.ROAST, (table, pick, starter, now) -> new RoastRun(table, pick, now));
		HandsOn.factory(Pick.Kind.GRILL, (table, pick, starter, now) -> new GrillRun(table, pick, now));
		HandsOn.factory(Pick.Kind.OVEN, (table, pick, starter, now) -> new GrillRun(table, pick, now));
		HandsOn.factory(Pick.Kind.SEAR, (table, pick, starter, now) -> new SearRun(table, pick, now));
		HandsOn.factory(Pick.Kind.SMOKE, (table, pick, starter, now) -> new SmokeRun(table, pick, now));
	}

	/** Whether {@code pick} can begin on this table (the spit is up for a roast...); says so to the cook if not. */
	static boolean ready(Table table, Pick pick, ServerPlayer cook) {
		if (pick.kind() == Pick.Kind.ROAST && !table.level.getBlockState(table.pos.above()).is(com.jaymoskitchen.cooking.spit.SpitBlock.SPIT)) {
			cook.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hands.need_spit"));
			return false;
		}
		if (pick.kind() == Pick.Kind.TEA && com.jaymoskitchen.cooking.Station.of(table.level, table.pos.below()) == null) {
			cook.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hands.need_heat"));
			return false;
		}
		return true;
	}
}

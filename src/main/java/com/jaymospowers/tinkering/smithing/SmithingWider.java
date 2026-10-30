package com.jaymospowers.tinkering.smithing;

/**
 * (Smithing) The wider smithing patch, registered from one line in {@link Smithing#register()}: broken-not-gone
 * ({@link Wear}), graded loot and trades ({@link GradedLoot}), the maker's mark and Blacksmith rank
 * ({@link Provenance}) and forged armour ({@link ArmourForging}). docs/Smithing.md has the rules.
 */
public final class SmithingWider {
	private SmithingWider() {
	}

	public static void register() {
		Wear.register();
		Provenance.register();
		GradedLoot.register();
		ArmourForging.register();
		ProstheticForging.register();
	}
}

package com.jaymospowers.tinkering.smithing;

/**
 * (Smithing) The wider smithing patch, registered from one line in {@link Smithing#register()}: broken-not-gone
 * ({@link Wear}), graded loot and trades ({@link GradedLoot}), no diamond gear in loot or trades ({@link NoDiamondGear}), the maker's mark and Blacksmith rank
 * ({@link Provenance}) and forged armour ({@link ArmourForging}, {@link HeraldryForging}). docs/Smithing.md has the rules.
 */
public final class SmithingWider {
	private SmithingWider() {
	}

	public static void register() {
		Wear.register();
		Provenance.register();
		NoDiamondGear.register();
		GradedLoot.register();
		ArmourForging.register();
		FrontierForging.register();
		KnightArmorForging.register();
		HeraldryForging.register();
		ProstheticForging.register();
	}
}

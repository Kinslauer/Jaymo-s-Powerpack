package com.jaymospowers.tinkering.smithing;

import net.minecraft.network.chat.Component;

/**
 * (Smithing) Steel-class metals fight back. Anything with a {@link Forging#difficulty} of {@link #HARD_METAL} or more
 * (steel and slimesteel today) can only be worked on an iron Smithing Anvil, not a Stone Anvil, and only with a steel
 * Smithing Hammer.
 */
public final class SmithTiers {
	/** The difficulty from which a metal needs the better anvil and hammer. */
	public static final float HARD_METAL = 1.25F;

	private SmithTiers() {
	}

	static void register() {
		Forging.CHECKS.add((smith, anvil, option) -> {
			if (Forging.difficulty(option.metal()) < HARD_METAL) {
				return null;
			}
			if (anvil.tier() < SmithingAnvilBlock.IRON_TIER) {
				return Component.translatable("message.jaymospowers.smithing.needs_iron_anvil", option.metal().displayName());
			}
			if (!smith.getMainHandItem().is(Smithing.STEEL_SMITHING_HAMMER)) {
				return Component.translatable("message.jaymospowers.smithing.needs_steel_hammer", option.metal().displayName());
			}
			return null;
		});
	}
}

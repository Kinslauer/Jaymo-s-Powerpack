package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.core.Prosthetics;
import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * (0.18.82) Iron arms and legs for the missing ({@link Prosthetics}) are hammered out on the Smithing Anvil like iron
 * armour ({@link ArmourForging}): never a grid recipe. Wooden ones are a carpenter's grid recipe
 * (data/jaymospowers/recipe/wooden_arm.json, wooden_leg.json).
 */
public final class ProstheticForging {
	private ProstheticForging() {
	}

	static void register() {
		Forging.PROVIDERS.add(ProstheticForging::options);
	}

	private static List<Forging.Option> options() {
		int arm = Prosthetics.IRON_ARM_INGOTS, leg = Prosthetics.IRON_LEG_INGOTS;
		return List.of(
			new Forging.Option("prosthetic/iron_arm/iron", new ItemStack(Prosthetics.IRON_ARM_ITEM), Molten.IRON, arm, Forging.hitsFor(arm)),
			new Forging.Option("prosthetic/iron_leg/iron", new ItemStack(Prosthetics.IRON_LEG_ITEM), Molten.IRON, leg, Forging.hitsFor(leg)));
	}
}

package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.WeaponParts;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Folded steel (Andy: "the katana should only be craftable with iron sand, giving it a reason to go to mohknee"): the
 * steel Kingdoms' blossom smiths teach ({@code BlossomSteel}: Mohknee's iron sand, burnt to a bloom, folded on an anvil).
 * It is steel in every way (heats, hammers and melts as steel) and one thing more: the katana's blade
 * ({@link WeaponParts.Scheme#FOLDED}) is hammered from nothing else. A Hot Ingot remembers it was folded
 * ({@link #FOLDED}) and cools back to a Folded Steel Ingot.
 */
public final class FoldedSteel {
	/** The katana's forgings, by id. */
	static final String KATANA = "weapon/katana_blade/";

	public static Item INGOT;
	/** On a Hot Ingot of steel: it came from folded steel. */
	public static DataComponentType<Boolean> FOLDED;

	private FoldedSteel() {
	}

	static void register() {
		FOLDED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("folded_steel"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
		INGOT = Smithing.item("folded_steel_ingot", Item::new, p -> p);
		// plain steel on the anvil never offers the katana; once chosen, the blank is the proof
		Forging.CHECKS.add((smith, anvil, option) -> option.id().startsWith(KATANA) && anvil.work().is(Smithing.HOT_INGOT)
			&& !folded(anvil.work()) ? Component.translatable("message.jaymospowers.smithing.needs_folded") : null);
	}

	/** Whether this Hot Ingot came from folded steel. */
	public static boolean folded(ItemStack hot) {
		return hot.getOrDefault(FOLDED, false);
	}
}

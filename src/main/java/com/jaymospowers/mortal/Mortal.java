package com.jaymospowers.mortal;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Fighting without a power: the four moves anyone has ({@link MortalKit}), a falling attack for every kind of weapon
 * ({@link FallingAttacks}), launcher, juggle and slam for those who can fight in the air ({@link AirCombo}), the axe on
 * a chain ({@link ChainAxeItem}) and the cap on ability damage between players ({@link PvpCap}).
 */
public final class Mortal {
	/** The chain axe in the hand: an iron axe's blow, a little slower. */
	static final float CHAIN_AXE_DAMAGE = 6.0F, CHAIN_AXE_SPEED = -3.2F;

	public static Item CHAIN_AXE;

	private Mortal() {
	}

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("chain_axe"));
		CHAIN_AXE = Registry.register(BuiltInRegistries.ITEM, key, new ChainAxeItem(new Item.Properties().setId(key)
			.axe(ToolMaterial.IRON, CHAIN_AXE_DAMAGE, CHAIN_AXE_SPEED)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(CHAIN_AXE));
		MortalKit.init();
		FallingAttacks.init();
		AirCombo.init();
	}
}

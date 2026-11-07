package com.jaymospowers.freerun.gear;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.Rarity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.sounds.SoundEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

/**
 * Traceur boots (ParCool track B part 2): light footwear for freerunning. {@link #SAFE_FALL_BLOCKS} more safe fall
 * distance softens landings, and {@link #JUMP_BOOST} added jump strength helps the long jump, charge jump and safety
 * tap (ParCool track B, #1118). The tuning numbers are {@code static final} constants at the top; craftable, own
 * texture.
 */
public final class TraceurBoots {
	/** Extra blocks the wearer can fall without taking damage. */
	public static final double SAFE_FALL_BLOCKS = 1.5;
	/** Share of jump strength the boots add (multiplied base). */
	public static final double JUMP_BOOST = 0.05;

	public static Item ITEM;

	private TraceurBoots() {
	}

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("traceur_boots"));
		ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
			.add(Attributes.SAFE_FALL_DISTANCE,
				new AttributeModifier(JaymosPowers.id("traceur_boots_safe_fall"), SAFE_FALL_BLOCKS, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.FEET)
			.add(Attributes.JUMP_STRENGTH,
				new AttributeModifier(JaymosPowers.id("traceur_boots_jump"), JUMP_BOOST, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
				EquipmentSlotGroup.FEET)
			.build();
		ITEM = Registry.register(BuiltInRegistries.ITEM, key,
			new Item(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.COMMON)
				.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET)
					.setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
					.build())
				.attributes(modifiers)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(ITEM));
	}
}

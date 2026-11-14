package com.jaymospowers.armory;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Weapon;

/**
 * Three weapons of the armoured wars the Armory lacked (Andy's picks from Knights &amp; Heraldry, ideas only: all rights
 * reserved, so nothing of it is copied; the items, numbers, art and code are ours). Each is iron, and its head is forged
 * like any other ({@code tinkering/WeaponParts}: the Bec de Corbin Head, the War Dart Heads, the Heavy Crossbow Prod).
 * <ul>
 * <li><b>The bec de corbin</b>: a hammer face and a crow's beak on a pole. Its blow is blunt and a thrust at once
 * ({@code combat/WeaponKind#POLEHAMMER}), so plate and mail both fail under it; it breaks bones, and knocks a raised
 * shield aside for {@link #BEC_SHIELD_BREAK} seconds.</li>
 * <li><b>The war dart</b>: a weighted iron dart, carried {@link #DART_STACK} to a stack. Held back and thrown like a
 * trident, one from the stack, it lies where it lands to be picked up again ({@link ThrowingWeaponItem}); thrown, it
 * meets armour as a thrust ({@code combat/Blows}).</li>
 * <li><b>The heavy crossbow</b>: spanned with a winding crank ({@link HeavyCrossbowItem}).</li>
 * </ul>
 */
public final class WarArms {
	/** Bec de corbin: damage and attack speed over iron's (as a sword's are given), and seconds a shield it strikes stays down. */
	static final float BEC_DAMAGE = 5.0F, BEC_SPEED = -3.1F, BEC_SHIELD_BREAK = 5.0F;
	/** War dart: what one does thrown, what it adds to a jab in the hand, and how many stack. */
	static final float DART_THROWN = 7.0F;
	static final double DART_JAB = 3.0;
	static final int DART_STACK = 8;

	public static Item BEC_DE_CORBIN, WAR_DART, HEAVY_CROSSBOW;

	private WarArms() {
	}

	static void init() {
		BEC_DE_CORBIN = Armory.item("bec_de_corbin", Item::new, p -> p.sword(ToolMaterial.IRON, BEC_DAMAGE, BEC_SPEED)
			.component(DataComponents.WEAPON, new Weapon(1, BEC_SHIELD_BREAK))
			.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymospowers.bec_de_corbin.hint").withStyle(ChatFormatting.GRAY)))));
		WAR_DART = Armory.item("war_dart", props -> new ThrowingWeaponItem(props, DART_THROWN, false, true),
			p -> p.stacksTo(DART_STACK).attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, DART_JAB, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND)
				.build())
				.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymospowers.war_dart.hint").withStyle(ChatFormatting.GRAY)))));
		HEAVY_CROSSBOW = Armory.item("heavy_crossbow", HeavyCrossbowItem::new, p -> p.durability(HeavyCrossbowItem.DURABILITY).enchantable(1)
			.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymospowers.heavy_crossbow.hint").withStyle(ChatFormatting.GRAY)))));
	}
}

package com.jaymospowers.freerun.gear;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.Rarity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import java.util.function.Consumer;
import net.minecraft.sounds.SoundEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

/**
 * Traceur boots (ParCool track B part 2): light footwear for freerunning. {@link #SAFE_FALL_BLOCKS} more safe fall
 * distance softens landings, and while worn {@link #LONG_JUMP_BONUS} adds to the long jump's speed,
 * {@link #CHARGE_JUMP_BONUS} to the charge jump's height and {@link #SAFETY_CUT_BONUS} to the safety tap's cut
 * (ParCool track B, #1118). The tuning numbers are {@code static final} constants at the top; craftable, own texture,
 * and worn boots draw their own equipment texture ({@link #ASSET}).
 */
public final class TraceurBoots {
	/** Extra blocks the wearer can fall without taking damage. */
	public static final double SAFE_FALL_BLOCKS = 1.5;
	/** Share of jump strength the boots add (multiplied base). */
	public static final double JUMP_BOOST = 0.05;
	/** Share of extra speed the long jump gains while the boots are worn. */
	public static final double LONG_JUMP_BONUS = 0.10;
	/** Extra upward speed (blocks a tick) the charge jump gains while the boots are worn. */
	public static final double CHARGE_JUMP_BONUS = 0.05;
	/** Extra share of the fall damage the safety tap takes off while the boots are worn. */
	public static final float SAFETY_CUT_BONUS = 0.25F;

	/** The equipment asset worn boots draw on the feet. */
	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("traceur_boots"));

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
					.setAsset(ASSET)
					.build())
				.attributes(modifiers)) {
					@Override
					public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
						Consumer<Component> builder, TooltipFlag flag) {
						builder.accept(Component.translatable("item.jaymospowers.traceur_boots.desc")
							.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					}
				});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(ITEM));
	}

	/** Whether this player wears the traceur boots on the feet slot. */
	public static boolean wears(Player player) {
		return ITEM != null && player.getItemBySlot(EquipmentSlot.FEET).is(ITEM);
	}
}

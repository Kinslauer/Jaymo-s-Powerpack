package com.jaymoskingdoms.glowwood.creatures;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** Grown, not tanned. A full set wards off spores (Spore Ward). */
public class MycoleatherArmorItem extends Item {
	private final ArmorType type;

	public MycoleatherArmorItem(ArmorType type, Properties properties) {
		super(properties);
		this.type = type;
	}

	public static boolean wearsFullSet(LivingEntity entity) {
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!(entity.getItemBySlot(slot).getItem() instanceof MycoleatherArmorItem)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		// Only the tunic applies the set bonus, so it is applied once.
		if (this.type == ArmorType.CHESTPLATE && slot == EquipmentSlot.CHEST && entity instanceof LivingEntity living
				&& entity.tickCount % 20 == 0 && wearsFullSet(living)) {
			Mycosis.ward(living, 60);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
								TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.jaymoskingdoms.mycoleather_armor.desc").withStyle(ChatFormatting.GRAY));
	}
}

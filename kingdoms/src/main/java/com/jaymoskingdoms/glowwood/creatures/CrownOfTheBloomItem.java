package com.jaymoskingdoms.glowwood.creatures;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** The Myceliarch's crown: Night Vision and Spore Ward while worn, and Sporelings trust you. */
public class CrownOfTheBloomItem extends Item {

	public CrownOfTheBloomItem(Properties properties) {
		super(properties);
	}

	public static boolean isWorn(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof CrownOfTheBloomItem;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		if (slot == EquipmentSlot.HEAD && entity instanceof LivingEntity && entity.tickCount % 20 == 0) {
			LivingEntity living = (LivingEntity) entity;
			living.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
			Mycosis.ward(living, 60);
		}
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
								TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.jaymoskingdoms.crown_of_the_bloom.desc").withStyle(ChatFormatting.GRAY));
	}
}

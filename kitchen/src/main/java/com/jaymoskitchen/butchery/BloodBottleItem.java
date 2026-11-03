package com.jaymoskitchen.butchery;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;

/**
 * (0.5.51) A bottle of blood from a {@link BloodGrateBlock}. Whoever has a use for it drinks it (a vampire's thirst, a
 * blood sorcerer's pool, through {@link Butchery#addOnDrink}); anyone else retches (Nausea, Hunger).
 */
public class BloodBottleItem extends Item {
	/** What it does to someone with no use for it (ticks). */
	static final int NAUSEA_TICKS = 160, HUNGER_TICKS = 300;

	public BloodBottleItem(Item.Properties properties) {
		super(properties.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build()));
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player && !Butchery.drunkBySomeone(player)) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, HUNGER_TICKS, 0));
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.blood_bottle.retch"));
		}
		return super.finishUsingItem(stack, level, entity);
	}
}

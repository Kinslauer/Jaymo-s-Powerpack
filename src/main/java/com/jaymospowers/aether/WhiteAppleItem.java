package com.jaymospowers.aether;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The white apple: food that also clears every harmful effect (poison, weakness, nausea...). Edible when full. */
public class WhiteAppleItem extends Item {
	public WhiteAppleItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack left = super.finishUsingItem(stack, level, entity);
		if (level instanceof ServerLevel) {
			List<MobEffectInstance> ailments = new ArrayList<>();
			for (MobEffectInstance effect : entity.getActiveEffects()) {
				if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
					ailments.add(effect);
				}
			}
			for (MobEffectInstance effect : ailments) {
				entity.removeEffect(effect.getEffect());
			}
		}
		return left;
	}
}

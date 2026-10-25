package com.jaymoskitchen.delight;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Comfort and Nourishment: they do nothing by themselves; BodyHeat and Stomach check for them. */
final class DelightEffect extends MobEffect {
	DelightEffect(MobEffectCategory category, int color) {
		super(category, color);
	}
}

package com.jaymoskitchen.climate;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The status icon of a newcomer's first-wake grace ({@link BodyHeat#CLEMENCY}): for the first ten minutes in the world, the
 * cold and heat can't push body heat further from comfortable. The effect itself does nothing; {@link BodyHeat} keeps it
 * up while the grace lasts. Art: a small sun over a snowflake, our own, in {@code textures/mob_effect/clemency.png}.
 */
final class ClemencyEffect extends MobEffect {
	ClemencyEffect() {
		super(MobEffectCategory.BENEFICIAL, 0xF2D98C);
	}
}

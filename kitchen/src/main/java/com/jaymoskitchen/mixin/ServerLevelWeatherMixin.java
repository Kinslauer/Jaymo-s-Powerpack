package com.jaymoskitchen.mixin;

import com.jaymoskitchen.season.SeasonalWeather;
import com.jaymoskitchen.season.SeasonalWeather.Spell;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The world's weather clock follows the season. When vanilla rolls a new length for a clear wait, a rain, a calm or a
 * thunder ({@code advanceWeatherCycle}), {@link SeasonalWeather#stretch} stretches or shortens it. A spell set by hand
 * (a power, a rite, the weather command) never goes through the roll, so it is left alone; and Kingdoms' storm cells
 * run on their own clock beside it.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelWeatherMixin {
	@Shadow
	private static IntProvider THUNDER_DELAY;

	@WrapOperation(method = "advanceWeatherCycle", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/util/valueproviders/IntProvider;sample(Lnet/minecraft/util/RandomSource;)I"))
	private int jaymoskitchen$seasonalSpell(IntProvider provider, RandomSource random, Operation<Integer> original) {
		int rolled = original.call(provider, random);
		Spell spell;
		if (provider == ServerLevel.RAIN_DELAY) {
			spell = Spell.CLEAR;
		} else if (provider == ServerLevel.RAIN_DURATION) {
			spell = Spell.RAIN;
		} else if (provider == THUNDER_DELAY) {
			spell = Spell.CALM;
		} else if (provider == ServerLevel.THUNDER_DURATION) {
			spell = Spell.THUNDER;
		} else {
			return rolled;
		}
		return SeasonalWeather.stretch((ServerLevel)(Object)this, spell, rolled);
	}
}

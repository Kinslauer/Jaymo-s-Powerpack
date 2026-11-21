package com.jaymospowers.mixin;

import com.jaymospowers.tinkering.smithing.StuddedWeather;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A full studded set slows freezing ({@link StuddedWeather#frostCut}): a rise in frozen ticks (the game's powder snow, the
 * Freezing effect, a power's chill) keeps its whole part and rounds its fraction by chance; thawing is untouched.
 */
@Mixin(Entity.class)
abstract class StuddedFrostMixin {
	@ModifyVariable(method = "setTicksFrozen", at = @At("HEAD"), argsOnly = true)
	private int jaymospowers$slower(int ticks) {
		Entity self = (Entity)(Object)this;
		if (!(self instanceof LivingEntity living) || living.level().isClientSide() || ticks <= living.getTicksFrozen()) {
			return ticks;
		}
		float cut = StuddedWeather.frostCut(living);
		if (cut <= 0.0F) {
			return ticks;
		}
		float rise = (ticks - living.getTicksFrozen()) * (1.0F - cut);
		int whole = Mth.floor(rise);
		return living.getTicksFrozen() + whole + (living.getRandom().nextFloat() < rise - whole ? 1 : 0);
	}
}

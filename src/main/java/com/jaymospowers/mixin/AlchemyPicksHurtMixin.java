package com.jaymospowers.mixin;

import com.jaymospowers.alchemy.picks.AlchemyPicks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A glass skin takes more from blunt blows, the skins that burn take more from fire, and the Hollow Pact's hurts land harder. */
@Mixin(LivingEntity.class)
abstract class AlchemyPicksHurtMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$picksHurt(float damage, @Local(argsOnly = true) DamageSource source) {
		return AlchemyPicks.incoming((LivingEntity) (Object) this, source, damage);
	}
}

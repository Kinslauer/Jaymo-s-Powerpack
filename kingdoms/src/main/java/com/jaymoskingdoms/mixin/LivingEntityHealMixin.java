package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Necromancy;
import com.jaymoskingdoms.Turning;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.231) A death blight ({@link Necromancy#blighted}): no healing reaches the body (food, potions, regeneration) till it wears off. */
@Mixin(LivingEntity.class)
abstract class LivingEntityHealMixin {
	/** (0.7.513) The Hearthkeeper's trial and scar: a player heals slower (food, potions; Powers' part heals read it in healPart). */
	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$turnedHeal(float amount) {
		return (Object)this instanceof Player p && !p.level().isClientSide() ? amount * (1.0F - Turning.healing(p)) : amount;
	}

	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$blighted(float amount, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() && Necromancy.blighted(self)) {
			ci.cancel();
		}
	}
}

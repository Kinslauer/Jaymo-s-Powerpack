package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.MobAmmo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A drowned's thrown trident leaves its hand: it fights bare-handed until it gets it (or another) back ({@link MobAmmo#lost}). */
@Mixin(Drowned.class)
abstract class DrownedThrowMixin {
	@Inject(method = "performRangedAttack", at = @At("TAIL"))
	private void jaymoskingdoms$letGo(LivingEntity target, float power, CallbackInfo ci) {
		Drowned self = (Drowned) (Object) this;
		if (!self.level().isClientSide() && self.getMainHandItem().is(Items.TRIDENT)) {
			MobAmmo.lost(self, Items.TRIDENT);
		}
	}
}

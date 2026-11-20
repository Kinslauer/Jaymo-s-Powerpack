package com.jaymoskingdoms.mixin.forestfire;

import com.jaymoskingdoms.ForestFireCompat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PotionTags;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (ff-villagers) A splash of water (a villager's thrown bottle, {@code Firefighting}, or anyone's) puts Peterwolf's
 * fire out where it lands too, as far round as it puts out the flames (Powers' {@code SplashWaterDouseMixin}), or the
 * hot ground under them would light again. Nothing without it.
 */
@Mixin(AbstractThrownPotion.class)
abstract class SplashDousesForestFireMixin {
	@Unique
	private static final int DOUSE_RADIUS = 2;

	@Inject(method = "onHitBlock", at = @At("TAIL"))
	private void jaymoskingdoms$douseForestFire(BlockHitResult hitResult, CallbackInfo ci) {
		AbstractThrownPotion self = (AbstractThrownPotion) (Object) this;
		if (ForestFireCompat.LOADED && self.level() instanceof ServerLevel level
			&& self.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(PotionTags.DOUSES_FIRE)) {
			ForestFireCompat.douse(level, hitResult.getBlockPos().relative(hitResult.getDirection()), DOUSE_RADIUS);
		}
	}
}

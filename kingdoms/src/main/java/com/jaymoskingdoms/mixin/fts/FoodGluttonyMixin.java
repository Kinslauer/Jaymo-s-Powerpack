package com.jaymoskingdoms.mixin.fts;

import com.jaymoskingdoms.inhabitants.fts.FtsEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blessing of Gluttony (From the Shadows Reborn's effect, AGPL v3): what a blessed player eats feeds them more ({@link FtsEffects#gluttony}). */
@Mixin(FoodProperties.class)
abstract class FoodGluttonyMixin {
	@Inject(method = "onConsume", at = @At("TAIL"))
	private void jaymoskingdoms$gluttony(Level level, LivingEntity eater, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		if (!level.isClientSide()) {
			FtsEffects.gluttony(eater, (FoodProperties)(Object)this);
		}
	}
}

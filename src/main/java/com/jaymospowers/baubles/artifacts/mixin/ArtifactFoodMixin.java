package com.jaymospowers.baubles.artifacts.mixin;

import com.jaymospowers.baubles.artifacts.ArtifactEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (Artifacts) After eating: the glutton's ring's haste and the rooted boots' growth. */
@Mixin(FoodProperties.class)
public abstract class ArtifactFoodMixin {
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void jaymospowers$afterEating(Level level, LivingEntity user, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		ArtifactEffects.ate(level, user, (FoodProperties) (Object) this);
	}
}

package com.jaymoskitchen.mixin;

import com.jaymoskitchen.farm.SlowFarms;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.block.CropBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Weaker bone meal ({@link SlowFarms#BONE_MEAL_STAGES}): a crop grows one stage for it, whatever its kind would add. */
@Mixin(CropBlock.class)
abstract class CropBoneMealMixin {
	@ModifyExpressionValue(method = "growCrops", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/CropBlock;getBonemealAgeIncrease(Lnet/minecraft/world/level/Level;)I"))
	private int jaymoskitchen$oneStage(int stages) {
		return SlowFarms.BONE_MEAL_STAGES;
	}
}

package com.jaymoskitchen.mixin;

import com.jaymoskitchen.farm.SlowFarms;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.block.StemBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Weaker bone meal ({@link SlowFarms#BONE_MEAL_STAGES}): a melon or pumpkin stem grows one stage for it. */
@Mixin(StemBlock.class)
abstract class StemBoneMealMixin {
	@ModifyExpressionValue(method = "performBonemeal", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/util/Mth;nextInt(Lnet/minecraft/util/RandomSource;II)I"))
	private int jaymoskitchen$oneStage(int stages) {
		return SlowFarms.BONE_MEAL_STAGES;
	}
}

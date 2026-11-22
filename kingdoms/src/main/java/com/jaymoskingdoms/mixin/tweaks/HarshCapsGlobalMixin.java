package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.HarshLands;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The world-wide monster cap is the harshest land's ({@link HarshCapsMixin}): each chunk near a player is still held to its
 * own land's by the player-near cap.
 */
@Mixin(targets = "net.minecraft.world.level.NaturalSpawner$SpawnState")
abstract class HarshCapsGlobalMixin {
	@WrapOperation(method = "canSpawnForCategoryGlobal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
	private int jaymoskingdoms$harshGlobal(MobCategory category, Operation<Integer> original) {
		if (category != MobCategory.MONSTER) {
			return original.call(category);
		}
		HarshLands.asking(HarshLands.MOST);
		try {
			return original.call(category);
		} finally {
			HarshLands.asking(-1);
		}
	}
}

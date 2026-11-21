package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.seas.SeaLife;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The world-wide water caps are the open sea's ({@link SeaCapsMixin}): each chunk near a player is still held to its own
 * (the coast's, or the open sea's) by the player-near cap.
 */
@Mixin(targets = "net.minecraft.world.level.NaturalSpawner$SpawnState")
abstract class SeaCapsGlobalMixin {
	@WrapOperation(method = "canSpawnForCategoryGlobal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
	private int jaymoskingdoms$openSeaGlobal(MobCategory category, Operation<Integer> original) {
		if (!SeaLife.seaCategory(category)) {
			return original.call(category);
		}
		SeaLife.askingOpen(true);
		try {
			return original.call(category);
		} finally {
			SeaLife.askingOpen(false);
		}
	}
}

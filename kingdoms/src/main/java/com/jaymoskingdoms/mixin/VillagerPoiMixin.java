package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.VillagerFixes;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A villager looks for a bed, workplace or bell farther out, and not for a workplace or bell at bedtime ({@link VillagerFixes}). */
@Mixin(AcquirePoi.class)
abstract class VillagerPoiMixin {
	@ModifyConstant(method = "lambda$create$3", constant = @Constant(intValue = 48), require = 0)
	private static int jaymoskingdoms$range(int range, @Local(argsOnly = true) PathfinderMob body) {
		return body instanceof Villager ? Math.max(range, VillagerFixes.POI_RANGE) : range;
	}

	@ModifyVariable(method = "create(Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;ZLjava/util/Optional;Ljava/util/function/BiPredicate;)Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;",
		at = @At("HEAD"), argsOnly = true)
	private static BiPredicate<ServerLevel, BlockPos> jaymoskingdoms$byDay(BiPredicate<ServerLevel, BlockPos> valid,
		@Local(argsOnly = true, ordinal = 1) MemoryModuleType<GlobalPos> acquire) {
		return VillagerFixes.byDay(acquire, valid);
	}
}

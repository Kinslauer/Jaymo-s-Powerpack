package com.jaymoskitchen.mixin;

import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.ai.sensing.SecondaryPoiSensor;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.FarmlandBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (Farmer's Delight's, MIT, after a mixin by MehVahdJukaar.) A farmer looks for farmland as its secondary workplace, and
 * vanilla knows only the one block: any farmland will do now, so farmers also tend rich soil farmland and the
 * Kitchen's fertile farmland.
 */
@Mixin(SecondaryPoiSensor.class)
abstract class SecondaryPoiSensorMixin {
	@WrapOperation(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;)V",
		at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableSet;contains(Ljava/lang/Object;)Z"))
	private boolean jaymoskitchen$anyFarmland(ImmutableSet<?> instance, Object block, Operation<Boolean> original, @Local(argsOnly = true) Villager villager) {
		if (villager.getVillagerData().profession().is(VillagerProfession.FARMER)) {
			return original.call(instance, block) || block instanceof FarmlandBlock;
		}
		return original.call(instance, block);
	}
}

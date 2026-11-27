package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.VillagerFixes;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** A villager lies down in its bed from a little farther off (one behind a stair or a chest) ({@link VillagerFixes}). */
@Mixin(SleepInBed.class)
abstract class VillagerSleepMixin {
	@ModifyConstant(method = "checkExtraStartConditions", constant = @Constant(doubleValue = 2.0))
	private double jaymoskingdoms$sleepReach(double reach) {
		return Math.max(reach, VillagerFixes.SLEEP_REACH);
	}
}

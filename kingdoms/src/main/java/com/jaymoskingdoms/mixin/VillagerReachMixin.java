package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.VillagerFixes;
import net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** A villager takes up a workstation from a little farther off (one set behind a counter) ({@link VillagerFixes}). */
@Mixin(AssignProfessionFromJobSite.class)
abstract class VillagerReachMixin {
	@ModifyConstant(method = "lambda$create$2", constant = @Constant(doubleValue = 2.0))
	private static double jaymoskingdoms$claimReach(double reach) {
		return Math.max(reach, VillagerFixes.CLAIM_REACH);
	}
}

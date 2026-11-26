package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.VillagerFixes;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A villager keeps trying for its bed, workplace or bell longer before it forgets a place it can't reach ({@link VillagerFixes}). */
@Mixin(SetWalkTargetFromBlockMemory.class)
abstract class VillagerWalkMixin {
	@ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private static int jaymoskingdoms$giveUp(int tooLongUnreachable) {
		return Math.max(tooLongUnreachable, VillagerFixes.GIVE_UP_TICKS);
	}
}

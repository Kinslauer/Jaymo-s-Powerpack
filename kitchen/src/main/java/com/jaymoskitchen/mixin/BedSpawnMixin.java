package com.jaymoskitchen.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Better With Mods' hardcore beds ({@link com.jaymoskitchen.hardcore.Hardcore}): no bed sets your spawn. The bed's own
 * rule says so, so vanilla sleep, Kingdoms' bed rest and ship berths all leave your spawn where it was.
 */
@Mixin(AbstractBedBlock.class)
abstract class BedSpawnMixin {
	@Inject(method = "getBedRule", at = @At("RETURN"), cancellable = true)
	private void jaymoskitchen$noBedSpawn(Level level, BlockPos pos, CallbackInfoReturnable<BedRule> cir) {
		BedRule rule = cir.getReturnValue();
		if (rule.canSetSpawn() != BedRule.Rule.NEVER) {
			cir.setReturnValue(new BedRule(rule.canSleep(), BedRule.Rule.NEVER, rule.destroyOnUse(), rule.destroyOnLeave(), rule.errorMessage()));
		}
	}
}

package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Rulers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.tasks.EquipmentTask;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.171) MCA's own armour routine puts on a villager the best armour from their inventory, or nothing, which took a
 * ruler's crown and robes off (Andy found a queen dressed as any villager). A ruler's regalia stays on.
 */
@Mixin(EquipmentTask.class)
abstract class McaEquipmentMixin {
	@Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/conczin/mca/entity/VillagerEntityMCA;)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$regalia(ServerLevel level, VillagerEntityMCA villager, CallbackInfoReturnable<Boolean> cir) {
		if (Rulers.is(villager)) {
			cir.setReturnValue(false);
		}
	}
}

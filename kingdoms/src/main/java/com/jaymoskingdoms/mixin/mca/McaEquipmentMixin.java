package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Regalia;
import com.jaymoskingdoms.Rulers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.tasks.EquipmentTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.171) MCA's own armour routine puts on a villager the best armour from their inventory, or nothing, which took a
 * ruler's crown and robes off (Andy found a queen dressed as any villager). A ruler's regalia stays on.
 * <p>
 * (0.7.630) Andy: "only queens of rich kingdoms should ever wear the diamond armor w/ trim, the kings should wear netherite,
 * and any below can wear whatever else, just not those tiers." MCA's best archers wear diamond and its elite guards
 * netherite, and a villager puts on any diamond they're given: everyone the routine dresses (never a ruler) wears iron
 * in its place ({@link Regalia#lesser}).
 */
@Mixin(EquipmentTask.class)
abstract class McaEquipmentMixin {
	@Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/conczin/mca/entity/VillagerEntityMCA;)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$regalia(ServerLevel level, VillagerEntityMCA villager, CallbackInfoReturnable<Boolean> cir) {
		if (Rulers.is(villager)) {
			cir.setReturnValue(false);
		}
	}

	@Redirect(method = "equipBestArmor", at = @At(value = "INVOKE", target = "Lnet/conczin/mca/entity/VillagerEntityMCA;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"))
	private void jaymoskingdoms$belowTheCrown(VillagerEntityMCA villager, EquipmentSlot slot, ItemStack stack) {
		villager.setItemSlot(slot, Regalia.lesser(stack, slot));
	}
}

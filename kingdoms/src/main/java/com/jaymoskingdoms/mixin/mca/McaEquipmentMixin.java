package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.FrontierWear;
import com.jaymoskingdoms.Regalia;
import com.jaymoskingdoms.Rulers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.tasks.EquipmentTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
 * (0.7.631) Andy: "only queens of rich kingdoms should ever wear the diamond armor w/ trim, the kings should wear netherite,
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
		// (and a guard's issue is their settlement's: Templar plate, the settler's gear, a gunner's harness)
		villager.setItemSlot(slot, FrontierWear.issue(villager, slot, Regalia.lesser(stack, slot)));
	}

	/** A caravan guard's musketoon stays in his hands when MCA would arm him with a sword or a bow. */
	@Redirect(method = {"equipBestWeapon", "equipBestRanged"}, at = @At(value = "INVOKE", target = "Lnet/conczin/mca/entity/VillagerEntityMCA;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"))
	private void jaymoskingdoms$gunStays(VillagerEntityMCA villager, EquipmentSlot slot, ItemStack stack) {
		if (!FrontierWear.keeps(villager, slot, villager.isGuard())) {
			villager.setItemSlot(slot, stack);
		}
	}

	/** Nor is it put away with a guard's off-duty hands, and what a carter, a fence or a wizard was dressed in isn't taken off them. */
	@Redirect(method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/conczin/mca/entity/VillagerEntityMCA;J)V", at = @At(value = "INVOKE", target = "Lnet/conczin/mca/entity/VillagerEntityMCA;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"))
	private void jaymoskingdoms$issueStays(VillagerEntityMCA villager, EquipmentSlot slot, ItemStack stack) {
		if (!FrontierWear.keeps(villager, slot, villager.isGuard())) {
			villager.setItemSlot(slot, stack);
		}
	}

	@Redirect(method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/conczin/mca/entity/VillagerEntityMCA;J)V", at = @At(value = "INVOKE", target = "Lnet/conczin/mca/entity/VillagerEntityMCA;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"))
	private void jaymoskingdoms$handStays(VillagerEntityMCA villager, InteractionHand hand, ItemStack stack) {
		if (!FrontierWear.keeps(villager, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, villager.isGuard())) {
			villager.setItemInHand(hand, stack);
		}
	}
}

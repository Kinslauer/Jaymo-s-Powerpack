package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Barbarians;
import com.jaymoskingdoms.Rulers;
import java.util.List;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.tasks.EquipmentTask;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.171) MCA's own armour routine puts on a villager the best armour from their inventory, or nothing, which took a
 * ruler's crown and robes off (Andy found a queen dressed as any villager). A ruler's regalia stays on.
 * <p>
 * (0.7.308) It also strips a guard bare whenever they aren't fighting (unless "wear armour" is set in MCA's menu), so a
 * camp's barbarians, greenskins and pirates went about in plain clothes (Andy: "barbarian's armor disappeared"). A camp's
 * villagers keep their gear on: whatever was taken off is put back on from their pack.
 */
@Mixin(EquipmentTask.class)
abstract class McaEquipmentMixin {
	/** How often (ticks) a camp's villager missing a piece is dressed again from their pack. */
	@Unique
	private static final int REDRESS_TICKS = 20;
	@Unique
	private static final List<EquipmentSlot> ARMOUR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	@Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/conczin/mca/entity/VillagerEntityMCA;)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$regalia(ServerLevel level, VillagerEntityMCA villager, CallbackInfoReturnable<Boolean> cir) {
		if (Rulers.is(villager)) {
			cir.setReturnValue(false);
		} else if (Barbarians.isCampVillager(villager)) {
			if (villager.tickCount % REDRESS_TICKS == 0) {
				jaymoskingdoms$redress(villager);
			}
			cir.setReturnValue(false);
		}
	}

	/** Each empty armour slot (and an empty weapon hand) filled from {@code villager}'s pack, where they carry one. */
	@Unique
	private static void jaymoskingdoms$redress(VillagerEntityMCA villager) {
		SimpleContainer pack = villager.getInventory();
		for (EquipmentSlot slot : ARMOUR) {
			if (villager.getItemBySlot(slot).isEmpty()) {
				InventoryUtils.getBestArmor(pack, slot).ifPresent(s -> villager.setItemSlot(slot, s.copy()));
			}
		}
		if (villager.getMainHandItem().isEmpty()) {
			for (int i = 0; i < pack.getContainerSize(); i++) {
				ItemStack s = pack.getItem(i);
				if (s.is(ItemTags.AXES) || s.is(ItemTags.SWORDS)) {
					villager.setItemSlot(EquipmentSlot.MAINHAND, s.copy());
					break;
				}
			}
		}
	}
}

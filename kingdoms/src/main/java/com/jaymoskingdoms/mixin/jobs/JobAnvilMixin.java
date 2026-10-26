package com.jaymoskingdoms.mixin.jobs;

import com.jaymoskingdoms.Careers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.218) A Journeyman Smith's anvil work costs less ({@link Careers#anvilCost}). */
@Mixin(AnvilMenu.class)
abstract class JobAnvilMixin extends ItemCombinerMenu {
	@Shadow
	@Final
	private DataSlot cost;

	protected JobAnvilMixin(@Nullable MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slots) {
		super(type, id, inventory, access, slots);
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void jaymoskingdoms$smithsCost(CallbackInfo ci) {
		int now = cost.get(), cut = Careers.anvilCost(player, now);
		if (cut != now && !resultSlots.getItem(0).isEmpty()) {
			cost.set(cut);
			broadcastChanges();
		}
	}
}

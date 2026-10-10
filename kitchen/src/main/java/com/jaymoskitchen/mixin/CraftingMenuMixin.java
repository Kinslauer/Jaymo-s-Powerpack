package com.jaymoskitchen.mixin;

import com.jaymoskitchen.table.CraftingTableEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A crafting table's grid is the table's own: opening it lays out what was left there, every change is stored (and
 * shown on the table), and closing it leaves the grid behind instead of handing it back.
 */
@Mixin(CraftingMenu.class)
abstract class CraftingMenuMixin {
	@Shadow
	@Final
	private ContainerLevelAccess access;

	@Unique
	private boolean jaymoskitchen$loading;

	@Unique
	private CraftingContainer jaymoskitchen$grid() {
		return ((AbstractCraftingMenuAccessor) this).jaymoskitchen$craftSlots();
	}

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
	private void jaymoskitchen$layOut(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		Player player = inventory.player;
		access.execute((level, pos) -> {
			if (level.getBlockEntity(pos) instanceof CraftingTableEntity table) {
				table.lock(player);
				List<ItemStack> left = new ArrayList<>(table.grid());
				CraftingContainer grid = jaymoskitchen$grid();
				jaymoskitchen$loading = true;
				for (int i = 0; i < grid.getContainerSize() && i < left.size(); i++) {
					grid.setItem(i, left.get(i).copy());
				}
				jaymoskitchen$loading = false;
				((CraftingMenu) (Object) this).slotsChanged(grid);
			}
		});
	}

	@Inject(method = "slotsChanged", at = @At("TAIL"))
	private void jaymoskitchen$store(Container container, CallbackInfo ci) {
		if (jaymoskitchen$loading) {
			return;
		}
		access.execute((level, pos) -> {
			if (level.getBlockEntity(pos) instanceof CraftingTableEntity table) {
				table.store(jaymoskitchen$grid().getItems());
			}
		});
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void jaymoskitchen$leaveIt(Player player, CallbackInfo ci) {
		access.execute((level, pos) -> {
			if (level.getBlockEntity(pos) instanceof CraftingTableEntity table && !table.isRemoved()) {
				CraftingContainer grid = jaymoskitchen$grid();
				jaymoskitchen$loading = true;
				table.store(grid.getItems());
				// left on the table: nothing to hand back
				for (int i = 0; i < grid.getContainerSize(); i++) {
					grid.removeItemNoUpdate(i);
				}
				jaymoskitchen$loading = false;
				table.unlock(player);
			}
		});
	}
}

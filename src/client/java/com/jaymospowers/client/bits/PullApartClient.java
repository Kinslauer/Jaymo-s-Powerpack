package com.jaymospowers.client.bits;

import com.jaymospowers.bits.PullApart;
import com.jaymospowers.client.bits.mixin.HoveredSlotAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Pulling things apart in the inventory, the client's half ({@link PullApart} has the rest): Ctrl + right-click a stack
 * that comes apart (wool, cobblestone, leather...) with nothing on the cursor pulls one apart, Ctrl + Shift + right-click
 * the whole stack. A modified click and not a key of its own, so it clashes with none of the many keys already bound; a
 * plain right-click still splits a stack as ever. Not in the creative inventory (its slots aren't the server's).
 */
public final class PullApartClient {
	private PullApartClient() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> container && !(screen instanceof CreativeModeInventoryScreen)) {
				ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
					if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT || !event.hasControlDown()
						|| !ClientPlayNetworking.canSend(PullApart.Pull.TYPE) || !container.getMenu().getCarried().isEmpty()) {
						return true;
					}
					Slot slot = ((HoveredSlotAccessor)container).jaymospowers$hoveredSlot();
					if (slot == null || PullApart.of(slot.getItem()) == null) {
						return true;
					}
					int number = container.getMenu().slots.indexOf(slot);
					if (number < 0) {
						return true;
					}
					ClientPlayNetworking.send(new PullApart.Pull(container.getMenu().containerId, number, event.hasShiftDown()));
					return false;
				});
			}
		});
	}
}

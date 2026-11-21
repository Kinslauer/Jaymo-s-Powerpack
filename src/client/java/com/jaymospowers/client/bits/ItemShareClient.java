package com.jaymospowers.client.bits;

import com.jaymospowers.bits.ItemShare;
import com.jaymospowers.client.bits.mixin.HoveredSlotAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Item sharing, the client's half ({@link ItemShare} has the rest): Shift+T while the mouse is over an item in any container
 * screen (inventory, chests, a trader's, your backpack...) sends the slot to the server, which says the item in chat with its
 * hover tooltip. Not in the creative inventory (its slots aren't the server's), and not while a text box has the keyboard, so
 * typing a capital T into a search or a rename still types it.
 */
public final class ItemShareClient {
	/** GLFW's code for the T key. */
	static final int KEY_T = 84;

	private ItemShareClient() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> container && !(screen instanceof CreativeModeInventoryScreen)) {
				ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
					if (event.key() != KEY_T || !event.hasShiftDown() || !ClientPlayNetworking.canSend(ItemShare.Share.TYPE)
						|| s.getFocused() instanceof EditBox box && box.isFocused()) {
						return true;
					}
					Slot slot = ((HoveredSlotAccessor)container).jaymospowers$hoveredSlot();
					if (slot == null || !slot.hasItem()) {
						return true;
					}
					int number = container.getMenu().slots.indexOf(slot);
					if (number < 0) {
						return true;
					}
					ClientPlayNetworking.send(new ItemShare.Share(container.getMenu().containerId, number));
					return false;
				});
			}
		});
	}
}

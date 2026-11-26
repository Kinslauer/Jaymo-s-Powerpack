package com.jaymospowers.client.bits;

import com.jaymospowers.bits.PullApart;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.client.bits.mixin.HoveredSlotAccessor;
import com.jaymospowers.client.mixin.ContainerScreenAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Pulling things apart in the inventory, the client's half ({@link PullApart} has the rest). Two ways in:
 * <ul>
 * <li>Andy: "there should also be an iron pick icon that you can click to enter the deconstruct mode, hovering the button
 * should tell you this, and a tool tip should appear after clicking explaining", then "make it smaller than an item, and
 * place it next to the crafting square, within the inventory window". So a small pick sits in the survival inventory,
 * under the crafting grid's result slot (clear of the recipe book's button). Hover it and it says what it does;
 * click it and deconstruct mode is on: the things that come apart are outlined in gold, an explanation shows beside
 * the pick for a few seconds, and a click on one pulls one apart (Shift-click, the whole stack). Click the pick again,
 * or close the screen, and it's off.</li>
 * <li>Without the mode, Ctrl + right-click pulls one apart and Ctrl + Shift + right-click the whole stack.</li>
 * </ul>
 * The Ctrl click works in every container screen (chests too); both only with nothing on the cursor, and not in the
 * creative inventory (its slots aren't the server's).
 */
public final class PullApartClient {
	/** How long the explanation stays up after the mode is turned on (ms). */
	static final long EXPLAIN_MS = 6000L;

	/** Whether deconstruct mode is on, and the screen it was turned on in (a new screen starts with it off). */
	private static boolean on;
	private static @Nullable Screen modeScreen;
	/** When the mode was last turned on (for the explanation). */
	private static long turnedOn;

	private PullApartClient() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container) || screen instanceof CreativeModeInventoryScreen) {
				return;
			}
			if (screen != modeScreen) {
				on = false;
				modeScreen = screen;
			}
			if (screen instanceof InventoryScreen) {
				Pick pick = new Pick(container);
				pick.place();
				Screens.getWidgets(screen).add(pick);
				// the recipe book moves the inventory about: follow it every frame
				ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> pick.place());
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> drawMode(container, pick, graphics, mouseX, mouseY));
			}
			ScreenEvents.remove(screen).register(s -> {
				if (s == modeScreen) {
					on = false;
					modeScreen = null;
				}
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				boolean modeClick = on && event.button() == InputConstants.MOUSE_BUTTON_LEFT;
				boolean ctrlClick = event.button() == InputConstants.MOUSE_BUTTON_RIGHT && event.hasControlDown();
				if (!modeClick && !ctrlClick || !ClientPlayNetworking.canSend(PullApart.Pull.TYPE) || !container.getMenu().getCarried().isEmpty()) {
					return true;
				}
				Slot slot = ((HoveredSlotAccessor)container).jaymospowers$hoveredSlot();
				if (!pullable(slot)) {
					return true;
				}
				int number = container.getMenu().slots.indexOf(slot);
				if (number < 0) {
					return true;
				}
				ClientPlayNetworking.send(new PullApart.Pull(container.getMenu().containerId, number, event.hasShiftDown()));
				return false;
			});
		});
	}

	/** Whether the server will pull this slot's stack apart: one that comes apart, in a slot that isn't an output (a crafted wool is taken, not torn). */
	private static boolean pullable(@Nullable Slot slot) {
		return slot != null && PullApart.of(slot.getItem()) != null && slot.mayPlace(slot.getItem());
	}

	/** While the mode is on: gold outlines on what comes apart, and the explanation beside the pick for a while. */
	private static void drawMode(AbstractContainerScreen<?> screen, Pick pick, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!on || screen != modeScreen) {
			return;
		}
		ContainerScreenAccessor at = (ContainerScreenAccessor)screen;
		for (Slot slot : screen.getMenu().slots) {
			if (slot.isActive() && pullable(slot)) {
				graphics.outline(at.jaymospowers$leftPos() + slot.x - 1, at.jaymospowers$topPos() + slot.y - 1, 18, 18, UiStyle.GOLD);
			}
		}
		if (Util.getMillis() - turnedOn < EXPLAIN_MS && !pick.isHovered()) {
			// Andy: "use this shorter descp", the hover text
			UiStyle.tip(graphics, Minecraft.getInstance().font, Component.translatable("button.jaymospowers.pull_apart.hover"),
				pick.getX() + Pick.SIZE, pick.getY() + Pick.SIZE + 4);
		}
	}

	/**
	 * The iron pick: a vanilla-grey square (the inventory keeps vanilla's look) smaller than a slot, with the pickaxe drawn
	 * at half size in it, its rim gold while on.
	 */
	static final class Pick extends AbstractButton {
		static final int SIZE = 10;
		/**
		 * Under the crafting result slot (154, 28 to 170, 44 from the inventory's corner), centred on it; the recipe book's
		 * button is further left (104 to 124) and lower (61 on).
		 */
		static final int RIGHT = 157, DOWN = 47;
		private static final int BORDER = 0xFF373737, FACE = 0xFF8B8B8B, FACE_HOVER = 0xFFC6C6C6;
		private static final ItemStack ICON = new ItemStack(Items.IRON_PICKAXE);

		private final AbstractContainerScreen<?> screen;
		private boolean shownOn;

		Pick(AbstractContainerScreen<?> screen) {
			super(0, 0, SIZE, SIZE, Component.translatable("button.jaymospowers.pull_apart"));
			this.screen = screen;
			tooltip();
		}

		static int x(ContainerScreenAccessor at) {
			return at.jaymospowers$leftPos() + RIGHT;
		}

		static int y(ContainerScreenAccessor at) {
			return at.jaymospowers$topPos() + DOWN;
		}

		void place() {
			ContainerScreenAccessor at = (ContainerScreenAccessor)this.screen;
			setPosition(x(at), y(at));
			if (this.shownOn != on) {
				tooltip();
			}
		}

		private void tooltip() {
			this.shownOn = on;
			setTooltip(UiStyle.tipOf(Component.translatable(on ? "button.jaymospowers.pull_apart.hover_on" : "button.jaymospowers.pull_apart.hover")));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			on = !on;
			modeScreen = this.screen;
			if (on) {
				turnedOn = Util.getMillis();
			}
			tooltip();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			int x = getX();
			int y = getY();
			graphics.fill(x, y, x + SIZE, y + SIZE, on ? UiStyle.GOLD : BORDER);
			graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, isHoveredOrFocused() ? FACE_HOVER : FACE);
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + 1, y + 1);
			graphics.pose().scale(0.5F, 0.5F);
			graphics.item(ICON, 0, 0);
			graphics.pose().popMatrix();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}

package com.jaymospowers.client.wounds;

import com.jaymospowers.client.mixin.ContainerScreenAccessor;
import com.jaymospowers.core.wounds.Treatment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * (0.15.18) The body button: a little figure in the corner of the inventory's player box, each part coloured by how
 * whole it is (as the HUD silhouette). Clicking it opens your own {@link BodyScreen}, to look over your wounds (and treat
 * them, with medicine in hand). Escape there comes back to the inventory.
 */
public final class BodyButton extends AbstractButton {
	/** The button's size, in GUI pixels. */
	static final int W = 10, H = 15;
	private static final int BORDER = 0xFF373737;
	private static final int FACE = 0xFF8B8B8B;
	private static final int FACE_HOVER = 0xFFC6C6C6;
	/** The figure's parts in plan order (head, body, left arm, right arm, left leg, right leg, left foot, right foot): x, y, w, h in pixels. */
	private static final int[][] FIGURE = {
		{1, 0, 4, 3}, {1, 3, 4, 4}, {0, 3, 1, 4}, {5, 3, 1, 4}, {1, 7, 2, 3}, {3, 7, 2, 3}, {1, 10, 2, 1}, {3, 10, 2, 1},
	};

	private final AbstractContainerScreen<?> screen;

	private BodyButton(AbstractContainerScreen<?> screen) {
		super(0, 0, W, H, Component.translatable("button.jaymospowers.wounds.body"));
		this.screen = screen;
		setTooltip(Tooltip.create(Component.translatable("button.jaymospowers.wounds.body")));
	}

	static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
				BodyButton button = new BodyButton((AbstractContainerScreen<?>)screen);
				button.place();
				Screens.getWidgets(screen).add(button);
				// the recipe book and the creative tabs move things about: follow them every frame
				ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> button.place());
			}
		});
	}

	/** In the player box's lower right corner: beside the feet, clear of the off-hand slot. */
	void place() {
		ContainerScreenAccessor at = (ContainerScreenAccessor)this.screen;
		int x = at.jaymospowers$leftPos();
		int y = at.jaymospowers$topPos();
		if (this.screen instanceof CreativeModeInventoryScreen creative) {
			// the creative inventory tab's player box: x 73-105, y 6-49
			this.visible = creative.isInventoryOpen();
			x += 105 - W - 1;
			y += 49 - H - 1;
		} else {
			// the survival inventory's player box: x 26-75, y 8-78
			this.visible = true;
			x += 75 - W - 1;
			y += 78 - H - 1;
		}
		setPosition(x, y);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (ClientPlayNetworking.canSend(Treatment.LookBody.TYPE)) {
			ClientPlayNetworking.send(new Treatment.LookBody());
		}
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = getX();
		int y = getY();
		graphics.fill(x, y, x + W, y + H, BORDER);
		graphics.fill(x + 1, y + 1, x + W - 1, y + H - 1, isHoveredOrFocused() ? FACE_HOVER : FACE);
		int fx = x + 2;
		int fy = y + 2;
		for (int i = 0; i < FIGURE.length; i++) {
			int[] box = FIGURE[i];
			graphics.fill(fx + box[0], fy + box[1], fx + box[0] + box[2], fy + box[1] + box[3], 0xFF000000 | BodyScreen.colour(WoundsHud.part(i)));
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}

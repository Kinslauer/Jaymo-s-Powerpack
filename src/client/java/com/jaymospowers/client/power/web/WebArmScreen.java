package com.jaymospowers.client.power.web;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.client.mixin.ContainerScreenAccessor;
import com.jaymospowers.client.ui.FlatButton;
import com.jaymospowers.power.web.WebArmSwap;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteBody;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The symbiote arm's slots in a screen (the Arm, {@link WebSymbioteBody#ARM}: three slots): the arm on top, your pack
 * under it (the three rows of it, then the hotbar). Click one thing, then another, and they swap (a pack slot with an arm
 * slot: that is how things go in and out); Shift-click an arm slot sends it to the pack, Shift-click a pack slot sends
 * it into the first free arm slot. The server does the moving ({@link WebArmSwap}). Opened by a small button in the
 * inventory screen once the arm is unlocked; Escape goes back to the inventory.
 */
final class WebArmScreen extends Screen {
	private static final int CELL = 18, PAD = 12, ACCENT = 0xFF8A5AB0, SELECT = 0xFFD86CFF;
	private static final int PANEL_W = 9 * CELL + PAD * 2;
	/** The arm's row, the pack's label and rows under the header, and the help line under them. */
	private static final int ARM_ROW = UiStyle.HEADER + 4, PACK_LABEL = ARM_ROW + CELL + 8, PACK_ROWS = PACK_LABEL + 12, HOTBAR_GAP = 4;
	private static final int PANEL_H = PACK_ROWS + 4 * CELL + HOTBAR_GAP + 26;
	/** The inventory button: size, and where it sits in the player box. */
	private static final int BUTTON_W = 24, BUTTON_H = 12;

	private final Screen parent;
	/** What is picked up: an arm slot ({@code arm}) or a pack slot ({@code inv}), or -1 for neither. */
	private int pickedArm = -1, pickedInv = -1;

	private WebArmScreen(Screen parent) {
		super(Component.translatable("screen.jaymospowers.web_arm"));
		this.parent = parent;
	}

	/** Puts the Arm button in the inventory screen (from the client's setup). */
	static void installButton() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				FlatButton button = FlatButton.builder(Component.translatable("gui.jaymospowers.web_arm.button"), b -> minecraft.gui.setScreen(new WebArmScreen(screen)))
					.bounds(0, 0, BUTTON_W, BUTTON_H).accent(ACCENT).build();
				Screens.getWidgets(screen).add(button);
				ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
					LocalPlayer player = minecraft.player;
					button.visible = player != null && WebSymbioteBody.armUnlocked(player);
					ContainerScreenAccessor at = (ContainerScreenAccessor)s;
					// the player box's lower left corner of the survival inventory (the body button has the lower right)
					button.setPosition(at.jaymospowers$leftPos() + 27, at.jaymospowers$topPos() + 78 - BUTTON_H - 1);
				});
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		LocalPlayer player = minecraft.player;
		if (player == null || !WebSymbiote.isBonded(player) || !player.isAlive() || !WebSymbioteBody.armUnlocked(player)) {
			onClose();
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private int left() {
		return (width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(6, (height - PANEL_H) / 2);
	}

	private int armX(int slot) {
		return left() + (PANEL_W - WebSymbioteBody.ARM_SLOTS * CELL - (WebSymbioteBody.ARM_SLOTS - 1) * 4) / 2 + slot * (CELL + 4);
	}

	private int armY() {
		return top() + ARM_ROW;
	}

	/** Pack slot {@code i} in the player's inventory numbering (0-8 the hotbar, 9-35 the rows above it): its column and its row on screen. */
	private int invX(int i) {
		return left() + PAD + i % 9 * CELL;
	}

	private int invY(int i) {
		return top() + PACK_ROWS + (i < 9 ? 3 * CELL + HOTBAR_GAP : (i - 9) / 9 * CELL);
	}

	private int armAt(double mx, double my) {
		for (int i = 0; i < WebSymbioteBody.ARM_SLOTS; i++) {
			if (UiStyle.over((int)mx, (int)my, armX(i), armY(), CELL, CELL)) {
				return i;
			}
		}
		return -1;
	}

	private int invAt(double mx, double my) {
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			if (UiStyle.over((int)mx, (int)my, invX(i), invY(i), CELL, CELL)) {
				return i;
			}
		}
		return -1;
	}

	private void send(int inv, int arm) {
		if (ClientPlayNetworking.canSend(WebArmSwap.Swap.TYPE)) {
			ClientPlayNetworking.send(new WebArmSwap.Swap(inv, arm));
		}
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		LocalPlayer player = minecraft.player;
		if (player != null) {
			int arm = armAt(event.x(), event.y());
			int inv = invAt(event.x(), event.y());
			WebSymbioteBody.Arm held = WebSymbioteBody.arm(player);
			if (arm >= 0) {
				if (event.hasShiftDown()) {
					send(-1, arm);
					clear();
				} else if (pickedInv >= 0) {
					send(pickedInv, arm);
					clear();
				} else {
					pickedArm = arm;
				}
				return true;
			}
			if (inv >= 0) {
				if (event.hasShiftDown() && !player.getInventory().getItem(inv).isEmpty()) {
					for (int i = 0; i < WebSymbioteBody.ARM_SLOTS; i++) {
						if (held.get(i).isEmpty()) {
							send(inv, i);
							break;
						}
					}
					clear();
				} else if (pickedArm >= 0) {
					send(inv, pickedArm);
					clear();
				} else {
					pickedInv = inv;
					pickedArm = -1;
				}
				return true;
			}
			clear();
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void clear() {
		pickedArm = -1;
		pickedInv = -1;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		int x = left();
		int y = top();
		UiStyle.panel(graphics, x, y, PANEL_W, PANEL_H, ACCENT);
		UiStyle.header(graphics, font, x, y, PANEL_W, null, title, Component.translatable("gui.jaymospowers.web_arm.subtitle"), ACCENT, 0);
		WebSymbioteBody.Arm arm = WebSymbioteBody.arm(player);
		ItemStack hover = ItemStack.EMPTY;
		for (int i = 0; i < WebSymbioteBody.ARM_SLOTS; i++) {
			ItemStack stack = arm.get(i);
			cell(graphics, armX(i), armY(), stack, pickedArm == i, i == arm.selected());
			if (UiStyle.over(mouseX, mouseY, armX(i), armY(), CELL, CELL)) {
				hover = stack;
			}
		}
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.web_arm.inventory"), x + PAD, y + PACK_LABEL, PANEL_W - PAD * 2, UiStyle.DIM);
		Inventory inventory = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			cell(graphics, invX(i), invY(i), stack, pickedInv == i, false);
			if (UiStyle.over(mouseX, mouseY, invX(i), invY(i), CELL, CELL)) {
				hover = stack;
			}
		}
		UiStyle.wrapped(graphics, font, Component.translatable("gui.jaymospowers.web_arm.help"), x + PAD, y + PANEL_H - 24, PANEL_W - PAD * 2 - 6, 2, 9, UiStyle.HINT, false);
		UiStyle.lineRight(graphics, font, Component.translatable("gui.jaymospowers.web_arm.close"), x + PANEL_W - 8, y + PANEL_H - 12, 70, UiStyle.HINT);
		if (!hover.isEmpty()) {
			graphics.setTooltipForNextFrame(font, hover, mouseX, mouseY);
		}
	}

	/** One slot: a well, the item in it, a bright rim when picked up and a dim one when it is the arm's current pick. */
	private void cell(GuiGraphicsExtractor graphics, int x, int y, ItemStack stack, boolean picked, boolean current) {
		UiStyle.well(graphics, x, y, CELL, CELL);
		if (!stack.isEmpty()) {
			graphics.item(stack, x + 1, y + 1);
			graphics.itemDecorations(font, stack, x + 1, y + 1, null);
		}
		if (picked) {
			graphics.outline(x, y, CELL, CELL, SELECT);
		} else if (current) {
			graphics.outline(x, y, CELL, CELL, ACCENT);
		}
	}
}

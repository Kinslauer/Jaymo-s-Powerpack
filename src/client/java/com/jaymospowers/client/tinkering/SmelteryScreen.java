package com.jaymospowers.client.tinkering;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.smeltery.Molten;
import com.jaymospowers.tinkering.smeltery.SmelteryEntity;
import com.jaymospowers.tinkering.smeltery.SmelteryMenu;
import com.jaymospowers.tinkering.smeltery.Tank;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * The smeltery, on Tinkers' screen (MIT): melting slots down the left with their heat, the molten pool in the middle
 * (hover for amounts, click to pour that one first), the lava bucket and its gauge on the right. (0.10.127) Drawn like
 * the H screen: dark panel, bronze stripe, sunken slots and pool.
 */
public class SmelteryScreen extends AbstractContainerScreen<SmelteryMenu> {
	/** The pool's box and the fuel gauge's, on the texture. */
	private static final int POOL_X = 8, POOL_Y = 16, POOL_W = 106, POOL_H = 106;
	private static final int GAUGE_X = 152, GAUGE_Y = 32, GAUGE_W = 16, GAUGE_H = 90;
	private static final int PANEL_X = SmelteryMenu.SLOT_X - 6, PANEL_W = 3 * 18 + 12;

	public SmelteryScreen(SmelteryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 220);
		this.inventoryLabelY = 127;
	}

	private @Nullable SmelteryEntity smeltery() {
		return menu.smeltery(minecraft == null ? null : minecraft.level);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		TinkerUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
		int slots = menu.slotsInUse();
		int rows = (slots + SmelteryMenu.COLUMNS - 1) / SmelteryMenu.COLUMNS;
		if (slots > 0) {
			// the melting slots, on a panel of their own to the left
			TinkerUi.panel(graphics, leftPos + PANEL_X, topPos + SmelteryMenu.SLOT_Y - 6, PANEL_W, rows * 18 + 12);
		}
		TinkerUi.slots(graphics, menu, leftPos, topPos, -1);
		TinkerUi.well(graphics, leftPos + POOL_X - 1, topPos + POOL_Y - 1, POOL_W + 2, POOL_H + 2);
		TinkerUi.well(graphics, leftPos + GAUGE_X - 1, topPos + GAUGE_Y - 1, GAUGE_W + 2, GAUGE_H + 2);
		// lava goes in above, the empty bucket comes out below
		TinkerUi.arrowDown(graphics, leftPos + SmelteryMenu.FUEL_X + 8, topPos + SmelteryMenu.FUEL_Y + 20, SmelteryMenu.BUCKET_Y - SmelteryMenu.FUEL_Y - 22, TinkerUi.DIM);
		for (int i = 0; i < slots; i++) {
			int x = leftPos + SmelteryMenu.SLOT_X + i % SmelteryMenu.COLUMNS * 18 - 1;
			int y = topPos + SmelteryMenu.SLOT_Y + i / SmelteryMenu.COLUMNS * 18 - 1;
			int progress = menu.progress(i);
			if (progress < 0) {
				graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x60FF0000);
			} else if (progress > 0) {
				int h = Math.max(1, progress * 16 / 100);
				graphics.fill(x + 1, y + 17 - h, x + 17, y + 17, 0x70FF7A00);
			}
		}
		SmelteryEntity smeltery = smeltery();
		if (smeltery != null) {
			Tank tank = smeltery.tank();
			int capacity = Math.max(tank.capacity(), tank.total());
			int bottom = topPos + POOL_Y + POOL_H;
			for (Tank.Layer layer : tank.layers()) {
				int h = capacity <= 0 ? 0 : Math.max(1, Math.round(layer.amount * (float) POOL_H / capacity));
				h = Math.min(h, bottom - topPos - POOL_Y);
				tile(graphics, layer.molten, leftPos + POOL_X, bottom - h, POOL_W, h);
				bottom -= h;
			}
		}
		int fuel = menu.fuel();
		if (fuel > 0) {
			int h = Math.max(1, fuel * GAUGE_H / SmelteryEntity.FUEL_MAX);
			tileTexture(graphics, JaymosPowers.id("textures/gui/tinkering/molten/lava.png"), leftPos + GAUGE_X, topPos + GAUGE_Y + GAUGE_H - h, GAUGE_W, h);
		}
		if (smeltery != null && !smeltery.formed()) {
			graphics.fill(leftPos + POOL_X, topPos + POOL_Y, leftPos + POOL_X + POOL_W, topPos + POOL_Y + POOL_H, 0xA0000000);
			int y = topPos + POOL_Y + POOL_H / 2 - 10;
			for (var line : font.split(Component.translatable("gui.jaymospowers.smeltery.unformed"), POOL_W - 8)) {
				graphics.text(font, line, leftPos + POOL_X + 4, y, 0xFFFF8080, true);
				y += 10;
			}
		}
	}

	private static void tile(GuiGraphicsExtractor graphics, Molten molten, int x, int y, int w, int h) {
		tileTexture(graphics, JaymosPowers.id("textures/gui/tinkering/molten/" + molten.id + ".png"), x, y, w, h);
	}

	/** Fills a box with a 16x16 texture, tiled from the bottom. */
	private static void tileTexture(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int w, int h) {
		for (int ty = y + h; ty > y; ty -= 16) {
			int th = Math.min(16, ty - y);
			for (int tx = x; tx < x + w; tx += 16) {
				int tw = Math.min(16, x + w - tx);
				graphics.blit(RenderPipelines.GUI_TEXTURED, texture, tx, ty - th, 0.0F, 16 - th, tw, th, 16, 16);
			}
		}
	}

	/** The pool layer under the mouse, by index, or -1. */
	private int layerAt(double mouseX, double mouseY) {
		SmelteryEntity smeltery = smeltery();
		if (smeltery == null || mouseX < leftPos + POOL_X || mouseX >= leftPos + POOL_X + POOL_W
			|| mouseY < topPos + POOL_Y || mouseY >= topPos + POOL_Y + POOL_H) {
			return -1;
		}
		Tank tank = smeltery.tank();
		int capacity = Math.max(tank.capacity(), tank.total());
		int bottom = topPos + POOL_Y + POOL_H;
		List<Tank.Layer> layers = tank.layers();
		for (int i = 0; i < layers.size(); i++) {
			int h = capacity <= 0 ? 0 : Math.max(1, Math.round(layers.get(i).amount * (float) POOL_H / capacity));
			if (mouseY >= bottom - h && mouseY < bottom) {
				return i;
			}
			bottom -= h;
		}
		return -1;
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		SmelteryEntity smeltery = smeltery();
		int index = layerAt(mouseX, mouseY);
		if (smeltery != null && index >= 0) {
			Tank.Layer layer = smeltery.tank().layers().get(index);
			List<Component> lines = new java.util.ArrayList<>(List.of(layer.molten.displayName(),
				Molten.amount(layer.amount, layer.molten.metal()).copy().withStyle(ChatFormatting.GRAY)));
			if (index > 0) {
				lines.add(Component.translatable("gui.jaymospowers.smeltery.to_bottom").withStyle(ChatFormatting.DARK_GRAY));
			}
			graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		} else if (smeltery != null && mouseX >= leftPos + POOL_X && mouseX < leftPos + POOL_X + POOL_W && mouseY >= topPos + POOL_Y
			&& mouseY < topPos + POOL_Y + POOL_H) {
			graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.jaymospowers.smeltery.capacity",
				Molten.amount(smeltery.tank().total(), true), Molten.amount(smeltery.tank().capacity(), true))), mouseX, mouseY);
		} else if (mouseX >= leftPos + GAUGE_X && mouseX < leftPos + GAUGE_X + GAUGE_W && mouseY >= topPos + GAUGE_Y
			&& mouseY < topPos + GAUGE_Y + GAUGE_H) {
			graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.jaymospowers.smeltery.fuel", menu.fuel(),
				SmelteryEntity.FUEL_MAX), Component.translatable("gui.jaymospowers.smeltery.fuel_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int index = layerAt(event.x(), event.y());
		if (index > 0 && minecraft != null && minecraft.gameMode != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TinkerUi.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		boolean inPanel = mouseX >= left + PANEL_X && mouseX < left && mouseY >= top && mouseY < top + imageHeight;
		return !inPanel && super.hasClickedOutside(mouseX, mouseY, left, top);
	}
}

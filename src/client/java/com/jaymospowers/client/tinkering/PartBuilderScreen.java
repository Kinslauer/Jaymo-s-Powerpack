package com.jaymospowers.client.tinkering;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.PartBuilderMenu;
import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.ToolPartItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The Part Builder, on Tinkers' screen (MIT): pattern and material left, the parts to pick in the middle, info to the right. */
public class PartBuilderScreen extends AbstractContainerScreen<PartBuilderMenu> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/gui/tinkering/part_builder.png");
	private static final int GRID_X = 51, GRID_Y = 15, CELL = 18, COLUMNS = 4;

	public PartBuilderScreen(PartBuilderMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 184);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
		TinkerMaterial material = TinkerMaterial.of(menu.material());
		PartType[] parts = PartType.values();
		for (int i = 0; i < parts.length; i++) {
			int x = leftPos + GRID_X + i % COLUMNS * CELL, y = topPos + GRID_Y + i / COLUMNS * CELL;
			boolean hover = mouseX >= x && mouseY >= y && mouseX < x + CELL && mouseY < y + CELL;
			boolean chosen = parts[i] == menu.selectedPart();
			graphics.fill(x, y, x + CELL, y + CELL, chosen ? 0xFF6E8B3D : hover ? 0xFF9A9A9A : 0xFF7A7A7A);
			graphics.fill(x, y, x + CELL, y + 1, 0xFFC6C6C6);
			graphics.fill(x, y, x + 1, y + CELL, 0xFFC6C6C6);
			graphics.fill(x, y + CELL - 1, x + CELL, y + CELL, 0xFF373737);
			graphics.fill(x + CELL - 1, y, x + CELL, y + CELL, 0xFF373737);
			if (material != null && material.makes(parts[i].kind)) {
				graphics.item(ToolPartItem.of(parts[i], material, 1), x + 1, y + 1);
			} else {
				graphics.blit(RenderPipelines.GUI_TEXTURED, JaymosPowers.id("textures/gui/tinkering/icon/" + parts[i].id + ".png"),
					x + 1, y + 1, 0.0F, 0.0F, 16, 16, 16, 16);
				if (material != null) {
					graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x80000000);
				}
			}
		}
		SidePanel.draw(graphics, font, leftPos - SidePanel.WIDTH - 2, topPos, title(material), info(material));
	}

	private Component title(TinkerMaterial material) {
		PartType part = menu.selectedPart();
		if (part != null && material != null) {
			return Component.translatable("jaymospowers.tinkering.named", material.displayName(), Component.translatable("item.jaymospowers." + part.id));
		}
		return material != null ? material.displayName() : Component.translatable("container.jaymospowers.part_builder");
	}

	private List<Component> info(TinkerMaterial material) {
		List<Component> lines = new ArrayList<>();
		ItemStack stack = menu.material();
		PartType part = menu.selectedPart();
		if (!menu.hasPattern()) {
			lines.add(Component.translatable("gui.jaymospowers.part_builder.pattern"));
		}
		if (stack.isEmpty()) {
			lines.add(Component.translatable("gui.jaymospowers.part_builder.material"));
		} else if (material == null) {
			lines.add(Component.translatable("gui.jaymospowers.part_builder.unknown"));
		} else {
			lines.add(Component.translatable("gui.jaymospowers.part_builder.value", stack.getHoverName(), TinkerMaterial.units(stack)));
		}
		if (part == null) {
			lines.add(Component.translatable("gui.jaymospowers.part_builder.pick"));
		} else if (material != null) {
			if (material.cast) {
				lines.add(Component.translatable("gui.jaymospowers.part_builder.cast", material.displayName()));
			} else if (!material.makes(part.kind)) {
				lines.add(Component.translatable("gui.jaymospowers.part_builder.cannot", material.displayName(),
					Component.translatable("item.jaymospowers." + part.id)));
			} else {
				int need = PartBuilderMenu.itemsNeeded(part, stack);
				lines.add(Component.translatable("gui.jaymospowers.part_builder.need", need, stack.getHoverName(), stack.getCount()));
				lines.addAll(ToolPartItem.lines(part, material));
			}
		} else {
			lines.add(Component.translatable("tooltip.jaymospowers.tinkering.cost", part.cost));
		}
		return lines;
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		int index = cellAt(mouseX, mouseY);
		if (index >= 0) {
			PartType part = PartType.values()[index];
			graphics.setTooltipForNextFrame(font, Component.translatable("item.jaymospowers." + part.id), mouseX, mouseY);
		}
	}

	private int cellAt(double mouseX, double mouseY) {
		int cx = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL), cy = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
		if (cx < 0 || cy < 0 || cx >= COLUMNS || cy >= COLUMNS) {
			return -1;
		}
		int index = cy * COLUMNS + cx;
		return index < PartType.values().length ? index : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int index = cellAt(event.x(), event.y());
		if (index >= 0 && menu.clickMenuButton(minecraft.player, index)) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}
}

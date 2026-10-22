package com.jaymospowers.client.tinkering;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.PartBuilderMenu;
import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.jaymospowers.tinkering.WeaponParts;
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

/**
 * The Part Builder, laid out like Tinkers' screen (MIT): pattern and material left, the parts to pick in the middle, info
 * beside it. (0.10.127) Drawn like the H screen: dark panel, bronze stripe, sunken slots, flat part tiles. (0.11.77) The
 * arrows page through to the weapon heads ({@link WeaponParts}).
 */
public class PartBuilderScreen extends AbstractContainerScreen<PartBuilderMenu> {
	private static final int GRID_X = 51, GRID_Y = 15, CELL = 18, COLUMNS = 4, CELLS = 16;
	/** (0.11.77) The page arrows (tool parts, then weapon heads), above the pattern and material slots. */
	private static final int PAGE_X = 8, PAGE_Y = 20, PAGE_W = 16, PAGE_H = 14, PAGE_STEP = 21;

	public PartBuilderScreen(PartBuilderMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 184);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	/** Page 0: tool parts; page 1 on: weapon heads, 16 a page. */
	private int page;

	/** The button ids on this page, in grid order. */
	private int[] buttons() {
		if (page == 0) {
			return java.util.stream.IntStream.range(0, PartType.values().length).toArray();
		}
		int count = WeaponParts.loaded().size(), from = (page - 1) * CELLS;
		return java.util.stream.IntStream.range(from, Math.min(count, from + CELLS)).map(i -> PartBuilderMenu.WEAPON_BUTTONS + i).toArray();
	}

	private int pages() {
		return 1 + (WeaponParts.loaded().size() + CELLS - 1) / CELLS;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		TinkerUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
		TableUi.draw(graphics, font, menu.extras(), leftPos, topPos);
		TinkerUi.slots(graphics, menu, leftPos, topPos, PartBuilderMenu.RESULT);
		int[] buttons = buttons();
		int rows = Math.max(1, (buttons.length + COLUMNS - 1) / COLUMNS);
		TinkerUi.well(graphics, leftPos + GRID_X - 1, topPos + GRID_Y - 1, COLUMNS * CELL + 2, rows * CELL + 2);
		TinkerMaterial material = TinkerMaterial.of(menu.material());
		for (int i = 0; i < buttons.length; i++) {
			int x = leftPos + GRID_X + i % COLUMNS * CELL, y = topPos + GRID_Y + i / COLUMNS * CELL;
			boolean hover = mouseX >= x && mouseY >= y && mouseX < x + CELL && mouseY < y + CELL;
			boolean chosen = buttons[i] == selectedButton();
			graphics.fill(x, y, x + CELL, y + CELL, hover ? TinkerUi.ROW_HOVER : chosen ? TinkerUi.ROW : (i + i / COLUMNS) % 2 == 0 ? 0x18FFFFFF : 0x0CFFFFFF);
			if (chosen) {
				graphics.fill(x, y + CELL - 1, x + CELL, y + CELL, TinkerUi.ACCENT);
			}
			if (buttons[i] >= PartBuilderMenu.WEAPON_BUTTONS) {
				WeaponParts.Kind kind = WeaponParts.loaded().get(buttons[i] - PartBuilderMenu.WEAPON_BUTTONS);
				graphics.item(WeaponPartItem.of(kind, WeaponParts.Metal.STONE, 1), x + 1, y + 1);
				if (material != null && !PartBuilderMenu.shapesWeapons(material)) {
					graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x80000000);
				}
				continue;
			}
			PartType part = PartType.values()[buttons[i]];
			if (material != null && material.makes(part.kind)) {
				graphics.item(ToolPartItem.of(part, material, 1), x + 1, y + 1);
			} else {
				graphics.blit(RenderPipelines.GUI_TEXTURED, JaymosPowers.id("textures/gui/tinkering/icon/" + part.id + ".png"),
					x + 1, y + 1, 0.0F, 0.0F, 16, 16, 16, 16);
				if (material != null) {
					graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x80000000);
				}
			}
		}
		// page arrows above the pattern and material slots
		for (int side = 0; side < 2; side++) {
			int x = leftPos + PAGE_X + side * PAGE_STEP, y = topPos + PAGE_Y;
			boolean hover = mouseX >= x && mouseY >= y && mouseX < x + PAGE_W && mouseY < y + PAGE_H;
			graphics.fill(x, y, x + PAGE_W, y + PAGE_H, hover ? TinkerUi.ROW_HOVER : TinkerUi.ROW);
			graphics.centeredText(font, Component.literal(side == 0 ? "<" : ">"), x + PAGE_W / 2, y + 3, TinkerUi.ACCENT);
		}
		SidePanel.draw(graphics, font, TableUi.infoX(menu.extras(), leftPos, imageWidth), topPos, title(material), info(material));
	}

	private int selectedButton() {
		PartType part = menu.selectedPart();
		if (part != null) {
			return part.ordinal();
		}
		WeaponParts.Kind kind = menu.selectedWeapon();
		return kind == null ? -1 : PartBuilderMenu.WEAPON_BUTTONS + WeaponParts.loaded().indexOf(kind);
	}

	private Component pageName() {
		return page == 0 ? Component.translatable("gui.jaymospowers.part_builder.tools")
			: Component.translatable("gui.jaymospowers.part_builder.weapons", page, pages() - 1);
	}

	private Component title(TinkerMaterial material) {
		WeaponParts.Kind weapon = menu.selectedWeapon();
		if (weapon != null) {
			return Component.translatable("item.jaymospowers." + weapon.part());
		}
		PartType part = menu.selectedPart();
		if (part != null && material != null) {
			return Component.translatable("jaymospowers.tinkering.named", material.displayName(), Component.translatable("item.jaymospowers." + part.id));
		}
		return material != null ? material.displayName() : pageName();
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
		WeaponParts.Kind weapon = menu.selectedWeapon();
		if (weapon != null) {
			if (material != null && !PartBuilderMenu.shapesWeapons(material)) {
				lines.add(Component.translatable("gui.jaymospowers.part_builder.stone_only"));
			} else if (material != null) {
				lines.add(Component.translatable("gui.jaymospowers.part_builder.need", PartBuilderMenu.itemsNeeded(weapon.stoneCost(), stack),
					stack.getHoverName(), stack.getCount()));
			} else {
				lines.add(Component.translatable("tooltip.jaymospowers.tinkering.cost", weapon.stoneCost()));
			}
			ItemStack made = WeaponPartItem.weapon(WeaponPartItem.of(weapon, WeaponParts.Metal.STONE, 1));
			lines.add(made != null ? Component.translatable("tooltip.jaymospowers.tinkering.weapon_part.makes", made.getHoverName())
				: Component.translatable("tooltip.jaymospowers.tinkering.weapon_part.cast_only"));
			return lines;
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
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TinkerUi.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		int index = cellAt(mouseX, mouseY);
		if (index >= 0) {
			int button = buttons()[index];
			Component name = button >= PartBuilderMenu.WEAPON_BUTTONS
				? Component.translatable("item.jaymospowers." + WeaponParts.loaded().get(button - PartBuilderMenu.WEAPON_BUTTONS).part())
				: Component.translatable("item.jaymospowers." + PartType.values()[button].id);
			graphics.setTooltipForNextFrame(font, name, mouseX, mouseY);
		} else if (pageArrowAt(mouseX, mouseY) != 0) {
			graphics.setTooltipForNextFrame(font, pageName(), mouseX, mouseY);
		}
		TableUi.tooltip(graphics, font, menu.extras(), leftPos, topPos, mouseX, mouseY);
	}

	private int cellAt(double mouseX, double mouseY) {
		int cx = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL), cy = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
		if (cx < 0 || cy < 0 || cx >= COLUMNS || cy >= COLUMNS) {
			return -1;
		}
		int index = cy * COLUMNS + cx;
		return index < buttons().length ? index : -1;
	}

	/** -1 or 1 for the page arrow under the mouse, else 0. */
	private int pageArrowAt(double mouseX, double mouseY) {
		for (int side = 0; side < 2; side++) {
			int x = leftPos + PAGE_X + side * PAGE_STEP, y = topPos + PAGE_Y;
			if (mouseX >= x && mouseY >= y && mouseX < x + PAGE_W && mouseY < y + PAGE_H) {
				return side == 0 ? -1 : 1;
			}
		}
		return 0;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (TableUi.click(minecraft, menu.containerId, menu.extras(), leftPos, topPos, event.x(), event.y())) {
			return true;
		}
		int arrow = pageArrowAt(event.x(), event.y());
		if (arrow != 0) {
			page = Math.floorMod(page + arrow, pages());
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		int index = cellAt(event.x(), event.y());
		if (index >= 0) {
			int button = buttons()[index];
			if (menu.clickMenuButton(minecraft.player, button)) {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}

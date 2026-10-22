package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.ToolStationMenu;
import com.jaymospowers.tinkering.ToolType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Tool Station, laid out like Tinkers' screen (MIT): parts around the big result slot; recipes or the result's stats
 * beside it. (0.10.127) Drawn like the H screen: dark panel, bronze stripe, sunken slots. (Tool Forge) The same screen draws
 * the Tool Forge's five slots; both have the side inventory and tabs ({@link TableUi}).
 */
public class ToolStationScreen extends AbstractContainerScreen<ToolStationMenu> {
	/** (0.8.125) Andy: an empty station shows each tool's parts in turn, this long apiece. */
	static final long GHOST_CYCLE_MS = 1500L;
	/** Laid over a ghost part so it reads as a faint outline: the slot's own dark, mostly opaque. */
	static final int GHOST_FADE = 0xA80C0C12;
	/** The tool whose outlines are showing (named in the recipe list), or null. */
	private @Nullable ToolType shown;

	public ToolStationScreen(ToolStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 184);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		TinkerUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
		TableUi.draw(graphics, font, menu.extras(), leftPos, topPos);
		TinkerUi.slots(graphics, menu, leftPos, topPos, menu.resultSlot());
		int infoX = TableUi.infoX(menu.extras(), leftPos, imageWidth);
		TinkerUi.arrowRight(graphics, leftPos + 80, topPos + 46, 22, TinkerUi.DIM);
		PartType[] ghosts = ghosts();
		for (int i = 0; i < ghosts.length; i++) {
			if (ghosts[i] != null) {
				int x = leftPos + menu.slotAt(i)[0], y = topPos + menu.slotAt(i)[1];
				graphics.fakeItem(ToolPartItem.of(ghosts[i], TinkerMaterial.ROCK_STONE, 1), x, y);
				graphics.fill(x, y, x + 16, y + 16, GHOST_FADE);
			}
		}
		ItemStack result = menu.getSlot(menu.resultSlot()).getItem();
		if (!result.isEmpty()) {
			List<Component> lines = new ArrayList<>();
			if (menu.status() != null) {
				lines.add(menu.status());
			}
			lines.addAll(result.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player,
				net.minecraft.world.item.TooltipFlag.Default.NORMAL).stream().skip(1).toList());
			SidePanel.draw(graphics, font, infoX, topPos, result.getHoverName(), lines);
		} else if (menu.status() != null) {
			// why a modifier won't go on (no slots, at its max, doesn't fit a weapon)
			SidePanel.draw(graphics, font, infoX, topPos,
				menu.status().copy().withStyle(net.minecraft.ChatFormatting.RED), List.of());
		} else {
			List<Component> lines = new ArrayList<>();
			boolean skipped = false;
			for (ToolType type : ToolType.values()) {
				if (type.parts.size() > menu.maxParts()) {
					skipped = true;
					continue;
				}
				MutableComponent parts = Component.empty();
				for (int i = 0; i < type.parts.size(); i++) {
					PartType part = type.parts.get(i).part();
					parts.append(i > 0 ? Component.literal(", ") : Component.empty()).append(Component.translatable("item.jaymospowers." + part.id + ".short"));
				}
				lines.add(Component.translatable("gui.jaymospowers.tool_station.recipe",
					Component.translatable("item.jaymospowers." + type.itemId()).withStyle(type == shown
						? new net.minecraft.ChatFormatting[] {net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.UNDERLINE}
						: new net.minecraft.ChatFormatting[] {net.minecraft.ChatFormatting.YELLOW}),
					parts.withStyle(net.minecraft.ChatFormatting.GRAY)));
			}
			if (skipped) {
				lines.add(Component.translatable("gui.jaymospowers.tool_station.forge_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			SidePanel.draw(graphics, font, infoX, topPos, Component.translatable("gui.jaymospowers.tool_station.recipes"), lines);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TinkerUi.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
	}

	/**
	 * (0.8.125) Andy: which part goes where, as faint outlines in the empty slots. A tool's parts go in order head (top
	 * right), handle (bottom left), binding or second head (middle), extra (top left): Tinkers' layout. With nothing in,
	 * the tools take turns; with some parts in, the tools those parts could make take turns, showing what's missing.
	 * Nothing with a tool or anything else in the station (repairs and modifiers have no layout).
	 */
	private PartType[] ghosts() {
		PartType[] ghosts = new PartType[menu.inputCount()];
		shown = null;
		List<PartType> placed = new ArrayList<>();
		for (int i = 0; i < menu.inputCount(); i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			if (!(stack.getItem() instanceof ToolPartItem part)) {
				return ghosts;
			}
			placed.add(part.part);
		}
		List<ToolType> fits = new ArrayList<>();
		for (ToolType type : ToolType.values()) {
			List<PartType> missing = missing(type, placed);
			if (missing != null && !missing.isEmpty() && missing.size() + placed.size() <= menu.inputCount() && type.parts.size() <= menu.maxParts()) {
				fits.add(type);
			}
		}
		if (fits.isEmpty()) {
			return ghosts;
		}
		ToolType type = fits.get((int) (System.currentTimeMillis() / GHOST_CYCLE_MS % fits.size()));
		shown = type;
		List<PartType> missing = missing(type, placed);
		// each missing part in its own place when that's free, the rest in whatever slots are left
		for (int i = 0; i < type.parts.size(); i++) {
			PartType part = type.parts.get(i).part();
			if (menu.getSlot(i).getItem().isEmpty() && missing.remove(part)) {
				ghosts[i] = part;
			}
		}
		for (int i = 0; i < ghosts.length && !missing.isEmpty(); i++) {
			if (ghosts[i] == null && menu.getSlot(i).getItem().isEmpty()) {
				ghosts[i] = missing.removeFirst();
			}
		}
		return ghosts;
	}

	/** The parts this tool still needs on top of these, or null if these aren't all its parts. */
	private static @Nullable List<PartType> missing(ToolType type, List<PartType> placed) {
		List<PartType> missing = new ArrayList<>(type.parts.stream().map(ToolType.Slot::part).toList());
		for (PartType part : placed) {
			if (!missing.remove(part)) {
				return null;
			}
		}
		return missing;
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		TableUi.tooltip(graphics, font, menu.extras(), leftPos, topPos, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return TableUi.click(minecraft, menu.containerId, menu.extras(), leftPos, topPos, event.x(), event.y()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		// the side inventory's slots lie left of the screen: clicks there aren't drops
		return !TableUi.inSide(menu.extras(), left, top, mouseX, mouseY) && super.hasClickedOutside(mouseX, mouseY, left, top);
	}
}

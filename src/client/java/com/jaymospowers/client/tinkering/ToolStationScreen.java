package com.jaymospowers.client.tinkering;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.ToolStationMenu;
import com.jaymospowers.tinkering.ToolType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** The Tool Station, on Tinkers' screen (MIT): parts around the big result slot; recipes or the result's stats to the right. */
public class ToolStationScreen extends AbstractContainerScreen<ToolStationMenu> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/gui/tinkering/tool_station.png");
	/** (0.8.125) Andy: an empty station shows each tool's parts in turn, this long apiece. */
	static final long GHOST_CYCLE_MS = 1500L;
	/** Laid over a ghost part so it reads as a faint outline: the slot's own grey, mostly opaque. */
	static final int GHOST_FADE = 0x908B8B8B;
	/** The tool whose outlines are showing (named in the recipe list), or null. */
	private @Nullable ToolType shown;

	public ToolStationScreen(ToolStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 184);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
		for (int[] at : ToolStationMenu.SLOT_AT) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + at[0] - 1, topPos + at[1] - 1, 0.0F, 238.0F, 18, 18, 256, 256);
		}
		PartType[] ghosts = ghosts();
		for (int i = 0; i < ghosts.length; i++) {
			if (ghosts[i] != null) {
				int x = leftPos + ToolStationMenu.SLOT_AT[i][0], y = topPos + ToolStationMenu.SLOT_AT[i][1];
				graphics.fakeItem(ToolPartItem.of(ghosts[i], TinkerMaterial.ROCK_STONE, 1), x, y);
				graphics.fill(x, y, x + 16, y + 16, GHOST_FADE);
			}
		}
		ItemStack result = menu.getSlot(ToolStationMenu.RESULT).getItem();
		if (!result.isEmpty()) {
			List<Component> lines = new ArrayList<>();
			if (menu.status() != null) {
				lines.add(menu.status());
			}
			lines.addAll(result.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player,
				net.minecraft.world.item.TooltipFlag.Default.NORMAL).stream().skip(1).toList());
			SidePanel.draw(graphics, font, leftPos - SidePanel.WIDTH - 2, topPos, result.getHoverName(), lines);
		} else if (menu.status() != null) {
			// why a modifier won't go on (no slots, at its max, doesn't fit a weapon)
			SidePanel.draw(graphics, font, leftPos - SidePanel.WIDTH - 2, topPos,
				menu.status().copy().withStyle(net.minecraft.ChatFormatting.RED), List.of());
		} else {
			List<Component> lines = new ArrayList<>();
			for (ToolType type : ToolType.values()) {
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
			SidePanel.draw(graphics, font, leftPos - SidePanel.WIDTH - 2, topPos, Component.translatable("gui.jaymospowers.tool_station.recipes"), lines);
		}
	}

	/**
	 * (0.8.125) Andy: which part goes where, as faint outlines in the empty slots. A tool's parts go in order head (top
	 * right), handle (bottom left), binding or second head (middle), extra (top left): Tinkers' layout. With nothing in,
	 * the tools take turns; with some parts in, the tools those parts could make take turns, showing what's missing.
	 * Nothing with a tool or anything else in the station (repairs and modifiers have no layout).
	 */
	private PartType[] ghosts() {
		PartType[] ghosts = new PartType[ToolStationMenu.INPUTS];
		shown = null;
		List<PartType> placed = new ArrayList<>();
		for (int i = 0; i < ToolStationMenu.INPUTS; i++) {
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
			if (missing != null && !missing.isEmpty() && missing.size() + placed.size() <= ToolStationMenu.INPUTS) {
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
}

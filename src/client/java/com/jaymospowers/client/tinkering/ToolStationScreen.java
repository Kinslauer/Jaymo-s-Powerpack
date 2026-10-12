package com.jaymospowers.client.tinkering;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.PartType;
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

/** The Tool Station, on Tinkers' screen (MIT): parts around the big result slot; recipes or the result's stats to the right. */
public class ToolStationScreen extends AbstractContainerScreen<ToolStationMenu> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/gui/tinkering/tool_station.png");

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
		ItemStack result = menu.getSlot(ToolStationMenu.RESULT).getItem();
		if (!result.isEmpty()) {
			List<Component> lines = new ArrayList<>();
			if (menu.status() != null) {
				lines.add(menu.status());
			}
			lines.addAll(result.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player,
				net.minecraft.world.item.TooltipFlag.Default.NORMAL).stream().skip(1).toList());
			SidePanel.draw(graphics, font, leftPos - SidePanel.WIDTH - 2, topPos, result.getHoverName(), lines);
		} else {
			List<Component> lines = new ArrayList<>();
			for (ToolType type : ToolType.values()) {
				MutableComponent parts = Component.empty();
				for (int i = 0; i < type.parts.size(); i++) {
					PartType part = type.parts.get(i).part();
					parts.append(i > 0 ? Component.literal(", ") : Component.empty()).append(Component.translatable("item.jaymospowers." + part.id + ".short"));
				}
				lines.add(Component.translatable("gui.jaymospowers.tool_station.recipe",
					Component.translatable("item.jaymospowers." + type.itemId()).withStyle(net.minecraft.ChatFormatting.YELLOW),
					parts.withStyle(net.minecraft.ChatFormatting.GRAY)));
			}
			SidePanel.draw(graphics, font, leftPos - SidePanel.WIDTH - 2, topPos, Component.translatable("gui.jaymospowers.tool_station.recipes"), lines);
		}
	}
}

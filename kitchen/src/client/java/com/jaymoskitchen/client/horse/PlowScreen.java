package com.jaymoskitchen.client.horse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.horse.PlowMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** The plow's three hoe slots, on NiftyCarts' plow screen (MIT, see LICENSE_niftycarts). */
public class PlowScreen extends AbstractContainerScreen<PlowMenu> {
	private static final Identifier TEXTURE = JaymosKitchen.id("textures/gui/container/plow.png");

	public PlowScreen(PlowMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int xo = (this.width - this.imageWidth) / 2;
		int yo = (this.height - this.imageHeight) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, xo, yo, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
	}
}

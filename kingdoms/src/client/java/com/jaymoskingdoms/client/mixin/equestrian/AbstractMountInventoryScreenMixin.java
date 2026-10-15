package com.jaymoskingdoms.client.mixin.equestrian;

import com.jaymoskingdoms.client.Ui;
import com.jaymoskingdoms.client.equestrian.EquestrianClient;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.5.34) A tamed horse's breed and stats, in the empty space of its inventory screen; (0.5.44) its animal, for the Tack
 * button. (0.7.44) Every mount's inventory drawn like the H screen: a dark panel with a gold stripe in place of vanilla's
 * grey texture, sunken wells for the slots and the animal, no chest-slot or saddle/armour frame sprites.
 */
@Mixin(AbstractMountInventoryScreen.class)
abstract class AbstractMountInventoryScreenMixin implements EquestrianClient.MountScreen {
	@Shadow
	@Final
	protected int inventoryColumns;
	@Shadow
	@Final
	protected LivingEntity mount;

	@Override
	public LivingEntity jaymoskingdoms$mount() {
		return this.mount;
	}

	@Redirect(method = "extractBackground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void jaymoskingdoms$panel(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h,
		int texW, int texH) {
		AbstractMountInventoryScreen<?> self = (AbstractMountInventoryScreen<?>)(Object)this;
		Ui.panel(graphics, x, y, w, h, Ui.GOLD);
		Ui.slots(graphics, self.getMenu().slots, x, y);
		// the animal's box
		Ui.well(graphics, x + 25, y + 17, 54, 54);
	}

	@Redirect(method = "extractBackground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"))
	private void jaymoskingdoms$noChestSprite(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int texW, int texH, int u, int v, int x, int y,
		int w, int h) {
		// (the chest slots have wells of their own)
	}

	@Redirect(method = "extractBackground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/inventory/AbstractMountInventoryScreen;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
	private void jaymoskingdoms$noSlotSprite(AbstractMountInventoryScreen<?> self, GuiGraphicsExtractor graphics, int x, int y) {
		// (the saddle and armour slots have wells of their own)
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void jaymoskingdoms$stats(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (this.inventoryColumns == 0) {
			AbstractMountInventoryScreen<?> self = (AbstractMountInventoryScreen<?>)(Object)this;
			int xo = (self.width - EquestrianClient.imageWidth(self)) / 2;
			int yo = (self.height - EquestrianClient.imageHeight(self)) / 2;
			EquestrianClient.stats(graphics, this.mount, xo + 79, yo + 17);
		}
	}
}

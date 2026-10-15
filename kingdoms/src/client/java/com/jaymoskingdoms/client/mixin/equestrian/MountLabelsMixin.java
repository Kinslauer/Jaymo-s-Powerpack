package com.jaymoskingdoms.client.mixin.equestrian;

import com.jaymoskingdoms.client.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.44) A mount's inventory screen labels its animal in gold and the player's inventory dim, readable on the dark panel. */
@Mixin(AbstractContainerScreen.class)
abstract class MountLabelsMixin extends Screen {
	@Shadow
	protected int titleLabelX;
	@Shadow
	protected int titleLabelY;
	@Shadow
	protected int inventoryLabelX;
	@Shadow
	protected int inventoryLabelY;
	@Shadow
	@Final
	protected Component playerInventoryTitle;

	private MountLabelsMixin(Component title) {
		super(title);
	}

	@Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$labels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if ((Object)this instanceof AbstractMountInventoryScreen<?>) {
			Font font = this.font;
			graphics.text(font, this.title, this.titleLabelX, this.titleLabelY, Ui.GOLD, false);
			graphics.text(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, Ui.DIM, false);
			ci.cancel();
		}
	}
}

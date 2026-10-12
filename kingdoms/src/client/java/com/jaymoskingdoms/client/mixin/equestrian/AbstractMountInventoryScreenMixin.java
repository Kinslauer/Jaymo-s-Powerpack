package com.jaymoskingdoms.client.mixin.equestrian;

import com.jaymoskingdoms.client.equestrian.EquestrianClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.34) A tamed horse's breed and stats, in the empty space of its inventory screen. */
@Mixin(AbstractMountInventoryScreen.class)
abstract class AbstractMountInventoryScreenMixin {
	@Shadow
	@Final
	protected int inventoryColumns;
	@Shadow
	@Final
	protected LivingEntity mount;

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

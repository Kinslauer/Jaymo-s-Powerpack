package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.client.gui.DWScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ConfigurationScreenMixin {
    @Shadow
    protected abstract void extractPanorama(GuiGraphicsExtractor graphics, float a);

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void onRenderBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        final Screen screen = (Screen) (Object) this;
        if (DWScreen.configLike(screen) && DWScreen.customPanorama()) {
            extractPanorama(graphics, partialTick);
            graphics.fill(0, 0, screen.width, screen.height, 0xAA000000);
            ci.cancel();
        }
    }
}

package com.AdamNew.dynamicwaters.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.AdamNew.dynamicwaters.client.LoadingProgressTracker;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    protected LevelLoadingScreenMixin(Component title) {
        super(title);
    }

    @org.spongepowered.asm.mixin.Unique
    private static String dynamicwaters$slowTerrain;

    @org.spongepowered.asm.mixin.Unique
    private static String dynamicwaters$slowTerrainText() {
        if (dynamicwaters$slowTerrain == null) {
            final java.util.List<String> names = new java.util.ArrayList<>();
            if (com.AdamNew.dynamicwaters.platform.Platform.isModLoaded("larion")) names.add("Larion");
            if (com.AdamNew.dynamicwaters.platform.Platform.isModLoaded("mr_lithosphere")) names.add("Lithosphere");
            dynamicwaters$slowTerrain = names.isEmpty() ? ""
                    : String.join(" and ", names) + " will load much longer than expected";
        }
        return dynamicwaters$slowTerrain;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderDynamicWatersProgress(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        final String slow = dynamicwaters$slowTerrainText();
        if (!slow.isEmpty()) {
            final int slowWidth = this.font.width(slow);
            guiGraphics.text(this.font, slow, (this.width - slowWidth) / 2, this.height / 2 + 105, 0xFFFFFF55);
        }
        if (!LoadingProgressTracker.isGenerating) {
            return;
        }

        int width = this.width;
        int height = this.height;

        String prefixText = "Dynamic Waters: ";
        String progressText = LoadingProgressTracker.progress + "%";
        String fullProgressText = prefixText + progressText;
        String stateText = LoadingProgressTracker.state;

        int yOffset = height / 2 + 60;
        int textWidth = this.font.width(fullProgressText);
        guiGraphics.text(this.font, fullProgressText, (width - textWidth) / 2, yOffset, 0xFFFFFFFF);

        int stateWidth = this.font.width(stateText);
        guiGraphics.text(this.font, stateText, (width - stateWidth) / 2, yOffset + 15, 0xFFAAAAAA);

        if (LoadingProgressTracker.progress == 100) {
            String warningText = "Minecraft may freeze momentarily, do not click anything!";
            int warningWidth = this.font.width(warningText);
            guiGraphics.text(this.font, warningText, (width - warningWidth) / 2, yOffset + 30, 0xFFFF5555);
        }
    }
}

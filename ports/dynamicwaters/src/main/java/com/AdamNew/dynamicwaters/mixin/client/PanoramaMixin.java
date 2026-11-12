package com.AdamNew.dynamicwaters.mixin.client;

import com.AdamNew.dynamicwaters.DynamicWaters;
import com.AdamNew.dynamicwaters.client.gui.DWScreen;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Dynamic Waters panorama. In 26.3 screens only pass the spin of the panorama; the GUI renderer owns the one cube
 * map and draws it, so the river panorama is a second cube map drawn in its place on the screens that want it.
 */
@Mixin(GuiRenderer.class)
public class PanoramaMixin {
    @Unique
    private final CubeMap dynamicwaters$cubeMap = new CubeMap(
            Identifier.fromNamespaceAndPath(DynamicWaters.MODID, "textures/gui/title/background/panorama"));

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;render(FF)V"))
    private void dynamicwaters$riverPanorama(CubeMap vanilla, float rotX, float rotY) {
        (DWScreen.wantsRiverPanorama() ? dynamicwaters$cubeMap : vanilla).render(rotX, rotY);
    }

    @Inject(method = "registerPanoramaTextures", at = @At("TAIL"))
    private void dynamicwaters$registerTextures(TextureManager textureManager, CallbackInfo ci) {
        dynamicwaters$cubeMap.registerTextures(textureManager);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void dynamicwaters$close(CallbackInfo ci) {
        dynamicwaters$cubeMap.close();
    }
}

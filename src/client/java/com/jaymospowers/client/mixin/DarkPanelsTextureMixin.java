package com.jaymospowers.client.mixin;

import com.jaymospowers.client.packlook.DarkPanels;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The other mods' light grey panels are repainted dark as their textures are read ({@link DarkPanels#repaint}). */
@Mixin(TextureContents.class)
public class DarkPanelsTextureMixin {
	@Inject(method = "load", at = @At("RETURN"))
	private static void jaymospowers$darkPanels(ResourceManager manager, Identifier location, CallbackInfoReturnable<TextureContents> cir) {
		TextureContents contents = cir.getReturnValue();
		if (contents != null) {
			DarkPanels.repaint(location, contents.image());
		}
	}
}

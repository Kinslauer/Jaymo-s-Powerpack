package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wet.ArmourGrimeTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.19.74) A dirty armour piece's inventory icon wears the same grime splotches as the piece itself (0.20.41: and any item its blood)
 * ({@link ArmourGrimeTextures#icon}), under the durability bar and count, so dunking it in a cauldron visibly cleans it.
 */
@Mixin(GuiGraphicsExtractor.class)
abstract class ArmourGrimeIconMixin {
	@Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemBar(Lnet/minecraft/world/item/ItemStack;II)V"), require = 0)
	private void jaymospowers$grimeIcon(Font font, ItemStack stack, int x, int y, String countText, CallbackInfo ci) {
		Identifier grime = ArmourGrimeTextures.icon(stack);
		if (grime != null) {
			((GuiGraphicsExtractor) (Object) this).blit(RenderPipelines.GUI_TEXTURED, grime, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
		}
	}
}

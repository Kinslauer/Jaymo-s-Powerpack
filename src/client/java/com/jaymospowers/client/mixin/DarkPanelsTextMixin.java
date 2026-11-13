package com.jaymospowers.client.mixin;

import com.jaymospowers.client.packlook.DarkPanels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Vanilla's dark label grey is drawn light on the other mods' screens we repaint dark ({@link DarkPanels#text}). */
@Mixin(GuiGraphicsExtractor.class)
public class DarkPanelsTextMixin {
	@ModifyVariable(method = {
		"text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
		"text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
		"text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
	}, at = @At("HEAD"), ordinal = 2, argsOnly = true)
	private int jaymospowers$darkPanelLabels(int color) {
		return DarkPanels.text(color);
	}
}

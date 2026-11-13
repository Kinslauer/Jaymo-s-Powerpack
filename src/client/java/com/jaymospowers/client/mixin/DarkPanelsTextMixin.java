package com.jaymospowers.client.mixin;

import com.jaymospowers.client.packlook.DarkPanels;
import com.jaymospowers.client.packlook.GreyInk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Text colour by screen look: vanilla's dark label grey is drawn light on the other mods' screens we repaint dark
 * ({@link DarkPanels#text}), and our own light text is drawn as ink on our panels in the grey look ({@link GreyInk}).
 */
// (the Component overload hands on to the FormattedCharSequence one, so it isn't hooked: a colour is inked once)
@Mixin(GuiGraphicsExtractor.class)
public class DarkPanelsTextMixin {
	@ModifyVariable(method = {
		"text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
		"text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"
	}, at = @At("HEAD"), ordinal = 2, argsOnly = true)
	private int jaymospowers$screenLookColor(int color) {
		return GreyInk.color(DarkPanels.text(color));
	}

	@ModifyVariable(method = {
		"text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
		"text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"
	}, at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private boolean jaymospowers$screenLookShadow(boolean shadow) {
		return GreyInk.shadow(shadow);
	}

	@Inject(method = "itemCount", at = @At("HEAD"))
	private void jaymospowers$countKeepsItsColor(CallbackInfo ci) {
		GreyInk.pause(true);
	}

	@Inject(method = "itemCount", at = @At("RETURN"))
	private void jaymospowers$countDone(CallbackInfo ci) {
		GreyInk.pause(false);
	}

	@Inject(method = "extractDeferredElements", at = @At("HEAD"))
	private void jaymospowers$inkEndsWithTheScreen(CallbackInfo ci) {
		GreyInk.end();
	}
}

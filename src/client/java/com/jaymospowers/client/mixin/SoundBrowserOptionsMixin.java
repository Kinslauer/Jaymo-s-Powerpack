package com.jaymospowers.client.mixin;

import com.jaymospowers.client.sound.SoundBrowserScreen;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Sound Browser button ({@link SoundBrowserScreen}) at the end of Music &amp; Sounds. */
@Mixin(SoundOptionsScreen.class)
abstract class SoundBrowserOptionsMixin extends OptionsSubScreen {
	SoundBrowserOptionsMixin(Screen lastScreen, Options options, Component title) {
		super(lastScreen, options, title);
	}

	@Inject(method = "addOptions", at = @At("TAIL"))
	private void jaymospowers$soundBrowser(CallbackInfo ci) {
		if (this.list == null) {
			return;
		}
		Button button = Button.builder(Component.translatable("options.jaymospowers.sound_browser"),
			b -> this.minecraft.gui.setScreen(new SoundBrowserScreen(this))).build();
		button.setTooltip(Tooltip.create(Component.translatable("options.jaymospowers.sound_browser.tooltip")));
		this.list.addBig(button);
	}
}

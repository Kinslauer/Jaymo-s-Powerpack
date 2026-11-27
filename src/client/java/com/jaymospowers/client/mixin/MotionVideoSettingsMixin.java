package com.jaymospowers.client.mixin;

import com.jaymospowers.client.MotionSettings;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Reduced motion: On / Off button ({@link MotionSettings}) at the end of Video Settings. */
@Mixin(VideoSettingsScreen.class)
abstract class MotionVideoSettingsMixin extends OptionsSubScreen {
	MotionVideoSettingsMixin(Screen lastScreen, Options options, Component title) {
		super(lastScreen, options, title);
	}

	@Inject(method = "addOptions", at = @At("TAIL"))
	private void jaymospowers$reducedMotion(CallbackInfo ci) {
		if (this.list == null) {
			return;
		}
		CycleButton<Boolean> button = CycleButton.onOffBuilder(MotionSettings.reduced())
			.create(Component.translatable("options.jaymospowers.motion"), (b, value) -> MotionSettings.setReduced(value));
		button.setTooltip(Tooltip.create(Component.translatable("options.jaymospowers.motion.tooltip")));
		this.list.addBig(button);
	}
}

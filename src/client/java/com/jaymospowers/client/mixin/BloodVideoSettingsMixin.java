package com.jaymospowers.client.mixin;

import com.jaymospowers.client.blood.BloodSettings;
import net.minecraft.client.Minecraft;
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

/** The Blood: Light / Normal / Heavy button ({@link BloodSettings}) at the end of Video Settings. */
@Mixin(VideoSettingsScreen.class)
abstract class BloodVideoSettingsMixin extends OptionsSubScreen {
	BloodVideoSettingsMixin(Screen lastScreen, Options options, Component title) {
		super(lastScreen, options, title);
	}

	@Inject(method = "addOptions", at = @At("TAIL"))
	private void jaymospowers$bloodAmount(CallbackInfo ci) {
		if (this.list == null) {
			return;
		}
		CycleButton<BloodSettings.Amount> button = CycleButton.builder(BloodSettings.Amount::label, BloodSettings.amount())
			.withValues(BloodSettings.Amount.values())
			.create(Component.translatable("options.jaymospowers.blood"), (b, value) -> BloodSettings.set(value));
		button.setTooltip(Tooltip.create(Component.translatable("options.jaymospowers.blood.tooltip")));
		this.list.addBig(button);
	}
}

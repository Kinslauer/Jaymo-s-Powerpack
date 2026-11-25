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

/** The Blood: Light / Normal / Heavy button, the gore presets (Light, Cinematic, Brutal) and Streamer safe ({@link BloodSettings}) at the end of Video Settings. */
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
		@SuppressWarnings("unchecked")
		CycleButton<BloodSettings.Preset>[] presetButton = new CycleButton[1];
		CycleButton<BloodSettings.Amount> button = CycleButton.builder(BloodSettings.Amount::label, BloodSettings.amount())
			.withValues(BloodSettings.Amount.values())
			.create(Component.translatable("options.jaymospowers.blood"), (b, value) -> {
				BloodSettings.set(value);
				BloodSettings.Preset now = BloodSettings.preset();
				if (presetButton[0] != null && now != null) {
					presetButton[0].setValue(now);
				}
			});
		button.setTooltip(Tooltip.create(Component.translatable("options.jaymospowers.blood.tooltip")));
		this.list.addBig(button);
		// the gore presets: each sets the blood amount above (and the gore level)
		BloodSettings.Preset current = BloodSettings.preset();
		CycleButton<BloodSettings.Preset> presets = CycleButton.builder(BloodSettings.Preset::label,
				current != null ? current : BloodSettings.Preset.CINEMATIC)
			.withValues(BloodSettings.Preset.values())
			.create(Component.translatable("options.jaymospowers.gore"), (b, value) -> {
				BloodSettings.set(value);
				button.setValue(value.amount);
			});
		presetButton[0] = presets;
		presets.setTooltip(Tooltip.create(Component.translatable("options.jaymospowers.gore.tooltip")));
		this.list.addBig(presets);
		CycleButton<Boolean> safe = CycleButton.onOffBuilder(BloodSettings.streamerSafe())
			.create(Component.translatable("options.jaymospowers.streamer_safe"), (b, value) -> BloodSettings.setStreamerSafe(value));
		safe.setTooltip(Tooltip.create(Component.translatable("options.jaymospowers.streamer_safe.tooltip")));
		this.list.addBig(safe);
	}
}

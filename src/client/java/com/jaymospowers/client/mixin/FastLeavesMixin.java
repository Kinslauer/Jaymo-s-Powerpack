package com.jaymospowers.client.mixin;

import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Fast graphics preset (and low-end mode, which picks it) keeps Cutout Leaves on: with it off every leaf block is
 * drawn solid, Mohknee's maples and ginkgos too (Andy, 2026-11-26: "turn it on by default"). It costs very little.
 */
@Mixin(GraphicsPreset.class)
abstract class FastLeavesMixin {
	@Shadow
	private static <T> void set(@Nullable OptionsSubScreen screen, OptionInstance<T> option, T value) {
		throw new AssertionError();
	}

	@Inject(method = "apply", at = @At("TAIL"))
	private void jaymospowers$keepCutoutLeaves(Minecraft minecraft, CallbackInfo ci) {
		if ((Object) this == GraphicsPreset.FAST) {
			OptionsSubScreen screen = minecraft.gui != null && minecraft.gui.screen() instanceof OptionsSubScreen sub ? sub : null;
			set(screen, minecraft.options.cutoutLeaves(), true);
		}
	}
}

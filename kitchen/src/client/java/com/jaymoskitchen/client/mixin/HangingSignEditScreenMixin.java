package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.delight.CanvasSign;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (From Farmer's Delight, MIT.) Editing a hanging canvas sign shows its own canvas behind the text (gui/hanging_signs/canvas*.png). */
@Mixin(HangingSignEditScreen.class)
public class HangingSignEditScreenMixin {
	@Shadow
	@Final
	@Mutable
	private Identifier texture;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void jaymoskitchen$canvasTexture(SignBlockEntity sign, SignTextSlot slot, boolean filter, CallbackInfo ci) {
		if (sign.getBlockState().getBlock() instanceof CanvasSign canvas) {
			texture = JaymosKitchen.id("textures/gui/hanging_signs/canvas" + (canvas.getBackgroundColor() == null ? "" : "_" + canvas.getBackgroundColor().getName()) + ".png");
		}
	}
}

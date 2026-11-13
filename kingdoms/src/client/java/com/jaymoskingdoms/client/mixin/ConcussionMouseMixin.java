package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.inhabitants.Concussed;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A concussed player's eyes lag behind their head: the mouse turns them as the cinematic camera does. */
@Mixin(MouseHandler.class)
abstract class ConcussionMouseMixin {
	@Redirect(method = "turnPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
	private boolean jaymoskingdoms$concussedLag(Options options) {
		return options.smoothCamera || Concussed.now();
	}
}

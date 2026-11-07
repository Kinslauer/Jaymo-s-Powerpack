package com.jaymoskingdoms.mixin.tweaks;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.537) Cube Animals hands every player its Zoological Guide (and a chat line about it) the first time they join
 * (its {@code ExampleMixin.cubeanimals$handleJoin}, merged into {@link Player}). Andy wants it crafted instead (recipe
 * {@code jaymoskingdoms:zoological_guide}), so the join gift is skipped. The priority applies this after Cube Animals'
 * own mixin has added the method; {@code require = 0} so the pack still loads without Cube Animals.
 */
@Mixin(value = Player.class, priority = 1500)
abstract class NoStartGuideMixin {
	@Inject(method = "cubeanimals$handleJoin", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private void jaymoskingdoms$noStartGuide(CallbackInfo ci) {
		ci.cancel();
	}
}

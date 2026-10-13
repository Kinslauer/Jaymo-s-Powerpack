package com.jaymoskingdoms.mixin.moons;

import com.jaymoskingdoms.Moons;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.6.6) The Overworld's sky, fog, night light and beds as tonight's moon has them ({@link Moons#addLayers}), on both sides. */
@Mixin(EnvironmentAttributeSystem.Builder.class)
abstract class EnvironmentAttributeSystemBuilderMixin {
	@Inject(method = "addDefaultLayers", at = @At("RETURN"))
	private void jaymoskingdoms$moonLayers(Level level, CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
		Moons.addLayers((EnvironmentAttributeSystem.Builder)(Object)this, level);
	}
}

package com.jaymoskingdoms.client.mixin.heritage;

import com.jaymoskingdoms.client.DeepSense;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.106) What a sneaking Deepborn senses through the walls is outlined on their screen alone ({@link DeepSense}). */
@Mixin(Minecraft.class)
abstract class MinecraftGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$deepSense(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (DeepSense.senses(entity)) {
			cir.setReturnValue(true);
		}
	}
}

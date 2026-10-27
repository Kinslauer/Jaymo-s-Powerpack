package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.client.power.zfighter.ZFighterKiSenseClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.17.42, Andy's NOEA picks) Ki sense: the living things it senses glow through walls, on the sensing player's screen only. */
@Mixin(Minecraft.class)
abstract class ZFighterKiSenseGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$kiSense(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && ZFighterKiSenseClient.color(entity) != 0) {
			cir.setReturnValue(true);
		}
	}
}

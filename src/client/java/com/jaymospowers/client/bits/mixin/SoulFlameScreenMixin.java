package com.jaymospowers.client.bits.mixin;

import com.jaymospowers.client.bits.SoulFlames;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.sprite.SpriteId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Soul fire burns blue, over your own view as well: the fire across the screen is the soul fire's when yours is ({@link SoulFlames}). */
@Mixin(ScreenEffectRenderer.class)
abstract class SoulFlameScreenMixin {
	@Redirect(method = "submit", at = @At(value = "FIELD", target = "Lnet/minecraft/client/resources/model/ModelBakery;FIRE_1:Lnet/minecraft/client/resources/model/sprite/SpriteId;"))
	private SpriteId jaymospowers$soulFire() {
		return SoulFlames.ownBlue() ? SoulFlames.SOUL_1 : ModelBakery.FIRE_1;
	}
}

package com.jaymoskingdoms.client.mixin.deadlooks;

import com.jaymoskingdoms.client.TissouPack;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While Tissou's Zombie Pack is on, the textures it repaints outright (the drowned's outer layer, the zombie villagers,
 * the villager's base) are the game's own, read from its built-in files ({@link TissouPack#mirror}).
 */
@Mixin(TextureManager.class)
abstract class TissouTextureManagerMixin {
	@Inject(method = "getTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/AbstractTexture;",
		at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$gameTexture(Identifier id, CallbackInfoReturnable<AbstractTexture> cir) {
		AbstractTexture mirror = TissouPack.mirror((TextureManager) (Object) this, id);
		if (mirror != null) {
			cir.setReturnValue(mirror);
		}
	}
}

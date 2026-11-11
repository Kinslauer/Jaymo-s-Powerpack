package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.films.LoreFilmClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a lore film plays ({@link LoreFilmClient}) you aren't drawn: the film is shot in third person from wherever its
 * camera is, and you would be standing in the back of its shots.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class FilmHidesViewerMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void jaymoskingdoms$filmHidesViewer(E entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks,
		CallbackInfoReturnable<Boolean> cir) {
		if (LoreFilmClient.locked() && entity == Minecraft.getInstance().player) {
			cir.setReturnValue(false);
		}
	}
}

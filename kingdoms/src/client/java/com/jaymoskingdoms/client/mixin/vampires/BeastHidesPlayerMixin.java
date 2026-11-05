package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.NightGifts;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (2026-11-05, Andy: Jung's beast "was still holding a knife, nothing should animate like the suits and costumes") A werewolf
 * player who is the beast now (torn out, not while doubled over: NightGifts.Gift#beast) isn't drawn at all. Invisibility alone left
 * every layer that ignores it (held items, Powers suits and costumes, worn weapons) floating where the body was; the beast
 * (WerewolfRenderer) is all anyone sees of them.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class BeastHidesPlayerMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void jaymoskingdoms$beastHidesPlayer(E entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks,
		CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player player) {
			NightGifts.Gift gift = player.getAttached(NightGifts.GIFT);
			if (gift != null && gift.wolf() && gift.beast()) {
				cir.setReturnValue(false);
			}
		}
	}
}

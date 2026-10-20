package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.corpse.CorpseEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.5.0) A player who has died is replaced by their corpse (a ragdoll from the first tick of dying), so their own model
 * isn't drawn while their corpse is by.
 */
@Mixin(AvatarRenderer.class)
abstract class DeadPlayerMixin {
	// (their corpse counts as by within 4 blocks)
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskitchen$hideDead(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (entity instanceof Player player && player.isDeadOrDying()
			&& !player.level().getEntitiesOfClass(CorpseEntity.class, player.getBoundingBox().inflate(4.0),
				corpse -> corpse.owner().filter(player.getUUID()::equals).isPresent()).isEmpty()) {
			// both, or the viewer sees a see-through ghost of them
			state.isInvisible = true;
			state.isInvisibleToPlayer = true;
		}
	}
}

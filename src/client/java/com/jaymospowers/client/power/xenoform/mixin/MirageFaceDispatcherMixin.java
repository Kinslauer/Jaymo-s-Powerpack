package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.MirageFaceClient;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Mirage wearing a face is drawn as that face ({@link MirageFaceClient}), last of the extract's hooks. */
@Mixin(value = EntityRenderDispatcher.class, priority = 2200)
abstract class MirageFaceDispatcherMixin {
	@Inject(method = "extractEntity", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$mirageFace(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
		EntityRenderState state = cir.getReturnValue();
		EntityRenderState swapped = MirageFaceClient.swap(entity, state, partialTicks, (EntityRenderDispatcher)(Object)this);
		if (swapped != state) {
			cir.setReturnValue(swapped);
		}
	}
}

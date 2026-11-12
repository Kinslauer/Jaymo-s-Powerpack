package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.EntityPhysicsContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Remembers which entity the fluid current is being worked out for, so {@code FlowingFluidMixin} can scale the river
 * push per entity. In 26.3 the current is gathered in {@link EntityFluidInteraction#update} (it was
 * {@code Entity.updateFluidHeightAndDoFluidPushing} before).
 */
@Mixin(EntityFluidInteraction.class)
public class EntityFluidInteractionMixin {
    @Inject(method = "update", at = @At("HEAD"))
    private void dynamicwaters$captureContextHead(Entity entity, boolean ignoreCurrent, CallbackInfoReturnable<Boolean> cir) {
        EntityPhysicsContext.set(entity);
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void dynamicwaters$captureContextReturn(Entity entity, boolean ignoreCurrent, CallbackInfoReturnable<Boolean> cir) {
        EntityPhysicsContext.clear();
    }
}

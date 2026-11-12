package com.AdamNew.dynamicwaters.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void dynamicwaters$pushVehiclesAndPhysics(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Entity entity = (Entity)(Object)this;
        if (entity.level().isClientSide()) return;
        if (!entity.isInWater()) return;

        boolean isBoat = entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat;
        String className = entity.getClass().getName().toLowerCase();
        boolean isPhysicsObj = className.contains("physics") ||
                               className.contains("ship") ||
                               className.contains("valkyrien") ||
                               className.contains("eureka") ||
                               className.contains("vs2") ||
                               className.contains("sable") ||
                               className.contains("aeronautics") ||
                               className.contains("contraption");

        if (isBoat || isPhysicsObj) {
            net.minecraft.world.phys.Vec3 globalPos = com.AdamNew.dynamicwaters.SableCompat.getGlobalPos(entity);
            net.minecraft.world.phys.Vec3 flow = com.AdamNew.dynamicwaters.HydrologyManager.getRiverFlow(
                globalPos.x, (int)globalPos.y, globalPos.z, entity.level());

            if (!flow.equals(net.minecraft.world.phys.Vec3.ZERO)) {
                double mult = com.AdamNew.dynamicwaters.Config.getFlowMultiplierForEntity(entity);

                entity.setDeltaMovement(entity.getDeltaMovement().add(flow.scale(mult * 0.05)));
            }
        }
    }
}

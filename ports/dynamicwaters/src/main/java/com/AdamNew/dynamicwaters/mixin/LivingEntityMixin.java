package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.HydrologyManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void dynamicwaters$pushMobsInRiver(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;

        if (entity.isInWater()) {
            Vec3 globalPos = com.AdamNew.dynamicwaters.SableCompat.getGlobalPos(entity);
            Vec3 flow = HydrologyManager.getRiverFlow(globalPos.x, (int)globalPos.y, globalPos.z, entity.level());
            if (!flow.equals(Vec3.ZERO)) {
                double mult = Config.getFlowMultiplierForEntity(entity);
                if (mult > 0.0) {
                    entity.setDeltaMovement(entity.getDeltaMovement().add(flow.scale(mult * 0.05)));
                }
            }
        }
    }
}

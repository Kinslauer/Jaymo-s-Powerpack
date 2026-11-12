package com.AdamNew.dynamicwaters.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void dynamicwaters$slowDownInWater(CallbackInfo ci) {
        ItemEntity item = (ItemEntity)(Object)this;
        if (item.isInWater()) {
            net.minecraft.world.phys.Vec3 globalPos = com.AdamNew.dynamicwaters.SableCompat.getGlobalPos(item);
            net.minecraft.world.phys.Vec3 flow = com.AdamNew.dynamicwaters.HydrologyManager.getRiverFlow(globalPos.x, (int)globalPos.y, globalPos.z, item.level());
            if (!flow.equals(net.minecraft.world.phys.Vec3.ZERO)) {
                double mult = com.AdamNew.dynamicwaters.Config.ITEM_SPEED.get();
                Vec3 delta = item.getDeltaMovement();

                item.setDeltaMovement(flow.x * mult * 0.2, delta.y, flow.z * mult * 0.2);
            }
        }
    }
}

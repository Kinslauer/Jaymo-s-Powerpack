package com.jaymospowers.mixin;

import com.jaymospowers.mutation.ConjoinedTwins;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Positions a conjoined twin baby passenger on a villager's shoulder.
 * Similar to {@link com.jaymospowers.core.Perch} but for villagers carrying babies.
 */
@Mixin(Villager.class)
public abstract class ConjoinedTwinVillagerMixin extends Mob {

    protected ConjoinedTwinVillagerMixin(EntityType<? extends Mob> type, net.minecraft.world.level.Level level) {
        super(type, level);
    }

    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V", at = @At("HEAD"), cancellable = true)
    private void jaymospowers$positionConjoinedTwin(Entity passenger, Entity.MoveFunction moveFunction, CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        Entity conjoinedTwin = ConjoinedTwins.getConjoinedTwin(villager);
        if (conjoinedTwin != null && passenger == conjoinedTwin) {
            // Position on left shoulder
            double height = villager.getBbHeight();
            float yaw = villager.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 ahead = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));

            // Shoulder position: slightly to the left, up at ~75% height, slightly forward
            double side = ConjoinedTwins.VILLAGER_SIDE_OFFSET;
            double up = height * ConjoinedTwins.VILLAGER_UP_FACTOR;
            double forward = ConjoinedTwins.VILLAGER_FORWARD_OFFSET;

            Vec3 pos = villager.position().add(right.scale(side)).add(ahead.scale(forward)).add(0.0, up, 0.0);
            moveFunction.accept(passenger, pos.x, pos.y, pos.z);

            // Face the same direction as the villager
            passenger.setYRot(villager.yBodyRot);
            passenger.yRotO = villager.yBodyRot;
            if (passenger instanceof net.minecraft.world.entity.LivingEntity living) {
                living.yBodyRot = villager.yBodyRot;
                living.yBodyRotO = villager.yBodyRot;
                living.setYHeadRot(villager.yBodyRot);
            }
            ci.cancel();
        }
    }
}
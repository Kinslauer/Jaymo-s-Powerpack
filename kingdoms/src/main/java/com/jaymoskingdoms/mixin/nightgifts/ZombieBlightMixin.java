package com.jaymoskingdoms.mixin.nightgifts;

import com.jaymoskingdoms.HollowBlight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While a player carries the Hollow's blight, zombies never target them: they're passing out and turning,
 * not food. Hooked into {@link NearestAttackableTargetGoal#findTarget} so any attacker skips blighted
 * players and never re-targets one, for the whole blight from stage 0. {@link HollowBlight#standDownAndSpeak}
 * clears any target on you each tick, so an attacker that already had you targeted stands down at once. */
@Mixin(NearestAttackableTargetGoal.class)
final class ZombieBlightMixin {
    @Inject(method = "findTarget", at = @At("RETURN"), cancellable = true)
    private void jaymoskingdoms$skipBlighted(CallbackInfoReturnable<LivingEntity> cir) {
        LivingEntity target = cir.getReturnValue();
        if (target instanceof ServerPlayer player && HollowBlight.blighted(player)) {
            cir.setReturnValue(null);
        }
    }
}

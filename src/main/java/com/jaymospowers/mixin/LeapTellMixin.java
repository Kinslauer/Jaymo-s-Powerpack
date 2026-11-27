package com.jaymospowers.mixin;

import com.jaymospowers.combat.Pounce;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A vanilla leap at a player (spiders, wolves, cats, foxes, and every modded mob that uses the goal) is told first with a
 * yellow star and a held breath, and its blow can't be blocked ({@link Pounce}).
 */
@Mixin(LeapAtTargetGoal.class)
abstract class LeapTellMixin {
	@Shadow
	@Final
	private Mob mob;
	@Shadow
	private LivingEntity target;

	@ModifyReturnValue(method = "canUse", at = @At("RETURN"))
	private boolean jaymospowers$tell(boolean can) {
		LivingEntity aim = this.mob.getTarget();
		boolean go = Pounce.gate(this.mob, aim, can);
		if (go) {
			this.target = aim;
		}
		return go;
	}

	@Inject(method = "start", at = @At("TAIL"))
	private void jaymospowers$flight(CallbackInfo ci) {
		if (this.target instanceof Player) {
			Pounce.launch(this.mob, this.target);
		}
	}
}

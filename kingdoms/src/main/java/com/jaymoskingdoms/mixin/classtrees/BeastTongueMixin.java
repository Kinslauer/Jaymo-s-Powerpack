package com.jaymoskingdoms.mixin.classtrees;

import com.jaymoskingdoms.FaithExtras;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A beast doesn't run from a Druid who has studied its kind and speaks its tongue ({@link FaithExtras#beastFriend}). */
@Mixin(AvoidEntityGoal.class)
abstract class BeastTongueMixin<T extends LivingEntity> {
	@Shadow
	@Final
	protected PathfinderMob mob;
	@Shadow
	protected @Nullable T toAvoid;

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$beastTongue(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && this.toAvoid instanceof Player player && FaithExtras.beastFriend(player, this.mob)) {
			this.toAvoid = null;
			cir.setReturnValue(false);
		}
	}
}

package com.jaymospowers.mixin;

import com.jaymospowers.alchemy.bomb.BombStatus;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nothing heals under the healing-stopper dust ({@link BombStatus#unhealed}: a troll's mending, a vampire's regeneration, a
 * potion), and nothing snuffed teleports itself ({@link BombStatus#snuffed}: an enderman, chorus fruit).
 */
@Mixin(LivingEntity.class)
abstract class BombStatusLivingMixin {
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noHealing(float amount, CallbackInfo ci) {
		if (BombStatus.unhealed((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "randomTeleport(DDDZLjava/util/function/Predicate;)Z", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noTeleport(double x, double y, double z, boolean particles, Predicate<BlockState> floor, CallbackInfoReturnable<Boolean> cir) {
		if (BombStatus.snuffed((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}

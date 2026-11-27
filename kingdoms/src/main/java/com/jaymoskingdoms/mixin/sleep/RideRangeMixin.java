package com.jaymoskingdoms.mixin.sleep;

import com.jaymoskingdoms.ThrallRide;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Riding a thrall by day ({@link ThrallRide}): what the rider reaches for, a block or a creature, is measured from the thrall's eyes,
 * not from the coffin, so the thrall's hands do all a player's do: fight, dig, build, open chests, talk and trade.
 */
@Mixin(Player.class)
abstract class RideRangeMixin {
	@Inject(method = "isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$blockFromThrall(BlockPos pos, double buffer, CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		Vec3 eye = ThrallRide.eyes(self);
		if (eye != null) {
			double reach = self.blockInteractionRange() + buffer;
			cir.setReturnValue(new AABB(pos).distanceToSqr(eye) < reach * reach);
		}
	}

	@Inject(method = "isWithinEntityInteractionRange(Lnet/minecraft/world/phys/AABB;D)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$entityFromThrall(AABB box, double buffer, CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		Vec3 eye = ThrallRide.eyes(self);
		if (eye != null) {
			double reach = self.entityInteractionRange() + buffer;
			cir.setReturnValue(box.distanceToSqr(eye) < reach * reach);
		}
	}

	@Inject(method = "isWithinAttackRange(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/AABB;D)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$attackFromThrall(ItemStack weapon, AABB box, double buffer, CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		Vec3 eye = ThrallRide.eyes(self);
		if (eye != null) {
			double reach = self.entityInteractionRange() + buffer;
			cir.setReturnValue(box.distanceToSqr(eye) < reach * reach);
		}
	}
}

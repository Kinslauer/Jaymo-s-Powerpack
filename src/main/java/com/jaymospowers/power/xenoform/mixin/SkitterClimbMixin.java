package com.jaymospowers.power.xenoform.mixin;

import com.jaymospowers.core.MagicMoves;
import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.20.59) Skitter climbs walls the way a spider does: any wall beside it counts as a ladder. Walking into it (or
 * holding jump) climbs, sneaking holds it still, letting go slides it slowly down. On the ground a wall only counts
 * while it presses into it, so running along one isn't slowed to a ladder's pace. Runs on both sides, so the client's
 * own movement climbs too (the server alone setting the speed was overruled by the client's prediction).
 */
@Mixin(LivingEntity.class)
abstract class SkitterClimbMixin {
	/** How near a wall has to be to hold on to it, in blocks. */
	private static final double WALL_GAP = 0.08;

	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$skitterClimbs(CallbackInfoReturnable<Boolean> cir) {
		if ((Object)this instanceof Player player && !player.isSpectator() && !player.isInWater()
			&& ("skitter".equals(player.getAttached(XenoformState.FORM)) || MagicMoves.climbing(player))
			&& (player.horizontalCollision || !player.onGround() && besideWall(player))) {
			cir.setReturnValue(true);
		}
	}

	/** A block's side within a hair of its box (not the floor under it or the ceiling over it). */
	private static boolean besideWall(Player player) {
		AABB box = player.getBoundingBox();
		AABB around = new AABB(box.minX - WALL_GAP, box.minY + 0.1, box.minZ - WALL_GAP, box.maxX + WALL_GAP, box.maxY - 0.1, box.maxZ + WALL_GAP);
		return !player.level().noCollision(player, around);
	}
}

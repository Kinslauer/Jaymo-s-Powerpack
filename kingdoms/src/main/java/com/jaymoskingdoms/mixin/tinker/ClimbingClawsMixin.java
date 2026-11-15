package com.jaymoskingdoms.mixin.tinker;

import com.jaymoskingdoms.tinker.Mimic;
import com.jaymoskingdoms.tinker.MimicGadgets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The climbing claws ({@link Mimic#CLIMB}): held in either hand, a wall beside the player counts as a ladder, as it does for a
 * spider. On the ground a wall only counts while they press into it, so running along one isn't slowed. Runs on both sides, so
 * the client's own movement climbs too.
 */
@Mixin(LivingEntity.class)
abstract class ClimbingClawsMixin {
	/** How near a wall has to be to hold on to it, in blocks. */
	private static final double WALL_GAP = 0.08;

	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$claws(CallbackInfoReturnable<Boolean> cir) {
		if ((Object)this instanceof Player player && !player.isSpectator() && !player.isInWater()
			&& (MimicGadgets.is(player.getMainHandItem(), Mimic.CLIMB) || MimicGadgets.is(player.getOffhandItem(), Mimic.CLIMB))
			&& (player.horizontalCollision || !player.onGround() && besideWall(player))) {
			cir.setReturnValue(true);
		}
	}

	private static boolean besideWall(Player player) {
		AABB box = player.getBoundingBox();
		AABB around = new AABB(box.minX - WALL_GAP, box.minY + 0.1, box.minZ - WALL_GAP, box.maxX + WALL_GAP, box.maxY - 0.1, box.maxZ + WALL_GAP);
		return !player.level().noCollision(player, around);
	}
}

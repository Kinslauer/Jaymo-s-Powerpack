package com.jaymospowers.client.compat.mixin;

import com.jaymospowers.core.LimbBoxes;
import java.util.ArrayList;
import java.util.List;
import net.bettercombat.client.collision.OrientedBoundingBox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Better Combat's swing hits a person only where it crosses their body ({@link LimbBoxes}), not anywhere in their box.
 * Only applied when Better Combat is loaded ({@code jaymospowers.bettercombat.client.mixins.json} is optional).
 */
@Mixin(targets = "net.bettercombat.client.collision.TargetFinder$CollisionFilter", remap = false)
abstract class LimbSwingMixin {
	@Shadow
	private OrientedBoundingBox obb;

	@Inject(method = "filter", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$limbSwing(List<Entity> entities, CallbackInfoReturnable<List<Entity>> cir) {
		OrientedBoundingBox swing = this.obb;
		Vec3[] axes = {swing.axisX, swing.axisY, swing.axisZ};
		double[] half = {swing.extent.x, swing.extent.y, swing.extent.z};
		List<Entity> hit = new ArrayList<>(cir.getReturnValue());
		hit.removeIf(e -> !LimbBoxes.overlaps(e, swing.center, axes, half));
		cir.setReturnValue(hit);
	}
}

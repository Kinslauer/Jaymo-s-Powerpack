package com.jaymospowers.mixin;

import com.jaymospowers.aether.EdgeGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mobs in the Aether back off from island edges as a sneaking player does ({@link EdgeGuard}). Players keep their own. */
@Mixin(Entity.class)
abstract class AetherEdgeMixin {
	@Inject(method = "maybeBackOffFromEdge", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$aetherEdge(Vec3 delta, MoverType type, CallbackInfoReturnable<Vec3> cir) {
		Vec3 kept = EdgeGuard.guard((Entity) (Object) this, cir.getReturnValue(), type);
		if (kept != cir.getReturnValue()) {
			cir.setReturnValue(kept);
		}
	}
}

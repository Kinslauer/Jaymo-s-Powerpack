package com.jaymospowers.mixin;

import com.jaymospowers.backpack.Backpack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.11.82) A strong explosion that breaks blocks tears apart the set-down backpacks in its reach (Backpack.blast). */
@Mixin(ServerExplosion.class)
abstract class BackpackExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;
	@Shadow
	@Final
	private Vec3 center;
	@Shadow
	@Final
	private float radius;

	@Shadow
	private boolean interactsWithBlocks() {
		throw new AssertionError();
	}

	@Inject(method = "explode", at = @At("TAIL"))
	private void jaymospowers$tearBackpacks(CallbackInfoReturnable<Integer> cir) {
		if (this.interactsWithBlocks()) {
			Backpack.blast(this.level, this.center, this.radius);
		}
	}
}

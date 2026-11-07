package com.jaymospowers.mixin;

import com.jaymospowers.compat.Stacked;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.20.30) In a One Dimension world (the Aether, the Overworld and the Nether stacked in one level, {@link Stacked}) the
 * ground height at (x, z) is the ground of the band you are in, the Overworld's outside one, not the top of an Aether island
 * three hundred blocks overhead. Without it every villager, camp, meteor and spawn search that asks for the surface would
 * land on the islands. Nothing changes in an ordinary world.
 */
@Mixin(Level.class)
abstract class StackedHeightMixin {
	@Inject(method = "getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$bandSurface(Heightmap.Types type, int x, int z, CallbackInfoReturnable<Integer> cir) {
		Integer y = Stacked.surface((Level)(Object)this, type, x, z);
		if (y != null) {
			cir.setReturnValue(y);
		}
	}
}

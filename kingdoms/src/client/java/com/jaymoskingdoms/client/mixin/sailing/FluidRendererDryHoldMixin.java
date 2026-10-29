package com.jaymoskingdoms.client.mixin.sailing;

import com.jaymoskingdoms.sailing.Ships;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.306) The sea isn't drawn inside a ship's dry hold at all. Her water mask (ShipRenderer) hides it from above, but
 * a depth mask can't hide what lies between you and it: down in a hold below the waterline the sea still showed round
 * your feet (Andy: "still water in the hold"). The sections she lies in are meshed again as she moves (ShipClient.remesh),
 * so the sea comes back where she was and goes where she is.
 */
@Mixin(FluidRenderer.class)
abstract class FluidRendererDryHoldMixin {
	@Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$dryHold(BlockAndTintGetter region, BlockPos pos, FluidRenderer.Output out, BlockState state, FluidState fluid,
		CallbackInfo ci) {
		// (meshed off the render thread: Ships keeps its list safe to read from any thread)
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && Ships.dry(level, pos)) {
			ci.cancel();
		}
	}
}

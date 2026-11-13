package com.jaymoskingdoms.client.mixin.xaero;

import com.jaymoskingdoms.client.CastOutMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Cast Out road ({@link CastOutMap}): no waypoint is drawn, in the world or on the map, while it holds. Skipped without Xaero. */
@Pseudo
@Mixin(targets = {"xaero.hud.minimap.waypoint.render.world.WaypointWorldRenderer", "xaero.hud.minimap.waypoint.render.WaypointMapRenderer"},
	remap = false)
abstract class XaeroWaypointsOffMixin {
	@Inject(method = "renderElement", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymoskingdoms$noWaypoints(CallbackInfoReturnable<Boolean> cir) {
		if (CastOutMap.empty()) {
			cir.setReturnValue(false);
		}
	}
}

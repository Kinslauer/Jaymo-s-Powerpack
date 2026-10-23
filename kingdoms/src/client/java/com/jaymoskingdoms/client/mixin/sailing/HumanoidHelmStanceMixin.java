package com.jaymoskingdoms.client.mixin.sailing;

import com.jaymoskingdoms.sailing.Ship;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.7) Whoever is at a ship's wheel stands to it rather than sitting. (0.7.179) And her crew, who ride her, stride
 * as they walk her deck: their legs swing as their station moves ({@link Ship#SEAT}), as they would walking.
 */
@Mixin(HumanoidMobRenderer.class)
abstract class HumanoidHelmStanceMixin {
	/** Each crewman's last station (x, y, z), the tick it was read, and their stride (position, speed). */
	@Unique
	private static final Map<LivingEntity, double[]> jaymoskingdoms$strides = new WeakHashMap<>();

	@Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
	private static void jaymoskingdoms$standAtHelm(LivingEntity entity, HumanoidRenderState state, float partialTicks, ItemModelResolver itemModelResolver,
		CallbackInfo ci) {
		if (!(entity.getVehicle() instanceof Ship)) {
			return;
		}
		state.isPassenger = false;
		Vec3 seat = entity.getAttached(Ship.SEAT);
		if (seat == null) {
			return;
		}
		double[] s = jaymoskingdoms$strides.computeIfAbsent(entity, e -> new double[] {seat.x, seat.y, seat.z, entity.tickCount, 0.0, 0.0});
		if (entity.tickCount != (int)s[3]) {
			// once a tick, as vanilla's walk animation: speed eases toward how far they went
			double moved = Math.sqrt((seat.x - s[0]) * (seat.x - s[0]) + (seat.z - s[2]) * (seat.z - s[2]));
			s[5] += (Math.min(1.0, moved * 4.0) - s[5]) * 0.4;
			s[4] += s[5];
			s[0] = seat.x;
			s[1] = seat.y;
			s[2] = seat.z;
			s[3] = entity.tickCount;
		}
		if (s[5] > 0.01) {
			state.walkAnimationPos = (float)s[4];
			state.walkAnimationSpeed = (float)Mth.clamp(s[5], 0.0, 1.0);
		}
	}
}

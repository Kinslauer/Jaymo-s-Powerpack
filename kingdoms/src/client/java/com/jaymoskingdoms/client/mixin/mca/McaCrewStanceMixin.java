package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.sailing.Ship;
import net.conczin.mca.client.render.VillagerLikeEntityMCARenderer;
import net.conczin.mca.client.render.VillagerRenderState;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.246) A ship's crew (MCA's villagers since 0.7.242) ride her only to keep their stations: Andy saw them all seated
 * as if mounted. Last thing MCA sets on their state, they stand, as HumanoidHelmStanceMixin has folk crews stand.
 */
@Mixin(VillagerLikeEntityMCARenderer.class)
abstract class McaCrewStanceMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Mob;Lnet/conczin/mca/client/render/VillagerRenderState;F)V", at = @At("TAIL"))
	private void jaymoskingdoms$standOnDeck(Mob entity, VillagerRenderState state, float partialTicks, CallbackInfo ci) {
		if (entity.getVehicle() instanceof Ship) {
			state.isPassenger = false;
		}
	}
}

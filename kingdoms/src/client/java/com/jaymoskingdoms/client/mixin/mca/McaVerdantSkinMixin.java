package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.Verdants;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.23) A Verdant villager's skin is their shade of green instead of MCA's melanin colours ({@link Verdants}). */
@Mixin(SkinLayer.class)
abstract class McaVerdantSkinMixin {
	@Inject(method = "getColor(Lnet/conczin/mca/client/render/VillagerVisuals;F)I", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$verdantSkin(VillagerVisuals visuals, float partialTicks, CallbackInfoReturnable<Integer> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(visuals.entityId());
		if (entity != null) {
			int green = Verdants.skin(entity);
			if (green != 0) {
				cir.setReturnValue(green);
			}
		}
	}
}

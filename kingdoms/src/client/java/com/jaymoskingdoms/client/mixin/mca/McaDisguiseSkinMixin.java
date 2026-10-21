package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.dragons.Disguises;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.141) Eurus and Aeolus in their villager disguise ({@link Disguises}) wear the dragon's own skin (a 64x64 player
 * skin, {@code textures/entity/dragon/<kind>/villager.png}) in place of MCA's, with no tint on it.
 */
@Mixin(SkinLayer.class)
abstract class McaDisguiseSkinMixin {
	@Inject(method = "getSkin(Lnet/conczin/mca/client/render/VillagerVisuals;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$disguiseSkin(VillagerVisuals visuals, CallbackInfoReturnable<Identifier> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		Identifier skin = minecraft.level == null ? null : Disguises.skin(minecraft.level.getEntity(visuals.entityId()));
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}

	@Inject(method = "getColor(Lnet/conczin/mca/client/render/VillagerVisuals;F)I", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$disguiseColor(VillagerVisuals visuals, float partialTicks, CallbackInfoReturnable<Integer> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null && Disguises.skin(minecraft.level.getEntity(visuals.entityId())) != null) {
			// (opaque white: the skin as painted)
			cir.setReturnValue(0xFFFFFFFF);
		}
	}
}

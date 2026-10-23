package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.177) Andy: "the default skins are still being drawn underneath the MCA villager". MCA hides a villager-bodied
 * player's own Minecraft body by having LivingEntityRenderer.getRenderType give no render type. Shoulder Surfing
 * Reloaded gives AvatarRenderer its own getRenderType, which, while it fades the player in front of its camera (close
 * over the shoulder, as Time Flow holds the view), returns a see-through one of the player's Minecraft skin without
 * asking the renderer above it, so that body was drawn under MCA's again and poked out of it. Applied after Shoulder
 * Surfing's (priority), only when its method is there: an MCA body still gives none.
 */
@Mixin(value = AvatarRenderer.class, priority = 1500)
abstract class McaShoulderSurfingBodyMixin {
	@Inject(method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;",
		at = @At("RETURN"), cancellable = true, require = 0)
	private void jaymoskingdoms$noBodyUnderMca(LivingEntityRenderState state, boolean bodyVisible, boolean translucent, boolean glowing,
		CallbackInfoReturnable<RenderType> cir) {
		if (state instanceof VillagerStateHolder holder && holder.mca$isVillagerRendererActive()) {
			cir.setReturnValue(null);
		}
	}
}

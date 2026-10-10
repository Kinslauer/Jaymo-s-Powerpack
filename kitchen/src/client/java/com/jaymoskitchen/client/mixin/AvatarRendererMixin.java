package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.corpse.CarryPose;
import com.jaymoskitchen.client.quiver.QuiverRenderData;
import com.jaymoskitchen.corpse.CorpseEntity;
import net.minecraft.world.entity.player.Player;
import com.jaymoskitchen.quiver.QuiverContents;
import com.jaymoskitchen.quiver.Quivers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copies the (server-synced) worn quiver into the render state so {@code QuiverLayer} can draw it. */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskitchen$quiver(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		QuiverContents worn = ((AttachmentTarget) entity).getAttached(Quivers.WORN);
		state.setData(QuiverRenderData.KEY, worn == null ? null : QuiverRenderData.of(worn));
		// carrying a corpse: arms out in front, under it
		state.setData(CarryPose.KEY, entity instanceof Player player && CorpseEntity.carriedBy(player) != null);
	}
}

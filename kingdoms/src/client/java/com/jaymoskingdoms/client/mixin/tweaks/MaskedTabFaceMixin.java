package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.MaskedFacesClient;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A masked player's face in the tab list is the mask's ({@link MaskedFacesClient}), not their own skin's. */
@Mixin(PlayerTabOverlay.class)
abstract class MaskedTabFaceMixin {
	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerInfo;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;"))
	private PlayerSkin jaymoskingdoms$maskedFace(PlayerInfo info) {
		return MaskedFacesClient.face(info.getProfile().id(), info.getSkin());
	}
}

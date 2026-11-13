package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.MaskedFacesClient;
import com.jaymoskingdoms.client.TabListNames;
import java.util.UUID;
import net.minecraft.client.gui.screens.social.PlayerEntry;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The social interactions screen (P) shows a player as the tab list does: by the name it gives them (a costume's while
 * masked, "Stranger" if you haven't met, their character name otherwise; {@link TabListNames}) and, while masked, with the
 * mask's face ({@link MaskedFacesClient}).
 */
@Mixin(PlayerEntry.class)
abstract class MaskedSocialEntryMixin {
	@Shadow
	@Final
	private UUID id;

	@ModifyArg(method = "extractContent", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/PlayerFaceExtractor;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/PlayerSkin;III)V"),
		index = 1)
	private PlayerSkin jaymoskingdoms$maskedFace(PlayerSkin own) {
		return MaskedFacesClient.face(this.id, own);
	}

	@Redirect(method = {"extractContent", "getEntryNarationMessage"}, at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/gui/screens/social/PlayerEntry;playerName:Ljava/lang/String;", opcode = org.objectweb.asm.Opcodes.GETFIELD))
	private String jaymoskingdoms$shownName(PlayerEntry entry) {
		return TabListNames.shown(this.id, entry.getPlayerName());
	}
}

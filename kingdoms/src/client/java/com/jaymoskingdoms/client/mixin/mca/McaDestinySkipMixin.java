package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.LifeStory;
import net.conczin.mca.client.gui.DestinyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * (0.7.216) Andy's life story: its last question is where you start ({@code LifeStory.Start}), so MCA's start-up
 * creator skips its own "where do you start" page for a newcomer. It goes on as it does when travel is switched off:
 * it tells the server it's done (no destination). (0.7.229) The life story now comes before the creator
 * ({@code McaDestinyWaitMixin}), so the page is skipped for anyone who has told one too.
 */
@Mixin(DestinyScreen.class)
abstract class McaDestinySkipMixin {
	@Redirect(method = "setPage", at = @At(value = "FIELD", target = "Lnet/conczin/mca/client/gui/DestinyScreen;allowTeleportation:Z"))
	private boolean jaymoskingdoms$noJourney(DestinyScreen screen) {
		LocalPlayer player = Minecraft.getInstance().player;
		boolean newcomer = player != null && LifeStory.of(player) == null && Heritage.of(player) == null && Classes.classOf(player) == null;
		// (0.7.229: the story is told before the creator now, so one who has just told it skips the page too)
		boolean told = player != null && LifeStory.of(player) != null;
		return !newcomer && !told && ((McaDestinyAccessor)(Object)screen).jaymoskingdoms$allowTeleportation();
	}
}

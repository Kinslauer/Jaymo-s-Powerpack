package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.JobTitles;
import com.jaymoskingdoms.client.TabListNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.180) The tab list calls a player you haven't been introduced to a stranger ({@code Introductions}). (0.7.218) One
 * you know has their job after their name ({@link JobTitles}).
 */
@Mixin(PlayerTabOverlay.class)
abstract class IntroTabListMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$stranger(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		if (TabListNames.stranger(info.getProfile().id())) {
			cir.setReturnValue(TabListNames.strangerName());
			return;
		}
		Component job = JobTitles.of(info.getProfile().id());
		if (job != null && cir.getReturnValue() != null) {
			// (0.7.218) and their job, once you know them
			cir.setReturnValue(cir.getReturnValue().copy().append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(job));
		}
	}
}

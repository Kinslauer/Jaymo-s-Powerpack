package com.jaymoskingdoms.client.mixin.xaero;

import com.jaymoskingdoms.client.TabListNames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.149) Xaero's Minimap (optional, nothing of it copied) labels a tracked player by their username; it shows the
 * name the tab list does instead ({@link TabListNames}). Skipped when Xaero's Minimap isn't there.
 */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementRenderer", remap = false)
abstract class XaeroTrackerRendererMixin {
	@WrapOperation(method = "renderElement", at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;name()Ljava/lang/String;"), require = 0)
	private String jaymoskingdoms$characterName(GameProfile profile, Operation<String> original) {
		return TabListNames.shown(profile, original.call(profile));
	}
}

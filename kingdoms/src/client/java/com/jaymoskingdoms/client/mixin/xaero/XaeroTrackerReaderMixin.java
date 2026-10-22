package com.jaymoskingdoms.client.mixin.xaero;

import com.jaymoskingdoms.client.TabListNames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.149) The same for the label's width and the name in Xaero's Minimap's right-click menu
 * ({@link XaeroTrackerRendererMixin}).
 */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementReader", remap = false)
abstract class XaeroTrackerReaderMixin {
	@WrapOperation(method = {"getLeftSideLength", "getMenuName"}, at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;name()Ljava/lang/String;"), require = 0)
	private String jaymoskingdoms$characterName(GameProfile profile, Operation<String> original) {
		return TabListNames.shown(profile, original.call(profile));
	}
}

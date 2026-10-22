package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.CharacterNames;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.149) The tab list shows a player by their character name ({@link CharacterNames}) when nothing else (a costume's
 * name) has been given; {@code CharacterNames#refresh} resends it when it changes.
 */
@Mixin(ServerPlayer.class)
abstract class McaTabListNameMixin {
	@Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$characterName(CallbackInfoReturnable<Component> cir) {
		if (cir.getReturnValue() == null) {
			Component name = CharacterNames.shown((ServerPlayer)(Object)this);
			if (name != null) {
				cir.setReturnValue(name);
			}
		}
	}
}

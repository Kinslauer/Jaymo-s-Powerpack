package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.CharacterNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.149) A player goes by the name they picked in MCA's creator ({@link CharacterNames}) wherever their display name
 * shows. At the return, so Jaymo's Powers' costume name (which answers at the head) still wins while masked.
 */
@Mixin(Player.class)
abstract class McaPlayerNameMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$characterName(CallbackInfoReturnable<Component> cir) {
		Component name = CharacterNames.shown((Player)(Object)this);
		if (name != null) {
			cir.setReturnValue(name);
		}
	}
}

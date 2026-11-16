package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.SignIn;
import net.conczin.mca.server.ServerInteractionManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * MCA opens its creator on a newcomer's first join. An offline account that hasn't signed in yet (EasyAuth) couldn't
 * send it back, and the creator can't be shut, so it waits: {@link SignIn} opens it once they're in.
 */
@Mixin(ServerInteractionManager.class)
abstract class McaJoinDestinyMixin {
	@Redirect(method = "onPlayerJoin", at = @At(value = "INVOKE",
		target = "Lnet/conczin/mca/server/ServerInteractionManager;launchDestiny(Lnet/minecraft/server/level/ServerPlayer;)V"))
	private void jaymoskingdoms$afterSignIn(ServerPlayer player) {
		if (SignIn.signedIn(player)) {
			ServerInteractionManager.launchDestiny(player);
		}
	}
}

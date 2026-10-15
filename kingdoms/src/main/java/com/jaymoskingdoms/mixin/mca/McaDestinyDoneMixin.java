package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Classes;
import net.conczin.mca.network.c2s.DestinyMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.45) Andy: "force character creation on spawn". MCA's destiny screen tells the server it's done with an empty
 * {@link DestinyMessage}; the class screen ({@link Classes#afterDestiny}) opens next.
 */
@Mixin(DestinyMessage.class)
abstract class McaDestinyDoneMixin {
	@Inject(method = "handle", at = @At("HEAD"))
	private void jaymoskingdoms$classNext(Player player, CallbackInfo ci) {
		if (((DestinyMessage) (Object) this).destination().isEmpty() && player instanceof ServerPlayer sp) {
			sp.level().getServer().execute(() -> Classes.afterDestiny(sp));
		}
	}
}

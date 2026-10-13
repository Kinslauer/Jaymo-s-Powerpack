package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.power.arsenal.ArsenalFlintlock;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dropping (Q) one of a pair of summoned guns drops the other hand's too. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDropMixin {
	@Inject(method = "drop(Z)V", at = @At("HEAD"))
	private void jaymospowers$dropPair(boolean all, CallbackInfo ci) {
		ArsenalFlintlock.dropPartner((ServerPlayer) (Object) this);
	}
}

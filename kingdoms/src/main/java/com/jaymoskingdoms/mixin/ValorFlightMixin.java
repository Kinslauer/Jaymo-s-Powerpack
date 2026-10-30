package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.ValorWings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.410) Flying on a Skyward Valor's wings isn't floating where you shouldn't: no flying kick. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ValorFlightMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$valorWings(Entity entity, CallbackInfoReturnable<Integer> cir) {
		if (entity == this.player && ValorWings.flying(this.player)) {
			cir.setReturnValue(Integer.MAX_VALUE);
		}
	}
}

package com.jaymoskingdoms.mixin.locks;

import com.jaymoskingdoms.Locks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (0.7.497) A copper golem leaves a locked container alone, as it does one with a vanilla lock code ({@link Locks#sealed}). */
@Mixin(TransportItemsBetweenContainers.class)
abstract class TransportItemsBetweenContainersMixin {
	@ModifyReturnValue(method = "isContainerLocked", at = @At("RETURN"))
	private boolean jaymoskingdoms$sealed(boolean locked, TransportItemsBetweenContainers.TransportItemTarget target) {
		return locked || target.blockEntity().getLevel() instanceof ServerLevel server && Locks.sealed(server, target.pos());
	}
}

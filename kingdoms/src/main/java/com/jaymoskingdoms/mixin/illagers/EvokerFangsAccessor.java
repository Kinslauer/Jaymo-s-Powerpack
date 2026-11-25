package com.jaymoskingdoms.mixin.illagers;

import net.minecraft.world.entity.projectile.EvokerFangs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The invoker's fangs (illagers/InvokerFangs) set the warm-up and the client's bite as vanilla's own fangs do. */
@Mixin(EvokerFangs.class)
public interface EvokerFangsAccessor {
	@Accessor("warmupDelayTicks")
	void jaymoskingdoms$setWarmupDelayTicks(int ticks);

	@Accessor("clientSideAttackStarted")
	void jaymoskingdoms$setClientSideAttackStarted(boolean started);
}

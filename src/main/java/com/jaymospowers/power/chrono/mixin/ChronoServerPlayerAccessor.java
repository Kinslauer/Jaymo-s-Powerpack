package com.jaymospowers.power.chrono.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.20.50) Chrono's held-back death: the health last sent, so the real (held) health isn't sent over the death screen. */
@Mixin(ServerPlayer.class)
public interface ChronoServerPlayerAccessor {
	@Accessor("lastSentHealth")
	void jaymospowers$setLastSentHealth(float health);
}

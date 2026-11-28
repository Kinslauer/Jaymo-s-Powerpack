package com.jaymoskingdoms.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The parrots on a player's shoulders let down into the world, so a life's end can make strangers of them too ({@code ForgottenLives}). */
@Mixin(Player.class)
public interface PlayerShoulderInvoker {
	@Invoker("removeEntitiesOnShoulder")
	void jaymoskingdoms$removeEntitiesOnShoulder();
}

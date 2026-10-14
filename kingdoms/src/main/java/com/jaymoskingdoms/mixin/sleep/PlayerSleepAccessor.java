package com.jaymoskingdoms.mixin.sleep;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.7.4) Bed rest ({@link com.jaymoskingdoms.BedRest}) restarts a player's sleep counter on lying down and on falling asleep. */
@Mixin(Player.class)
public interface PlayerSleepAccessor {
	@Accessor("sleepCounter")
	void jaymoskingdoms$setSleepCounter(int ticks);
}

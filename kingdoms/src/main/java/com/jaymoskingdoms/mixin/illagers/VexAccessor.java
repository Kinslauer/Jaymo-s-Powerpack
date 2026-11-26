package com.jaymoskingdoms.mixin.illagers;

import net.minecraft.world.entity.monster.Vex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A surrendered (illagers/Surrendered) fades soon after its invoker dies. */
@Mixin(Vex.class)
public interface VexAccessor {
	@Accessor("limitedLifeTicks")
	int jaymoskingdoms$limitedLifeTicks();

	@Accessor("limitedLifeTicks")
	void jaymoskingdoms$setLimitedLifeTicks(int ticks);
}

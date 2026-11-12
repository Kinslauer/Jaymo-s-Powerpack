package com.jaymoskingdoms.mixin.jobs;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a hunter in the saddle draw a bow faster ({@code HunterBow}): the ticks left on the item in use. */
@Mixin(LivingEntity.class)
public interface HunterDrawAccessor {
	@Accessor("useItemRemaining")
	int jaymoskingdoms$left();

	@Accessor("useItemRemaining")
	void jaymoskingdoms$left(int ticks);
}

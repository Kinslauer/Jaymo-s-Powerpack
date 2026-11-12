package com.jaymoskingdoms.mixin.jobs;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets a hunter's bullseye shot ({@code HunterBow}) set how many things an arrow goes through. */
@Mixin(AbstractArrow.class)
public interface HunterArrowAccessor {
	@Invoker("setPierceLevel")
	void jaymoskingdoms$pierce(byte level);
}

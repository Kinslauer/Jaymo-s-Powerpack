package com.jaymospowers.gear.arrow.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets a drill head set how many extra targets its arrow goes through. */
@Mixin(AbstractArrow.class)
public interface ArrowPierceInvoker {
	@Invoker("setPierceLevel")
	void jaymospowers$setPierceLevel(byte level);
}

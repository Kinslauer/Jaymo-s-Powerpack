package com.jaymoskingdoms.mixin.illagers;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A marauder's thrown hatchet (illagers/ThrownHatchet) shines when its axe is enchanted, as a trident does. */
@Mixin(ThrownTrident.class)
public interface ThrownTridentAccessor {
	@Accessor("ID_FOIL")
	static EntityDataAccessor<Boolean> jaymoskingdoms$foil() {
		throw new AssertionError();
	}
}

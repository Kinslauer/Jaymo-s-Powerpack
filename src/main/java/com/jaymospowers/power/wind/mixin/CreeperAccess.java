package com.jaymospowers.power.wind.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A Stormbound or Thunderhead strike charges a creeper, as real lightning does (without lightning's own damage). */
@Mixin(Creeper.class)
public interface CreeperAccess {
	@Accessor("DATA_IS_POWERED")
	static EntityDataAccessor<Boolean> jaymospowers$powered() {
		throw new AssertionError();
	}
}

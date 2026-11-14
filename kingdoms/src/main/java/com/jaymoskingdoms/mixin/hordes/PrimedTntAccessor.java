package com.jaymoskingdoms.mixin.hordes;

import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A pirate lobber's powder keg blows smaller than a block of TNT ({@code ZombieHordes#KEG_POWER}). */
@Mixin(PrimedTnt.class)
public interface PrimedTntAccessor {
	@Accessor("explosionPower")
	void jaymoskingdoms$setExplosionPower(float power);
}

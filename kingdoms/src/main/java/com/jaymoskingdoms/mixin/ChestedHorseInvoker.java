package com.jaymoskingdoms.mixin;

import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** (0.7.13) A horse's inventory made again (to the size its chest gives it), so a roaming caravan's pack animals can be loaded ({@code RoamingCaravans}). */
@Mixin(AbstractHorse.class)
public interface ChestedHorseInvoker {
	@Invoker("createInventory")
	void jaymoskingdoms$createInventory();
}

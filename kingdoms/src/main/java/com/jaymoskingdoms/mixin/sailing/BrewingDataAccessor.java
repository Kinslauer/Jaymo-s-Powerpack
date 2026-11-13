package com.jaymoskingdoms.mixin.sailing;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A brewing stand aboard a ship opens from her deck ({@code ShipWorks#menu}): its menu wants the stand's brew clock and fuel. */
@Mixin(BrewingStandBlockEntity.class)
public interface BrewingDataAccessor {
	@Accessor("dataAccess")
	ContainerData jaymoskingdoms$data();
}

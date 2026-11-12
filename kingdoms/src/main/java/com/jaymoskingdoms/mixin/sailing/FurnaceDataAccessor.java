package com.jaymoskingdoms.mixin.sailing;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A furnace aboard a ship opens from her deck ({@code ShipWorks#menu}): its menu wants the furnace's burn and cook clocks. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceDataAccessor {
	@Accessor("dataAccess")
	ContainerData jaymoskingdoms$data();
}

package com.jaymoskitchen.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reaches a village's processor list, to plant our crops in its farms ({@code DelightVillages}). */
@Mixin(StructureProcessorList.class)
public interface StructureProcessorListAccessor {
	@Mutable
	@Accessor("list")
	void jaymoskitchen$setList(List<StructureProcessor> processors);
}

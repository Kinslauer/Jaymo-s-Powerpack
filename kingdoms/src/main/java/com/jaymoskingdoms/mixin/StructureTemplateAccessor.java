package com.jaymoskingdoms.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.7.533) A template's palettes, the blocks it saved (structure voids aren't saved), for {@code BuriedRooms}. */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
	@Accessor("palettes")
	List<StructureTemplate.Palette> jaymoskingdoms$palettes();
}

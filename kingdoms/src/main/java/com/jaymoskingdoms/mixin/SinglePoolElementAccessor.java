package com.jaymoskingdoms.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.7.533) A jigsaw piece's template (by id, or inline), for {@code BuriedRooms}. */
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccessor {
	@Accessor("template")
	Either<Identifier, StructureTemplate> jaymoskingdoms$template();
}

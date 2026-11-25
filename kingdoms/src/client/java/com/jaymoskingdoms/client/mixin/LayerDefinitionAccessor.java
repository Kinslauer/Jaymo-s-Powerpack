package com.jaymoskingdoms.client.mixin;

import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The illager kinds' bodies (client/illagers) are vanilla's illager mesh with parts added or swapped. */
@Mixin(LayerDefinition.class)
public interface LayerDefinitionAccessor {
	@Accessor("mesh")
	MeshDefinition jaymoskingdoms$mesh();
}

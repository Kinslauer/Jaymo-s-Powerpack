package com.jaymospowers.client.mixin;

import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.17.18) A leg's boxes, swapped for bent ones while it is drawn on one knee (see KneeBend). */
@Mixin(ModelPart.class)
public interface ModelPartCubesAccessor {
	@Accessor("cubes")
	List<ModelPart.Cube> jaymospowers$cubes();

	@Mutable
	@Accessor("cubes")
	void jaymospowers$setCubes(List<ModelPart.Cube> cubes);
}

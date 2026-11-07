package com.jaymoskitchen.client.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.5.40) A model's parts by name and their boxes, so a dead body's loose parts can hang (see LooseParts). */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
	@Accessor("children")
	Map<String, ModelPart> jaymoskitchen$children();

	@Accessor("cubes")
	List<ModelPart.Cube> jaymoskitchen$cubes();
}

package com.jaymospowers.client.mixin;

import java.util.function.Supplier;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.20.41) One drawn layer of an item: how it's placed and how big it is, for the blood laid over it (BloodItemOverlay). */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemLayerRenderStateAccessor {
	@Accessor("itemTransform")
	ItemTransform jaymospowers$itemTransform();

	@Accessor("localTransform")
	Matrix4f jaymospowers$localTransform();

	@Accessor("extents")
	Supplier<Vector3fc[]> jaymospowers$extents();

	@Accessor("specialRenderer")
	SpecialModelRenderer<Object> jaymospowers$specialRenderer();
}

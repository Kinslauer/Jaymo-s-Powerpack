package com.jaymospowers.client.mixin;

import java.util.List;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.15.32) A renderer's layers, so the costume can find MCA's body layers on it (see CostumeCover). */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererLayersAccessor {
	@Accessor("layers")
	List<RenderLayer<?, ?>> jaymospowers$layers();
}

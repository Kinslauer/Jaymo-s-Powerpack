package com.jaymospowers.client.power.gravity.mixin;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A post pass's settings blocks by name (see LensClient). */
@Mixin(PostPass.class)
public interface LensPostPassAccessor {
	@Accessor("customUniforms")
	Map<String, GpuBuffer> jaymospowers$customUniforms();
}

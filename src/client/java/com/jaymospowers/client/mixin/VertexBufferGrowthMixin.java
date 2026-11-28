package com.jaymospowers.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A vertex buffer that runs out of room grows by half its size instead of vanilla's flat 2 MB.
 * <p>
 * Vanilla copies the whole buffer every time it grows, so a big frame (a felled forest, a field of bodies) that needs
 * the shared world buffer to reach a few hundred MB copies it dozens of times over; growing by half reaches any size in
 * a handful of copies. (Andy's spark profile of 2026-11-28 had 58% of the render thread inside that copy.)
 */
@Mixin(ByteBufferBuilder.class)
public abstract class VertexBufferGrowthMixin {
	@Shadow
	private long capacity;

	@WrapOperation(method = "ensureCapacity", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(JJJ)J"))
	private long jaymospowers$growByHalf(long grown, long required, long max, Operation<Long> original) {
		return original.call(Math.max(grown, this.capacity + (this.capacity >> 1)), required, max);
	}
}

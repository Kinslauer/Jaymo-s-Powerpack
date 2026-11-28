package com.jaymospowers.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.StagedVertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The world's shared vertex buffer's staging memory and the vertex builder left open on it, for {@link SharedBufferLeakMixin}. */
@Mixin(StagedVertexBuffer.class)
public interface StagedVertexBufferAccessor {
	@Accessor("stagingBuffer")
	ByteBufferBuilder jaymospowers$stagingBuffer();

	@Accessor("lastVertexBuilder")
	BufferBuilder jaymospowers$lastVertexBuilder();

	@Accessor("lastVertexBuilder")
	void jaymospowers$setLastVertexBuilder(BufferBuilder builder);

	@Accessor("lastBuildingDraw")
	void jaymospowers$setLastBuildingDraw(StagedVertexBuffer.Draw draw);
}

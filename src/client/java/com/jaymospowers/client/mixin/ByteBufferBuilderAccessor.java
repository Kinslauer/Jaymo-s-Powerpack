package com.jaymospowers.client.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A vertex buffer's bookkeeping, for {@link SharedBufferLeakMixin}. */
@Mixin(ByteBufferBuilder.class)
public interface ByteBufferBuilderAccessor {
	@Accessor("capacity")
	long jaymospowers$capacity();

	@Accessor("writeOffset")
	long jaymospowers$writeOffset();

	@Accessor("writeOffset")
	void jaymospowers$setWriteOffset(long offset);

	@Accessor("nextResultOffset")
	void jaymospowers$setNextResultOffset(long offset);

	@Accessor("resultCount")
	int jaymospowers$resultCount();

	@Accessor("resultCount")
	void jaymospowers$setResultCount(int count);

	@Accessor("generation")
	int jaymospowers$generation();

	@Accessor("generation")
	void jaymospowers$setGeneration(int generation);
}

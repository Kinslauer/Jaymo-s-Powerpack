# smoother frames: the shared vertex buffer no longer regrows every frame
- Andy's spark profile (2026-11-28, after a graveyard horde) spent 58% of the render thread copying the world's one shared vertex buffer as it grew, and the share rose through the capture: the buffer was still growing every frame.
- **Let go at frame end** (`SharedBufferLeakMixin`, on `RenderBuffers.endFrame`): whatever is still held in the shared buffer after the frame (a vertex builder left open, slices never handed back) is let go, so the buffer empties every frame as vanilla means it to. The first time it happens the log says how much was held ("the shared vertex buffer still held ..."), and the log warns if the buffer ever passes 512 MB.
- **Grows by half** (`VertexBufferGrowthMixin`): a vertex buffer that runs out of room grows by half its size instead of vanilla's flat 2 MB, so a big frame reaches its size in a few copies instead of dozens.
- Accessors: `ByteBufferBuilderAccessor`, `StagedVertexBufferAccessor`.
- Built; not played.

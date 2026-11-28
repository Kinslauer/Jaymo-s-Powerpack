package com.jaymospowers.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.StagedVertexBuffer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * At the end of every frame, the world's shared vertex buffer is emptied of anything that outlived the frame.
 * <p>
 * Every vertex drawn in the world (blocks of felled trees, bodies, particles, puddles) is written into this one buffer,
 * which vanilla empties only once every slice written into it that frame has been handed back. A slice nobody hands back
 * (a mod writing outside the frame, whose draw vanilla drops at the frame's end) keeps it from ever emptying: it then
 * grows every frame, and every growth copies it whole, so the game slows the longer it runs and its memory climbs.
 * Andy's spark profile of 2026-11-28 had 58% of the render thread inside that copy, rising through the capture.
 * <p>
 * By the end of the frame vanilla has uploaded and freed every slice it will draw, so whatever is still held then can
 * never be drawn: an open vertex builder is closed, and held slices are let go (their generation goes stale, so a
 * late reader gets vanilla's "no longer valid" error rather than another frame's vertices). The first time, the log
 * says how much was held.
 */
@Mixin(RenderBuffers.class)
public abstract class SharedBufferLeakMixin {
	@Unique
	private static final Logger JAYMOSPOWERS_LOG = LogUtils.getLogger();
	@Unique
	private static boolean jaymospowers$warned;
	@Unique
	private static long jaymospowers$bigWarned = 512L << 20;

	@Shadow
	@Final
	private StagedVertexBuffer stagedVertexBuffer;

	@Inject(method = "endFrame", at = @At("TAIL"))
	private void jaymospowers$letGoOfLeftovers(CallbackInfo ci) {
		StagedVertexBufferAccessor staged = (StagedVertexBufferAccessor) this.stagedVertexBuffer;
		BufferBuilder open = staged.jaymospowers$lastVertexBuilder();
		int closed = 0;
		if (open != null) {
			staged.jaymospowers$setLastVertexBuilder(null);
			staged.jaymospowers$setLastBuildingDraw(null);
			try {
				MeshData mesh = open.build();
				if (mesh != null) {
					mesh.close();
					closed = 1;
				}
			} catch (IllegalStateException ignored) {
				// already built: nothing of its is held
			}
		}

		ByteBufferBuilder buffer = staged.jaymospowers$stagingBuffer();
		ByteBufferBuilderAccessor memory = (ByteBufferBuilderAccessor) buffer;
		int held = memory.jaymospowers$resultCount();
		long used = memory.jaymospowers$writeOffset();
		if (held > 0 || used > 0) {
			if (!jaymospowers$warned) {
				jaymospowers$warned = true;
				JAYMOSPOWERS_LOG.warn("jaymospowers: the shared vertex buffer still held {} slice(s), {} KB, after the frame ({} builder left open); letting go (capacity {} MB)",
					held, used >> 10, closed, memory.jaymospowers$capacity() >> 20);
			}
			memory.jaymospowers$setResultCount(0);
			memory.jaymospowers$setWriteOffset(0L);
			memory.jaymospowers$setNextResultOffset(0L);
			if (held > 0) {
				memory.jaymospowers$setGeneration(memory.jaymospowers$generation() + 1);
			}
		}

		long capacity = memory.jaymospowers$capacity();
		if (capacity >= jaymospowers$bigWarned) {
			jaymospowers$bigWarned = capacity * 2;
			JAYMOSPOWERS_LOG.warn("jaymospowers: the shared vertex buffer has grown to {} MB", capacity >> 20);
		}
	}
}

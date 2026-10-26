package com.jaymospowers.alchemy;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Flux: the magic every alchemical work leaves behind, pooled per chunk. Dissolving things in a crucible, spilling one,
 * an infusion going wrong, essence enchanting and the rod's foci all add some here; what it then does to the land
 * (blight) and to the people in it (corruption) is {@link com.jaymospowers.alchemy.flux.FluxEffects}.
 * This class only keeps the numbers.
 */
public final class Flux {
	/** The most flux one chunk holds; what would go past it is pushed into the neighbours by the spreading. */
	public static final int MAX = 2000;

	/** Per chunk, saved with it. */
	public static final AttachmentType<Integer> FLUX = AttachmentRegistry.create(JaymosPowers.id("alchemy_flux"),
		builder -> builder.persistent(Codec.INT).initializer(() -> 0));

	private Flux() {
	}

	public static int get(ServerLevel level, BlockPos pos) {
		Integer n = level.getChunkAt(pos).getAttached(FLUX);
		return n == null ? 0 : n;
	}

	/** Adds flux to the chunk at {@code pos} (capped at {@link #MAX}). */
	public static void add(ServerLevel level, BlockPos pos, int amount) {
		if (amount <= 0) {
			return;
		}
		LevelChunk chunk = level.getChunkAt(pos);
		Integer n = chunk.getAttached(FLUX);
		chunk.setAttached(FLUX, Math.min(MAX, (n == null ? 0 : n) + amount));
		chunk.markUnsaved();
	}

	/** Takes up to {@code amount} flux out of the chunk at {@code pos}; returns how much was there to take. */
	public static int take(ServerLevel level, BlockPos pos, int amount) {
		LevelChunk chunk = level.getChunkAt(pos);
		Integer n = chunk.getAttached(FLUX);
		int have = n == null ? 0 : n;
		int took = Math.min(have, Math.max(0, amount));
		if (took > 0) {
			chunk.setAttached(FLUX, have - took);
			chunk.markUnsaved();
		}
		return took;
	}
}

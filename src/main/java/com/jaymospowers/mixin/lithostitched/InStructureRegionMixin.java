package com.jaymospowers.mixin.lithostitched;

import com.jaymospowers.mixin.WorldGenRegionStepAccessor;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (2026-11-26) A new single-player world froze on "Saving world" / before spawn, twice in 25 minutes on the Deck: all
 * three C2ME world-gen workers sat in Lithostitched's {@code lithostitched:in_structure} block predicate, and the server
 * thread sat waiting for a chunk only those workers could make. Lithostitched 2.0.6 answers "is this spot inside a
 * structure?" by asking the live world ({@code WorldGenLevel.getLevel().getChunk(x, z, STRUCTURE_REFERENCES)}) for every
 * chunk the search box touches, from inside world gen. A chunk two over from the one being decorated may not be that far
 * along yet, so the worker hands the request to the server thread and waits; the server thread asks the workers to make
 * it, and when every worker is waiting the same way nothing moves again. Only Enderscape's End features use the
 * predicate (void lakes, celestial chanterelles, magnia arches and towers, mirestone pillars, veiled trees: "don't grow
 * inside an End city"), and One Dimension runs them in every stacked Overworld chunk with an End band, so it happens
 * while the first chunks are made. The real End and the server have the same risk.
 *
 * <p>When the predicate is tested during world gen (the level is a {@link WorldGenRegion}, One Dimension's band regions
 * included), this answers it from the chunks the region already holds, which never waits: the structure references of
 * the chunks it holds that far along (the decorated chunk and the ring around it), then those references' starts, and
 * for a box that reaches past that ring (magnia arches look 24 blocks out) the starts of every chunk the region holds.
 * Same answer as Lithostitched's for every structure that starts within the region (eight chunks out for features),
 * which covers End cities near an End feature; it also adds up the references of every chunk it looks at, where
 * Lithostitched's map kept only the last chunk's. Outside world gen (a level that isn't a region) Lithostitched runs as
 * it is. Lithostitched is MIT; nothing of it is copied. Without Lithostitched (the dev game) this applies to nothing.
 */
@Pseudo
@Mixin(targets = "dev.worldgen.lithostitched.impl.worldgen.blockpredicate.InStructurePredicate", remap = false)
abstract class InStructureRegionMixin {
	@Shadow
	@Final
	private Optional<Holder<Structure>> structure;

	@Inject(method = "test(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;intersectingChunks()Ljava/util/stream/Stream;"),
			cancellable = true, require = 0)
	private void jaymospowers$fromRegion(LevelAccessor level, BlockPos pos, CallbackInfoReturnable<Boolean> cir,
			@Local BoundingBox box) {
		if (level instanceof WorldGenRegion region) {
			Structure wanted = this.structure.isPresent() ? this.structure.get().value() : null;
			cir.setReturnValue(jaymospowers$inStructure(region, box, wanted));
		}
	}

	/** Whether a piece of {@code wanted} (any structure when null) lies in {@code box}, read only from the region. */
	@Unique
	private static boolean jaymospowers$inStructure(WorldGenRegion region, BoundingBox box, Structure wanted) {
		ChunkDependencies held = ((WorldGenRegionStepAccessor) region).jaymospowers$step().directDependencies();
		ChunkPos center = region.getCenter();
		int minX = SectionPos.blockToSectionCoord(box.minX());
		int maxX = SectionPos.blockToSectionCoord(box.maxX());
		int minZ = SectionPos.blockToSectionCoord(box.minZ());
		int maxZ = SectionPos.blockToSectionCoord(box.maxZ());
		boolean pastReferences = false;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				ChunkAccess chunk = jaymospowers$held(region, held, center, x, z, ChunkStatus.STRUCTURE_REFERENCES);
				if (chunk == null) {
					pastReferences = true;
					continue;
				}
				for (Map.Entry<Structure, LongSet> refs : chunk.getAllReferences().entrySet()) {
					if (wanted != null && !wanted.equals(refs.getKey())) {
						continue;
					}
					LongIterator starts = refs.getValue().iterator();
					while (starts.hasNext()) {
						long start = starts.nextLong();
						ChunkAccess from = jaymospowers$held(region, held, center, ChunkPos.getX(start), ChunkPos.getZ(start), ChunkStatus.STRUCTURE_STARTS);
						if (from != null && jaymospowers$touches(from.getStartForStructure(refs.getKey()), box)) {
							return true;
						}
					}
				}
			}
		}
		if (pastReferences) {
			// Part of the box lies past the chunks whose references the region holds: look at every start it holds.
			int radius = held.size() - 1;
			for (int x = center.x() - radius; x <= center.x() + radius; x++) {
				for (int z = center.z() - radius; z <= center.z() + radius; z++) {
					ChunkAccess chunk = jaymospowers$held(region, held, center, x, z, ChunkStatus.STRUCTURE_STARTS);
					if (chunk == null) {
						continue;
					}
					for (Map.Entry<Structure, StructureStart> start : chunk.getAllStarts().entrySet()) {
						if ((wanted == null || wanted.equals(start.getKey())) && jaymospowers$touches(start.getValue(), box)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/** The region's chunk at x, z if it holds it at least at {@code status}, else null; never waits or throws. */
	@Unique
	private static ChunkAccess jaymospowers$held(WorldGenRegion region, ChunkDependencies held, ChunkPos center, int x, int z, ChunkStatus status) {
		int distance = center.getChessboardDistance(x, z);
		if (distance >= held.size() || !held.get(distance).isOrAfter(status)) {
			return null;
		}
		return region.getChunk(x, z, status, false);
	}

	/** Lithostitched's test: a valid start with a piece whose box meets {@code box}. */
	@Unique
	private static boolean jaymospowers$touches(StructureStart start, BoundingBox box) {
		if (start == null || !start.isValid() || !start.getBoundingBox().intersects(box)) {
			return false;
		}
		for (StructurePiece piece : start.getPieces()) {
			if (piece.getBoundingBox().intersects(box)) {
				return true;
			}
		}
		return false;
	}
}

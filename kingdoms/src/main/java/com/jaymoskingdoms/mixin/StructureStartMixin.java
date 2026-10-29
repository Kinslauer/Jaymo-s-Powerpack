package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Foundations;
import com.jaymoskingdoms.GolemLairs;
import com.jaymoskingdoms.Hearths;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After a structure's pieces are placed in a chunk, the gaps under any that float are filled ({@link Foundations}), and a
 * village's fires get stone hearths and chimneys ({@link Hearths}), and (0.7.330) dungeons may get a rogue clockwork golem
 * ({@link GolemLairs}).
 */
@Mixin(StructureStart.class)
abstract class StructureStartMixin {
	@Inject(method = "placeInChunk", at = @At("RETURN"))
	private void jaymoskingdoms$foundations(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
		BoundingBox chunkBB, ChunkPos chunkPos, CallbackInfo ci) {
		StructureStart self = (StructureStart)(Object)this;
		Foundations.fill(level, self.getStructure(), self.getPieces(), chunkBB);
		Hearths.build(level, self.getStructure(), self.getPieces(), chunkBB);
		GolemLairs.place(level, self, chunkBB);
	}
}

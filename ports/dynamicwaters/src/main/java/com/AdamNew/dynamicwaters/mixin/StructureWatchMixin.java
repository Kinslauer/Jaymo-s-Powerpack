package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.ColumnWatch;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Column watch: names each structure that changes a watched soil top as it is placed in a chunk. */
@Mixin(StructureStart.class)
public class StructureWatchMixin {
    @Unique
    private static final ThreadLocal<Object[]> DYNAMICWATERS$SNAPSHOT = new ThreadLocal<>();

    @Inject(method = "placeInChunk", at = @At("HEAD"))
    private void dynamicwaters$watchStart(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunkPos, CallbackInfo ci) {
        DYNAMICWATERS$SNAPSHOT.set(ColumnWatch.before(level, chunkPos.x(), chunkPos.z()));
    }

    @Inject(method = "placeInChunk", at = @At("RETURN"))
    private void dynamicwaters$watchEnd(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunkPos, CallbackInfo ci) {
        final Object[] snapshot = DYNAMICWATERS$SNAPSHOT.get();
        DYNAMICWATERS$SNAPSHOT.remove();
        if (snapshot == null) return;
        final StructureStart self = (StructureStart) (Object) this;
        ColumnWatch.after(level, snapshot, "structure " + level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(self.getStructure()));
    }
}

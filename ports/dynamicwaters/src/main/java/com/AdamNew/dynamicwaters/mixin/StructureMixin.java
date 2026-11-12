package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.RiverClearance;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

@Mixin(Structure.class)
public class StructureMixin {
    @Inject(method = "generate", at = @At("RETURN"), cancellable = true)
    private void dynamicwaters$cancelStructureInRiver(
            net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure> pSelected,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> pDimension,
            RegistryAccess pRegistryAccess,
            ChunkGenerator pChunkGenerator,
            BiomeSource pBiomeSource,
            net.minecraft.world.level.biome.Climate.Sampler pClimateSampler,
            RandomState pRandomState,
            StructureTemplateManager pStructureTemplateManager,
            long pSeed,
            ChunkPos pChunkPos,
            int pReferences,
            LevelHeightAccessor pLevel,
            Predicate<Holder<Biome>> pValidBiome,
            CallbackInfoReturnable<StructureStart> cir) {
        StructureStart start = cir.getReturnValue();
        if (start != null && start.isValid()) {
            if (pChunkGenerator != HydrologyManager.getGlobalGenerator()) {
                return;
            }

            if (((Structure) (Object) this).type() == StructureType.STRONGHOLD) {
                return;
            }

            if (com.AdamNew.dynamicwaters.AbridgedCompat.isBridge((Structure) (Object) this)) {
                return;
            }
            HydrologyManager.setSeed(pSeed);

            start = RiverClearance.clear(start, pChunkGenerator, pRandomState);
            if (!start.isValid()) {
                cir.setReturnValue(StructureStart.INVALID_START);
                return;
            }

            boolean tooClose = false;

            BoundingBox box = start.getBoundingBox();

            int cx = (box.minX() + box.maxX()) / 2;
            int cz = (box.minZ() + box.maxZ()) / 2;
            int radius = Math.max(box.maxX() - box.minX(), box.maxZ() - box.minZ()) / 2;

            if (HydrologyManager.isNearMountainWater(cx, cz, radius)) {
                if (HydrologyManager.isTooCloseToMountainWater(box.minX(), box.maxX(), box.minZ(), box.maxZ(), box.minY(), box.maxY(), pChunkGenerator, pLevel, pRandomState)) {
                    tooClose = true;
                } else {
                    for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
                        BoundingBox pBox = piece.getBoundingBox();
                        if (HydrologyManager.isTooCloseToMountainWater(pBox.minX(), pBox.maxX(), pBox.minZ(), pBox.maxZ(), pBox.minY(), pBox.maxY(), pChunkGenerator, pLevel, pRandomState)) {
                            tooClose = true;
                            break;
                        }
                    }
                }
            }

            cir.setReturnValue(tooClose ? StructureStart.INVALID_START : start);
        }
    }
}

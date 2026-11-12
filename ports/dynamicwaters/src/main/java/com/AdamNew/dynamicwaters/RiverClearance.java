package com.AdamNew.dynamicwaters;

import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

import java.util.ArrayList;
import java.util.List;

public final class RiverClearance {
    private static final int SAMPLE_STEP = 4;

    private static final int CUT_DEPTH = 2;

    private static final int STANDS_ON = 32;

    private static final int UNDER_FLOOR = 2;

    private static final int DEEPEST_BED = 9;

    private RiverClearance() {
    }

    public static StructureStart clear(StructureStart start, ChunkGenerator generator, RandomState randomState) {
        if (!Config.dynamicNormalRivers()) return start;

        final BoundingBox all = start.getBoundingBox();
        MainRivers.findNearest((all.minX() + all.maxX()) / 2.0, (all.minZ() + all.maxZ()) / 2.0, generator,
                HydrologyManager.getDummyAccessor(), randomState);
        final boolean mineshaft = start.getStructure().type() == StructureType.MINESHAFT;
        final int seaLevel = generator.getSeaLevel();
        final List<StructurePiece> pieces = start.getPieces();
        List<StructurePiece> kept = null;
        for (int i = 0; i < pieces.size(); i++) {
            final StructurePiece piece = pieces.get(i);
            if (!reached(piece.getBoundingBox(), seaLevel, generator, randomState)) {
                if (kept != null) kept.add(piece);
                continue;
            }
            if (!mineshaft) return StructureStart.INVALID_START;
            if (kept == null) kept = new ArrayList<>(pieces.subList(0, i));
        }
        if (kept == null) return start;
        if (kept.isEmpty()) return StructureStart.INVALID_START;
        return new StructureStart(start.getStructure(), start.getChunkPos(), start.getReferences(),
                new PiecesContainer(kept));
    }

    private static boolean reached(BoundingBox box, int seaLevel, ChunkGenerator generator, RandomState randomState) {
        if (box.maxY() < seaLevel - 1 - DEEPEST_BED - UNDER_FLOOR) return false;
        for (int x = box.minX(); ; x = Math.min(x + SAMPLE_STEP, box.maxX())) {
            for (int z = box.minZ(); ; z = Math.min(z + SAMPLE_STEP, box.maxZ())) {
                if (reachedAt(box, x, z, seaLevel, generator, randomState)) return true;
                if (z == box.maxZ()) break;
            }
            if (x == box.maxX()) break;
        }
        return false;
    }

    private static boolean reachedAt(BoundingBox box, int x, int z, int seaLevel, ChunkGenerator generator,
            RandomState randomState) {
        if (!HydrologyManager.lowlandMayReach(x, z)) return false;

        final int ground = Mth.floor(HydrologyManager.coarseGroundAt(x, z, generator, randomState)) - 1;
        final HydrologyManager.RiverCarving cut = HydrologyManager.lowlandCut(x, z, ground, seaLevel, generator,
                randomState);
        if (!cut.modifies) return false;

        final int top = Math.min(ground, cut.cavernRoofY);
        if (!cut.isMainRiverWater && cut.targetY > top - CUT_DEPTH) return false;
        return box.minY() <= top + STANDS_ON && box.maxY() >= cut.targetY - UNDER_FLOOR;
    }
}

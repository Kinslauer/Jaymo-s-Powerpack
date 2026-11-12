package com.AdamNew.dynamicwaters;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class AbridgedCompat {
    private static final Identifier BRIDGE = Identifier.fromNamespaceAndPath("abridged", "bridge");

    private static final int UNDER_SEA = 61;
    private static final int CENTRE_REACH = 3;

    private static final double BRIDGE_REACH = 64.0;

    private AbridgedCompat() {
    }

    public static boolean active() {
        return !Config.vanillaNormalRivers();
    }

    public static boolean isBridge(Structure structure) {
        return BRIDGE.equals(BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type()));
    }

    public static boolean overWater(Structure.GenerationContext context) {
        final ChunkGenerator generator = context.chunkGenerator();
        final RandomState randomState = context.randomState();
        final int cx = context.chunkPos().getMiddleBlockX(), cz = context.chunkPos().getMiddleBlockZ();
        boolean low = true;
        for (int k = 0; k < 4 && low; k++) {
            low = generator.getFirstFreeHeight(cornerX(cx, k), cornerZ(cz, k), Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), randomState) <= UNDER_SEA;
        }
        if (low) return true;
        if (!Config.dynamicNormalRivers() || generator != HydrologyManager.getGlobalGenerator()) return false;

        MainRivers.findNearest(cx, cz, generator, HydrologyManager.getDummyAccessor(), randomState);
        for (int k = 0; k < 4; k++) {
            final HydrologyManager.RiverCarving cut = cutAt(cornerX(cx, k), cornerZ(cz, k), generator, randomState);
            if (cut == null || !cut.isMainRiverWater || cut.targetY + 1 > UNDER_SEA) return false;
        }
        return !HydrologyManager.nearRiverHead(cx, cz, BRIDGE_REACH);
    }

    public static int groundAsCut(int x, int z, int height, Structure.GenerationContext context) {
        final ChunkGenerator generator = context.chunkGenerator();
        if (!Config.dynamicNormalRivers() || generator != HydrologyManager.getGlobalGenerator()) return height;
        final HydrologyManager.RiverCarving cut = cutAt(x, z, generator, context.randomState());
        if (cut == null) return height;
        return Math.min(height, cut.isMainRiverWater ? UNDER_SEA : cut.targetY + 1);
    }

    private static HydrologyManager.RiverCarving cutAt(int x, int z, ChunkGenerator generator, RandomState randomState) {
        if (!HydrologyManager.lowlandMayReach(x, z)) return null;
        final int ground = Mth.floor(HydrologyManager.coarseGroundAt(x, z, generator, randomState)) - 1;
        final HydrologyManager.RiverCarving cut = HydrologyManager.lowlandCut(x, z, ground, generator.getSeaLevel(),
                generator, randomState);
        return cut.modifies && cut.cavernRoofY >= ground ? cut : null;
    }

    private static int cornerX(int cx, int k) {
        return cx + ((k & 1) == 0 ? -CENTRE_REACH : CENTRE_REACH);
    }

    private static int cornerZ(int cz, int k) {
        return cz + ((k & 2) == 0 ? -CENTRE_REACH : CENTRE_REACH);
    }
}

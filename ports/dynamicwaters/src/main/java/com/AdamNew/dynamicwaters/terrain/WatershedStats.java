package com.AdamNew.dynamicwaters.terrain;

import java.util.concurrent.atomic.LongAdder;

public final class WatershedStats {
    public static final LongAdder baseHeightCalls = new LongAdder();
    public static final LongAdder baseHeightNanos = new LongAdder();
    public static final LongAdder heightBlocksBuilt = new LongAdder();

    public static final LongAdder tileBuilds = new LongAdder();
    public static final LongAdder tileNanos = new LongAdder();
    public static final LongAdder tileSampleNanos = new LongAdder();
    public static final LongAdder tileFloodNanos = new LongAdder();
    public static final LongAdder tileExtractNanos = new LongAdder();
    public static final LongAdder tileWaits = new LongAdder();

    public static final LongAdder pathsEmitted = new LongAdder();

    public static final LongAdder endSea = new LongAdder();
    public static final LongAdder endMainRiver = new LongAdder();
    public static final LongAdder endLake = new LongAdder();
    public static final LongAdder endJunction = new LongAdder();
    public static final LongAdder endNowhere = new LongAdder();

    public static final LongAdder mouthsWalked = new LongAdder();
    public static final LongAdder mouthsFellBack = new LongAdder();

    public static final LongAdder necksCut = new LongAdder();
    public static final LongAdder foldsEased = new LongAdder();

    public static final LongAdder joinsAlongside = new LongAdder();

    public static final LongAdder fusesWide = new LongAdder();
    public static final LongAdder lakesEmitted = new LongAdder();

    public static final LongAdder lakesSettled = new LongAdder();
    public static final LongAdder lakesDropped = new LongAdder();
    public static final LongAdder lakesUndercut = new LongAdder();

    public static final LongAdder lakesClosed = new LongAdder();

    public static final LongAdder lakesCapped = new LongAdder();

    public static final LongAdder deadEndLakes = new LongAdder();
    public static final LongAdder deadEndsLeft = new LongAdder();

    public static final LongAdder seamLakes = new LongAdder();

    public static final LongAdder riverBiomeAsked = new LongAdder();
    public static final LongAdder riverBiomeChunks = new LongAdder();
    public static final LongAdder riverBiomeColumns = new LongAdder();
    public static final LongAdder riverBiomeNanos = new LongAdder();

    public static final LongAdder iceFloeTicks = new LongAdder();
    public static final LongAdder iceFloeNanos = new LongAdder();

    public static final LongAdder iceFloeLooks = new LongAdder();
    public static final LongAdder iceFloeOverlaps = new LongAdder();
    public static final LongAdder iceFloeCrowded = new LongAdder();

    public static final LongAdder riverLogsAsked = new LongAdder();
    public static final LongAdder riverLogs = new LongAdder();
    public static final LongAdder riverLogNanos = new LongAdder();
    public static final LongAdder deadEndStreams = new LongAdder();

    public static final LongAdder lakeHollowsFlooded = new LongAdder();
    public static final LongAdder lakeHollowsHeld = new LongAdder();

    public static final LongAdder seamSidesTaken = new LongAdder();
    public static final LongAdder seamSidesPooled = new LongAdder();
    public static final LongAdder seamSidesKept = new LongAdder();
    public static final LongAdder seamNeighbourRoutes = new LongAdder();

    public static final LongAdder seamCyclesBroken = new LongAdder();
    public static final LongAdder seamCyclesLeft = new LongAdder();
    public static final LongAdder seamFlowLoops = new LongAdder();
    public static final LongAdder seamCycleRoundTrips = new LongAdder();

    public static final LongAdder seamExitsHeld = new LongAdder();

    public static final LongAdder seamPassingThrough = new LongAdder();

    public static final LongAdder seamPassingClimb = new LongAdder();

    public static final LongAdder sharedLakeUndercuts = new LongAdder();

    public static final LongAdder deadEndOutletsClosed = new LongAdder();

    public static final LongAdder seamWidenedAsTold = new LongAdder();

    public static final LongAdder loweredAlongside = new LongAdder();

    public static final LongAdder fastColumns = new LongAdder();
    public static final LongAdder rockColumns = new LongAdder();

    public static final LongAdder caveSpikesFilled = new LongAdder();

    public static final LongAdder bankSkins = new LongAdder();

    public static final LongAdder waterPlants = new LongAdder();

    public static final LongAdder gorgeNodes = new LongAdder();
    public static final java.util.concurrent.atomic.AtomicInteger maxGorge = new java.util.concurrent.atomic.AtomicInteger();

    public static final LongAdder underStretches = new LongAdder();
    public static final LongAdder underCells = new LongAdder();

    public static final LongAdder carveCalls = new LongAdder();
    public static final LongAdder carveNanos = new LongAdder();

    public static final LongAdder lowlandPassNanos = new LongAdder();

    public static final LongAdder lowlandRegions = new LongAdder();
    public static final LongAdder lowlandRegionNanos = new LongAdder();
    public static final LongAdder lowlandShapeNanos = new LongAdder();

    public static final LongAdder lowlandGrown = new LongAdder();
    public static final LongAdder lowlandYielded = new LongAdder();

    private WatershedStats() {
    }

    public static void reset() {
        baseHeightCalls.reset();
        baseHeightNanos.reset();
        heightBlocksBuilt.reset();
        tileBuilds.reset();
        tileNanos.reset();
        tileSampleNanos.reset();
        tileFloodNanos.reset();
        tileExtractNanos.reset();
        tileWaits.reset();
        pathsEmitted.reset();
        endSea.reset();
        endMainRiver.reset();
        endLake.reset();
        endJunction.reset();
        endNowhere.reset();
        mouthsWalked.reset();
        mouthsFellBack.reset();
        necksCut.reset();
        foldsEased.reset();
        joinsAlongside.reset();
        fusesWide.reset();
        lakesEmitted.reset();
        lakesSettled.reset();
        lakesDropped.reset();
        lakesUndercut.reset();
        lakesClosed.reset();
        lakesCapped.reset();
        deadEndLakes.reset();
        deadEndsLeft.reset();
        seamLakes.reset();
        riverBiomeAsked.reset();
        riverBiomeChunks.reset();
        riverBiomeColumns.reset();
        riverBiomeNanos.reset();
        iceFloeTicks.reset();
        iceFloeNanos.reset();
        iceFloeLooks.reset();
        iceFloeOverlaps.reset();
        iceFloeCrowded.reset();
        riverLogsAsked.reset();
        riverLogs.reset();
        riverLogNanos.reset();
        deadEndStreams.reset();
        lakeHollowsFlooded.reset();
        lakeHollowsHeld.reset();
        seamSidesTaken.reset();
        seamSidesPooled.reset();
        seamSidesKept.reset();
        seamNeighbourRoutes.reset();
        seamCyclesBroken.reset();
        seamCyclesLeft.reset();
        seamFlowLoops.reset();
        seamCycleRoundTrips.reset();
        seamExitsHeld.reset();
        seamPassingThrough.reset();
        seamPassingClimb.reset();
        sharedLakeUndercuts.reset();
        deadEndOutletsClosed.reset();
        seamWidenedAsTold.reset();
        loweredAlongside.reset();
        fastColumns.reset();
        rockColumns.reset();
        caveSpikesFilled.reset();
        bankSkins.reset();
        waterPlants.reset();
        gorgeNodes.reset();
        maxGorge.set(0);
        underStretches.reset();
        underCells.reset();
        carveCalls.reset();
        carveNanos.reset();
        lowlandPassNanos.reset();
        lowlandRegions.reset();
        lowlandRegionNanos.reset();
        lowlandShapeNanos.reset();
        lowlandGrown.reset();
        lowlandYielded.reset();
    }

    public static String summary() {
        long tiles = tileBuilds.sum();
        long hCalls = baseHeightCalls.sum();
        long carves = carveCalls.sum();
        StringBuilder sb = new StringBuilder(512);
        sb.append("DynamicWaters watershed stats\n");
        sb.append(String.format("  getBaseHeight calls: %d (%.1f ms total, %.1f us avg)\n",
                hCalls, baseHeightNanos.sum() / 1e6, hCalls == 0 ? 0.0 : baseHeightNanos.sum() / 1e3 / hCalls));
        sb.append(String.format("  height blocks built: %d\n", heightBlocksBuilt.sum()));
        sb.append(String.format("  tiles built: %d (%.1f ms avg; sample %.1f / flood %.1f / extract %.1f ms avg), waits: %d\n",
                tiles,
                tiles == 0 ? 0.0 : tileNanos.sum() / 1e6 / tiles,
                tiles == 0 ? 0.0 : tileSampleNanos.sum() / 1e6 / tiles,
                tiles == 0 ? 0.0 : tileFloodNanos.sum() / 1e6 / tiles,
                tiles == 0 ? 0.0 : tileExtractNanos.sum() / 1e6 / tiles,
                tileWaits.sum()));
        sb.append(String.format("  height calls per tile: %.0f (per 256-region equivalent: %.0f)\n",
                tiles == 0 ? 0.0 : (double) hCalls / tiles,
                tiles == 0 ? 0.0 : (double) hCalls / tiles / 16.0));
        sb.append(String.format("  paths emitted: %d, lakes emitted: %d\n", pathsEmitted.sum(), lakesEmitted.sum()));
        sb.append(String.format("  lakes given another level: %d, dropped: %d (for a river running in under them: %d)%n",
                lakesSettled.sum(), lakesDropped.sum(), lakesUndercut.sum()));
        sb.append(String.format("  lakes closed (keeping their water): %d%n", lakesClosed.sum()));
        sb.append(String.format("  lakes kept to the level a too wide bowl reached: %d%n", lakesCapped.sum()));
        sb.append(String.format("  rivers ended in a lake before a seam: %d (no room for one: %d; side streams ended with them: %d;"
                + " under the water of a lake over a seam: %d; lake outlets closed instead: %d)%n",
                deadEndLakes.sum(), deadEndsLeft.sum(), deadEndStreams.sum(), sharedLakeUndercuts.sum(), deadEndOutletsClosed.sum()));
        sb.append(String.format("  lakes over a stretch of seam with no way out, by each tile: %d%n", seamLakes.sum()));
        sb.append(String.format("  mountain river biome: %d chunks asked, %d got it (%d columns), %.3f ms a chunk%n",
                riverBiomeAsked.sum(), riverBiomeChunks.sum(), riverBiomeColumns.sum(),
                riverBiomeNanos.sum() / 1.0e6 / Math.max(1L, riverBiomeAsked.sum())));
        sb.append(String.format("  hollows beside a lake's water: %d columns flooded, %d raised to hold it%n",
                lakeHollowsFlooded.sum(), lakeHollowsHeld.sum()));
        sb.append(String.format("  seam sides: %d taken over (%d for water pooled by the seam), %d kept to the tile's own routing;"
                + " neighbours routed for them: %d%n",
                seamSidesTaken.sum(), seamSidesPooled.sum(), seamSidesKept.sum(), seamNeighbourRoutes.sum()));
        sb.append(String.format("  seam cycles: %d led out, %d ended in lakes either side, %d flow loops;"
                + " ways out refused for coming round a corner: %d; crossings held up to the next tile's water: %d;"
                + " rivers through a corner ended before it: %d (%d that would have climbed); river cells widened to the water"
                + " told next door: %d%n",
                seamCyclesBroken.sum(), seamCyclesLeft.sum(), seamFlowLoops.sum(), seamCycleRoundTrips.sum(), seamExitsHeld.sum(),
                seamPassingThrough.sum(), seamPassingClimb.sum(), seamWidenedAsTold.sum()));
        sb.append(String.format("  river pieces brought down to the river they join alongside: %d%n", loweredAlongside.sum()));
        sb.append(String.format("  cave openings filled in the heightfield: %d%n", caveSpikesFilled.sum()));
        sb.append(String.format("  rocks in the rivers: %d columns of %d they could stand in%n", rockColumns.sum(), fastColumns.sum()));
        sb.append(String.format("  columns dressed in a riverbank palette: %d%n", bankSkins.sum()));
        sb.append(String.format("  water plants planted: %d%n", waterPlants.sum()));
        sb.append(String.format("  gorge nodes (water > 12 under terrain): %d, deepest gorge: %d blocks%n",
                gorgeNodes.sum(), maxGorge.get()));
        sb.append(String.format("  river stretches sent underground: %d, %d cells of river%n", underStretches.sum(), underCells.sum()));
        sb.append(String.format("  ice floes: %d floe ticks, %.4f ms each; of %d looks round, %d overlapping, %d well over%n", iceFloeTicks.sum(),
                iceFloeNanos.sum() / 1.0e6 / Math.max(1L, iceFloeTicks.sum()), iceFloeLooks.sum(), iceFloeOverlaps.sum(), iceFloeCrowded.sum()));
        sb.append(String.format("  fallen trunks: %d in %d chunks asked, %.3f ms a chunk%n", riverLogs.sum(), riverLogsAsked.sum(),
                riverLogNanos.sum() / 1.0e6 / Math.max(1L, riverLogsAsked.sum())));
        sb.append(String.format("  reach ends: sea %d, main river %d, lake %d, junction/region %d, NOWHERE %d%n",
                endSea.sum(), endMainRiver.sum(), endLake.sum(), endJunction.sum(), endNowhere.sum()));
        sb.append(String.format("  mouths walked onto real water: %d, fell back on the sample point: %d%n",
                mouthsWalked.sum(), mouthsFellBack.sum()));
        sb.append(String.format("  necks cut through: %d, folded bends eased: %d, streams joined alongside: %d,"
                + " led over to one too close: %d%n", necksCut.sum(), foldsEased.sum(), joinsAlongside.sum(), fusesWide.sum()));
        sb.append(String.format("  column carve calls: %d (%.2f us avg)\n",
                carves, carves == 0 ? 0.0 : carveNanos.sum() / 1e3 / carves));
        sb.append(String.format("  lowland river pass: %.2f us avg per column\n",
                carves == 0 ? 0.0 : lowlandPassNanos.sum() / 1e3 / carves));
        final long regions = lowlandRegions.sum();
        sb.append(String.format("  lowland river regions built: %d (%.1f ms avg, of it shaping %.1f ms)\n", regions,
                regions == 0 ? 0.0 : lowlandRegionNanos.sum() / 1e6 / regions,
                regions == 0 ? 0.0 : lowlandShapeNanos.sum() / 1e6 / regions));
        sb.append(String.format("  lowland regions grown: %d, rivers yielded to a neighbour's: %d\n",
                lowlandGrown.sum(), lowlandYielded.sum()));
        return sb.toString();
    }
}

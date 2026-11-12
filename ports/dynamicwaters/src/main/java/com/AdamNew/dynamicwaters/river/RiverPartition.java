/*
 * Adapted from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * specifically net.dries007.tfc.world.region.RegionPartition.
 * Original work Copyright (c) TerraFirmaCraft team, licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Modified for Dynamic Waters by AdamNew, 2026-07-13.
 * As a Derivative Work under the EUPL, this file remains available under the
 * EUPL v1.2, or a licence listed as compatible in the EUPL's Appendix
 * (e.g. GPLv3, LGPLv3, MPL2), for as long as it carries this notice.
 *
 * Unlike RiverNetworkBuilder's per-region cache (which is keyed by "which
 * region generated this data"), a RiverPartition is keyed by "what world
 * space does this data cover". A river generated while processing region
 * (3, 0) can easily bend into region (4, 0)'s territory - the partition is
 * what lets a query at an arbitrary (x, z) find every nearby edge in one
 * cheap bucket lookup, regardless of which region originally built it.
 */

package com.AdamNew.dynamicwaters.river;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple spatial hash of {@link RiverGraphEdge}s, bucketed into fixed-size square cells in block space.
 * Thread-safe for concurrent chunk generation: each cell holds an immutable list, replaced whole on
 * insert, so a reader iterates a list nobody can change under it.
 */
public final class RiverPartition
{
    /** Side length, in blocks, of one partition cell. Tune for edge density vs. lookup cost. */
    public static final int CELL_SIZE = 256;

    public static int blockToCell(int block)
    {
        return Math.floorDiv(block, CELL_SIZE);
    }

    private final ConcurrentHashMap<Long, List<RiverGraphEdge>> cells = new ConcurrentHashMap<>();

    /**
     * Inserts an edge into every cell its bounding box overlaps. Call once per edge, right after generation.
     * <p>
     * The cell's list is copied, never appended to. It used to be a plain ArrayList handed out by
     * {@link #get} and added to here under a lock that covered the map but not the iteration: a region
     * finished on one thread while biomes were being filled from the same cell on another, and the
     * biome thread died of a ConcurrentModificationException - and the chunk never generated.
     */
    public void insert(RiverGraphEdge edge)
    {
        for (int px = edge.minPartX; px <= edge.maxPartX; px++)
        {
            for (int pz = edge.minPartZ; pz <= edge.maxPartZ; pz++)
            {
                cells.compute(cellKey(px, pz), (k, old) -> {
                    if (old == null) return List.of(edge);
                    final RiverGraphEdge[] next = old.toArray(new RiverGraphEdge[old.size() + 1]);
                    next[old.size()] = edge;
                    return List.of(next);
                });
            }
        }
    }

    /** @return An immutable snapshot of every edge whose bounding box overlaps the cell containing (blockX, blockZ). */
    public List<RiverGraphEdge> get(int blockX, int blockZ)
    {
        final List<RiverGraphEdge> result = cells.get(cellKey(blockToCell(blockX), blockToCell(blockZ)));
        return result != null ? result : List.of();
    }

    private static long cellKey(int px, int pz)
    {
        return ((long) px & 0xFFFFFFFFL) | (((long) pz & 0xFFFFFFFFL) << 32);
    }
}

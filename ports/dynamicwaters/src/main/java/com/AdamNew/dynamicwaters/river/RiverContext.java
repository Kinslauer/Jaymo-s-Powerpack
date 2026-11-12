/*
 * Adapted from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * specifically net.dries007.tfc.world.river.RiverInfo.
 * Original work Copyright (c) TerraFirmaCraft team, licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Modified for Dynamic Waters by AdamNew, 2026-07-13.
 * As a Derivative Work under the EUPL, this file remains available under the
 * EUPL v1.2, or a licence listed as compatible in the EUPL's Appendix
 * (e.g. GPLv3, LGPLv3, MPL2), for as long as it carries this notice.
 *
 * Changed from upstream: holds a RiverGraphEdge (this mod's own type, with width
 * tapering and downstream linking) instead of TFC's net.dries007.tfc.world.region.RiverEdge.
 *
 * @param distSq  Square distance, in blocks, from the queried point to this river's meandering fractal.
 * @param widthSq Square width, in blocks, of the river at the queried point (tapered - see RiverGraphEdge#widthSq).
 */
package com.AdamNew.dynamicwaters.river;

public record RiverContext(RiverGraphEdge edge, RiverFlow flow, double distSq, double widthSq)
{
    /**
     * @return A normalized distance to this river. 0 = at the center of the river, 1 = at the edge of the river (~roughly).
     */
    public double normDistSq()
    {
        return distSq / widthSq;
    }
}

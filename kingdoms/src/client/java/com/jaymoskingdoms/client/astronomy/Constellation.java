package com.jaymoskingdoms.client.astronomy;

import java.util.List;

/**
 * One constellation figure: its IAU id, abbreviation and name, and its line
 * segments as pairs of indexes into {@link StarCatalog#stars()}. A figure
 * "connects" once every star on its segments has been discovered
 * ({@link Discovery}); segments between undiscovered stars are not drawn.
 */
public record Constellation(String id, String name, String abbr, List<int[]> segments) {}

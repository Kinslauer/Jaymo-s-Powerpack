package com.AdamNew.dynamicwaters.shore;

import java.util.List;

public final class WaterPlantSet {
    public static final int LOWLAND = 0;
    public static final int MOUNTAIN = 1;
    public static final int LAKE = 2;

    public static final String[] KINDS = { "lowland", "mountain", "lake" };

    final String source;

    final BiomeMatch where;
    final boolean enabled;
    private final List<List<WaterPlant>> plants;

    WaterPlantSet(String source, BiomeMatch where, boolean enabled, List<List<WaterPlant>> plants) {
        this.source = source;
        this.where = where;
        this.enabled = enabled;
        this.plants = plants;
    }

    List<WaterPlant> plants(int kind) {
        return plants.get(kind);
    }

    @Override
    public String toString() {
        return "WaterPlantSet[" + source + "]";
    }
}

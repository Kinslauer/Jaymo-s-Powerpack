package com.AdamNew.dynamicwaters.shore;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class RiverbankPalette {
    public static final int RAPIDS = 0;
    public static final int SILT = 1;
    public static final int SHORE = 2;
    public static final int SLOPE = 3;
    public static final int BARS = 4;
    public static final int FLATS = 5;

    public static final String[] PLACES = { "rapids", "silt", "shore", "slope", "bars", "flats" };

    public static final class Mix {
        private final BlockState[] blocks;
        private final int[] weights;
        private final int total;

        public Mix(List<BlockState> blocks, List<Integer> weights) {
            this.blocks = blocks.toArray(new BlockState[0]);
            this.weights = new int[this.blocks.length];
            int t = 0;
            for (int i = 0; i < this.weights.length; i++) {
                this.weights[i] = Math.max(1, weights.get(i));
                t += this.weights[i];
            }
            this.total = t;
        }

        public BlockState pick(int x, int z, int size, int salt) {
            return blocks.length == 0 ? null : blocks[Patches.spot(weights, total, x, z, size, salt)];
        }
    }

    final String source;

    final BiomeMatch where;
    final boolean enabled;

    private final Mix[] places;

    final int patchSize;

    final int shoreHeight;

    RiverbankPalette(String source, BiomeMatch where, boolean enabled, Mix[] places, int patchSize, int shoreHeight) {
        this.source = source;
        this.where = where;
        this.enabled = enabled;
        this.places = places;
        this.patchSize = patchSize;
        this.shoreHeight = shoreHeight;
    }

    public Mix place(int place) {
        Mix m = places[place];
        if (m == null && place == FLATS) m = places[BARS];
        if (m == null && (place == FLATS || place == BARS)) m = places[SILT];
        return m;
    }

    @Override
    public String toString() {
        return "RiverbankPalette[" + source + "]";
    }
}

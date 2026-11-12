package com.AdamNew.dynamicwaters.noise;

import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public class Noise3D {
    private final SimplexNoise noise;
    private float spread = 1.0f;
    private float scaleMin = -1.0f;
    private float scaleMax = 1.0f;
    private int octaves = 1;

    public Noise3D(long seed) {
        this.noise = new SimplexNoise(new XoroshiroRandomSource(seed));
    }

    public Noise3D spread(float s) { this.spread = s; return this; }
    public Noise3D scaled(float min, float max) { this.scaleMin = min; this.scaleMax = max; return this; }
    public Noise3D octaves(int o) { this.octaves = o; return this; }

    public double noise(double x, double y, double z) {
        double val = noise.get(x * spread, y * spread, z * spread);
        return scaleMin + ((val + 1.0) / 2.0) * (scaleMax - scaleMin);
    }
}

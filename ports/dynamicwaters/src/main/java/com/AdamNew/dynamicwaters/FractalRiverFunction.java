package com.AdamNew.dynamicwaters;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;

public record FractalRiverFunction(DensityFunction delegate) implements DensityFunction {
    public static final MapCodec<FractalRiverFunction> CODEC = DensityFunction.CODEC
            .fieldOf("delegate").xmap(FractalRiverFunction::new, FractalRiverFunction::delegate);

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return delegate.compileSampler(context);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        final DensityFunction inner = rule.rewrite(delegate);
        return inner == delegate ? this : new FractalRiverFunction(inner);
    }

    @Override
    public Interval range() {
        return Interval.of(-1.0f, delegate.range().max());
    }

    @Override
    public int domainAxes() {
        return delegate.domainAxes();
    }

    @Override
    public MapCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}

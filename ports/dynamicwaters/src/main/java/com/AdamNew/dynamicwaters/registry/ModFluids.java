package com.AdamNew.dynamicwaters.registry;

import com.AdamNew.dynamicwaters.fluid.RiverWaterFluid;
import com.AdamNew.dynamicwaters.platform.Registrar;
import java.util.function.Supplier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.registries.Registries;

public class ModFluids {
    public static final Registrar<Fluid> FLUIDS = Registrar.create(Registries.FLUID);

    public static final Supplier<Fluid> RIVER_WATER = FLUIDS.register("river_water", RiverWaterFluid::new);
}

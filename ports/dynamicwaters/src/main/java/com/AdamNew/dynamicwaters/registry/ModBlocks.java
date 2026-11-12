package com.AdamNew.dynamicwaters.registry;

import com.AdamNew.dynamicwaters.block.FlowOverrideBlock;
import com.AdamNew.dynamicwaters.block.RiverWaterBlock;
import com.AdamNew.dynamicwaters.platform.Registrar;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.registries.Registries;

public class ModBlocks {
    public static final Registrar<Block> BLOCKS = Registrar.create(Registries.BLOCK);

    public static final Supplier<Block> RIVER_WATER = BLOCKS.register("river_water",
            () -> new RiverWaterBlock((net.minecraft.world.level.material.FlowingFluid) ModFluids.RIVER_WATER.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).setId(BLOCKS.key("river_water"))));

    public static final Supplier<Block> FLOW_OVERRIDE = BLOCKS.register("flow_override",
            () -> new FlowOverrideBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.AIR)
                    .setId(BLOCKS.key("flow_override"))
                    .noCollision()
                    .noLootTable()
                    .air()));
}

package com.jaymospowers.client.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingParticlesLeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets {@code LeafRustle} shake a leaves block's own falling leaves loose (its tinted, cherry or pale oak leaves). */
@Mixin(FallingParticlesLeavesBlock.class)
public interface FallingLeavesInvoker {
	@Invoker("spawnFallingLeavesParticle")
	void jaymospowers$shakeLoose(Level level, BlockPos pos, RandomSource random);
}

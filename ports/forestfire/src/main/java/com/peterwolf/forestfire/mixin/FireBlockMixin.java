package com.peterwolf.forestfire.mixin;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({FireBlock.class})
public class FireBlockMixin {
   @Inject(
      method = {"tick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void forestfire$adoptVanillaFire(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
      if (ForestFireConfig.get().replaceVanillaFireInIncidents) {
         FireSimulation sim = FireSimulation.get(level);
         if (sim.shouldSuppressVanillaFire(pos) || ForestFireConfig.get().allowNaturalIgnition) {
            sim.adoptVanillaFire(pos);
            if (sim.shouldSuppressVanillaFire(pos) || sim.getCell(pos.below()) != null) {
               ci.cancel();
            }
         }
      }
   }
}

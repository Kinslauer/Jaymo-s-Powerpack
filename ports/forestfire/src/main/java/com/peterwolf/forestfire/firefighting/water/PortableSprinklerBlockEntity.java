package com.peterwolf.forestfire.firefighting.water;

import com.peterwolf.forestfire.block.ModBlockEntities;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.firefighting.hose.HoseNetwork;
import com.peterwolf.forestfire.firefighting.pump.PortablePumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PortableSprinklerBlockEntity extends BlockEntity {
   private int angle;

   public PortableSprinklerBlockEntity(BlockPos pos, BlockState state) {
      super(ModBlockEntities.SPRINKLER, pos, state);
   }

   public static void serverTick(Level level, BlockPos pos, BlockState state, PortableSprinklerBlockEntity sprinkler) {
      if (level instanceof ServerLevel serverLevel) {
         if (serverLevel.getGameTime() % 10L == 0L) {
            PortablePumpBlockEntity pump = HoseNetwork.findSupplyingPump(serverLevel, pos);
            if (pump != null && pump.canSupplyNozzle()) {
               float supplied = pump.consumeForNozzle(0.15F);
               if (!(supplied <= 0.0F)) {
                  sprinkler.angle = (sprinkler.angle + 20) % 360;
                  double rad = Math.toRadians(sprinkler.angle);
                  int tx = pos.getX() + (int)Math.round(Math.cos(rad) * 3.0);
                  int tz = pos.getZ() + (int)Math.round(Math.sin(rad) * 3.0);
                  BlockPos target = new BlockPos(tx, pos.getY(), tz);
                  FireSimulation.get(serverLevel).applyWater(target, 0.35F * supplied, 1.0F);
                  serverLevel.sendParticles(ParticleTypes.RAIN, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.8, 0.2, 0.8, 0.02);
               }
            }
         }
      }
   }
}

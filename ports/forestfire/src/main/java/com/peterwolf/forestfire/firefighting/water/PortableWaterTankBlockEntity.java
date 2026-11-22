package com.peterwolf.forestfire.firefighting.water;

import com.peterwolf.forestfire.block.ModBlockEntities;
import com.peterwolf.forestfire.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PortableWaterTankBlockEntity extends BlockEntity {
   private int water;
   private TankSize size;

   public PortableWaterTankBlockEntity(BlockPos pos, BlockState state) {
      this(pos, state, sizeFromState(state));
   }

   public PortableWaterTankBlockEntity(BlockPos pos, BlockState state, TankSize size) {
      super(ModBlockEntities.WATER_TANK, pos, state);
      this.size = size;
      this.water = size.capacity() / 2;
   }

   private static TankSize sizeFromState(BlockState state) {
      if (state.is(ModBlocks.WATER_TANK_LARGE)) {
         return TankSize.LARGE;
      } else {
         return state.is(ModBlocks.WATER_TANK_MEDIUM) ? TankSize.MEDIUM : TankSize.SMALL;
      }
   }

   public int getWater() {
      return this.water;
   }

   public int getCapacity() {
      return this.size.capacity();
   }

   public int fill(int amount) {
      int before = this.water;
      this.water = Math.min(this.getCapacity(), this.water + amount);
      this.setChanged();
      return this.water - before;
   }

   public int drain(int amount) {
      int drained = Math.min(this.water, amount);
      this.water -= drained;
      this.setChanged();
      return drained;
   }

   protected void saveAdditional(ValueOutput tag) {
      super.saveAdditional(tag);
      tag.putInt("water", this.water);
      tag.putString("size", this.size.name());
   }

   protected void loadAdditional(ValueInput tag) {
      super.loadAdditional(tag);
      this.water = tag.getIntOr("water", 0);

      try {
         this.size = TankSize.valueOf(tag.getStringOr("size", sizeFromState(this.getBlockState()).name()));
      } catch (Exception var3) {
         this.size = sizeFromState(this.getBlockState());
      }
   }
}

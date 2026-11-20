package com.peterwolf.forestfire.world;

import com.peterwolf.forestfire.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CommandPostBlockEntity extends BlockEntity {
   private int incidentId = -1;

   public CommandPostBlockEntity(BlockPos pos, BlockState state) {
      super(ModBlockEntities.COMMAND_POST, pos, state);
   }

   public int getIncidentId() {
      return this.incidentId;
   }

   public void setIncidentId(int incidentId) {
      this.incidentId = incidentId;
      this.setChanged();
   }

   protected void saveAdditional(ValueOutput tag) {
      super.saveAdditional(tag);
      tag.putInt("incidentId", this.incidentId);
   }

   protected void loadAdditional(ValueInput tag) {
      super.loadAdditional(tag);
      this.incidentId = tag.getIntOr("incidentId", -1);
   }
}

package com.peterwolf.forestfire.firefighting.nozzle;

import com.peterwolf.forestfire.block.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class GroundNozzleBlockEntity extends BlockEntity {
   @Nullable
   private UUID endpointId;
   private float yaw;

   public GroundNozzleBlockEntity(BlockPos pos, BlockState state) {
      super(ModBlockEntities.GROUND_NOZZLE, pos, state);
   }

   @Nullable
   public UUID getEndpointId() {
      return this.endpointId;
   }

   public void setEndpointId(UUID endpointId) {
      this.endpointId = endpointId;
      this.setChanged();
   }

   public float getYaw() {
      return this.yaw;
   }

   public void setYaw(float yaw) {
      this.yaw = yaw;
      this.setChanged();
   }

   protected void saveAdditional(ValueOutput tag) {
      super.saveAdditional(tag);
      if (this.endpointId != null) {
         tag.putString("endpointId", this.endpointId.toString());
      }

      tag.putFloat("yaw", this.yaw);
   }

   protected void loadAdditional(ValueInput tag) {
      super.loadAdditional(tag);
      String raw = tag.getStringOr("endpointId", "");
      if (!raw.isEmpty()) {
         try {
            this.endpointId = UUID.fromString(raw);
         } catch (IllegalArgumentException var4) {
            this.endpointId = null;
         }
      }

      this.yaw = tag.getFloatOr("yaw", 0.0F);
   }
}

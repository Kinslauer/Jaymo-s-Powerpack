package com.jaymospowers.dino.block;

import com.jaymospowers.dino.DinoBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class DinoSkullBlockEntity extends BlockEntity {
   public DinoSkullBlockEntity(BlockPos pos, BlockState state) {
      super((BlockEntityType)DinoBlocks.SKULL_ENTITY, pos, state);
   }

   public AABB getRenderBoundingBox() {
      return new AABB(this.getBlockPos()).inflate(3.2);
   }
}

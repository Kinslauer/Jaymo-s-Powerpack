package com.jaymospowers.dino.block;

import com.jaymospowers.dino.entity.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DinoSkullBlock extends BaseEntityBlock {
   public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
   public static final BooleanProperty WALL = BooleanProperty.create("wall");
   private final Species species;
   private final VoxelShape floor;
   private final VoxelShape[] wall = new VoxelShape[4];

   public DinoSkullBlock(Species species, Properties properties) {
      super(properties);
      this.species = species;

      int[] size = switch (species) {
         case TYRANNOSAURUS, TRICERATOPS -> new int[]{14, 14};
         case BRACHIOSAURUS, SPINOSAURUS -> new int[]{14, 12};
         case CARNOTAURUS -> new int[]{12, 12};
         case DODO -> new int[]{5, 5};
         case PARASAUROLOPHUS -> new int[]{12, 12};
         case DILOPHOSAURUS -> new int[]{10, 10};
         case VELOCIRAPTOR -> new int[]{8, 8};
         case GALLIMIMUS -> new int[]{6, 6};
      };
      double w = size[0];
      double h = size[1];
      double lo = 8.0 - w / 2.0;
      double hi = 8.0 + w / 2.0;
      double y0 = 8.0 - h / 2.0;
      double y1 = 8.0 + h / 2.0;
      this.floor = Block.column(w, 0.0, h);
      this.wall[0] = Block.box(lo, y0, 0.0, hi, y1, w);
      this.wall[1] = Block.box(16.0 - w, y0, lo, 16.0, y1, hi);
      this.wall[2] = Block.box(lo, y0, 16.0 - w, hi, y1, 16.0);
      this.wall[3] = Block.box(0.0, y0, lo, w, y1, hi);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(ROTATION, 0)).setValue(WALL, false));
   }

   public Species species() {
      return this.species;
   }

   public static float yaw(BlockState state) {
      return ((Integer)state.getValue(ROTATION)).intValue() * 22.5F;
   }

   private static int segment(float yaw) {
      return Mth.floor(Mth.positiveModulo(yaw, 360.0F) / 22.5F + 0.5F) & 15;
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new DinoSkullBlockEntity(pos, state);
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      Direction face = context.getClickedFace();
      return face.getAxis().isHorizontal() && !context.replacingClickedOnBlock()
         ? (BlockState)((BlockState)this.defaultBlockState().setValue(WALL, true)).setValue(ROTATION, segment(face.toYRot()))
         : (BlockState)((BlockState)this.defaultBlockState().setValue(WALL, false)).setValue(ROTATION, segment(context.getRotation() + 180.0F));
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return state.getValue(WALL) ? this.wall[state.getValue(ROTATION) / 4 & 3] : this.floor;
   }

   protected BlockState rotate(BlockState state, Rotation rotation) {
      return (BlockState)state.setValue(ROTATION, rotation.rotate((Integer)state.getValue(ROTATION), 16));
   }

   protected BlockState mirror(BlockState state, Mirror mirror) {
      return (BlockState)state.setValue(ROTATION, mirror.mirror((Integer)state.getValue(ROTATION), 16));
   }

   protected boolean isPathfindable(BlockState state, PathComputationType type) {
      return false;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{ROTATION, WALL});
   }
}

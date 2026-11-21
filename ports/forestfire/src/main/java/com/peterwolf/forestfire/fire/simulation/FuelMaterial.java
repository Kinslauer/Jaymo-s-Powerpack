package com.peterwolf.forestfire.fire.simulation;

import net.minecraft.core.Direction.Axis;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

public enum FuelMaterial {
   GRASS(0.9F, 0.12F, 0.3F, 140, 0.18F, false, false),
   DRY_LEAVES(0.95F, 0.2F, 0.55F, 90, 0.55F, true, false),
   LIVING_LEAVES(0.45F, 0.45F, 0.45F, 100, 0.4F, true, false),
   LOG(0.18F, 0.3F, 0.7F, 220, 0.22F, false, true),
   TRUNK(0.12F, 0.3F, 0.85F, 320, 0.2F, false, true),
   DEAD_BUSH(0.98F, 0.05F, 0.35F, 80, 0.28F, false, false),
   WOODEN_STRUCTURE(0.28F, 0.25F, 0.75F, 180, 0.25F, false, true),
   PEAT(0.08F, 0.7F, 0.5F, 400, 0.05F, false, true),
   NON_FLAMMABLE(0.0F, 1.0F, 0.0F, 0, 0.0F, false, false);

   public final float igniteEase;
   public final float baseMoisture;
   public final float flameIntensity;
   public final int baseFuel;
   public final float emberRate;
   public final boolean crownFuel;
   public final boolean longBurn;

   private FuelMaterial(float igniteEase, float baseMoisture, float flameIntensity, int baseFuel, float emberRate, boolean crownFuel, boolean longBurn) {
      this.igniteEase = igniteEase;
      this.baseMoisture = baseMoisture;
      this.flameIntensity = flameIntensity;
      this.baseFuel = baseFuel;
      this.emberRate = emberRate;
      this.crownFuel = crownFuel;
      this.longBurn = longBurn;
   }

   public static FuelMaterial of(BlockState state) {
      Block block = state.getBlock();
      if (block != Blocks.GRASS_BLOCK
         && block != Blocks.SHORT_GRASS
         && block != Blocks.TALL_GRASS
         && block != Blocks.FERN
         && block != Blocks.LARGE_FERN
         && block != Blocks.DEAD_BUSH
         && !state.is(BlockTags.FLOWERS)) {
         if (block == Blocks.LEAF_LITTER || block == Blocks.VINE || block == Blocks.GLOW_LICHEN) {
            return DRY_LEAVES;
         } else if (state.is(BlockTags.LEAVES) || block instanceof LeavesBlock) {
            return LIVING_LEAVES;
         } else if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.OVERWORLD_NATURAL_LOGS)) {
            if (state.is(BlockTags.PLANKS)
               || state.is(BlockTags.WOODEN_DOORS)
               || state.is(BlockTags.WOODEN_STAIRS)
               || state.is(BlockTags.WOODEN_SLABS)
               || state.is(BlockTags.FENCES)
               || state.is(BlockTags.FENCE_GATES)
               || state.is(BlockTags.WOODEN_TRAPDOORS)
               || block == Blocks.CRAFTING_TABLE
               || block == Blocks.BOOKSHELF
               || block == Blocks.CHEST
               || block == Blocks.BARREL
               || block == Blocks.LADDER) {
               return WOODEN_STRUCTURE;
            } else if (block == Blocks.ROOTED_DIRT) {
               return PEAT;
            } else if (state.is(BlockTags.WOOL) || state.is(BlockTags.BEDS)) {
               return WOODEN_STRUCTURE;
            } else {
               return state.ignitedByLava() ? WOODEN_STRUCTURE : NON_FLAMMABLE;
            }
         } else {
            return state.hasProperty(RotatedPillarBlock.AXIS) && state.getValue(RotatedPillarBlock.AXIS) != Axis.Y ? LOG : TRUNK;
         }
      } else {
         return block == Blocks.DEAD_BUSH ? DEAD_BUSH : GRASS;
      }
   }

   public boolean flammable() {
      return this != NON_FLAMMABLE && this.baseFuel > 0;
   }

   public int initialFuelPercent() {
      if (!this.flammable()) {
         return 0;
      } else if (this == GRASS) {
         return 95;
      } else {
         return this == DEAD_BUSH ? 70 : Math.min(100, Math.max(30, this.baseFuel / 2));
      }
   }

   public float fuelConsumePerTick() {
      return switch (this) {
         case GRASS -> 0.1F;
         case DRY_LEAVES, LIVING_LEAVES -> 0.28F;
         case LOG, WOODEN_STRUCTURE -> 0.18F;
         case TRUNK -> 0.12F;
         case DEAD_BUSH -> 0.18F;
         case PEAT -> 0.08F;
         case NON_FLAMMABLE -> 0.0F;
      };
   }
}

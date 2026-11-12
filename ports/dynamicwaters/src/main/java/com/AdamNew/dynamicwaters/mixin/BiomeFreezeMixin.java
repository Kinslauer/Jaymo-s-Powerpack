package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.HydrologyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class BiomeFreezeMixin {
    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("RETURN"), cancellable = true)
    private void dynamicwaters$runningWaterStaysOpen(LevelReader level, BlockPos pos, boolean mustBeAtEdge,
                                                     CallbackInfoReturnable<Boolean> cir) {
        final Config.MountainRiverIce ice = Config.MOUNTAIN_RIVER_ICE.get();
        if (!cir.getReturnValueZ()) {
            if (ice == Config.MountainRiverIce.FULL && freezesRunning(level, pos)) {
                cir.setReturnValue(true);
            }
            return;
        }
        if (ice == Config.MountainRiverIce.FULL || !riversHere(level)) {
            return;
        }

        if (higherWaterBeside(level, pos)) {
            cir.setReturnValue(false);
            return;
        }
        if (wideWater(level, pos)) {
            return;
        }
        final int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (ice == Config.MountainRiverIce.OPEN && HydrologyManager.isMountainRiverWater(x, y, z)
                || HydrologyManager.nearMountainFall(x, y, z)) {
            cir.setReturnValue(false);
        }
    }

    private boolean freezesRunning(LevelReader level, BlockPos pos) {
        if (((Biome) (Object) this).warmEnoughToRain(pos, com.AdamNew.dynamicwaters.HydrologyManager.getSeaLevel())) return false;
        if (pos.getY() < level.getMinY() || pos.getY() >= (level.getMaxY() + 1)) return false;
        if (level.getBrightness(LightLayer.BLOCK, pos) >= 10) return false;
        final FluidState fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.WATER) || fluid.isSource() || !(level.getBlockState(pos).getBlock() instanceof LiquidBlock)) {
            return false;
        }
        if (!riversHere(level) || wideWater(level, pos)) return false;
        final int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return HydrologyManager.isMountainRiverWater(x, y, z) || HydrologyManager.nearMountainFall(x, y, z);
    }

    private static boolean riversHere(LevelReader level) {
        final Level home = level instanceof WorldGenRegion region ? region.getLevel() : level instanceof Level l ? l : null;
        return home != null && HydrologyManager.riversIn(home);
    }

    private static boolean wideWater(LevelReader level, BlockPos pos) {
        final Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH)) return true;
        return biome.is(BiomeTags.IS_RIVER)
                && (!Config.dynamicNormalRivers() || HydrologyManager.inLowlandWater(pos.getX() + 0.5, pos.getZ() + 0.5));
    }

    private static boolean higherWaterBeside(LevelReader level, BlockPos pos) {
        final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= 2; k++) {
                at.set(pos.getX() + d.getStepX() * k, pos.getY() + 1, pos.getZ() + d.getStepZ() * k);
                if (!level.hasChunkAt(at)) break;
                if (level.getFluidState(at).is(FluidTags.WATER)) return true;
            }
        }
        return false;
    }
}

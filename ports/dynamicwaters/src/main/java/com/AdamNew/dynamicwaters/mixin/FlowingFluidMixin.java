package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.block.FlowOverrideBlock;
import com.AdamNew.dynamicwaters.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.StackWalker;
import net.minecraft.core.Direction;

@Mixin(FlowingFluid.class)
public class FlowingFluidMixin {
    @Inject(method = "isWaterHole", at = @At("HEAD"), cancellable = true)
    private void dynamicwaters_isWaterHole(BlockGetter level, BlockPos pos, BlockState state, BlockPos spreadPos, BlockState spreadState, CallbackInfoReturnable<Boolean> cir) {
        final FluidState here = state.getFluidState();
        if (here.hasProperty(FlowingFluid.FALLING) && here.getValue(FlowingFluid.FALLING)) {
            return;
        }
        FluidState below = spreadState.getFluidState();
        if (below.isSource() && below.getType().isSame((net.minecraft.world.level.material.Fluid)(Object)this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getFlow", at = @At("RETURN"), cancellable = true)
    private void dynamicwaters_getFlow(BlockGetter level, BlockPos pos, FluidState state, CallbackInfoReturnable<Vec3> cir) {
        {
            BlockPos scanPos = pos.above();
            for (int i = 0; i < 16; i++) {
                BlockState scanState = level.getBlockState(scanPos);
                if (scanState.getBlock() instanceof FlowOverrideBlock) {
                    boolean flowEnabled = scanState.getValue(FlowOverrideBlock.FLOW_ENABLED);
                    if (!flowEnabled) {
                        cir.setReturnValue(Vec3.ZERO);
                        return;
                    }
                    int direction = scanState.getValue(FlowOverrideBlock.FLOW_DIRECTION);
                    cir.setReturnValue(FlowOverrideBlock.directionToVec3(direction));
                    return;
                }

                if (!scanState.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                    break;
                }
                scanPos = scanPos.above();
            }
        }

        final Vec3 zoneFlow = com.AdamNew.dynamicwaters.FlowZones.flowAt(level, pos.getX(), pos.getY(), pos.getZ());
        if (zoneFlow != null) {
            final net.minecraft.world.entity.Entity pushed = level instanceof net.minecraft.world.level.Level
                    ? com.AdamNew.dynamicwaters.EntityPhysicsContext.get() : null;
            cir.setReturnValue(pushed != null
                    ? zoneFlow.scale(com.AdamNew.dynamicwaters.Config.getFlowMultiplierForEntity(pushed)) : zoneFlow);
            return;
        }

        boolean foundOcean = false;

        if (level instanceof LevelReader levelReader) {
            for (int dx = -2; dx <= 2; dx += 2) {
                for (int dz = -2; dz <= 2; dz += 2) {
                    BlockPos neighborPos = pos.offset(dx, 0, dz);
                    if (levelReader.getFluidState(neighborPos).is(net.minecraft.tags.FluidTags.WATER) && HydrologyManager.isOceanBiome(levelReader.getBiome(neighborPos))) {
                        foundOcean = true; break;
                    }
                }
                if (foundOcean) break;
            }
        } else {
            try {
                if (com.AdamNew.dynamicwaters.platform.Platform.isClient()) {
                    Object result = Class.forName("com.AdamNew.dynamicwaters.client.ClientOceanChecker")
                            .getMethod("isOceanAtClient", net.minecraft.core.BlockPos.class)
                            .invoke(null, pos);
                    if (result instanceof Boolean && (Boolean) result) {
                        foundOcean = true;
                    }
                }
            } catch (Throwable t) {
            }
        }

        if (foundOcean && state.isSource()) {
            cir.setReturnValue(Vec3.ZERO);
            return;
        }

        BlockPos globalPos = com.AdamNew.dynamicwaters.SableCompat.getGlobalBlockPos(level, pos);
        Vec3 riverFlow = HydrologyManager.getRiverFlow(globalPos.getX(), globalPos.getY(), globalPos.getZ(), (level instanceof net.minecraft.world.level.Level l) ? l : null);

        if (!riverFlow.equals(Vec3.ZERO)) {
            boolean isPhysics = level instanceof net.minecraft.world.level.Level;
            if (isPhysics) {
                net.minecraft.world.entity.Entity ctxEntity = com.AdamNew.dynamicwaters.EntityPhysicsContext.get();
                if (ctxEntity != null) {
                    double mult = com.AdamNew.dynamicwaters.Config.getFlowMultiplierForEntity(ctxEntity);
                    cir.setReturnValue(riverFlow.scale(mult));
                } else {
                    cir.setReturnValue(riverFlow);
                }
            } else if (com.AdamNew.dynamicwaters.Config.ENABLE_RIVER_VISUAL_FLOW.get()) {
                cir.setReturnValue(riverFlow);
            }
        } else {
        }
    }
}

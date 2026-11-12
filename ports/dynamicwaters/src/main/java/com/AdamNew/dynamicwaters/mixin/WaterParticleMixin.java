package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.HydrologyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class WaterParticleMixin {
    private static long lastRiverSoundTime = 0;

    @Inject(method = "animateTick", at = @At("HEAD"))
    private void dynamicwaters$animateRiverParticles(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!level.isClientSide()) return;

        if (!((Object)this instanceof LiquidBlock)) return;

        if (state.getFluidState().isSource() && level.getBlockState(pos.above()).isAir()) {
            BlockPos globalPos = com.AdamNew.dynamicwaters.SableCompat.getGlobalBlockPos(level, pos);
            Vec3 flow = HydrologyManager.getRiverFlow(globalPos.getX() + 0.5, globalPos.getY(), globalPos.getZ() + 0.5, level);

            if (flow.lengthSqr() > 0.01) {
                if (com.AdamNew.dynamicwaters.Config.ENABLE_RIVER_SOUND.get()) {
                    long currentTime = level.getGameTime();

                    if (currentTime - lastRiverSoundTime > 10) {
                        if (random.nextInt(3) == 0) {
                            lastRiverSoundTime = currentTime;
                            float volume = 0.08f + (float)Math.min(0.05, flow.lengthSqr() * 0.02);
                            float pitch = 0.3f + random.nextFloat() * 0.05f;
                            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                    com.AdamNew.dynamicwaters.registry.ModSounds.RIVER_FLOW.get(),
                                    net.minecraft.sounds.SoundSource.AMBIENT, volume, pitch, false);
                        }
                    }
                }

                if (com.AdamNew.dynamicwaters.Config.ENABLE_RIVER_PARTICLES.get()) {
                    if (random.nextInt(com.AdamNew.dynamicwaters.Config.RIVER_PARTICLE_DENSITY.get()) == 0) {
                        int count = 1 + random.nextInt(2);
                    for (int i = 0; i < count; i++) {
                        double px = pos.getX() + random.nextDouble();

                        double py = pos.getY() + 0.88;
                        double pz = pos.getZ() + random.nextDouble();

                        double vx = flow.x * 0.1;
                        double vy = 0.0;
                        double vz = flow.z * 0.1;

                        addAlwaysVisibleParticle(level, com.AdamNew.dynamicwaters.registry.ModParticles.RIVER_BUBBLE.get(), px, py, pz, vx, vy, vz);
                    }
                    }

                    if (flow.lengthSqr() > 1.2 && random.nextInt(3) == 0) {
                        addAlwaysVisibleParticle(level, ParticleTypes.CLOUD, pos.getX() + random.nextDouble(), pos.getY() + 0.9, pos.getZ() + random.nextDouble(), flow.x * 0.1, 0.05, flow.z * 0.1);
                    }
                }
            }
        }
    }

    private void addAlwaysVisibleParticle(Level level, net.minecraft.core.particles.ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
        try {
            level.getClass().getMethod("addAlwaysVisibleParticle", net.minecraft.core.particles.ParticleOptions.class, double.class, double.class, double.class, double.class, double.class, double.class)
                 .invoke(level, particle, x, y, z, vx, vy, vz);
        } catch (Exception e) {
            level.addParticle(particle, x, y, z, vx, vy, vz);
        }
    }
}

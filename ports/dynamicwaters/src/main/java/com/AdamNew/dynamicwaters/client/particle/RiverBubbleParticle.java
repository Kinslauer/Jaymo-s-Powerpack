package com.AdamNew.dynamicwaters.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleTypes;

public class RiverBubbleParticle extends SingleQuadParticle {
    public RiverBubbleParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.setSize(0.02F, 0.02F);
        this.quadSize *= random.nextFloat() * 0.4F + 0.3F;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;

        this.lifetime = 100 + random.nextInt(60);
        this.gravity = 0.04F;
        this.friction = 0.98F;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.lifetime-- <= 0) {
            this.remove();
            return;
        }

        net.minecraft.world.phys.Vec3 flow = com.AdamNew.dynamicwaters.HydrologyManager.getRiverFlow(this.x, (int)this.y, this.z, this.level);
        if (flow.lengthSqr() > 0.001) {
            this.xd = this.xd * 0.9 + flow.x * 0.02;
            this.zd = this.zd * 0.9 + flow.z * 0.02;
        }

        this.yd -= this.gravity;
        this.move(this.xd, this.yd, this.zd);

        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(this.x, this.y, this.z);
        net.minecraft.world.level.material.FluidState fluid = this.level.getFluidState(pos);
        if (fluid.isEmpty()) {
            pos = pos.below();
            fluid = this.level.getFluidState(pos);
        }

        if (!fluid.isEmpty()) {
            float fh = fluid.getHeight(this.level, pos);
            double surfaceY = pos.getY() + (fh > 0 ? fh : 0.88);
            if (this.y <= surfaceY) {
                this.y = surfaceY + 0.01;
                this.yd = 0;
                this.gravity = 0;
            } else {
                this.gravity = 0.04F;
            }
        } else {
            this.gravity = 0.04F;
        }
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public Provider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                net.minecraft.util.RandomSource random) {
            return new RiverBubbleParticle(level, x, y, z, vx, vy, vz, this.sprite.get(random));
        }
    }
}

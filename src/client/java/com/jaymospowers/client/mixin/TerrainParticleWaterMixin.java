package com.jaymospowers.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Motion patch 2) Breaking under water, after Actions &amp; Stuff: the bits of a block broken (or hit) under water drift
 * down slowly instead of dropping as in air, last longer, and some trail a bubble. Every terrain bit goes through this
 * one constructor.
 */
@Mixin(TerrainParticle.class)
abstract class TerrainParticleWaterMixin extends SingleQuadParticle {
	private TerrainParticleWaterMixin(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
		super(level, x, y, z, sprite);
	}

	/** Under water: this share of the usual fall, this share of the spread, this many times as long. */
	private static final float JAYMOSPOWERS$SINK = 0.15F, JAYMOSPOWERS$SPREAD = 0.4F, JAYMOSPOWERS$LIFE = 2.0F;
	/** The chance a bit lets off a bubble. */
	private static final float JAYMOSPOWERS$BUBBLE_CHANCE = 0.35F;

	@Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
		at = @At("TAIL"))
	private void jaymospowers$sinkSlowly(ClientLevel level, double x, double y, double z, double xa, double ya, double za, BlockState state,
		BlockPos pos, CallbackInfo ci) {
		if (!level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)) {
			return;
		}
		this.gravity *= JAYMOSPOWERS$SINK;
		this.xd *= JAYMOSPOWERS$SPREAD;
		this.yd *= JAYMOSPOWERS$SPREAD;
		this.zd *= JAYMOSPOWERS$SPREAD;
		this.lifetime = (int)(this.lifetime * JAYMOSPOWERS$LIFE);
		if (level.getRandom().nextFloat() < JAYMOSPOWERS$BUBBLE_CHANCE) {
			level.addParticle(ParticleTypes.BUBBLE, x, y, z, xa * 0.2, 0.05, za * 0.2);
		}
	}
}

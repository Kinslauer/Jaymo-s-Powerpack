package com.jaymospowers.client.air;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * A fallen leaf floating on water (X2): it lies flat on the surface, turns slowly, is carried by the current and
 * wanders on the still water, and once it drifts against the bank it stays there, so in autumn the leaves pile up
 * along a pond's edges. Gone if the water under it goes.
 */
final class FloatingLeaf extends AirDecal {
	/** How much the current carries it, how far it wanders a tick at most, and how fast it may turn. */
	static final double CURRENT = 0.02, WANDER = 0.0025, WANDER_MAX = 0.01;
	static final float SPIN = 0.01F;

	private final float half;
	private float yaw, spin;
	private double driftX, driftZ;
	private boolean ashore;

	FloatingLeaf(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, int rgb, float size, int life) {
		super(level, x, y, z, new Vec3(size / 2.0, 0.0, 0.0), new Vec3(0.0, 0.0, size / 2.0), sprite, rgb, 1.0F, life, 20, 60, false);
		this.half = size / 2.0F;
		this.yaw = this.random.nextFloat() * Mth.TWO_PI;
		this.spin = (this.random.nextFloat() - 0.5F) * SPIN;
		this.turn();
	}

	private void turn() {
		float c = Mth.cos(this.yaw) * this.half, s = Mth.sin(this.yaw) * this.half;
		this.u = new Vec3(c, 0.0, s);
		this.v = new Vec3(-s, 0.0, c);
	}

	/** Whether it has drifted against the bank and stays there. */
	boolean ashore() {
		return this.ashore;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.isAlive()) {
			return;
		}
		BlockPos here = BlockPos.containing(this.x, this.y - 0.05, this.z);
		FluidState water = this.level.getFluidState(here);
		if (!water.is(FluidTags.WATER) || !this.level.getBlockState(here.above()).isAir()) {
			this.fadeNow();
			return;
		}
		this.y = here.getY() + water.getHeight(this.level, here) + LIFT;
		if (this.ashore) {
			return;
		}
		Vec3 flow = water.getFlow(this.level, here);
		this.driftX = Mth.clamp(this.driftX + (this.random.nextDouble() - 0.5) * WANDER, -WANDER_MAX, WANDER_MAX);
		this.driftZ = Mth.clamp(this.driftZ + (this.random.nextDouble() - 0.5) * WANDER, -WANDER_MAX, WANDER_MAX);
		double nx = this.x + flow.x * CURRENT + this.driftX, nz = this.z + flow.z * CURRENT + this.driftZ;
		BlockPos next = BlockPos.containing(nx, this.y - 0.05, nz);
		if (!next.equals(here) && !this.level.getFluidState(next).is(FluidTags.WATER)) {
			// against the bank: it stays
			this.ashore = true;
			return;
		}
		this.x = nx;
		this.z = nz;
		this.yaw += this.spin;
		this.turn();
	}
}

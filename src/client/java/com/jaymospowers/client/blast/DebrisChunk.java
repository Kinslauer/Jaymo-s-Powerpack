package com.jaymospowers.client.blast;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A chunk of a blasted block, thrown by {@link BlastFx}: the block's own texture, tumbling as it flies, bouncing a little
 * where it lands, then lying still until it shrinks away. It keeps its own simple physics (vanilla's particles stop for
 * good at their first touch of the ground, so they can't bounce).
 */
final class DebrisChunk extends TerrainParticle {
	/** Pull down each tick (blocks/tick², a little more than an item's), and the share of speed the air keeps each tick. */
	static final double GRAVITY = 0.05, DRAG = 0.98;
	/** A landing throws it back up at this share of its fall and keeps this share of its slide; slower than STILL, it lies. */
	static final double BOUNCE = 0.3, SLIDE = 0.55, STILL = 0.07;
	/** Its spin while flying (radians a tick, at most), and how long it takes to shrink away at the end (ticks). */
	static final float SPIN = 0.6F;
	static final int SHRINK = 12;
	/** It flies through blocks for its first few ticks: the blast's own blocks are still there on this side for a moment. */
	static final int CLEAR = 3;

	private float spin;
	private boolean lying;

	DebrisChunk(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, BlockState state) {
		super(level, x, y, z, 0.0, 0.0, 0.0, state);
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		this.hasPhysics = false;
		this.spin = (this.random.nextFloat() * 2.0F - 1.0F) * SPIN;
		this.roll = this.random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
	}

	/** A chunk that drops loose from a ceiling: no throw, no spin to begin with. */
	DebrisChunk falling() {
		this.xd = 0.0;
		this.zd = 0.0;
		this.spin *= 0.3F;
		return this;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		this.oRoll = this.roll;
		if (this.age++ >= this.lifetime) {
			this.remove();
			return;
		}
		if (this.lying) {
			return;
		}
		this.yd -= GRAVITY;
		double nx = this.x + this.xd, ny = this.y + this.yd, nz = this.z + this.zd;
		if (this.age <= CLEAR) {
			this.roll += this.spin;
			this.setPos(nx, ny, nz);
			return;
		}
		// into a wall: knocked back off it
		if (solidAt(nx, this.y, this.z) >= 0.0) {
			this.xd *= -BOUNCE;
			nx = this.x;
		}
		if (solidAt(this.x, this.y, nz) >= 0.0) {
			this.zd *= -BOUNCE;
			nz = this.z;
		}
		double top = solidAt(nx, ny, nz);
		if (top >= 0.0) {
			if (this.yd < 0.0) {
				// landed on top of it
				ny = top + this.quadSize;
				this.yd = -this.yd * BOUNCE;
				this.xd *= SLIDE;
				this.zd *= SLIDE;
				this.spin *= 0.5F;
				if (this.yd < STILL) {
					this.yd = 0.0;
					this.lying = true;
				}
			} else {
				// hit a ceiling
				ny = this.y;
				this.yd = 0.0;
			}
		}
		this.xd *= DRAG;
		this.yd *= DRAG;
		this.zd *= DRAG;
		this.roll += this.spin;
		this.setPos(nx, ny, nz);
	}

	/** The top of whatever solid is at this point (its block's collision top), or -1 if it's open air. */
	private double solidAt(double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		VoxelShape shape = this.level.getBlockState(pos).getCollisionShape(this.level, pos);
		if (shape.isEmpty()) {
			return -1.0;
		}
		double top = pos.getY() + shape.max(Direction.Axis.Y);
		return y < top ? top : -1.0;
	}

	@Override
	public float getQuadSize(float partial) {
		float left = this.lifetime - (this.age + partial);
		return left >= SHRINK ? this.quadSize : this.quadSize * Mth.clamp(left / SHRINK, 0.0F, 1.0F);
	}
}

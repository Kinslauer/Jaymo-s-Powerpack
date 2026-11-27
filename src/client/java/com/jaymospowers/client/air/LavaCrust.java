package com.jaymospowers.client.air;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Lava crust (X9): where still lava lies against rock, its edge cools to a dark crust with glowing cracks, a strip
 * along that bank that spreads out from it over {@link #GROW} ticks. It glows by itself. It breaks up when the lava or
 * the bank goes (or something covers the lava).
 */
final class LavaCrust extends AirDecal {
	/** How far out from the bank it reaches at most (blocks) and how long it takes to spread that far (ticks). */
	static final float DEPTH_MIN = 0.3F, DEPTH_MAX = 0.5F;
	static final int GROW = 200, CHECK_EVERY = 40;
	static final int COLOUR = 0xFFFFFF;

	private final BlockPos lava;
	private final Direction bank;
	private final float depth;

	LavaCrust(ClientLevel level, BlockPos lava, Direction bank, double surface, TextureAtlasSprite sprite, float depth) {
		super(level, lava.getX() + 0.5 + bank.getStepX() * 0.5, surface + LIFT, lava.getZ() + 0.5 + bank.getStepZ() * 0.5, along(bank),
			Vec3.ZERO, sprite, COLOUR, 1.0F, Integer.MAX_VALUE / 2, 40, 40, true);
		this.lava = lava.immutable();
		this.bank = bank;
		this.depth = depth;
		this.spread(0.0F);
	}

	private static Vec3 along(Direction bank) {
		return new Vec3(Math.abs(bank.getStepZ()) * 0.5, 0.0, Math.abs(bank.getStepX()) * 0.5);
	}

	/** Its middle and inward half-side, {@code share} of the way spread. */
	private void spread(float share) {
		float d = this.depth * Math.max(0.05F, share);
		// the bank side is the sprite's top (v0): the middle sits half its depth in from the bank
		this.x = this.lava.getX() + 0.5 + this.bank.getStepX() * (0.5 - d / 2.0);
		this.z = this.lava.getZ() + 0.5 + this.bank.getStepZ() * (0.5 - d / 2.0);
		this.xo = this.x;
		this.zo = this.z;
		this.v = new Vec3(-this.bank.getStepX() * d / 2.0, 0.0, -this.bank.getStepZ() * d / 2.0);
	}

	/** Whether there's still lava here, open above, against a bank on its side. */
	static boolean holds(ClientLevel level, BlockPos lava, Direction bank) {
		FluidState fluid = level.getFluidState(lava);
		if (!fluid.is(FluidTags.LAVA) || !fluid.isSource() || !level.getBlockState(lava.above()).isAir()) {
			return false;
		}
		BlockPos side = lava.relative(bank);
		return level.getFluidState(side).isEmpty() && level.getBlockState(side).isFaceSturdy(level, side, bank.getOpposite());
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.isAlive()) {
			return;
		}
		if (this.age <= GROW) {
			this.spread(Mth.clamp(this.age / (float) GROW, 0.0F, 1.0F));
		}
		if (this.age % CHECK_EVERY == 0 && !holds(this.level, this.lava, this.bank)) {
			this.fadeNow();
		}
	}
}

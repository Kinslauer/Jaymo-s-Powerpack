package com.jaymospowers.client.blood;

import com.jaymospowers.core.blood.BloodParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Blood that fell into water: a short-lived red cloud on the surface that spreads, drifts the way the water flows
 * ({@code FluidState#getFlow}) and fades. Bits of it ({@link #sinking}) are heavy and sink instead, fading as they go.
 * Only drawn; nothing lies on the bottom.
 */
final class BloodCloud extends SingleQuadParticle {
	/** How long a cloud lasts (ticks, at least this, up to this many more) and how fast it spreads (share of its size a tick). */
	static final int LIFE = 70, LIFE_SPREAD = 40;
	static final float SPREAD = 0.012F;
	/** How far the flow moves it a tick (blocks per unit of flow), and how fast a heavy bit sinks (blocks a tick). */
	static final double DRIFT = 0.06, SINK = 0.025;
	/** A cloud starts this opaque; this many times its drop's size. */
	static final float ALPHA = 0.65F, SIZE = 2.2F;
	/** A drop's spill makes this many heavy bits, by chance each, drawn this much smaller. */
	static final int BITS = 3;
	static final float BIT_CHANCE = 0.5F, BIT_SIZE = 0.45F;

	private final boolean sinking;

	private BloodCloud(ClientLevel level, double x, double y, double z, SpriteSet sprites, int color, float size, boolean sinking) {
		super(level, x, y, z, 0.0, 0.0, 0.0, sprites.get(level.getRandom()));
		this.sinking = sinking;
		this.setColor(BloodPuddle.red(color), BloodPuddle.green(color), BloodPuddle.blue(color));
		this.alpha = ALPHA;
		this.scale(size);
		this.lifetime = LIFE + this.random.nextInt(LIFE_SPREAD);
		this.gravity = 0.0F;
		this.hasPhysics = false;
		this.xd = 0.0;
		this.yd = 0.0;
		this.zd = 0.0;
	}

	/** A drop of {@code color}, {@code dropSize} across, fell into the water at (x, surface, z): a cloud there, and some heavy bits that sink. */
	static void spill(ClientLevel level, double x, double surface, double z, int color, float dropSize) {
		SpriteSet sprites = BloodClient.dropSprites();
		if (sprites == null || BloodSettings.off()) {
			return;
		}
		color = BloodSettings.tint(color);
		var engine = Minecraft.getInstance().particleEngine;
		engine.add(new BloodCloud(level, x, surface, z, sprites, color, dropSize * SIZE, false));
		for (int i = 0; i < BITS; i++) {
			if (level.getRandom().nextFloat() < BIT_CHANCE * BloodSettings.gore().share) {
				engine.add(new BloodCloud(level, x + (level.getRandom().nextDouble() - 0.5) * 0.2, surface - 0.1, z + (level.getRandom().nextDouble() - 0.5) * 0.2,
					sprites, color, dropSize * SIZE * BIT_SIZE, true));
			}
		}
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.age++ >= this.lifetime) {
			this.remove();
			return;
		}
		BlockPos pos = BlockPos.containing(this.x, this.y, this.z);
		FluidState fluid = this.level.getFluidState(pos);
		if (!fluid.is(FluidTags.WATER)) {
			this.remove();
			return;
		}
		Vec3 flow = fluid.getFlow(this.level, pos);
		double y = this.y;
		if (this.sinking) {
			y -= SINK;
		} else {
			// a cloud rides the surface
			y = pos.getY() + fluid.getHeight(this.level, pos) - 0.05;
		}
		this.setPos(this.x + flow.x * DRIFT, y, this.z + flow.z * DRIFT);
		this.scale(1.0F + SPREAD);
		float life = this.age / (float) this.lifetime;
		this.alpha = ALPHA * (1.0F - Mth.clamp(life, 0.0F, 1.0F)) * (this.sinking ? 1.0F - life : 1.0F);
	}

	@Override
	protected SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}
}

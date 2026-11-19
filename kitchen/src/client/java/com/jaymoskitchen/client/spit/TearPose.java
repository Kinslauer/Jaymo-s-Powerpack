package com.jaymoskitchen.client.spit;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Tearing a piece off the spit (Andy, 2026-11-19: "animate ripping chunks off, for both them and players"): whoever tears
 * it (a player, a camp's cook, anyone drawn with a humanoid's arms) reaches in with both hands, takes hold, and rips it
 * away, the right hand pulling back with the piece while the left lets go and the body leans back against it. Started
 * by the server's word ({@code Payloads.SpitRip}); set on the render state by {@code LivingEntityRendererMixin} and
 * applied after the model animates ({@code ModelFeatureRendererMixin}).
 */
public final class TearPose {
	/** Ticks into the tear, for the model. */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:tearing");
	/** Reaching in (ticks), holding on until, done by. */
	static final float REACH = 6.0F, GRIP = 8.0F, DONE = 15.0F;
	/** The reach: arms forward (radians), turned in toward each other; the rip: the right arm back to, out by; the lean back. */
	static final float REACH_X = -1.45F, REACH_IN = 0.3F, PULL_X = -0.45F, PULL_OUT = 0.35F, LEAN = -0.18F;

	/** Entity id → the game tick its tear started. */
	private static final Map<Integer, Long> START = new HashMap<>();

	private TearPose() {
	}

	public static void start(int id, BlockPos pos, long tick) {
		if (id >= 0) {
			START.put(id, tick);
		}
	}

	public static void extract(LivingEntity entity, EntityRenderState state, float partialTicks) {
		Long start = START.get(entity.getId());
		Float age = null;
		if (start != null) {
			float t = entity.level().getGameTime() - start + partialTicks;
			if (t > DONE || t < 0.0F) {
				START.remove(entity.getId());
			} else {
				age = t;
			}
		}
		state.setData(KEY, age);
	}

	public static void apply(Model<?> model, Object state) {
		if (!(model instanceof HumanoidModel<?> humanoid) || !(state instanceof EntityRenderState render)) {
			return;
		}
		Float age = render.getData(KEY);
		if (age == null) {
			return;
		}
		float in = smooth(age / REACH);
		float rip = smooth((age - GRIP) / (DONE - GRIP));
		// eased off over the last few ticks
		float w = Math.min(1.0F, (DONE - age) / 3.0F) * in;
		set(humanoid.rightArm, Mth.lerp(rip, REACH_X, PULL_X), Mth.lerp(rip, -REACH_IN, 0.0F), rip * PULL_OUT, w);
		// the left hand lets go as the right pulls, and drops back
		set(humanoid.leftArm, Mth.lerp(rip, REACH_X, REACH_X * 0.4F), REACH_IN, 0.0F, w * (1.0F - rip * 0.5F));
		float lean = Mth.sin(rip * Mth.PI) * LEAN;
		humanoid.body.xRot += lean * w;
		humanoid.head.xRot += (0.25F * in - lean) * w;
	}

	private static float smooth(float t) {
		float x = Mth.clamp(t, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	private static void set(ModelPart part, float x, float y, float z, float w) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}

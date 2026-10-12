package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Wagon;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.5.27) A rickshaw puller's pose: bent forward into the shafts, hands down at their hips gripping them, tugging the
 * rickshaw along with each stride. The more worn out they are ({@link Wagon#tired()}), the lower they lean, the heavier
 * the tug and the more their head hangs; stood still and spent, they pant. Laid over whatever model draws them (our
 * {@code Folk}, or an MCA villager's) right after it's posed, like {@link MobAnimations}.
 */
public final class PullerPose {
	/** How far they lean (radians): fresh, and the most when spent; stood still it's this much less. */
	static final float LEAN = 0.32F, LEAN_TIRED = 0.38F, LEAN_STILL = 0.6F;
	/** Each stride's tug (radians), fresh and spent. */
	static final float TUG = 0.07F, TUG_TIRED = 0.13F;

	/** This frame's {lean, tug phase, tired, panting} for a puller, on their render state. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:puller");

	private static final boolean MCA = com.jaymoskingdoms.McaCompat.LOADED;

	/** Who's between a rickshaw's shafts (entity id → how worn out), refreshed every client tick. */
	private static final Int2FloatMap PULLING = new Int2FloatOpenHashMap();

	private PullerPose() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			PULLING.clear();
			if (client.level == null) {
				return;
			}
			for (Entity e : client.level.entitiesForRendering()) {
				if (e instanceof Wagon w && w.kind() == Wagon.RICKSHAW && w.pullerId() >= 0) {
					PULLING.put(w.pullerId(), w.tired());
				}
			}
		});
	}

	/** Render-state extraction: a puller's pose for this frame. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (entity instanceof Avatar || !PULLING.containsKey(entity.getId())) {
			return;
		}
		float tired = PULLING.get(entity.getId());
		float moving = Mth.clamp(state.walkAnimationSpeed * 2.5F, 0.0F, 1.0F);
		float lean = Mth.lerp(tired, LEAN, LEAN + LEAN_TIRED) * Mth.lerp(moving, LEAN_STILL, 1.0F);
		float tug = Mth.sin(state.walkAnimationPos * 0.6662F * 2.0F) * Mth.lerp(tired, TUG, TUG_TIRED) * moving;
		// (stood still and worn out: their shoulders heave)
		float pant = tired > 0.5F ? (1.0F - moving) * (tired - 0.5F) * 0.12F * Mth.sin(state.ageInTicks * 0.45F) : 0.0F;
		state.setData(KEY, new float[] {lean + tug + pant, tug, tired, pant});
	}

	/** Right after a model was posed for this state: bent into the shafts. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		float lean = pose[0], tug = pose[1], tired = pose[2];
		// bent at the waist as a crouch is, the head and shoulders brought down and the legs back to match
		float k = lean / 0.5F;
		m.body.xRot = lean;
		m.body.y += 3.2F * k;
		m.head.y += 4.2F * k;
		m.rightArm.y += 3.2F * k;
		m.leftArm.y += 3.2F * k;
		m.rightLeg.z += 4.0F * k;
		m.leftLeg.z += 4.0F * k;
		// eyes on the road ahead, or, worn out, on the ground at their feet
		m.head.xRot = -lean * 0.8F + tired * 0.55F;
		m.head.yRot *= 0.3F;
		// both hands down at their hips on the shafts, tugging back with each stride
		float grip = -0.2F + lean * 0.35F - tug * 1.5F;
		m.rightArm.xRot = grip;
		m.leftArm.xRot = grip;
		m.rightArm.yRot = 0.0F;
		m.leftArm.yRot = 0.0F;
		m.rightArm.zRot = 0.14F;
		m.leftArm.zRot = -0.14F;
		if (MCA) {
			McaPullerPose.follow(model, lean, k);
		}
	}
}

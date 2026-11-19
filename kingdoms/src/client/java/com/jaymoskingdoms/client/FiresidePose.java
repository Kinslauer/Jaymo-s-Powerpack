package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Firesides;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * How one sits at a camp's fire ({@link Firesides#POSE}), laid over the seated pose right after it's posed like
 * {@link CastPose}: the legs kept together out in front (not the rider's splayed knees), and the hands at what they're
 * about:
 * <ul>
 * <li>{@link Firesides#WARM}: both hands held out to the flames, head a little bowed.</li>
 * <li>{@link Firesides#LEAN}: leaning back on the hands, set on the ground behind, face up.</li>
 * <li>{@link Firesides#HUNCH}: elbows on the knees, hands together, looking into the fire.</li>
 * <li>{@link Firesides#EAT}: a share in the off hand, brought up to the mouth and down again.</li>
 * <li>{@link Firesides#SING}: a fist raised and swung in time, the head rocking.</li>
 * <li>{@link Firesides#WHETTING}: the blade across the lap, the stone hand drawing along it.</li>
 * </ul>
 * Arm turns are the model's (radians): x forward and up from hanging (negative), y in across the chest (right arm:
 * negative), z out to the side (right arm: positive).
 */
public final class FiresidePose {
	/** This frame's {pose, age in ticks} for one sitting at a fire, on its render state. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:fireside_pose");
	/** The legs out in front, side by side. */
	static final float LEGS_X = -1.45F;

	private FiresidePose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Integer pose = entity.getAttached(Firesides.POSE);
		if (pose != null && pose > 0 && entity.isPassenger()) {
			state.setData(KEY, new float[] {pose, entity.tickCount + partialTicks});
		}
	}

	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] data = living.getData(KEY);
		if (data == null) {
			return;
		}
		float age = data[1];
		legs(m.rightLeg);
		legs(m.leftLeg);
		switch ((int)data[0]) {
			case Firesides.WARM -> {
				float sway = Mth.sin(age * 0.05F) * 0.04F;
				set(m.rightArm, -1.2F + sway, -0.3F, 0.0F);
				set(m.leftArm, -1.2F - sway, 0.3F, 0.0F);
				m.head.xRot = 0.25F;
			}
			case Firesides.LEAN -> {
				set(m.rightArm, 0.65F, 0.0F, 0.35F);
				set(m.leftArm, 0.65F, 0.0F, -0.35F);
				m.head.xRot = Math.min(m.head.xRot, -0.1F);
			}
			case Firesides.HUNCH -> {
				set(m.rightArm, -0.95F, -0.5F, 0.0F);
				set(m.leftArm, -0.95F, 0.5F, 0.0F);
				m.head.xRot = Math.max(m.head.xRot, 0.3F);
			}
			case Firesides.EAT -> {
				// up to the mouth and down, a bite a second
				float up = 0.5F + 0.5F * Mth.sin(age * Mth.TWO_PI / 20.0F);
				set(m.leftArm, Mth.lerp(up, -1.1F, -1.95F), Mth.lerp(up, 0.2F, 0.55F), 0.0F);
				set(m.rightArm, -0.6F, -0.15F, 0.0F);
			}
			case Firesides.SING -> {
				float beat = Mth.sin(age * 0.35F);
				set(m.rightArm, -2.5F + beat * 0.3F, 0.0F, 0.25F);
				set(m.leftArm, -0.7F, 0.2F, 0.0F);
				m.head.xRot = -0.2F;
				m.head.zRot = beat * 0.12F;
			}
			case Firesides.WHETTING -> {
				float stroke = Mth.sin(age * 0.6F);
				set(m.rightArm, -0.85F + stroke * 0.12F, -0.35F + stroke * 0.25F, 0.0F);
				set(m.leftArm, -0.75F, 0.35F, 0.0F);
				m.head.xRot = 0.45F;
			}
			default -> {
			}
		}
	}

	private static void legs(ModelPart leg) {
		leg.xRot = LEGS_X;
		leg.yRot = 0.0F;
		leg.zRot = 0.0F;
	}

	private static void set(ModelPart arm, float x, float y, float z) {
		arm.xRot = x;
		arm.yRot = y;
		arm.zRot = z;
	}
}

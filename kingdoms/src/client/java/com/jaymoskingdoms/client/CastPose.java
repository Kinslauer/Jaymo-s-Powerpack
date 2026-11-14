package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Casting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/**
 * A caster's hands ({@link Casting}): for {@link Casting#TICKS} after a spell is thrown, the arms of whoever threw it
 * (a player, a villager, any body with a man's arms) make its gesture, eased in and out, laid over the model right after
 * it's posed like {@link PickpocketPose}. In first person the hand you see makes a small version of the same motion.
 * The lore films' mages make the same seven with their own bones (the moves of the same names in
 * {@code kingdoms/tools/humanoid_moves.py}).
 * <p>
 * Arm turns are the model's (radians): x forward and up from hanging (a quarter turn back is straight ahead, a half
 * turn straight up), y in across the chest (for the right arm, negative), z out to the side (for the right arm, positive).
 */
public final class CastPose {
	/** Easing in and out of a gesture (ticks), and how long the hand gathers at the chest before a push (ticks). */
	static final float IN = 4.0F, OUT = 5.0F, GATHER = 3.0F;
	/** Straight ahead and straight up. */
	static final float AHEAD = -Mth.HALF_PI, UP = -2.9F;
	/** First person: how far the hand travels in a gesture (hand view space, blocks). */
	static final float FIRST = 0.3F;

	/** This frame's {gesture, weight, ticks into it, 1 if the off hand leads} for a caster, on their render state. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:cast_pose");

	/** Entity id → {gesture, the entity's age when it began, 1 if the off hand leads}. */
	private static final Int2ObjectMap<float[]> CASTING = new Int2ObjectOpenHashMap<>();

	private CastPose() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Casting.Cast.TYPE, (payload, context) -> {
			Minecraft minecraft = context.client();
			Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(payload.entity());
			if (entity != null) {
				CASTING.put(payload.entity(), new float[] {payload.gesture(), entity.tickCount, payload.off() ? 1.0F : 0.0F});
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CASTING.clear());
	}

	/** {gesture, weight, ticks into it, off} for {@code entity} now, or null when it isn't casting. */
	private static float @org.jspecify.annotations.Nullable [] now(Entity entity, float partialTicks) {
		float[] cast = CASTING.get(entity.getId());
		if (cast == null) {
			return null;
		}
		float age = entity.tickCount + partialTicks - cast[1];
		if (age < 0.0F || age >= Casting.TICKS) {
			CASTING.remove(entity.getId());
			return null;
		}
		float w = Math.min(ease(age / IN), ease((Casting.TICKS - age) / OUT));
		return new float[] {cast[0], w, age, cast[2]};
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/** Render-state extraction: a caster's gesture for this frame. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (CASTING.isEmpty()) {
			return;
		}
		float[] pose = now(entity, partialTicks);
		if (pose != null) {
			state.setData(KEY, pose);
		}
	}

	/** Right after a model was posed for this state: the hands at their work. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		boolean left = (state instanceof HumanoidRenderState h && h.mainArm == HumanoidArm.LEFT) != (pose[3] > 0.5F);
		ModelPart lead = left ? m.leftArm : m.rightArm, other = left ? m.rightArm : m.leftArm;
		// (in across the chest is negative y for the right arm, out to the side positive z; the left arm is its mirror)
		float side = left ? -1.0F : 1.0F, w = pose[1], age = pose[2];
		float aim = AHEAD + m.head.xRot * 0.8F;
		switch (Casting.Gesture.values()[Mth.clamp((int)pose[0], 0, Casting.Gesture.values().length - 1)]) {
			case PUSH -> {
				// gathered at the chest, then the open palm pushed out along the eye, the other hand come up behind the wrist
				float out = ease((age - GATHER * 0.5F) / GATHER);
				set(lead, Mth.lerp(out, -0.9F, aim), Mth.lerp(out, -0.8F, -0.12F) * side, 0.0F, w);
				set(other, Mth.lerp(out, -0.8F, aim + 0.25F), Mth.lerp(out, 0.7F, 0.5F) * side, 0.0F, w);
			}
			case RAISE -> {
				set(lead, UP, 0.0F, 0.22F * side, w);
				set(other, -0.25F, 0.0F, -0.55F * side, w);
				m.head.xRot -= 0.35F * w;
			}
			case GROUND -> {
				// the palm driven down at the earth before the feet, the other arm thrown back for balance
				float down = ease(age / (IN + 2.0F));
				set(lead, Mth.lerp(down, -2.2F, -0.7F), -0.2F * side, 0.0F, w);
				set(other, 0.45F, 0.0F, -0.5F * side, w);
				m.head.xRot += 0.45F * w;
			}
			case WARD -> {
				set(lead, AHEAD + 0.15F, 0.5F * side, 0.0F, w);
				set(other, AHEAD + 0.15F, -0.5F * side, 0.0F, w);
			}
			case GIVE -> {
				set(lead, -1.0F, -0.15F * side, 0.0F, w);
				set(other, -0.25F, 0.3F * side, 0.0F, w);
				m.head.xRot += 0.15F * w;
			}
			case DRAW -> {
				// out at what is taken, then clawed back in to the chest
				float in = ease((age - 2.0F) / 7.0F);
				set(lead, Mth.lerp(in, aim, -0.85F), Mth.lerp(in, -0.1F, -0.95F) * side, 0.0F, w);
				set(other, -0.3F, 0.0F, -0.35F * side, w);
			}
			default -> {
				// the arm swept across the body, from the far shoulder out to its own side
				float across = ease((age - 1.0F) / 8.0F);
				set(lead, AHEAD + 0.1F, Mth.lerp(across, -1.0F, 0.75F) * side, 0.0F, w);
				set(other, -0.2F, 0.0F, -0.3F * side, w);
			}
		}
	}

	private static void set(ModelPart part, float x, float y, float z, float w) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	/**
	 * First person, before {@code hand} is drawn: moved through a small version of your gesture (the palm pushed out, lifted,
	 * driven down, opened, drawn in, swept across). Nothing while you aren't casting, or for the hand that isn't leading.
	 */
	public static void firstPerson(PoseStack poseStack, InteractionHand hand, HumanoidArm mainArm, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (CASTING.isEmpty() || minecraft.player == null) {
			return;
		}
		float[] pose = now(minecraft.player, partialTicks);
		if (pose == null || (hand == InteractionHand.OFF_HAND) != (pose[3] > 0.5F)) {
			return;
		}
		float side = (mainArm == HumanoidArm.LEFT) != (hand == InteractionHand.OFF_HAND) ? -1.0F : 1.0F, w = pose[1], age = pose[2];
		switch (Casting.Gesture.values()[Mth.clamp((int)pose[0], 0, Casting.Gesture.values().length - 1)]) {
			case PUSH -> {
				float out = ease((age - GATHER * 0.5F) / GATHER);
				poseStack.translate(-side * FIRST * 0.9F * w, FIRST * 0.5F * w, Mth.lerp(out, FIRST * 0.4F, -FIRST * 1.2F) * w);
				poseStack.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(-28.0F * w * out)));
			}
			case RAISE -> {
				poseStack.translate(-side * FIRST * 0.4F * w, FIRST * 1.5F * w, 0.0F);
				poseStack.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(24.0F * w)));
			}
			case GROUND -> {
				float down = ease(age / (IN + 2.0F));
				poseStack.translate(-side * FIRST * 0.7F * w, Mth.lerp(down, FIRST * 1.2F, -FIRST * 0.5F) * w, -FIRST * 0.6F * w);
				poseStack.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(-40.0F * w * down)));
			}
			case WARD -> {
				poseStack.translate(-side * FIRST * 0.3F * w, FIRST * 0.8F * w, -FIRST * 0.7F * w);
				poseStack.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(side * 20.0F * w)));
			}
			case GIVE -> {
				poseStack.translate(-side * FIRST * 0.8F * w, FIRST * 0.2F * w, -FIRST * 0.8F * w);
				poseStack.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(-side * 55.0F * w)));
			}
			case DRAW -> {
				float in = ease((age - 2.0F) / 7.0F);
				poseStack.translate(-side * FIRST * Mth.lerp(in, 0.6F, 1.4F) * w, FIRST * 0.4F * w, Mth.lerp(in, -FIRST * 1.1F, FIRST * 0.5F) * w);
				poseStack.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(side * 30.0F * w * in)));
			}
			default -> {
				float across = ease((age - 1.0F) / 8.0F);
				poseStack.translate(-side * Mth.lerp(across, FIRST * 2.6F, -FIRST * 0.6F) * w, FIRST * 0.6F * w, -FIRST * 0.6F * w);
				poseStack.mulPose(new Matrix4f().rotation(Axis.YP.rotationDegrees(side * Mth.lerp(across, 35.0F, -25.0F) * w)));
			}
		}
	}
}

package com.jaymoskingdoms.client;

import com.jaymoskingdoms.NpcDodge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A bandit's or assassin's roll out of a blow ({@link NpcDodge}), drawn: the whole body tumbles over sideways, toward the side it rolls
 * to, around its middle, knees and arms tucked in through it. Keyframed over the roll's length, as the server sends it (a
 * {@code MobSwing} of {@link NpcDodge#ROLL_ANIMATION}, mirrored for a roll to its left).
 */
public final class NpcRollPose {
	/** The tumble (degrees over) at each share of the roll, and how tucked the limbs are; between keys it eases. */
	static final float[] KEYS = {0.0F, 0.15F, 0.5F, 0.85F, 1.0F};
	static final float[] TURN = {0.0F, 25.0F, 180.0F, 335.0F, 360.0F};
	static final float[] TUCK = {0.0F, 1.0F, 1.0F, 1.0F, 0.0F};
	/** Fully tucked, the arms come forward this much, the legs (knees up) this much (radians). */
	static final float ARM_TUCK = 1.1F, LEG_TUCK = 1.3F;

	/** This frame's {turn (degrees, over to its left when positive), tuck} on the roller's render state. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:npc_roll");

	/** Entity id → {its age when the roll began, the roll's length (ticks), 1 for a roll to its left}. */
	private static final Int2ObjectMap<int[]> ROLLS = new Int2ObjectOpenHashMap<>();

	private NpcRollPose() {
	}

	static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ROLLS.clear());
	}

	/** (from {@link MobAnimations}' receiver) The mob {@code entityId} rolls now, over {@code length} ticks, to its left or right. */
	static void start(Minecraft minecraft, int entityId, int length, boolean left) {
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
		if (entity != null) {
			ROLLS.put(entityId, new int[] {entity.tickCount, Math.max(1, length), left ? 1 : 0});
		}
	}

	/** Render-state extraction: the roll's turn and tuck this frame, or none. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		int[] roll = ROLLS.isEmpty() ? null : ROLLS.get(entity.getId());
		float[] pose = null;
		if (roll != null) {
			float t = (entity.tickCount + partialTicks - roll[0]) / roll[1];
			if (t < 0.0F || t >= 1.0F || !entity.isAlive()) {
				ROLLS.remove(entity.getId());
			} else {
				pose = new float[] {(roll[2] == 1 ? 1.0F : -1.0F) * key(TURN, t), key(TUCK, t)};
			}
		}
		state.setData(KEY, pose);
	}

	/** (after {@code setupRotations}) The body turned over sideways around its middle. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		float[] pose = state.getData(KEY);
		if (pose == null) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotate(Axis.ZP.rotationDegrees(pose[0]));
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** Right after a model was posed: the limbs tucked in. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		float tuck = pose[1];
		m.rightArm.xRot = Mth.lerp(tuck, m.rightArm.xRot, -ARM_TUCK);
		m.leftArm.xRot = Mth.lerp(tuck, m.leftArm.xRot, -ARM_TUCK);
		m.rightLeg.xRot = Mth.lerp(tuck, m.rightLeg.xRot, -LEG_TUCK);
		m.leftLeg.xRot = Mth.lerp(tuck, m.leftLeg.xRot, -LEG_TUCK);
	}

	/** {@code values} at share {@code t} of the roll, eased between the two keys either side. */
	private static float key(float[] values, float t) {
		for (int i = 1; i < KEYS.length; i++) {
			if (t <= KEYS[i]) {
				float f = (t - KEYS[i - 1]) / (KEYS[i] - KEYS[i - 1]);
				f = f * f * (3.0F - 2.0F * f);
				return Mth.lerp(f, values[i - 1], values[i]);
			}
		}
		return values[values.length - 1];
	}
}

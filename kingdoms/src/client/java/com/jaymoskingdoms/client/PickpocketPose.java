package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Pickpocket;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.5.50) A pickpocket's reach: while a player's hand is in someone's pocket ({@link Pickpocket.Reach}), their main arm
 * goes forward and down into it, the fingers working, the other arm drawn in. Laid over the player's model right after
 * it's posed, like {@link PullerPose}.
 */
public final class PickpocketPose {
	/** The reaching arm: forward and down (radians), turned in toward the pocket, and how much the fingers work. */
	static final float REACH = -0.85F, REACH_IN = 0.25F, WORK = 0.06F;

	/** This frame's {age} for a player reaching, on their render state. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:pickpocket");

	/** Player entity id → the mark's entity id. */
	private static final Int2IntMap REACHING = new Int2IntOpenHashMap();

	private PickpocketPose() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Pickpocket.Reach.TYPE, (payload, context) -> {
			if (payload.markId() < 0) {
				REACHING.remove(payload.playerId());
			} else {
				REACHING.put(payload.playerId(), payload.markId());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> REACHING.clear());
	}

	/** Render-state extraction: a reaching player's pose for this frame. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (entity instanceof Avatar && REACHING.containsKey(entity.getId())) {
			state.setData(KEY, new float[] {state.ageInTicks});
		}
	}

	/** Right after a model was posed for this state: the hand in the pocket. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		boolean left = state instanceof HumanoidRenderState h && h.mainArm == HumanoidArm.LEFT;
		ModelPart reach = left ? m.leftArm : m.rightArm, other = left ? m.rightArm : m.leftArm;
		float side = left ? -1.0F : 1.0F;
		float work = Mth.sin(pose[0] * 0.9F) * WORK;
		reach.xRot = REACH + work;
		reach.yRot = REACH_IN * side;
		reach.zRot = 0.0F;
		// (the other hand kept close, out of the way)
		other.xRot = -0.2F;
		other.yRot = 0.0F;
		other.zRot = -0.1F * side;
	}
}

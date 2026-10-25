package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Handshakes;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.184) A handshake ({@link Handshakes}), for everyone to see: a hand held out (the right arm forward and turned in,
 * waiting), then taken: the two right hands meet, a few slow pumps, and let go. Eased on a spring like
 * {@link PickpocketPose}, and laid over the model right after it's posed. Between two who've shaken hands before, a
 * quick dap: the hand up high, one clasp and a tug in. (0.7.204) A villager taking your hand shakes the same way.
 */
public final class HandshakePose {
	/** The arm held out: forward and a little down, turned in toward the middle (radians). */
	static final float REACH = -1.2F, REACH_IN = -0.4F;
	/** The pumps while gripped: how many, and how far (radians). */
	static final float PUMPS = 3.0F, PUMP = 0.2F;
	/** (a dap) The hand up higher and further in, the one tug toward the chest, the lean into it (radians); its quicker ease. */
	static final float DAP_REACH = -1.65F, DAP_IN = -0.55F, DAP_TUG = 0.35F, DAP_LEAN = 0.08F, DAP_QUICKER = 2.5F;
	/** Head bowed a touch while shaking (radians). */
	static final float NOD = 0.12F;
	/** The ease: stiffness (per second squared), damping ratio, the longest step (seconds). */
	static final float STIFFNESS = 60.0F, DAMPING = 0.85F, MAX_DT = 0.1F, SUBSTEP = 0.008F;

	/** This frame's {weight, ticks into the shake (or -1 while just held out), 1 for a dap} on a render state. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:handshake");

	/** A player's hand: held out or shaking, toward whom, and since when (client game time, for a shake). */
	record Hand(int partner, int state, long since) {
	}

	/** Player entity id → their hand. */
	private static final Map<Integer, Hand> HANDS = new HashMap<>();
	/** Entity id → its ease {weight, velocity}, and when it was last stepped (nanoseconds). */
	private static final Map<Integer, float[]> EASES = new HashMap<>();
	private static final Map<Integer, Long> EASE_TIMES = new HashMap<>();

	private HandshakePose() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Handshakes.Hand.TYPE, (payload, context) -> {
			if (payload.state() == Handshakes.Hand.DOWN) {
				HANDS.remove(payload.playerId());
			} else {
				long now = context.client().level == null ? 0L : context.client().level.getGameTime();
				HANDS.put(payload.playerId(), new Hand(payload.partnerId(), payload.state(), now));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			HANDS.clear();
			EASES.clear();
			EASE_TIMES.clear();
		});
	}

	/** The hand of the player with entity id {@code id}, or null. */
	static @Nullable Hand hand(int id) {
		return HANDS.get(id);
	}

	/** Ticks into {@code hand}'s shake this frame. */
	static float into(Hand hand, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0.0F : minecraft.level.getGameTime() - hand.since + partialTicks;
	}

	/** Render-state extraction: the arm's weight this frame. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		// (0.7.204) a player's, or a villager's taking one
		int id = entity.getId();
		if (!(entity instanceof Avatar) && !HANDS.containsKey(id) && !EASES.containsKey(id)) {
			return;
		}
		Hand hand = HANDS.get(id);
		float t = -1.0F;
		boolean on = hand != null, dap = hand != null && hand.state == Handshakes.Hand.DAP;
		if (hand != null && (hand.state == Handshakes.Hand.SHAKING || dap)) {
			t = into(hand, partialTicks);
			on = t < (dap ? Handshakes.DAP_RELEASE : Handshakes.RELEASE_TICK);
		}
		float[] ease = EASES.get(id);
		long now = System.nanoTime();
		if (ease == null) {
			if (!on) {
				return;
			}
			ease = new float[2];
			EASES.put(id, ease);
			EASE_TIMES.put(id, now);
		}
		float dt = Mth.clamp((now - EASE_TIMES.get(id)) / 1.0E9F, 0.0F, MAX_DT);
		EASE_TIMES.put(id, now);
		int steps = Math.max(1, Mth.ceil(dt / SUBSTEP));
		float stiffness = dap ? STIFFNESS * DAP_QUICKER : STIFFNESS;
		float h = dt / steps, damping = 2.0F * DAMPING * Mth.sqrt(stiffness), target = on ? 1.0F : 0.0F;
		for (int i = 0; i < steps; i++) {
			ease[1] += (stiffness * (target - ease[0]) - damping * ease[1]) * h;
			ease[0] += ease[1] * h;
		}
		ease[0] = Mth.clamp(ease[0], 0.0F, 1.1F);
		if (!on && ease[0] < 0.003F && Math.abs(ease[1]) < 0.03F) {
			EASES.remove(id);
			EASE_TIMES.remove(id);
			return;
		}
		state.setData(KEY, new float[] {ease[0], t, dap ? 1.0F : 0.0F});
	}

	/** Right after a model was posed for this state: the right hand out, or shaking. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		float w = pose[0], t = pose[1];
		float pump = 0.0F, nod = 0.0F;
		ModelPart arm = m.rightArm;
		if (pose[2] > 0.0F) {
			// a dap: the hand comes up high, clasps, one tug in toward the chest
			float tug = 0.0F;
			if (t >= Handshakes.DAP_GRIP && t < Handshakes.DAP_RELEASE) {
				tug = Mth.sin((t - Handshakes.DAP_GRIP) / (Handshakes.DAP_RELEASE - Handshakes.DAP_GRIP) * Mth.PI) * DAP_TUG;
			}
			arm.xRot = Mth.lerp(w, arm.xRot, DAP_REACH + tug);
			arm.yRot = Mth.lerp(w, arm.yRot, DAP_IN);
			arm.zRot = Mth.lerp(w, arm.zRot, 0.0F);
			m.body.xRot += DAP_LEAN * (tug / DAP_TUG) * Math.min(1.0F, w);
			return;
		}
		if (t >= Handshakes.GRIP_TICK && t < Handshakes.RELEASE_TICK) {
			float g = (t - Handshakes.GRIP_TICK) / (Handshakes.RELEASE_TICK - Handshakes.GRIP_TICK);
			// the pumps start and end gently
			pump = Mth.sin(g * PUMPS * Mth.TWO_PI) * PUMP * Mth.sin(g * Mth.PI);
			nod = NOD * Mth.sin(g * Mth.PI);
		}
		arm.xRot = Mth.lerp(w, arm.xRot, REACH + pump);
		arm.yRot = Mth.lerp(w, arm.yRot, REACH_IN);
		arm.zRot = Mth.lerp(w, arm.zRot, 0.0F);
		m.head.xRot += nod * Math.min(1.0F, w);
	}
}

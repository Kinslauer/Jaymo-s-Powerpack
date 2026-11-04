package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ClassPerks;
import com.jaymoskingdoms.Handshakes;
import com.jaymoskingdoms.McaCompat;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.184) A handshake ({@link Handshakes}), for everyone to see: a hand held out (the right arm forward and turned in,
 * waiting), then taken: the two right hands meet, a few slow pumps, and let go. Eased on a spring like
 * {@link PickpocketPose}, and laid over the model right after it's posed. Between two who've shaken hands before, a
 * quick dap: the hand up high, one clasp and a tug in. (0.7.204) A villager taking your hand shakes the same way.
 * (0.7.289) A villager's wave at a first meeting: the right hand raised out to the side, swung to and fro a few times;
 * and a shake off film ({@code Hand.PLAIN}) looks the same as one on it.
 * (0.7.504) A bow to a ruler ({@code Hand.BOW}): bent from the waist, head down, the hands by what's in them. Empty
 * handed, the hand over the heart and the other swept behind; a blade in hand, held low at your side with the other hand
 * come across onto its pommel; anything else, held at your side with the free hand over the heart. A hand with something
 * in it (a shield, a torch) stays down by your side. The ruler's answer, a nod ({@code Hand.NOD}).
 */
public final class HandshakePose {
	/** The arm held out: forward and a little down, turned in toward the middle (radians). */
	static final float REACH = -1.2F, REACH_IN = -0.4F;
	/** The pumps while gripped: how many, and how far (radians). */
	static final float PUMPS = 3.0F, PUMP = 0.2F;
	/** (a dap) The hand up higher and further in, the one tug toward the chest, the lean into it (radians); its quicker ease. */
	static final float DAP_REACH = -1.65F, DAP_IN = -0.55F, DAP_TUG = 0.35F, DAP_LEAN = 0.08F, DAP_QUICKER = 2.5F;
	/** (0.7.205) How long (ticks) the one taking the hand waits before reaching, so it meets the other in the film's hand shot. */
	static final float REACH_DELAY = 3.0F;
	/** (0.7.289) The wave: the arm raised out to the side and a little forward, and its swing (radians), and how fast (radians per tick). */
	static final float WAVE_UP = 2.5F, WAVE_FORWARD = -0.25F, WAVE_SWING = 0.35F, WAVE_SPEED = 0.7F;
	/** (0.7.504) The bow: how far forward from the waist, and the head further down (radians). */
	static final float BOW_LEAN = 0.6F, BOW_HEAD = 0.45F;
	/** (0.7.504) The ruler's nod (radians). */
	static final float RULER_NOD = 0.35F;
	/**
	 * (0.7.504) The arms in a bow, as {forward (x), turned in (y), out to the side (z)} for either arm (mirrored for the
	 * left), in radians; HEART and BEHIND go with the body as it bends, the rest hang as they would.
	 */
	static final float[] HEART = {-1.05F, 0.65F, -0.05F}, BEHIND = {0.55F, 0.0F, 0.12F}, SIDE = {-0.1F, 0.0F, 0.08F},
		BLADE_LOW = {0.4F, -0.1F, 0.12F}, POMMEL = {-0.3F, 0.0F, -0.7F};
	/** (0.7.504) What the hands hold in a bow: nothing in the main hand, a blade, or something else; plus this if the off hand holds anything. */
	static final int FREE = 0, BLADE = 1, HOLDING = 2, OFF_HELD = 4;
	/** (0.7.504) What a pose is: a shake, a dap, a wave, a bow or a nod. */
	static final float SHAKE = 0.0F, DAP = 1.0F, WAVE = 2.0F, BOW = 3.0F, NOD = 4.0F;
	/** Head bowed a touch while shaking (radians). */
	static final float SHAKE_NOD = 0.12F;
	/** The ease: stiffness (per second squared), damping ratio, the longest step (seconds). */
	static final float STIFFNESS = 60.0F, DAMPING = 0.85F, MAX_DT = 0.1F, SUBSTEP = 0.008F;

	/**
	 * This frame's {weight, ticks into the shake (or -1 while just held out), 1 for a dap (0.7.289: 2 for a wave;
	 * (0.7.504) 3 a bow, 4 a nod), (0.7.504) what the hands hold for a bow} on a render state.
	 */
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
		boolean on = hand != null, dap = hand != null && hand.state == Handshakes.Hand.DAP, wave = hand != null && hand.state == Handshakes.Hand.WAVE;
		boolean bow = hand != null && hand.state == Handshakes.Hand.BOW, nod = hand != null && hand.state == Handshakes.Hand.NOD;
		float[] ease = EASES.get(id);
		if (bow || nod) {
			t = into(hand, partialTicks);
			on = t < (bow ? Handshakes.BOW_RELEASE : Handshakes.NOD_TICKS);
		} else if (wave) {
			t = into(hand, partialTicks);
			on = t < Handshakes.WAVE_TICKS;
		} else if (hand != null && (hand.state == Handshakes.Hand.SHAKING || hand.state == Handshakes.Hand.PLAIN || dap)) {
			t = into(hand, partialTicks);
			on = t < (dap ? Handshakes.DAP_RELEASE : Handshakes.RELEASE_TICK);
			if (!dap && t < REACH_DELAY && (ease == null || ease[0] < 0.5F)) {
				// (0.7.205) the one taking the hand waits a beat, so the film's hand shot sees it come in
				on = false;
			}
		}
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
		state.setData(KEY, new float[] {ease[0], t, bow ? BOW : nod ? NOD : wave ? WAVE : dap ? DAP : SHAKE, bow ? holds(entity) : 0.0F});
	}

	/** (0.7.504) What {@code entity}'s hands hold, for a bow ({@link #FREE}, {@link #BLADE} or {@link #HOLDING}, plus {@link #OFF_HELD}). */
	private static int holds(LivingEntity entity) {
		ItemStack main = entity.getMainHandItem();
		int kind = main.isEmpty() ? FREE : main.is(ItemTags.SWORDS) || main.is(ClassPerks.LIGHT_BLADES) ? BLADE : HOLDING;
		return entity.getOffhandItem().isEmpty() ? kind : kind | OFF_HELD;
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
		if (pose[2] == NOD) {
			// (0.7.504) a ruler inclines their head
			m.head.xRot += RULER_NOD * Math.min(1.0F, w);
			return;
		}
		if (pose[2] == BOW) {
			bow(model, m, state, w, (int)pose[3]);
			return;
		}
		if (pose[2] == WAVE) {
			// (0.7.289) a wave: up and out to the side, to and fro
			arm.xRot = Mth.lerp(w, arm.xRot, WAVE_FORWARD);
			arm.yRot = Mth.lerp(w, arm.yRot, 0.0F);
			arm.zRot = Mth.lerp(w, arm.zRot, WAVE_UP + Mth.sin(Math.max(0.0F, t) * WAVE_SPEED) * WAVE_SWING);
			return;
		}
		if (pose[2] == DAP) {
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
			nod = SHAKE_NOD * Mth.sin(g * Mth.PI);
		}
		arm.xRot = Mth.lerp(w, arm.xRot, REACH + pump);
		arm.yRot = Mth.lerp(w, arm.yRot, REACH_IN);
		arm.zRot = Mth.lerp(w, arm.zRot, 0.0F);
		m.head.xRot += nod * Math.min(1.0F, w);
	}

	/** (0.7.504) A bow from the waist, the head down and the hands as {@code holds} has them. */
	private static void bow(Model<?> model, HumanoidModel<?> m, Object state, float w, int holds) {
		float k = Math.min(1.0F, w);
		float lean = BOW_LEAN * k;
		// bent at the waist as a crouch is: the head and shoulders brought down and forward, the legs back to match
		float c = lean / 0.5F;
		m.body.xRot = Mth.lerp(k, m.body.xRot, BOW_LEAN);
		m.body.y += 3.2F * c;
		m.head.y += 4.2F * c;
		m.rightArm.y += 3.2F * c;
		m.leftArm.y += 3.2F * c;
		m.rightLeg.z += 4.0F * c;
		m.leftLeg.z += 4.0F * c;
		m.head.xRot = Mth.lerp(k, m.head.xRot, BOW_HEAD + BOW_LEAN);
		m.head.yRot = Mth.lerp(k, m.head.yRot, 0.0F);
		boolean leftHanded = state instanceof HumanoidRenderState h && h.mainArm == HumanoidArm.LEFT;
		ModelPart main = leftHanded ? m.leftArm : m.rightArm, off = leftHanded ? m.rightArm : m.leftArm;
		boolean offHeld = (holds & OFF_HELD) != 0;
		float[] mainPose, offPose;
		boolean mainBends, offBends;
		switch (holds & ~OFF_HELD) {
			case BLADE -> {
				// the blade held low at your side, the other hand come across onto its pommel
				mainPose = BLADE_LOW;
				mainBends = false;
				offPose = offHeld ? SIDE : POMMEL;
				offBends = false;
			}
			case HOLDING -> {
				// what you hold kept at your side, the free hand over your heart
				mainPose = SIDE;
				mainBends = false;
				offPose = offHeld ? SIDE : HEART;
				offBends = !offHeld;
			}
			default -> {
				// the hand over your heart, the other swept behind you
				mainPose = HEART;
				mainBends = true;
				offPose = offHeld ? SIDE : BEHIND;
				offBends = !offHeld;
			}
		}
		arm(main, mainPose, mainBends ? lean : 0.0F, main == m.leftArm, w);
		arm(off, offPose, offBends ? lean : 0.0F, off == m.leftArm, w);
		if (McaCompat.LOADED) {
			// an MCA body's breasts lean with it, and its clothes follow the limbs
			McaPullerPose.follow(model, lean, c);
		}
	}

	/** (0.7.504) {@code part} eased toward {@code pose} ({forward, in, out}; mirrored for a left arm), {@code bend} more forward with the body. */
	private static void arm(ModelPart part, float[] pose, float bend, boolean left, float w) {
		float side = left ? -1.0F : 1.0F;
		part.xRot = Mth.lerp(w, part.xRot, pose[0] + bend);
		part.yRot = Mth.lerp(w, part.yRot, -pose[1] * side);
		part.zRot = Mth.lerp(w, part.zRot, pose[2] * side);
	}
}

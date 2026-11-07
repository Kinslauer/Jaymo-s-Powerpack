package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Pickpocket;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.50) A pickpocket's reach: while a player's hand is in someone's pocket ({@link Pickpocket.Reach}), their main arm
 * goes forward and down into it, the fingers working, the other arm drawn in. Laid over the player's model right after
 * it's posed, like {@link PullerPose}.
 * <p>
 * (0.7.568) A crook villager picking a player's pocket reaches the same way, and a hand going for a coin purse worn on the
 * mark's hip ({@link Pickpocket.Reach#purse}) is aimed at it.
 * <p>
 * (0.7.133) Eased: the hand goes in on a soft spring (a little high first, then slipping down into the pocket), the body
 * leaning in and the head bowed to watch it, and comes back out the same way when it's done.
 */
public final class PickpocketPose {
	/** The reaching arm: forward and down (radians), turned in toward the pocket, and how much the fingers work. */
	static final float REACH = -0.85F, REACH_IN = 0.25F, WORK = 0.06F;
	/** Going in: the hand starts this much higher (radians) and slips down into the pocket over this long (seconds). */
	static final float SLIP = -0.3F, SLIP_TIME = 0.35F;
	/** Leaning in to it, and the head bowed to watch the hand (radians). */
	static final float LEAN = 0.08F, BOW = 0.18F;
	/** The ease: stiffness (per second squared), damping ratio (under 1 overshoots a little), the longest step (seconds). */
	static final float STIFFNESS = 70.0F, DAMPING = 0.8F, MAX_DT = 0.1F, SUBSTEP = 0.008F;
	/** An ease not stepped this long is dropped (nanoseconds). */
	static final long FORGET_NANOS = 3_000_000_000L;

	/** This frame's {age, weight, seconds since the reach began} for a player reaching (or easing out), on their render state. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:pickpocket");

	/** Entity id → its ease {weight, velocity}; and when it was last stepped and when the reach began (nanoseconds). */
	private static final Map<Integer, float[]> EASES = new HashMap<>();
	private static final Map<Integer, long[]> EASE_TIMES = new HashMap<>();

	/** Player entity id → the mark's entity id. */
	private static final Int2IntMap REACHING = new Int2IntOpenHashMap();
	/** (0.7.568) Those of them going for a coin purse on the mark's hip. */
	private static final IntSet FOR_PURSE = new IntOpenHashSet();
	/**
	 * (0.7.568) Where a worn purse hangs on the mark: this far out to their right and up from their feet, as parts of their
	 * height (Powers' {@code WornLanterns} hangs it at the right hip); the reaching shoulder, as parts of the thief's height.
	 */
	static final float PURSE_OUT = 0.17F, PURSE_UP = 0.42F, SHOULDER_UP = 0.75F, SHOULDER_OUT = 0.17F;

	private PickpocketPose() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Pickpocket.Reach.TYPE, (payload, context) -> {
			if (payload.markId() < 0) {
				REACHING.remove(payload.playerId());
				FOR_PURSE.remove(payload.playerId());
			} else {
				REACHING.put(payload.playerId(), payload.markId());
				if (payload.purse()) {
					FOR_PURSE.add(payload.playerId());
				} else {
					FOR_PURSE.remove(payload.playerId());
				}
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			REACHING.clear();
			FOR_PURSE.clear();
		});
	}

	/** Render-state extraction: a reaching player's pose for this frame. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		// ((0.7.568) a crook villager reaches too, at a player)
		int id = entity.getId();
		if (!(entity instanceof Avatar) && !REACHING.containsKey(id) && !EASES.containsKey(id)) {
			return;
		}
		boolean on = REACHING.containsKey(id);
		float[] ease = EASES.get(id);
		long now = System.nanoTime();
		if (ease == null) {
			if (!on) {
				return;
			}
			ease = new float[2];
			EASES.put(id, ease);
			EASE_TIMES.put(id, new long[] {now, now});
			// forget any left behind by someone who went out of sight mid-reach
			EASE_TIMES.entrySet().removeIf(entry -> now - entry.getValue()[0] > FORGET_NANOS && EASES.remove(entry.getKey()) != null);
		}
		long[] times = EASE_TIMES.get(id);
		float dt = Mth.clamp((now - times[0]) / 1.0E9F, 0.0F, MAX_DT);
		times[0] = now;
		int steps = Math.max(1, Mth.ceil(dt / SUBSTEP));
		float h = dt / steps, damping = 2.0F * DAMPING * Mth.sqrt(STIFFNESS), target = on ? 1.0F : 0.0F;
		for (int i = 0; i < steps; i++) {
			ease[1] += (STIFFNESS * (target - ease[0]) - damping * ease[1]) * h;
			ease[0] += ease[1] * h;
		}
		ease[0] = Mth.clamp(ease[0], 0.0F, 1.1F);
		if (!on && ease[0] < 0.003F && Math.abs(ease[1]) < 0.03F) {
			EASES.remove(id);
			EASE_TIMES.remove(id);
			LAST_AIM.remove(id);
			return;
		}
		float[] aim = on && FOR_PURSE.contains(id) ? aim(entity, REACHING.get(id), partialTicks) : null;
		if (aim != null) {
			LAST_AIM.put(id, aim);
		} else if (on) {
			LAST_AIM.remove(id);
		} else {
			// (easing out: still from where the hand was)
			aim = LAST_AIM.get(id);
		}
		if (!on && ease[0] < 0.003F) {
			LAST_AIM.remove(id);
		}
		state.setData(KEY, aim == null ? new float[] {state.ageInTicks, ease[0], (now - times[1]) / 1.0E9F}
			: new float[] {state.ageInTicks, ease[0], (now - times[1]) / 1.0E9F, aim[0], aim[1]});
	}

	/** (0.7.568) Entity id → the last aim at a purse {xRot, yRot}, so the hand comes back out from there. */
	private static final Map<Integer, float[]> LAST_AIM = new HashMap<>();

	/**
	 * (0.7.568) The reaching arm's {xRot, yRot} to point at the purse on {@code markId}'s right hip, from the thief's main
	 * shoulder; null if the mark isn't in sight.
	 */
	static float @Nullable [] aim(LivingEntity thief, int markId, float partialTicks) {
		Entity mark = thief.level().getEntity(markId);
		if (!(mark instanceof LivingEntity m)) {
			return null;
		}
		float markYaw = Mth.rotLerp(partialTicks, m.yBodyRotO, m.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 at = m.getPosition(partialTicks);
		// a body's right, for yaw θ: (-cos θ, 0, -sin θ)
		Vec3 purse = at.add(-Mth.cos(markYaw) * PURSE_OUT * m.getBbHeight(), PURSE_UP * m.getBbHeight(), -Mth.sin(markYaw) * PURSE_OUT * m.getBbHeight());
		float yaw = Mth.rotLerp(partialTicks, thief.yBodyRotO, thief.yBodyRot) * Mth.DEG_TO_RAD;
		float side = thief.getMainArm() == HumanoidArm.LEFT ? -1.0F : 1.0F;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 shoulder = thief.getPosition(partialTicks).add(right.scale(side * SHOULDER_OUT * thief.getBbHeight())).add(0.0, SHOULDER_UP * thief.getBbHeight(), 0.0);
		Vec3 d = purse.subtract(shoulder);
		double f = d.dot(fwd), r = d.dot(right), down = -d.y;
		// an arm hangs down; x turns it forward (negative), then y swings that to the body's right (positive)
		float xRot = (float)-Mth.atan2(Math.sqrt(f * f + r * r), down);
		float yRot = (float)Mth.atan2(r, Math.max(0.05, f));
		return new float[] {xRot, Mth.clamp(yRot, -1.2F, 1.2F)};
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
		float w = pose[1];
		// the hand goes in a little high and slips down into the pocket, the fingers working once it's there
		float in = Mth.clamp(pose[2] / SLIP_TIME, 0.0F, 1.0F);
		in = in * in * (3.0F - 2.0F * in);
		float work = Mth.sin(pose[0] * 0.9F) * WORK * in;
		if (pose.length >= 5) {
			// (0.7.568) at the purse on their hip
			set(reach, pose[3] + SLIP * (1.0F - in) + work, pose[4], 0.0F, w);
		} else {
			set(reach, REACH + SLIP * (1.0F - in) + work, REACH_IN * side, 0.0F, w);
		}
		// (the other hand kept close, out of the way)
		set(other, -0.2F, 0.0F, -0.1F * side, w);
		// leaning in, the head bowed to watch it
		float k = Math.min(1.0F, w);
		m.body.xRot += LEAN * k;
		m.head.xRot += BOW * k;
	}

	private static void set(ModelPart part, float x, float y, float z, float w) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}

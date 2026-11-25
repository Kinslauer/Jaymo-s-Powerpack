package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.corpse.Gnawing;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * Andy (2026-11-24): "zombies should also bend over and down to gnaw on bodies in a new gruesome animation". A zombie (or
 * anything drawn with a humanoid's body) feeding on a body ({@link Gnawing}) goes down on its knees over it, bent double
 * at the waist, head down at the meat, both hands on the body. Each bite: it dives in, head and shoulders, then rips back,
 * the head jerking up and worrying from side to side, the hands dragging what they hold up with it. With a part torn off
 * it sits back a little and gnaws at it in its hand, head bobbing. It goes down and gets up over a few ticks.
 * <p>
 * Worked from each part's own resting place (so a baby zombie's smaller body kneels the same way): the legs swing back
 * flat along the ground and the whole figure drops onto them; the torso turns about the hips; the head and arms go where
 * the bent torso carries the neck and shoulders. Set on the render state by {@code LivingEntityRendererMixin}, applied
 * after the model animates ({@code ModelFeatureRendererMixin}). Turns are the model's (radians): x forward (+, a part
 * hanging down swings back; - an arm reaches forward), y across, z out.
 */
public final class GnawPose {
	/** {weight 0..1, ticks into the bite (-1: none), chewing 0/1, age (ticks), which way it worries first (+1/-1)}. */
	public static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:gnawing");

	/** Ticks to get down over the body, and to get up off it. */
	static final float DOWN_TICKS = 8.0F, UP_TICKS = 8.0F;
	/** The kneel: how far the figure drops (share of its torso's length) and the legs' swing back, and how far apart. */
	static final float DROP = 0.8F, KNEEL = 1.45F, KNEES_APART = 0.12F;
	/** Bent over: the torso's lean forward, the head's further down, the arms' reach down to the body and in toward each other. */
	static final float BEND = 0.95F, HEAD_DOWN = 0.35F, ARMS_REACH = -0.65F, ARMS_IN = 0.25F;
	/** A bite: the dive in (ticks to the jaws closing, which is when the server tears), the rip back (ticks), done by. */
	static final float DIVE_TICKS = 4.0F, RIP_TICKS = 2.0F, BITE_DONE = 12.0F;
	/** The dive: extra lean, head further down; the rip: the torso pulled up, the head jerked back, the shake, the arms dragged up and in. */
	static final float DIVE_BEND = 0.25F, DIVE_HEAD = 0.25F, RIP_UP = 0.4F, RIP_HEAD = 0.9F, RIP_SHAKE = 0.35F, RIP_ARMS = 0.5F,
		RIP_IN = 0.2F;
	/** Chewing a part torn off: sat back to this lean, the hand holding it up at the mouth, the other on the body; the head's bob. */
	static final float CHEW_BEND = 0.6F, CHEW_HAND_X = -2.1F, CHEW_HAND_IN = 0.45F, CHEW_REST = -0.7F, CHEW_BOB = 0.12F;

	/** Entity ids drawn down over a body last frame, and those getting up (the game time they began to). */
	private static final IntSet DOWN = new IntOpenHashSet();
	private static final Int2LongMap UP = new Int2LongOpenHashMap();

	private GnawPose() {
	}

	/** Whether {@code entity} is drawn down over a body now (Jaymo's Kingdoms asks, by name, to keep Fresh Animations and its horde poses off it). */
	public static boolean posed(Object entity) {
		return entity instanceof LivingEntity living && (DOWN.contains(living.getId()) || UP.containsKey(living.getId()));
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		int id = entity.getId();
		Gnawing.Gnaw gnaw = entity.getAttached(Gnawing.GNAWING);
		float time = entity.level().getGameTime() + partialTicks;
		if (gnaw != null) {
			DOWN.add(id);
			UP.remove(id);
			float w = smooth((time - gnaw.since()) / DOWN_TICKS);
			float bite = gnaw.bite() < 0 ? -1.0F : time - gnaw.bite();
			state.setData(KEY, new float[] {w, bite > BITE_DONE ? -1.0F : bite, gnaw.chewing() ? 1.0F : 0.0F, state.ageInTicks,
				(gnaw.bite() & 1) == 0 ? 1.0F : -1.0F});
			return;
		}
		if (DOWN.remove(id)) {
			UP.put(id, entity.level().getGameTime());
		}
		if (UP.containsKey(id)) {
			float t = (time - UP.get(id)) / UP_TICKS;
			if (t >= 1.0F || t < 0.0F) {
				UP.remove(id);
			} else {
				state.setData(KEY, new float[] {1.0F - smooth(t), -1.0F, 0.0F, state.ageInTicks, 1.0F});
				return;
			}
		}
		state.setData(KEY, null);
	}

	public static void apply(Model<?> model, Object state) {
		if (!(model instanceof HumanoidModel<?> m) || !(state instanceof LivingEntityRenderState living)) {
			return;
		}
		float[] pose = living.getData(KEY);
		// (crawling on shot legs, or knocked limp, it's drawn that way instead)
		if (pose == null || living.getData(CrawlPose.KEY) != null) {
			return;
		}
		float w = pose[0], b = pose[1], age = pose[3], side = pose[4];
		boolean chewing = pose[2] > 0.5F;
		float dive = 0.0F, rip = 0.0F;
		if (b >= 0.0F) {
			dive = b < DIVE_TICKS ? smooth(b / DIVE_TICKS) : 1.0F - smooth((b - DIVE_TICKS) / RIP_TICKS);
			rip = b < DIVE_TICKS ? 0.0F
				: b < DIVE_TICKS + RIP_TICKS ? smooth((b - DIVE_TICKS) / RIP_TICKS)
				: 1.0F - smooth((b - DIVE_TICKS - RIP_TICKS) / (BITE_DONE - DIVE_TICKS - RIP_TICKS));
		}

		// the figure's own proportions, from where its parts rest (a baby's are smaller)
		float neck0 = m.body.getInitialPose().y(), hip0 = m.rightLeg.getInitialPose().y();
		float torso = hip0 - neck0;
		if (torso <= 0.0F) {
			return;
		}
		float shoulder = m.rightArm.getInitialPose().y() - neck0;

		// down on its knees: the legs swung back flat along the ground, the figure dropped onto them
		float hip = hip0 + DROP * torso * w;
		kneel(m.rightLeg, hip, -1.0F, w);
		kneel(m.leftLeg, hip, 1.0F, w);

		// bent over at the hips
		float lean = (chewing ? CHEW_BEND : BEND) + DIVE_BEND * dive - RIP_UP * rip;
		float a = lean * w;
		float neckY = hip - torso * Mth.cos(a), neckZ = m.body.getInitialPose().z() - torso * Mth.sin(a);
		m.body.xRot = a;
		m.body.y = neckY;
		m.body.z = neckZ;

		// the head down at the meat; ripping, it jerks back and worries from side to side
		float headAt = m.head.getInitialPose().y() - neck0;
		m.head.y = neckY + headAt * Mth.cos(a);
		m.head.z = neckZ + headAt * Mth.sin(a);
		float headDown = chewing ? HEAD_DOWN * 0.5F + CHEW_BOB * Mth.sin(age * 1.1F) : HEAD_DOWN + DIVE_HEAD * dive - RIP_HEAD * rip;
		float shake = chewing ? 0.1F * Mth.sin(age * 0.7F) : RIP_SHAKE * rip * Mth.sin(b * 2.4F) * side;
		m.head.xRot = Mth.lerp(w, m.head.xRot, lean + headDown);
		m.head.yRot = Mth.lerp(w, m.head.yRot, m.head.yRot * 0.3F + shake);
		m.head.zRot = Mth.lerp(w, m.head.zRot, 0.0F);

		// the shoulders where the bent torso carries them
		float shoulderY = neckY + shoulder * Mth.cos(a), shoulderZ = neckZ + shoulder * Mth.sin(a);
		m.rightArm.y = shoulderY;
		m.rightArm.z = shoulderZ;
		m.leftArm.y = shoulderY;
		m.leftArm.z = shoulderZ;
		if (chewing) {
			// the part torn off held up at the mouth in the hand it holds things in; the other hand still on the body
			boolean left = state instanceof ArmedEntityRenderState armed && armed.mainArm == HumanoidArm.LEFT;
			ModelPart holding = left ? m.leftArm : m.rightArm, other = left ? m.rightArm : m.leftArm;
			float in = left ? 1.0F : -1.0F;
			set(holding, CHEW_HAND_X + 0.08F * Mth.sin(age * 1.1F), CHEW_HAND_IN * in, 0.0F, w);
			set(other, CHEW_REST, -ARMS_IN * in, 0.0F, w);
		} else {
			// both hands on the body, dragging up what they hold as the jaws rip back
			float reach = ARMS_REACH - 0.1F * dive - RIP_ARMS * rip;
			float in = ARMS_IN + RIP_IN * rip;
			set(m.rightArm, reach, -in, 0.05F, w);
			set(m.leftArm, reach, in, -0.05F, w);
		}
	}

	/** One leg swung back flat along the ground from the dropped hip, a little out to its side ({@code out}: right -1, left +1). */
	private static void kneel(ModelPart leg, float hip, float out, float w) {
		leg.y = hip;
		set(leg, KNEEL, 0.0F, -out * KNEES_APART, w);
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

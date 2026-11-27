package com.jaymospowers.client.power.web;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * More swing poses. Andy's pick from Legends: Superheroes, whose swing "has four body poses that change with the swing's
 * phase (reach, pull, swing through, let go)": ours had fewer, so a long run of swings looked the same. These are our own
 * hand-made poses, only the idea and the rough timings carried over, drawn by {@link WebArms} on a swinging slinger's
 * free arm, legs and head, and chosen from how the swing is actually going:
 * <ul>
 * <li><b>Reach</b>, just after a web catches (the first few ticks): the free arm thrown up toward the anchor, the legs
 * trailing, the head up.</li>
 * <li><b>Pull</b>, falling down the arc: the arm hauled back and in, the knees coming up, the head tucked.</li>
 * <li><b>Swing through</b>, the fast bottom of the arc: laid out and streamlined, the arm swept back along the body, the
 * legs straight and together.</li>
 * <li><b>Let go</b>, rising to the top and slowing: the arm flung out and up, the legs scissored, the head back.</li>
 * </ul>
 * Each new web takes one of {@link #VARIANTS} looks for all four (the arm across the body, a straighter leg and a
 * lifted knee, a wide star), never the one the last web had, so no two swings in a row are quite alike. The release flips
 * off a let-go web ({@code WebTrickClient}, {@code WebBodyPose}) are the other half of it.
 */
final class WebSwingPoses {
	/** How many looks a swing has (its free arm and legs move differently in each). */
	static final int VARIANTS = 4;
	/** A web caught this many ticks ago is still reaching. */
	static final int REACH_TICKS = 8;
	/** How fast the phase weights follow the swing (0-1 a tick). */
	static final float FOLLOW = 0.3F;

	/** This frame's pose: how much of each phase (0-1 each) and which look the swing has. */
	record Frame(float reach, float pull, float through, float letGo, int variant) {
	}

	/** A slinger's phase weights, followed from their motion each tick. */
	static final class State {
		float reach, pull, through, letGo;
		float prevReach, prevPull, prevThrough, prevLetGo;
		int variant;
		private double lastVy;
		private @Nullable Vec3 lastAnchor;
		private int sinceCatch = REACH_TICKS;

		/**
		 * One tick. {@code v} is the slinger's motion, {@code swinging} whether they are driving a swing right now, and
		 * {@code anchor} where their web is stuck (a new place is a new web, and a new look).
		 */
		void tick(Vec3 v, boolean swinging, @Nullable Vec3 anchor) {
			prevReach = reach;
			prevPull = pull;
			prevThrough = through;
			prevLetGo = letGo;
			if (anchor == null) {
				lastAnchor = null;
			} else if (lastAnchor == null || lastAnchor.distanceToSqr(anchor) > 9.0) {
				// a new web: a new look, never the one before
				int next = Math.floorMod((int)Math.floor(anchor.x * 7.0) + (int)Math.floor(anchor.y * 13.0) + (int)Math.floor(anchor.z * 31.0), VARIANTS);
				variant = next == variant ? (next + 1) % VARIANTS : next;
				sinceCatch = 0;
				lastAnchor = anchor;
			}
			double dvy = v.y - lastVy;
			lastVy = v.y;
			double flat = Math.sqrt(v.x * v.x + v.z * v.z);
			float reachWant = 0.0F, pullWant = 0.0F, throughWant = 0.0F, letWant = 0.0F;
			if (swinging) {
				sinceCatch++;
				reachWant = Mth.clamp(1.0F - sinceCatch / (float)REACH_TICKS, 0.0F, 1.0F);
				pullWant = Mth.clamp((float)(-v.y / 0.35), 0.0F, 1.0F);
				throughWant = Mth.clamp(1.0F - (float)Math.abs(v.y) / 0.18F, 0.0F, 1.0F) * Mth.clamp((float)flat / 0.6F, 0.0F, 1.0F);
				letWant = Mth.clamp((float)(v.y / 0.3), 0.0F, 1.0F) * Mth.clamp(0.4F + (float)-dvy * 10.0F, 0.0F, 1.0F);
				// a reach is the start of the swing: the others wait for it
				pullWant *= 1.0F - reachWant;
				throughWant *= 1.0F - reachWant;
				letWant *= 1.0F - reachWant;
			}
			reach += (reachWant - reach) * FOLLOW;
			pull += (pullWant - pull) * FOLLOW;
			through += (throughWant - through) * FOLLOW;
			letGo += (letWant - letGo) * FOLLOW;
		}

		Frame frame(float partialTick) {
			return new Frame(Mth.lerp(partialTick, prevReach, reach), Mth.lerp(partialTick, prevPull, pull), Mth.lerp(partialTick, prevThrough, through),
				Mth.lerp(partialTick, prevLetGo, letGo), variant);
		}
	}

	private WebSwingPoses() {
	}

	/**
	 * Puts the phase poses on the model. {@code mainFree} / {@code offFree}: that arm has no web out (a web-holding arm is
	 * aimed along its web instead); {@code amount} is how much the swing drives the body (0-1).
	 */
	static void apply(HumanoidModel<?> h, Frame f, boolean mainRight, boolean mainFree, boolean offFree, float amount) {
		if (amount <= 0.001F) {
			return;
		}
		int v = f.variant();
		ModelPart mainArm = mainRight ? h.rightArm : h.leftArm, offArm = mainRight ? h.leftArm : h.rightArm;
		float mainSign = mainRight ? 1.0F : -1.0F;
		if (mainFree) {
			freeArm(mainArm, mainSign, f, v, amount);
		}
		if (offFree) {
			freeArm(offArm, -mainSign, f, v, amount);
		}
		// the legs and the head, phase by phase
		float r = f.reach() * amount, p = f.pull() * amount, t = f.through() * amount, l = f.letGo() * amount;
		// reach: trailing behind, head up
		set(h.rightLeg, 0.55F, 0.0F, 0.05F, r);
		set(h.leftLeg, 0.45F, 0.0F, -0.05F, r);
		h.head.xRot = Mth.lerp(r, h.head.xRot, -0.5F);
		// pull: the knees coming up (a look with just one knee lifted, a look with the knees wide), head tucked
		float pullRight = v == 2 ? -1.5F : -0.7F, pullLeft = v == 2 ? 0.2F : -0.95F;
		float pullSplay = v == 3 ? 0.45F : 0.08F;
		set(h.rightLeg, pullRight, 0.0F, pullSplay, p);
		set(h.leftLeg, pullLeft, 0.0F, -pullSplay, p);
		h.head.xRot = Mth.lerp(p, h.head.xRot, 0.3F);
		// swing through: straight and together, laid out
		set(h.rightLeg, 0.05F, 0.0F, 0.03F, t);
		set(h.leftLeg, 0.05F, 0.0F, -0.03F, t);
		h.head.xRot = Mth.lerp(t, h.head.xRot, -0.25F);
		// let go: scissored (the other way round in the second look), head back
		float scissor = v == 1 ? -1.0F : 1.0F;
		float letSplay = v == 3 ? 0.5F : 0.22F;
		set(h.rightLeg, scissor > 0.0F ? -1.0F : 0.25F, 0.0F, letSplay, l);
		set(h.leftLeg, scissor > 0.0F ? 0.25F : -1.0F, 0.0F, -letSplay, l);
		h.head.xRot = Mth.lerp(l, h.head.xRot, -0.5F);
	}

	/** A free arm (one with no web on it) through the phases; {@code sign} is +1 for the right arm, -1 for the left. */
	private static void freeArm(ModelPart arm, float sign, Frame f, int variant, float amount) {
		float r = f.reach() * amount, p = f.pull() * amount, t = f.through() * amount, l = f.letGo() * amount;
		// reach: thrown up toward the anchor
		set(arm, -2.7F, 0.0F, sign * 0.35F, r);
		// pull: hauled back and in (the look with the arm across the body crosses further)
		set(arm, variant == 2 ? 1.3F : 0.9F, -sign * (variant == 1 ? 0.9F : 0.3F), sign * 0.15F, p);
		// swing through: swept back along the body
		set(arm, 1.3F, variant == 1 ? -sign * 0.5F : 0.0F, sign * 0.1F, t);
		// let go: flung out and up (wide in the star look)
		set(arm, -0.9F, 0.0F, sign * (variant == 3 ? 2.4F : 1.9F), l);
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}

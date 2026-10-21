package com.jaymospowers.client;

import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Wounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * How a player crawling looks, in place of vanilla's swimming strokes: by the gesture, downed ({@link Downed}: the downed
 * state holds them in the crawl) or on two broken legs. A roll turns the body over once along its length, arms
 * stretched past the head and legs together.
 * <p>
 * (0.15.42) An army crawl: one forearm reaches ahead, lifted off the ground, and plants while the other pulls back under
 * the chest; the opposite knee drives up the side as each arm reaches; the body rocks onto the driving side and the
 * head lifts to look ahead on each pull. A broken arm or leg (Wounds, synced to all) is left to drag (it hangs limp,
 * {@code Dismembered}) and the good ones work harder, the body rocking more onto them. Downed, it's slower and uneven,
 * the breathing laboured; lying still they rest the head, now and then try to push up and sink back. Passed out, they
 * lie limp, the head turned to one side. Falling down they hit the ground with a jolt. It all eases in and out
 * ({@link PoseBlend}).
 */
public final class CrawlPose {
	/** Strokes per unit of the walk cycle (vanilla's walk is 0.6662), and the slower, uneven downed crawl's share of it. */
	static final float STROKE = 0.42F, DOWNED_STROKE = 0.8F, DOWNED_HITCH = 0.35F;
	/** Moving: how much of the stroke shows at a crawl's speed (walkAnimationSpeed times this, at the most 1). */
	static final float MOVING_PER_SPEED = 2.5F;
	/**
	 * The arms sweep flat along the ground (face down, a roll (z) of PI points an arm ahead past the head, PI/2 straight
	 * out to its side): reached ahead, pulled back to the side, and at rest folded ahead under the head. They dip a little
	 * to the ground (x, - is down) and lift off it coming forward.
	 */
	static final float REACH_SWEEP = 2.6F, PULL_SWEEP = 1.1F, REST_SWEEP = 2.3F, ARM_DIP = -0.25F, REACH_LIFT = 0.35F;
	/** The knees driven up the side (radians out), at rest and at the drive, and the foot lifted with it. */
	static final float LEG_OUT = 0.1F, KNEE_DRIVE = 0.55F, KNEE_LIFT = 0.15F;
	/** The head: resting, and lifted to look ahead on each pull (radians: -PI/2 looks along the ground). */
	static final float HEAD_REST = -0.75F, HEAD_LOOK = -1.3F, HEAD_DOWN_REST = -0.3F;
	/** The body: rocking onto the driving side (degrees) and twisting at the waist against it (radians). */
	static final float ROCK_DEGREES = 6.0F, TWIST = 0.08F;
	/** A broken limb: the good ones work this much harder, and the body rocks this much more onto them. */
	public static final float HURT_EFFORT = 1.35F, HURT_ROCK = 1.8F;
	/** Downed: the breathing (degrees of heave, beats per tick), quicker and deeper than at rest. */
	static final float BREATH_DEGREES = 1.4F, BREATH_RATE = 0.22F;
	/** Downed and lying still: an attempt to push up every this many ticks (give or take), how far up (degrees), how long (ticks). */
	static final float ATTEMPT_EVERY = 220.0F, ATTEMPT_DEGREES = 14.0F, ATTEMPT_TICKS = 40.0F;
	/** The arms pressed straight down for the attempt. */
	static final float PRESS_X = -1.6F, PRESS_OUT = 0.3F;
	/** Falling down: the jolt as the body hits (degrees), how fast it rings (per tick) and dies away (per tick). */
	static final float FALL_JOLT = 7.0F, FALL_RING = 0.9F, FALL_DECAY = 0.25F;
	/** Passed out: the head turned to the side, the arms slack by the body. */
	static final float OUT_HEAD_TURN = 1.1F, OUT_ARM_X = -0.35F, OUT_ARM_OUT = 0.25F;

	/**
	 * {@code roll}: progress through a roll (signed by direction), or null; {@code stride}/{@code speed}: the crawl's walk
	 * cycle; {@code downed}: ticks since they went down, or -1; {@code out}: passed out; {@code broken}: the broken limbs
	 * ({@link Dismember} bits); {@code age}: ticks, for the breathing; {@code id}: the player's entity id.
	 */
	public record State(@Nullable Float roll, float stride, float speed, float downed, boolean out, int broken, float age, int id) {
	}

	public static final RenderStateDataKey<State> KEY = RenderStateDataKey.create(() -> "jaymospowers:crawl");

	private static final PoseBlend.Channel BLEND = PoseBlend.channel("crawl");

	private CrawlPose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || !(Crawl.crawling(player) || Downed.isDowned(player)) || player.isInWater()) {
			state.setData(KEY, null);
			return;
		}
		long time = player.level().getGameTime();
		Float roll = player == Minecraft.getInstance().player ? CrawlTaps.localRollProgress(time, partialTicks)
			: Crawl.rollProgress(player, time, partialTicks);
		boolean downed = Downed.isDowned(player);
		float since = downed ? Math.max(0.0F, time - Downed.since(player) + partialTicks) : -1.0F;
		state.setData(KEY, new State(roll, state.walkAnimationPos, state.walkAnimationSpeed, since, downed && Downed.passedOut(player),
			Wounds.brokenLimbs(player), player.tickCount + partialTicks, player.getId()));
	}

	/**
	 * After the swimming tilt the model's Y axis runs head to foot (a turn about it rolls the body over) and +Z is up off
	 * the ground (a turn about X lifts the head end, about the feet).
	 */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		State crawl = state.getData(KEY);
		if (crawl == null || !state.isVisuallySwimming) {
			return;
		}
		if (crawl.roll() != null) {
			float t = Math.abs(crawl.roll());
			// mostly even, eased a little, so held rolls run into each other without a hitch
			float turn = Mth.lerp(0.35F, t, t * t * (3.0F - 2.0F * t));
			poseStack.rotateDegrees(Axis.YP, Math.signum(crawl.roll()) * 360.0F * turn);
			return;
		}
		PoseBlend.Track track = BLEND.peek(crawl.id());
		float w = track == null ? 0.0F : track.amount();
		float lift = 0.0F, rock = 0.0F;
		if (crawl.downed() >= 0.0F) {
			// the jolt of hitting the ground: the chest bouncing up off it, smaller each time
			lift += FALL_JOLT * (float)Math.exp(-crawl.downed() * FALL_DECAY) * Math.abs(Mth.sin(crawl.downed() * FALL_RING));
			if (!crawl.out()) {
				// laboured breathing, and now and then a try at pushing up
				lift += BREATH_DEGREES * (0.5F + 0.5F * Mth.sin(crawl.age() * BREATH_RATE)) * w;
				lift += ATTEMPT_DEGREES * attempt(crawl) * w;
			} else {
				lift += BREATH_DEGREES * 0.5F * (0.5F + 0.5F * Mth.sin(crawl.age() * BREATH_RATE * 0.5F)) * w;
			}
		}
		if (!crawl.out()) {
			float phase = phase(crawl);
			rock = ROCK_DEGREES * Mth.sin(phase) * moving(crawl) * hurtRock(crawl) * w;
		}
		if (Math.abs(rock) > 1.0E-3F) {
			poseStack.rotateDegrees(Axis.YP, rock);
		}
		if (Math.abs(lift) > 1.0E-3F) {
			poseStack.rotateDegrees(Axis.XP, lift);
		}
	}

	/** Right after the model animates (vanilla has just posed the swimming strokes): the crawl's or roll's limbs instead. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		State crawl = state.getData(KEY);
		boolean on = crawl != null && state.isVisuallySwimming;
		PoseBlend.Track track = BLEND.track(state.id, on);
		if (track == null) {
			return;
		}
		if (!on) {
			// eased out over the swimming strokes; stood up, the model's own pose takes over at once
			if (state.isVisuallySwimming) {
				track.apply(h, null);
			}
			return;
		}
		PoseBlend.Frame f = new PoseBlend.Frame(h);
		if (crawl.roll() != null) {
			// log roll: arms straight out past the head, legs together
			f.set(PoseBlend.RIGHT_ARM, -Mth.PI, 0.0F, 0.12F);
			f.set(PoseBlend.LEFT_ARM, -Mth.PI, 0.0F, -0.12F);
			f.set(PoseBlend.RIGHT_LEG, 0.0F, 0.0F, 0.03F);
			f.set(PoseBlend.LEFT_LEG, 0.0F, 0.0F, -0.03F);
			f.set(PoseBlend.BODY + 1, 0.0F);
			track.apply(h, f, 2.0F);
			return;
		}
		if (crawl.out()) {
			// passed out: limp, the arms slack by the body, the head turned to one side on the ground
			f.set(PoseBlend.RIGHT_ARM, OUT_ARM_X, 0.0F, OUT_ARM_OUT);
			f.set(PoseBlend.LEFT_ARM, OUT_ARM_X + 0.1F, 0.0F, -OUT_ARM_OUT * 0.6F);
			f.set(PoseBlend.RIGHT_LEG, 0.05F, 0.0F, 0.12F);
			f.set(PoseBlend.LEFT_LEG, 0.02F, 0.0F, -0.08F);
			f.set(PoseBlend.HEAD, HEAD_DOWN_REST, OUT_HEAD_TURN, 0.0F);
			f.set(PoseBlend.BODY + 1, 0.0F);
			track.apply(h, f, 0.6F);
			return;
		}
		int broken = crawl.broken();
		boolean rightArm = (broken & Dismember.RIGHT_ARM) == 0, leftArm = (broken & Dismember.LEFT_ARM) == 0;
		stroke(f, phase(crawl), moving(crawl), broken, crawl.downed() >= 0.0F);
		float press = crawl.downed() >= 0.0F ? attempt(crawl) : 0.0F;
		if (press > 0.0F) {
			// the try at pushing up: both (good) arms pressed straight down under the shoulders, head up
			if (rightArm) {
				f.mix(PoseBlend.RIGHT_ARM, PRESS_X, 0.0F, PRESS_OUT, press);
			}
			if (leftArm) {
				f.mix(PoseBlend.LEFT_ARM, PRESS_X, 0.0F, -PRESS_OUT, press);
			}
			f.mix(PoseBlend.HEAD, HEAD_REST, 0.0F, 0.0F, press);
			// trembling with the strain
			float shake = Mth.sin(crawl.age() * 2.3F) * 0.04F * press;
			f.shake(PoseBlend.RIGHT_ARM, shake);
			f.shake(PoseBlend.LEFT_ARM, -shake);
		}
		track.apply(h, f, crawl.downed() >= 0.0F ? 0.7F : 1.0F);
	}

	/**
	 * The army crawl's limbs on a frame lying face down (the swimming tilt, or a downed mob's): {@code phase} through the
	 * stroke, {@code moving} how much of it shows (0 lying still), {@code broken} the limbs left to drag ({@link Dismember}
	 * bits), {@code downed} resting the head lower when still. Shared with {@code DownedPose}'s crawlers.
	 */
	public static void stroke(PoseBlend.Frame f, float phase, float moving, int broken, boolean downed) {
		boolean rightArm = (broken & Dismember.RIGHT_ARM) == 0, leftArm = (broken & Dismember.LEFT_ARM) == 0;
		boolean rightLeg = (broken & Dismember.RIGHT_LEG) == 0, leftLeg = (broken & Dismember.LEFT_LEG) == 0;
		float effort = rightArm && leftArm && rightLeg && leftLeg ? 1.0F : HURT_EFFORT;
		// each arm's reach (0 pulled under the chest, 1 reached ahead); the right reaches while sin(phase) is rising
		float rightReach = 0.5F - 0.5F * Mth.cos(phase), leftReach = 1.0F - rightReach;
		float rightLift = Math.max(0.0F, Mth.sin(phase)), leftLift = Math.max(0.0F, -Mth.sin(phase));
		// a good arm doing a broken one's share reaches further; lying still the arms rest folded ahead
		float reach = REACH_SWEEP + 0.15F * (effort - 1.0F);
		float rest = 1.0F - moving;
		float rightSweep = Mth.lerp(rest, Mth.lerp(rightReach, PULL_SWEEP, reach), REST_SWEEP);
		float leftSweep = Mth.lerp(rest, Mth.lerp(leftReach, PULL_SWEEP, reach), REST_SWEEP + 0.1F);
		if (rightArm) {
			f.set(PoseBlend.RIGHT_ARM, ARM_DIP + REACH_LIFT * rightLift * moving, 0.0F, rightSweep);
		}
		if (leftArm) {
			f.set(PoseBlend.LEFT_ARM, ARM_DIP + REACH_LIFT * leftLift * moving, 0.0F, -leftSweep);
		}
		// the opposite knee drives up the side as an arm reaches (a good leg drives harder for a broken one)
		float rightDrive = Math.max(0.0F, -Mth.sin(phase)) * moving * (leftLeg ? 1.0F : effort);
		float leftDrive = Math.max(0.0F, Mth.sin(phase)) * moving * (rightLeg ? 1.0F : effort);
		if (rightLeg) {
			f.set(PoseBlend.RIGHT_LEG, 0.05F + KNEE_LIFT * rightDrive, 0.0F, LEG_OUT + KNEE_DRIVE * rightDrive);
		}
		if (leftLeg) {
			f.set(PoseBlend.LEFT_LEG, 0.05F + KNEE_LIFT * leftDrive, 0.0F, -LEG_OUT - KNEE_DRIVE * leftDrive);
		}
		// the shoulders twist against the hips
		f.set(PoseBlend.BODY + 1, 0.0F);
		f.set(PoseBlend.HIP_YAW, TWIST * Mth.sin(phase) * moving);
		// the head lifts to look ahead on each pull (each arm's, so twice a stroke), and rests when still
		float look = Math.abs(Mth.sin(phase)) * moving;
		float headRest = downed ? HEAD_DOWN_REST : HEAD_REST;
		f.set(PoseBlend.HEAD, Mth.lerp(look, Mth.lerp(moving, headRest, HEAD_REST), HEAD_LOOK), f.vanilla(PoseBlend.HEAD + 1) * 0.5F, 0.0F);
	}

	/** How much of the stroke shows (0 lying still to 1 crawling). */
	private static float moving(State crawl) {
		return Math.min(1.0F, crawl.speed() * MOVING_PER_SPEED);
	}

	/** Where in the stroke: from the distance crawled; downed, slower and hitching (lingering on each pull). */
	private static float phase(State crawl) {
		float phase = crawl.stride() * STROKE;
		if (crawl.downed() >= 0.0F) {
			phase *= DOWNED_STROKE;
			phase += DOWNED_HITCH * Mth.sin(phase * 2.0F);
		}
		return phase;
	}

	/** How much more the body rocks for a broken limb (onto the good side's work). */
	private static float hurtRock(State crawl) {
		return crawl.broken() == 0 ? 1.0F : HURT_ROCK;
	}

	/** Downed and lying still: the try at pushing up (0-1), now and then (each player on their own beat). */
	private static float attempt(State crawl) {
		if (crawl.out() || crawl.downed() < ATTEMPT_TICKS) {
			return 0.0F;
		}
		float still = 1.0F - moving(crawl);
		float every = ATTEMPT_EVERY + (crawl.id() & 63);
		float t = (crawl.downed() + (crawl.id() & 0xFF) * 3.0F) % every;
		// up slowly, held shaking, then sinking back faster
		return still * PoseBlend.bump(t, 0.0F, ATTEMPT_TICKS * 0.5F, ATTEMPT_TICKS * 0.7F, ATTEMPT_TICKS);
	}
}

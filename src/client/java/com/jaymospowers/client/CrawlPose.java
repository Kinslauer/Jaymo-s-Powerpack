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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
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
 * <p>
 * (0.15.86) Andy (2026-10-22): "legs and torso should go limp when downed ... animate arms dragging you". Downed, the legs
 * and torso go limp and trail along behind (no knees driving, no twist at the waist, only a slight sway from each pull),
 * and the arms haul the body hand over hand, the chest lifting with each pull, quick enough for the slow downed crawl to
 * read as dragging rather than gliding. Sneak (held to give up) no longer flattens the upper body into the ground
 * (vanilla's crouch on a body lying flat): it plays the push-up struggle instead.
 * <p>
 * (0.18.5) Getting up by themselves ({@link Downed#RISING}): pushed up on straight arms, then the knees drawn under and a
 * hand on one knee, then up onto their feet, the whole body turned up off the ground about the feet till it stands. Once
 * up, the swimming tilt vanilla eases out over a few ticks is cancelled ({@link #RISEN_KEY}), so they don't pitch
 * forward again as they stand.
 */
public final class CrawlPose {
	/** Strokes per unit of the walk cycle (vanilla's walk is 0.6662), and the uneven downed crawl's hitch on each pull. */
	static final float STROKE = 0.42F, DOWNED_HITCH = 0.35F;
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
	/**
	 * (0.15.86) Downed, dragging: strokes per unit of the walk cycle (the downed crawl covers little ground, so the arms
	 * work faster per block), how much of the stroke shows at its speed, the chest heaved up off the ground on each pull
	 * (degrees), and the limp torso's sway (share of {@link #ROCK_DEGREES}).
	 */
	static final float DRAG_STROKE = 2.3F, DRAG_MOVING_PER_SPEED = 8.0F, DRAG_HEAVE = 4.0F, LIMP_ROCK = 0.35F;
	/** (0.15.86) Downed, the limp legs trailing: spread apart, toes dragging (x), and the small bounce each pull gives them. */
	static final float LIMP_LEG_OUT = 0.12F, LIMP_LEG_X = 0.08F, LIMP_BOUNCE = 0.06F;
	/** Downed: the breathing (degrees of heave, beats per tick), quicker and deeper than at rest. */
	static final float BREATH_DEGREES = 1.4F, BREATH_RATE = 0.22F;
	/** Downed and lying still: an attempt to push up every this many ticks (give or take), how far up (degrees), how long (ticks). */
	static final float ATTEMPT_EVERY = 220.0F, ATTEMPT_DEGREES = 14.0F, ATTEMPT_TICKS = 40.0F;
	/** The arms pressed straight down for the attempt. */
	static final float PRESS_X = -1.6F, PRESS_OUT = 0.3F;
	/** Falling down: the jolt as the body hits (degrees), how fast it rings (per tick) and dies away (per tick). */
	static final float FALL_JOLT = 7.0F, FALL_RING = 0.9F, FALL_DECAY = 0.25F;
	/**
	 * (0.18.5) Rising: the share of the rise spent pushing up, then kneeling (the rest is standing), how far the body is
	 * turned up by the end of each (degrees: 90 stands), the knees drawn under kneeling (radians), and the hand on the knee.
	 */
	static final float PUSH_SHARE = 0.3F, KNEEL_SHARE = 0.65F, PUSH_DEGREES = 25.0F, KNEEL_DEGREES = 60.0F, KNEEL_LEGS = -1.25F,
		KNEE_HAND_X = -0.7F;
	/** (0.18.5) How long after rising the leftover swimming tilt is cancelled (ticks). */
	static final long RISEN_TICKS = 30L;
	/** Passed out: the head turned to the side, the arms slack by the body. */
	static final float OUT_HEAD_TURN = 1.1F, OUT_ARM_X = -0.35F, OUT_ARM_OUT = 0.25F;

	/**
	 * {@code roll}: progress through a roll (signed by direction), or null; {@code stride}/{@code speed}: the crawl's walk
	 * cycle; {@code downed}: ticks since they went down, or -1; {@code out}: passed out; {@code broken}: the broken limbs
	 * ({@link Dismember} bits); {@code age}: ticks, for the breathing; {@code id}: the player's entity id; {@code sneak}:
	 * (0.15.86) downed and holding Sneak to give up; {@code back}: (0.18.85) 0 to 1, turned over onto the back.
	 */
	public record State(@Nullable Float roll, float stride, float speed, float downed, boolean out, int broken, float age, int id,
		boolean sneak, float rise, float back) {
	}

	/**
	 * (0.18.85) On the back ({@code Crawl.SUPINE}): turning over takes this long (ticks), twisting about the middle of the
	 * body ({@link #BACK_PIVOT} blocks up it) so you end face up with your feet toward where you look, MGSV-style.
	 */
	static final float BACK_TURN_TICKS = 10.0F, BACK_PIVOT = 0.9F;
	/** On the back: the head up off the ground looking down past the feet; the arms at rest, or brought up to aim (radians). */
	static final float BACK_HEAD = 0.95F, BACK_ARMS_REST = 0.15F, BACK_ARMS_OUT = 0.18F, BACK_ARMS_AIM = -0.45F;
	/** On the back: one knee up, the other leg out straight (radians). */
	static final float BACK_KNEE_UP = -0.55F, BACK_LEG_OUT = 0.12F;
	/** (0.19.3) The neck up the body from the feet, and the eyes out from it (up and ahead, blocks). */
	static final float NECK = 1.5F, EYE_FROM_NECK = 0.25F;
	/** (0.18.85) Player id → how far over onto the back (0 to 1), and when it was last stepped (game time). */
	private static final Map<Integer, float[]> TURNED = new HashMap<>();

	public static final RenderStateDataKey<State> KEY = RenderStateDataKey.create(() -> "jaymospowers:crawl");
	/** (0.18.5) Just risen by themselves: vanilla's swimming tilt, still easing out, is cancelled. */
	static final RenderStateDataKey<Boolean> RISEN_KEY = RenderStateDataKey.create(() -> "jaymospowers:risen");
	/** (0.18.5) Player entity id → the game time they were last seen nearly risen (client side). */
	private static final Map<Integer, Long> RISEN = new HashMap<>();

	private static final PoseBlend.Channel BLEND = PoseBlend.channel("crawl");

	private CrawlPose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		// (0.19.3) going down into the crawl or getting up from it
		if (entity instanceof Player p) {
			ProneTransition.extract(p, state, partialTicks);
		} else {
			state.setData(ProneTransition.KEY, null);
		}
		// (0.15.108) climbing in onto the belly from a ledge, LedgePose draws it until the climb is done
		if (!(entity instanceof Player player) || !(Crawl.crawling(player) || Downed.isDowned(player)) || player.isInWater()
			|| com.jaymospowers.core.Ledge.proneClimbing(player)) {
			state.setData(KEY, null);
			Long risen = RISEN.get(entity.getId());
			boolean fresh = risen != null && entity.level().getGameTime() - risen <= RISEN_TICKS;
			if (risen != null && !fresh) {
				RISEN.remove(entity.getId());
			}
			state.setData(RISEN_KEY, fresh && !entity.isInWater() ? Boolean.TRUE : null);
			return;
		}
		state.setData(RISEN_KEY, null);
		long time = player.level().getGameTime();
		Float roll = player == Minecraft.getInstance().player ? CrawlTaps.localRollProgress(time, partialTicks)
			: Crawl.rollProgress(player, time, partialTicks);
		boolean downed = Downed.isDowned(player);
		float since = downed ? Math.max(0.0F, time - Downed.since(player) + partialTicks) : -1.0F;
		Long rising = downed ? player.getAttached(Downed.RISING) : null;
		float rise = rising == null ? -1.0F : Mth.clamp((time - rising + partialTicks) / Downed.RISE_TICKS, 0.0F, 1.0F);
		if (rise >= 0.9F) {
			RISEN.put(player.getId(), time);
		} else if (downed) {
			RISEN.remove(player.getId());
		}
		state.setData(KEY, new State(roll, state.walkAnimationPos, state.walkAnimationSpeed, since, downed && Downed.passedOut(player),
			Wounds.brokenLimbs(player), player.tickCount + partialTicks, player.getId(), downed && player.isShiftKeyDown(), rise,
			turned(player, time + partialTicks)));
		// (0.15.86) lying flat, Sneak isn't a crouch: vanilla's would bend the upper body down into the ground
		state.isCrouching = false;
	}

	/**
	 * (0.19.3) Andy: "the swap from belly down to belly up prone should move your fov with your head so you can see your
	 * body correctly". First person, turning over: how far the eyes have moved from where they are lying on the belly
	 * (world blocks), following the head round the twist ({@link #rotate}) to where it lies face up behind the hips; or
	 * null lying on the belly. The view keeps where it sits on the belly and moves by this.
	 */
	public static @Nullable Vec3 supineEyeShift(Player player, float partialTicks) {
		if (!Crawl.crawling(player) || player.isInWater() || Downed.isDowned(player)) {
			return null;
		}
		float back = turned(player, player.level().getGameTime() + partialTicks);
		if (back <= 0.0F) {
			return null;
		}
		Vector3f shift = bodyEye(back).sub(bodyEye(0.0F));
		// the body's frame turns with its yaw (as setupRotations does), and with the player's size
		float bodyRot = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
		shift.rotateY((180.0F - bodyRot) * Mth.DEG_TO_RAD).mul(player.getScale());
		return new Vec3(shift.x, shift.y, shift.z);
	}

	/**
	 * (0.19.3) Where the eyes are, {@code back} over onto the back, from the feet in the body's own frame (-Z ahead): up
	 * the body to the neck and out from it as the head is pitched, then through vanilla's crawl tilt and the twist.
	 */
	private static Vector3f bodyEye(float back) {
		float pitch = Mth.lerp(back, HEAD_REST, BACK_HEAD);
		float s = Mth.sin(pitch), c = Mth.cos(pitch);
		Vector3f eye = new Vector3f(0.0F, NECK + EYE_FROM_NECK * (c - s), -EYE_FROM_NECK * (s + c));
		Matrix4f m = new Matrix4f().rotateX(-Mth.HALF_PI).translate(0.0F, -1.0F, 0.3F)
			.translate(0.0F, BACK_PIVOT, 0.0F).rotateZ(Mth.PI * back).rotateY(Mth.PI * back).translate(0.0F, -BACK_PIVOT, 0.0F);
		return m.transformPosition(eye);
	}

	/** (0.18.85) How far over onto the back this player is drawn (0 to 1, eased), stepped toward where they lie. */
	private static float turned(Player player, float now) {
		boolean back = Crawl.supine(player);
		float[] t = TURNED.get(player.getId());
		if (t == null) {
			if (!back) {
				return 0.0F;
			}
			t = new float[] {0.0F, now};
			TURNED.put(player.getId(), t);
		}
		float dt = Mth.clamp(now - t[1], 0.0F, 5.0F);
		t[1] = now;
		t[0] = back ? Math.min(1.0F, t[0] + dt / BACK_TURN_TICKS) : Math.max(0.0F, t[0] - dt / BACK_TURN_TICKS);
		if (t[0] <= 0.0F && !back) {
			TURNED.remove(player.getId());
		}
		return PoseBlend.smooth(t[0]);
	}

	/**
	 * After the swimming tilt the model's Y axis runs head to foot (a turn about it rolls the body over) and +Z is up off
	 * the ground (a turn about X lifts the head end, about the feet).
	 */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		State crawl = state.getData(KEY);
		Float move = state.getData(ProneTransition.KEY);
		if (move != null && (crawl == null || crawl.rise() < 0.0F && crawl.downed() < 0.0F)) {
			// (0.19.3) crouching down into it (or getting up), in place of vanilla's tilt
			ProneTransition.rotate(state, poseStack, move);
			return;
		}
		if (crawl == null && state.getData(RISEN_KEY) != null && state.swimAmount > 0.0F) {
			// (0.18.5) up on their feet: vanilla's tilt (and its shift, while the pose lasts) undone as it eases out
			if (state.isVisuallySwimming) {
				poseStack.translate(0.0F, 1.0F, -0.3F);
			}
			poseStack.rotateDegrees(Axis.XP, 90.0F * state.swimAmount);
			return;
		}
		if (crawl == null || !state.isVisuallySwimming) {
			return;
		}
		if (crawl.rise() >= 0.0F) {
			// (0.18.5) turned up off the ground about the feet; stood, vanilla's tilt and shift are undone outright
			float up = PoseBlend.smooth(crawl.rise());
			poseStack.translate(0.0F, up, -0.3F * up);
			poseStack.rotateDegrees(Axis.XP, riseDegrees(crawl.rise()) + Mth.sin(crawl.age() * 2.3F) * 0.6F * (1.0F - crawl.rise()));
			return;
		}
		if (crawl.back() > 0.0F) {
			// (0.18.85) over onto the back: a twist about the middle, head and feet trading ends as the body rolls face up
			poseStack.translate(0.0F, BACK_PIVOT, 0.0F);
			poseStack.rotateDegrees(Axis.ZP, 180.0F * crawl.back());
			poseStack.rotateDegrees(Axis.YP, 180.0F * crawl.back());
			poseStack.translate(0.0F, -BACK_PIVOT, 0.0F);
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
				// (0.15.86) each arm's pull hauls the chest up off the ground a little
				lift += DRAG_HEAVE * Math.abs(Mth.sin(phase(crawl))) * moving(crawl) * w;
			} else {
				lift += BREATH_DEGREES * 0.5F * (0.5F + 0.5F * Mth.sin(crawl.age() * BREATH_RATE * 0.5F)) * w;
			}
		}
		if (!crawl.out()) {
			float phase = phase(crawl);
			rock = ROCK_DEGREES * Mth.sin(phase) * moving(crawl) * hurtRock(crawl) * w;
			if (crawl.downed() >= 0.0F) {
				// (0.15.86) the limp torso only sways with the arms' pull
				rock *= LIMP_ROCK;
			}
		}
		// (0.18.85) on the back there's no chest to heave up or shoulders to rock
		rock *= 1.0F - crawl.back();
		lift *= 1.0F - crawl.back();
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
		Float move = state.getData(ProneTransition.KEY);
		if (crawl != null && (crawl.rise() >= 0.0F || crawl.downed() >= 0.0F)) {
			move = null;
		}
		boolean on = crawl != null && state.isVisuallySwimming || move != null;
		PoseBlend.Track track = BLEND.track(state.id, on);
		if (track == null) {
			return;
		}
		if (move != null) {
			// (0.19.3) going down into the crawl or getting up: crouched, then onto the hands
			PoseBlend.Frame f = new PoseBlend.Frame(h);
			ProneTransition.pose(f, move);
			track.apply(h, f, 1.6F);
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
		if (crawl.rise() >= 0.0F) {
			rise(f, crawl, rightArm, leftArm);
			track.apply(h, f, 1.2F);
			return;
		}
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
		float back = crawl.back();
		if (back > 0.0F) {
			// (0.18.85) on the back: head up looking down past the feet, one knee raised; the arms at the sides, or up
			// and out toward the feet to aim (a drawn bow, a crossbow or gun held ready)
			boolean aiming = state.isUsingItem || aims(state.rightArmPose) || aims(state.leftArmPose);
			float arms = aiming ? BACK_ARMS_AIM : BACK_ARMS_REST;
			f.mix(PoseBlend.HEAD, BACK_HEAD, f.vanilla(PoseBlend.HEAD + 1) * 0.3F, 0.0F, back);
			f.mix(PoseBlend.RIGHT_ARM, arms, aiming ? -0.1F : 0.0F, aiming ? 0.05F : BACK_ARMS_OUT, back);
			f.mix(PoseBlend.LEFT_ARM, arms, aiming ? 0.25F : 0.0F, aiming ? -0.05F : -BACK_ARMS_OUT, back);
			f.mix(PoseBlend.RIGHT_LEG, BACK_KNEE_UP, 0.0F, BACK_LEG_OUT, back);
			f.mix(PoseBlend.LEFT_LEG, 0.0F, 0.0F, -BACK_LEG_OUT, back);
		}
		track.apply(h, f, crawl.downed() >= 0.0F ? 0.7F : 1.0F);
	}

	/** (0.18.85) An arm pose that points a weapon. */
	private static boolean aims(HumanoidModel.ArmPose pose) {
		return pose == HumanoidModel.ArmPose.BOW_AND_ARROW || pose == HumanoidModel.ArmPose.CROSSBOW_CHARGE
			|| pose == HumanoidModel.ArmPose.CROSSBOW_HOLD || pose == HumanoidModel.ArmPose.SPYGLASS;
	}

	/**
	 * The army crawl's limbs on a frame lying face down (the swimming tilt, or a downed mob's): {@code phase} through the
	 * stroke, {@code moving} how much of it shows (0 lying still), {@code broken} the limbs left to drag ({@link Dismember}
	 * bits), {@code downed} resting the head lower when still and (0.15.86) the legs and torso limp, dragged along by the
	 * arms. Shared with {@code DownedPose}'s crawlers.
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
		if (downed) {
			// (0.15.86) limp: the legs trail apart, toes dragging, bounced a little by each pull; the waist doesn't twist
			float bounce = LIMP_BOUNCE * Math.abs(Mth.sin(phase)) * moving;
			if (rightLeg) {
				f.set(PoseBlend.RIGHT_LEG, LIMP_LEG_X + bounce, 0.0F, LIMP_LEG_OUT);
			}
			if (leftLeg) {
				f.set(PoseBlend.LEFT_LEG, LIMP_LEG_X + bounce * 0.8F, 0.0F, -LIMP_LEG_OUT * 0.8F);
			}
			f.set(PoseBlend.BODY + 1, 0.0F);
			f.set(PoseBlend.HIP_YAW, 0.0F);
			float look = Math.abs(Mth.sin(phase)) * moving;
			f.set(PoseBlend.HEAD, Mth.lerp(look, Mth.lerp(moving, HEAD_DOWN_REST, HEAD_REST), HEAD_LOOK), f.vanilla(PoseBlend.HEAD + 1) * 0.5F, 0.0F);
			return;
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

	/** (0.18.5) How far the body is turned up off the ground {@code t} through the rise (degrees; 90 stands). */
	static float riseDegrees(float t) {
		if (t < PUSH_SHARE) {
			return PUSH_DEGREES * PoseBlend.smooth(t / PUSH_SHARE);
		}
		if (t < KNEEL_SHARE) {
			return Mth.lerp(PoseBlend.smooth((t - PUSH_SHARE) / (KNEEL_SHARE - PUSH_SHARE)), PUSH_DEGREES, KNEEL_DEGREES);
		}
		return Mth.lerp(PoseBlend.smooth((t - KNEEL_SHARE) / (1.0F - KNEEL_SHARE)), KNEEL_DEGREES, 90.0F);
	}

	/**
	 * (0.18.5) Rising's limbs: arms pressed straight down under the shoulders, shaking; then the knees drawn under, a
	 * hand on the knee; then straightening, arms down by the sides. The head looks ahead, coming upright with the body.
	 */
	private static void rise(PoseBlend.Frame f, State crawl, boolean rightArm, boolean leftArm) {
		float t = crawl.rise();
		float press = 1.0F - PoseBlend.seg(t, PUSH_SHARE * 0.7F, KNEEL_SHARE);
		float kneel = PoseBlend.bump(t, PUSH_SHARE * 0.6F, PUSH_SHARE + 0.1F, KNEEL_SHARE, 1.0F);
		float stand = PoseBlend.seg(t, KNEEL_SHARE, 1.0F);
		float shake = Mth.sin(crawl.age() * 2.3F) * 0.05F * (1.0F - stand);
		if (rightArm) {
			f.set(PoseBlend.RIGHT_ARM, Mth.lerp(press, Mth.lerp(stand, KNEE_HAND_X, 0.0F), PRESS_X), 0.0F, Mth.lerp(press, 0.1F, PRESS_OUT));
			f.shake(PoseBlend.RIGHT_ARM, shake);
		}
		if (leftArm) {
			f.set(PoseBlend.LEFT_ARM, Mth.lerp(press, Mth.lerp(stand, -0.25F, 0.0F), PRESS_X), 0.0F, Mth.lerp(press, -0.1F, -PRESS_OUT));
			f.shake(PoseBlend.LEFT_ARM, -shake);
		}
		// the knees drawn under the body, the right more (the foot planted to stand on), straightening as they stand
		f.set(PoseBlend.RIGHT_LEG, KNEEL_LEGS * kneel * 1.1F, 0.0F, 0.05F);
		f.set(PoseBlend.LEFT_LEG, KNEEL_LEGS * kneel * 0.85F, 0.0F, -0.05F);
		f.set(PoseBlend.BODY + 1, 0.0F);
		f.set(PoseBlend.HIP_YAW, 0.0F);
		f.set(PoseBlend.HEAD, HEAD_LOOK * (1.0F - riseDegrees(t) / 90.0F), 0.0F, 0.0F);
	}

	/** How much of the stroke shows (0 lying still to 1 crawling); downed, the slow drag shows it all. */
	private static float moving(State crawl) {
		return Math.min(1.0F, crawl.speed() * (crawl.downed() >= 0.0F ? DRAG_MOVING_PER_SPEED : MOVING_PER_SPEED));
	}

	/** Where in the stroke: from the distance crawled; downed, slower and hitching (lingering on each pull). */
	private static float phase(State crawl) {
		float phase = crawl.stride() * STROKE;
		if (crawl.downed() >= 0.0F) {
			phase *= DRAG_STROKE;
			phase += DOWNED_HITCH * Mth.sin(phase * 2.0F);
		}
		return phase;
	}

	/** How much more the body rocks for a broken limb (onto the good side's work). */
	private static float hurtRock(State crawl) {
		return crawl.broken() == 0 ? 1.0F : HURT_ROCK;
	}

	/**
	 * Downed and lying still: the try at pushing up (0-1), now and then (each player on their own beat). (0.15.86) Held
	 * all the while Sneak is (giving up: the struggle that fails).
	 */
	private static float attempt(State crawl) {
		if (crawl.out()) {
			return 0.0F;
		}
		if (crawl.sneak()) {
			return 1.0F;
		}
		if (crawl.downed() < ATTEMPT_TICKS) {
			return 0.0F;
		}
		float still = 1.0F - moving(crawl);
		float every = ATTEMPT_EVERY + (crawl.id() & 63);
		float t = (crawl.downed() + (crawl.id() & 0xFF) * 3.0F) % every;
		// up slowly, held shaking, then sinking back faster
		return still * PoseBlend.bump(t, 0.0F, ATTEMPT_TICKS * 0.5F, ATTEMPT_TICKS * 0.7F, ATTEMPT_TICKS);
	}
}

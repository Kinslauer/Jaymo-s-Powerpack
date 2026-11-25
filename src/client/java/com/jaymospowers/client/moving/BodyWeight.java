package com.jaymospowers.client.moving;

import com.jaymospowers.client.KneeBend;
import com.jaymospowers.client.LowEndMode;
import com.jaymospowers.client.PoseTakeover;
import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.jaymospowers.combat.Armed;
import com.jaymospowers.core.ArmourKind;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-24, with a clip of a player hauling a greatsword about): "Can our movement have an increased weight to
 * the animation like this?" What makes that clip read as heavy, and what this lays over every player's walk (theirs and
 * everyone's in view, from positions the client already has, so everyone sees the same):
 * <ul>
 * <li><b>The body is a weight on the hips.</b> The torso swings like a pendulum on the hips' movement: it lags back a
 * moment as you set off, leans into the walk ({@link #STEADY_LEAN}) and the run, and when you stop it carries on forward
 * past upright and settles back ({@link #CARRY_ON}), never snapping straight.</li>
 * (The lean into a turn is BodyAnim's, which came in beside this.)
 * <li><b>Each step lands.</b> The body sinks as a foot comes down (the stride's widest point, where straight legs would
 * lift the feet off the ground) and rises over the planted leg ({@link #STEP_DIP}); the hips rock over the leg taking
 * the weight ({@link #HIP_SWAY}); the shoulders turn against the hips ({@link #COUNTER_TWIST}); a walking leg's knee
 * gives as it takes the weight and folds as it swings back ({@link #WALK_KNEE}).</li>
 * <li><b>Landings squash.</b> Coming down from a jump or a drop, the body sinks onto bent knees, tips forward and comes
 * back up with a small rebound, deeper the harder the landing ({@link #SQUASH_DROP}); in first person your view sinks
 * with it (never the field of view). Off the ground the legs tuck up a little ({@link #TUCK_KNEE}).</li>
 * <li><b>Heavier gear, heavier body.</b> Armour (by {@link ArmourKind#load}, plate the most) and a two-handed weapon or
 * polearm in hand make all of it slower and deeper ({@link #ARMOUR_WEIGHT}, {@link #HEAVY_WEAPON}).</li>
 * </ul>
 * It gives way to every pose of ours: the whole-body parts under any pose but the armed stance
 * ({@link PoseTakeover#posedOtherwise}: the crawl, dodge rolls, ledges, parkour, falls, slides, carries...), the arms and
 * shoulders under the armed stance too ({@link PoseTakeover#posed}). Standing still it does nothing, so still bodies keep
 * their stances. Your own body in first person (First-person Model) only sinks, never tips, so the view stays at the
 * head. Off in low-end mode ({@link LowEndMode}).
 */
public final class BodyWeight {
	// ===== the knobs ================================================================================================
	/** Players this near the camera (blocks) are weighed; farther ones are too small on screen to see it. */
	static final double NEAR = 48.0;
	/** A full sprint's speed along the ground (blocks a tick), the measure for "how fast" below. */
	static final float SPRINT_SPEED = 0.28F;
	/** The lean into moving (degrees) at a full sprint's speed; backing up leans back at most this share of it. */
	static final float STEADY_LEAN = 6.0F, BACK_LEAN_SHARE = 0.4F;
	/**
	 * The torso's pendulum on the hips: its spring (how hard it's pulled to the lean it wants, a share a tick), its damping
	 * (a share of its swing lost a tick), and how far the hips' change of speed swings it (degrees per block a tick a tick):
	 * slowing throws it forward past upright, setting off rocks it back.
	 */
	static final float LEAN_SPRING = 0.16F, LEAN_DAMP = 0.28F, CARRY_ON = 25.0F;
	/** The most the torso is swung either way (degrees). */
	static final float LEAN_MAX = 16.0F;
	/**
	 * The step: the share of a straight leg's lift at the stride's widest that the body sinks instead (blocks at most), the
	 * forward nod as the foot lands (degrees), the hips' rock over the planted leg (degrees), the shoulders turned against
	 * the hips (radians), all at full walking speed.
	 */
	static final float STEP_DIP = 0.2F, STEP_DIP_MAX = 0.085F, STEP_NOD = 1.5F, HIP_SWAY = 2.5F, COUNTER_TWIST = 0.14F;
	/** The walking knee (radians): always a little bent while walking, and folding this much more per radian swung back. */
	static final float WALK_KNEE = 0.12F, WALK_KNEE_BACK = 0.55F;
	/** Vanilla's stride beat (radians per unit walked), and the leg's length (blocks, a player's 15/16 scale). */
	static final float BEAT = 0.6662F, LEG = 0.703F;
	/**
	 * Landings: a fall this fast (blocks a tick, down) or slower barely shows, this fast is a full squash; the squash's
	 * spring and damping (a little under, so it comes back up past standing once); how far down a full squash sinks the
	 * body (blocks), tips it forward (degrees), bends the knees and brings the thighs and arms up (radians).
	 */
	static final float LAND_MIN = 0.12F, LAND_FULL = 0.95F, SQUASH_KICK = 0.55F, SQUASH_SPRING = 0.3F, SQUASH_DAMP = 0.42F;
	static final float SQUASH_DROP = 0.2F, SQUASH_PITCH = 9.0F, SQUASH_KNEE = 1.3F, SQUASH_THIGH = 0.6F, SQUASH_ARMS = 0.35F;
	/** First person: the share of the body's landing sink your view takes (all of it when your body is drawn). */
	static final float VIEW_SINK = 0.6F;
	/** Off the ground: the legs' tuck (knees and thighs, radians) and how fast it comes and goes (a share a tick). */
	static final float TUCK_KNEE = 0.55F, TUCK_THIGH = 0.3F, TUCK_EASE = 0.25F;
	/** The weight of gear: a full suit of plate adds this much, a two-handed weapon or polearm this much; at most this. */
	static final float ARMOUR_WEIGHT = 0.4F, HEAVY_WEAPON = 0.3F, MAX_WEIGHT = 1.7F;
	/** How often the gear is weighed again (ticks). */
	static final int WEIGH_TICKS = 20;
	// ================================================================================================================

	/**
	 * What the renderer needs, eased between ticks: {@code lean} (degrees, + forward), {@code squash} (0-1 or a little past, - for the rebound), {@code tuck} (0-1), {@code weight} (1 bare),
	 * {@code ground} on the ground, {@code ownView} your own body seen from inside it, {@code kept} how much of the stride's
	 * sink, rock and twist is drawn (1, less as a dodge takes over: {@link #weight}).
	 */
	public record Weight(float lean, float squash, float tuck, float weight, boolean ground, boolean ownView, float kept) {
	}

	public static final RenderStateDataKey<Weight> KEY = RenderStateDataKey.create(() -> "jaymospowers:body_weight");

	/** A damped spring, with last tick's value to ease from. */
	private static final class Spring {
		float x, v, last;

		void step(float target, float spring, float damp, float push) {
			this.last = this.x;
			this.v += (target - this.x) * spring - this.v * damp + push;
			this.x += this.v;
		}

		float at(float partialTicks) {
			return Mth.lerp(partialTicks, this.last, this.x);
		}
	}

	private static final class Body {
		double x, y, z;
		float speed, minVy;
		boolean ground = true, fresh = true;
		int weighed = -1;
		float weight = 1.0F, tuck, tuckLast;
		final Spring lean = new Spring(), squash = new Spring();
	}

	private static final Int2ObjectOpenHashMap<Body> BODIES = new Int2ObjectOpenHashMap<>();
	private static final IntOpenHashSet SEEN = new IntOpenHashSet();

	private BodyWeight() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BodyWeight::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level == null || LowEndMode.on() || minecraft.isPaused()) {
			if (minecraft.level == null || LowEndMode.on()) {
				BODIES.clear();
			}
			return;
		}
		Entity camera = minecraft.getCameraEntity() != null ? minecraft.getCameraEntity() : minecraft.player;
		SEEN.clear();
		for (Player player : minecraft.level.players()) {
			if (camera != null && player.distanceToSqr(camera) > NEAR * NEAR) {
				continue;
			}
			SEEN.add(player.getId());
			step(player, BODIES.computeIfAbsent(player.getId(), id -> new Body()));
		}
		BODIES.keySet().removeIf(id -> !SEEN.contains(id));
	}

	/** Whether {@code player}'s body is up and walking about (anything else lets the springs die away). */
	private static boolean upright(Player player) {
		return !player.isSpectator() && !player.isPassenger() && !player.isSleeping() && !player.isFallFlying() && !player.isSwimming()
			&& !player.isInWater() && !player.onClimbable() && (player.getPose() == Pose.STANDING || player.getPose() == Pose.CROUCHING)
			&& !Downed.isDowned(player) && player.isAlive();
	}

	private static void step(Player player, Body body) {
		double dx = player.getX() - body.x, dy = player.getY() - body.y, dz = player.getZ() - body.z;
		float yaw = player.yBodyRot;
		if (body.fresh || dx * dx + dy * dy + dz * dz > 16.0) {
			// new in view, or a teleport: nothing to swing from
			body.fresh = false;
			body.x = player.getX();
			body.y = player.getY();
			body.z = player.getZ();
			body.ground = player.onGround();
			return;
		}
		body.x = player.getX();
		body.y = player.getY();
		body.z = player.getZ();
		if (--body.weighed < 0) {
			body.weighed = WEIGH_TICKS;
			body.weight = weigh(player);
		}
		float w = body.weight;
		boolean up = upright(player);
		boolean ground = player.onGround();

		// along the way the body faces (+ forward), and how much that changed since last tick
		float rad = yaw * Mth.DEG_TO_RAD;
		float forward = (float)(dx * -Mth.sin(rad) + dz * Mth.cos(rad));
		float change = up && ground && body.ground ? forward - body.speed : 0.0F;
		body.speed = forward;
		float pace = Mth.clamp(forward / SPRINT_SPEED, -BACK_LEAN_SHARE, 1.0F);

		// heavier is slower to swing and swings further
		float leanTarget = up && ground ? STEADY_LEAN * pace * w : 0.0F;
		body.lean.step(leanTarget, LEAN_SPRING / w, LEAN_DAMP, -change * CARRY_ON * w);
		body.lean.x = Mth.clamp(body.lean.x, -LEAN_MAX, LEAN_MAX);

		// landings: the hardest downward speed of the time in the air, then a kick into the squash
		float vy = (float)dy;
		float kick = 0.0F;
		if (!ground) {
			body.minVy = Math.min(body.minVy, vy);
		} else if (!body.ground) {
			float hard = Mth.clamp((-body.minVy - LAND_MIN) / (LAND_FULL - LAND_MIN), 0.0F, 1.0F);
			if (up && hard > 0.0F) {
				kick = SQUASH_KICK * (0.35F + 0.65F * hard) * Mth.sqrt(w);
			}
			body.minVy = 0.0F;
		}
		body.squash.step(0.0F, SQUASH_SPRING / Mth.sqrt(w), SQUASH_DAMP, kick);
		body.squash.x = Mth.clamp(body.squash.x, -0.2F, 1.2F);
		body.ground = ground;

		body.tuckLast = body.tuck;
		boolean tucked = up && !ground && !player.getAbilities().flying;
		body.tuck = Mth.clamp(body.tuck + (tucked ? TUCK_EASE : -TUCK_EASE * 1.6F), 0.0F, 1.0F);
	}

	/** How heavy {@code player} moves: 1 bare-handed in no armour, more with plate and a great weapon. */
	private static float weigh(Player player) {
		float w = 1.0F + ArmourKind.load(player) * ARMOUR_WEIGHT;
		Armed.Kind kind = Armed.kind(player.getMainHandItem());
		if (kind == Armed.Kind.TWO_HAND || kind == Armed.Kind.POLE) {
			w += HEAVY_WEAPON;
		}
		return Math.min(MAX_WEIGHT, w);
	}

	// ---- drawing ----------------------------------------------------------------------------------------------------

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Body body = entity instanceof Player ? BODIES.get(entity.getId()) : null;
		if (body == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F || LowEndMode.on()) {
			state.setData(KEY, null);
			return;
		}
		float lean = body.lean.at(partialTicks), squash = body.squash.at(partialTicks);
		float tuck = Mth.lerp(partialTicks, body.tuckLast, body.tuck);
		boolean moving = state.walkAnimationSpeed > 0.01F;
		if (!moving && Math.abs(lean) < 0.05F && Math.abs(squash) < 0.005F && tuck <= 0.0F) {
			state.setData(KEY, null);
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean own = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		state.setData(KEY, new Weight(lean, squash, smooth(tuck), body.weight, entity.onGround(), own && FirstPersonCompat.bodyShown(), 1.0F));
	}

	private static @Nullable Weight weight(LivingEntityRenderState state) {
		if (!(state instanceof AvatarRenderState avatar) || state.deathTime > 0.0F) {
			return null;
		}
		Weight w = state.getData(KEY);
		if (w == null || avatar.isPassenger || avatar.isFallFlying || avatar.isVisuallySwimming || avatar.swimAmount > 0.0F) {
			return null;
		}
		// (Andy, 2026-11-25, ironing out the dodge) a dodge's dash or roll doesn't drop the weight in one frame: it fades out
		// as the dodge's pose comes on and back in as it lets go (DodgeRollPose#weightKept); every other pose of ours still
		// has the whole body
		com.jaymospowers.client.stamina.DodgeRollPose.Roll roll = avatar.getData(com.jaymospowers.client.stamina.DodgeRollPose.KEY);
		if (roll == null) {
			return PoseTakeover.posedOtherwise(avatar) ? null : w;
		}
		float kept = com.jaymospowers.client.stamina.DodgeRollPose.weightKept(roll);
		if (kept <= 0.01F) {
			return null;
		}
		avatar.setData(com.jaymospowers.client.stamina.DodgeRollPose.KEY, null);
		boolean other = PoseTakeover.posedOtherwise(avatar);
		avatar.setData(com.jaymospowers.client.stamina.DodgeRollPose.KEY, roll);
		return other ? null : new Weight(w.lean() * kept, w.squash() * kept, w.tuck() * kept, w.weight(), w.ground(), w.ownView(), kept);
	}

	/** How far into its stride the walk is, 0 standing to 1 at full speed (on the ground only). */
	private static float stride(AvatarRenderState state, Weight w) {
		return w.ground() && !state.isCrouching ? Math.min(1.0F, state.walkAnimationSpeed) * w.kept() : 0.0F;
	}

	/** After the body faces its way (about the feet): the step's sink and rock, the pendulum's lean, the squash. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Weight w = weight(state);
		if (w == null || !(state instanceof AvatarRenderState avatar)) {
			return;
		}
		float s = stride(avatar, w);
		float beat = state.walkAnimationPos * BEAT;
		// the body sinks by a share of what straight legs at this swing would lift the feet, and the landing's squash
		float swing = 1.4F * s * Mth.cos(beat);
		float dip = Math.min(STEP_DIP_MAX, LEG * (1.0F - Mth.cos(swing)) * STEP_DIP) * w.weight();
		// (your own body seen from inside sinks only with the landing, as your view does)
		float sink = (w.ownView() ? 0.0F : dip) + SQUASH_DROP * w.squash() * w.weight();
		if (sink != 0.0F) {
			poseStack.translate(0.0F, -sink * entityScale, 0.0F);
		}
		if (w.ownView()) {
			// your own body seen from inside: tipping it would swing the chest up in front of the view
			return;
		}
		// a negative turn about X tips the top forward, a negative turn about Z tips it to the right
		float plant = Math.abs(Mth.cos(beat));
		float lean = w.lean() + STEP_NOD * s * plant * w.weight() + SQUASH_PITCH * Math.max(0.0F, w.squash());
		if (state.getData(com.jaymospowers.client.freerun.RunPose.LEAN) != null) {
			// the fast run leans from the feet already: keep only the swing of stopping and starting
			lean -= STEADY_LEAN;
		}
		poseStack.rotateDegrees(Axis.XP, -lean);
		float rock = HIP_SWAY * s * Mth.sin(beat) * w.weight();
		poseStack.rotateDegrees(Axis.ZP, rock);
	}

	/** After the model animates: the shoulders against the hips, and the landing's and the jump's legs and arms. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Weight w = weight(state);
		if (w == null) {
			return;
		}
		float squash = Math.max(0.0F, w.squash());
		// the legs: up into the squash, tucked in the air (the armed stance leaves the legs to the walk, so these go under it too)
		if (squash > 0.0F) {
			h.rightLeg.xRot -= SQUASH_THIGH * squash;
			h.leftLeg.xRot -= SQUASH_THIGH * squash * 0.85F;
		}
		if (w.tuck() > 0.0F && !w.ground()) {
			h.rightLeg.xRot = Mth.lerp(w.tuck(), h.rightLeg.xRot, -TUCK_THIGH * 1.4F);
			h.leftLeg.xRot = Mth.lerp(w.tuck(), h.leftLeg.xRot, -TUCK_THIGH * 0.5F);
		}
		if (PoseTakeover.posed(state) || state.swingAnimation > 0.0F || state.isUsingItem) {
			// the armed stance (or a swing, or something in use) has the arms and shoulders
			return;
		}
		if (squash > 0.0F) {
			h.rightArm.xRot -= SQUASH_ARMS * squash;
			h.leftArm.xRot -= SQUASH_ARMS * squash;
		}
		float s = stride(state, w);
		if (s > 0.0F) {
			// the right shoulder comes forward as the right arm swings forward (the left leg forward)
			float twist = -COUNTER_TWIST * s * Mth.cos(state.walkAnimationPos * BEAT) * Math.min(1.3F, w.weight());
			float a = h.body.yRot + twist;
			h.body.yRot = a;
			h.rightArm.z = Mth.sin(a) * 5.0F;
			h.rightArm.x = -Mth.cos(a) * 5.0F;
			h.leftArm.z = -Mth.sin(a) * 5.0F;
			h.leftArm.x = Mth.cos(a) * 5.0F;
			h.rightArm.yRot += twist;
			h.leftArm.yRot += twist;
		}
	}

	/**
	 * Last thing before the model is drawn, before the run's knees (a leg bent here keeps this bend, which takes in the
	 * run's): a walking
	 * leg's knee giving under the weight and folding as it swings back, both knees deep in a landing, tucked in the air.
	 */
	public static void bendLimbs(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Weight w = weight(state);
		if (w == null || state.isCrouching) {
			return;
		}
		float s = stride(state, w);
		float squash = Math.max(0.0F, w.squash());
		float tuck = w.ground() ? 0.0F : w.tuck();
		knee(state, h.rightLeg, s, squash, tuck * 1.3F);
		knee(state, h.leftLeg, s, squash * 0.85F, tuck * 0.7F);
	}

	private static void knee(AvatarRenderState state, ModelPart leg, float stride, float squash, float tuck) {
		// the walk's give or the run's fold, whichever is deeper, then the landing and the tuck on top
		float walk = Math.max((WALK_KNEE + WALK_KNEE_BACK * Math.max(0.0F, leg.xRot)) * stride,
			com.jaymospowers.client.freerun.RunPose.runKnee(state, leg));
		float bend = walk + SQUASH_KNEE * squash + TUCK_KNEE * tuck;
		if (bend > 0.01F) {
			KneeBend.bend(leg, Math.min(2.0F, bend));
		}
	}

	/** First person: how far your view sinks with your body's landing (blocks), 0 when there's none. */
	public static float viewSink(Player player, float partialTicks) {
		Body body = BODIES.get(player.getId());
		if (body == null || LowEndMode.on() || !upright(player)) {
			return 0.0F;
		}
		float squash = body.squash.at(partialTicks);
		// with your body drawn under you (First-person Model) the view stays at its head; otherwise a gentler share
		return squash == 0.0F ? 0.0F : SQUASH_DROP * squash * body.weight * (FirstPersonCompat.bodyShown() ? 1.0F : VIEW_SINK);
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}
}

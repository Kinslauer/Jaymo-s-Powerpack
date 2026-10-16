package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/**
 * The big body, built like a towering green-giant hero but all symbiote ({@link WebHulkMesh}, {@code tools/gen_web_hulk.py}):
 * upright, a broad chest over deep lats, huge shoulders, thick arms ending in big fists with bone spurs breaking out of
 * the forearms, legs like tree trunks, and the head up on the traps with big angular eyes, a heavy muzzle and a hinged
 * jaw lined with fangs. The jaw hangs open
 * and breathes, now and then drawing back in a snarl; a long red tongue stays in the mouth and flicks out quickly every
 * {@code TONGUE_EVERY} ticks, and once just after a meal ({@link #submitTongue}; the maw's own tongue lashes out for
 * the last bite, WebMouthLayer). The forearms and shins hang off elbows and knees of their own ({@link Model#limbs}):
 * they bend with the stride, the forearms trailing the swing, the knees folding as each leg comes through, and sway a
 * little standing. Everything keeps the player model's pivots, so every pose drives it; on top of them the
 * arms sit wider, the legs further apart and the head higher and further forward ({@link #shift}), over every model
 * drawn for that body (the wearer's own, the suits, armour), so they all line up.
 */
final class WebHulk {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_hulk.png");
	/**
	 * How far the arms sit out, the legs apart, and the head sinks (negative: rises) and juts forward on the big body
	 * (pixels). The head's offset is in the torso's own frame (it rides the hunch, the breathing and the lean, Andy: "the
	 * head clips and animates separately it looks like"), forward of the traps, between the shoulders. The frame itself
	 * (the hips {@code WebHulkMesh.LEG_LIFT} up, the neck {@code WebHulkMesh.BODY_LIFT} up along the torso, Andy: "he
	 * looks short and stocky like a gremlin rather than a behemoth") is the mesh's.
	 */
	static final float ARM_OUT = 5.0F, LEG_OUT = 2.2F, HEAD_DOWN = -1.5F, HEAD_FORWARD = 4.0F;
	/**
	 * The head on its neck: turned at most {@code HEAD_YAW_MAX} either way and rolled {@code HEAD_ROLL_MAX} (radians);
	 * tipped back from the torso at most {@code HEAD_BACK_MAX} (further, the back of the skull sinks into the traps; in
	 * the gallop it looks down along the charge) and forward {@code HEAD_FORWARD_MAX}; it takes {@code HEAD_FOLLOW} of the
	 * torso's own pitch (its breathing and lean).
	 */
	static final float HEAD_YAW_MAX = 0.45F, HEAD_ROLL_MAX = 0.12F, HEAD_BACK_MAX = 0.4F, HEAD_FORWARD_MAX = 0.5F, HEAD_FOLLOW = 0.3F;
	/**
	 * The suit and the goo under the big body (as it's built or peeled) stretch to its frame: the torso by
	 * {@code TORSO / 12}, the legs by {@code LEG / 12} (the player model's are 12 long).
	 */
	static final float TORSO_STRETCH = WebHulkMesh.TORSO / 12.0F, LEG_STRETCH = WebHulkMesh.LEG / 12.0F;
	/** The jaw: how far it hangs open at rest (radians), how much it breathes, and a snarl's gape. */
	static final float JAW_REST = 0.22F, JAW_BREATH = 0.08F, JAW_SNARL = 0.5F;
	/** The tongue: pieces, each one's length (pixels), its width and thickness at the root (tapering to the tip). */
	static final int TONGUE_PIECES = 12;
	static final float TONGUE_PIECE = 1.7F, TONGUE_WIDTH = 1.6F, TONGUE_THICK = 0.55F;
	/** How it moves: drooping this much more each piece, rippling this hard, swaying, and its tip fluttering in a flick. */
	static final float TONGUE_DROOP = 0.1F, TONGUE_RIPPLE = 0.28F, TONGUE_SWAY = 0.22F, TONGUE_FLUTTER = 0.35F;
	/**
	 * It stays in the mouth: a quick flick out and back once every {@code TONGUE_EVERY} ticks (each player on their own
	 * beat), and once just after a meal, each taking {@code FLICK_TICKS}; out by {@code FLICK_OUT} of the flick, back in from
	 * {@code FLICK_BACK}. The jaw opens {@code JAW_FLICK} wider for it.
	 */
	static final float TONGUE_EVERY = 140.0F, FLICK_TICKS = 12.0F, FLICK_OUT = 0.3F, FLICK_BACK = 0.6F, JAW_FLICK = 0.18F;
	/**
	 * After a meal (a corpse eaten, in the big body or the plain suit) it licks its chops: {@code MEAL_FLICKS} flicks,
	 * one every {@code MEAL_FLICK_GAP} ticks, starting {@code MEAL_FLICK_DELAY} ticks after the maw lets go (so the maw
	 * has sunk back first and the flicks are seen).
	 */
	static final int MEAL_FLICKS = 3;
	static final float MEAL_FLICK_DELAY = 10.0F, MEAL_FLICK_GAP = 16.0F;
	/** The plain suit's tongue (only after a meal): smaller, from the mouth at the front of the head. */
	static final int SUIT_TONGUE_PIECES = 9;
	static final float SUIT_TONGUE_PIECE = 1.3F, SUIT_TONGUE_WIDTH = 1.2F, SUIT_TONGUE_THICK = 0.4F;
	/**
	 * Put on by hand (Living Armor from the menu; {@link com.jaymospowers.power.web.WebSymbiote#DONNED}), the big body
	 * lashes its tongue once {@code DON_LASH_AT} ticks after, as the head finishes forming, the lash taking
	 * {@code DON_LASH_TICKS}. The plain suit has no tongue (Andy: "I'd also like the tounge to be kept to the large
	 * suit"); only Consume's maw shows one on it, while it eats.
	 */
	static final float DON_LASH_AT = 20.0F, DON_LASH_TICKS = 22.0F;
	/**
	 * The elbows: bent {@code ELBOW_REST} hanging, up to {@code ELBOW_SWING} more as the arm comes forward in a full stride
	 * (the forearm trailing the swing by {@code ELBOW_LAG} of the cycle), breathing {@code ELBOW_IDLE} standing; an arm
	 * holding something or striking keeps {@code ELBOW_HOLDING} of it (so the hand stays where the item is drawn).
	 */
	static final float ELBOW_REST = 0.25F, ELBOW_SWING = 0.75F, ELBOW_LAG = 0.6F, ELBOW_IDLE = 0.08F, ELBOW_HOLDING = 0.25F;
	/**
	 * The knees: bent {@code KNEE_WALK} walking, folding up to {@code KNEE_SWING} as each leg swings through (peaking
	 * {@code KNEE_LEAD} of the cycle early, just after the foot leaves the ground). A thigh raised forward (a crouch, the
	 * leap's coil, knees drawn up) folds the shin back {@code KNEE_CROUCH} times as far (2 keeps the foot under the hip),
	 * sitting {@code KNEE_SIT} (shins hanging straight down); never past {@code KNEE_MAX}.
	 */
	static final float KNEE_WALK = 0.15F, KNEE_SWING = 1.1F, KNEE_LEAD = 0.4F, KNEE_CROUCH = 2.0F, KNEE_SIT = 1.0F, KNEE_MAX = 2.3F;
	/** Standing, the arms rock {@code IDLE_SWAY} (radians) at {@code IDLE_RATE} (radians a tick). */
	static final float IDLE_SWAY = 0.05F, IDLE_RATE = 0.06F;

	/** The big body's model: the player model's parts, each muscle a child part (grown on one at a time). */
	static final class Model extends PlayerModel {
		final ModelPart[] muscles;
		final int[] order;
		final ModelPart jaw;
		final ModelPart elbowRight, elbowLeft, kneeRight, kneeLeft;

		Model() {
			super(WebHulkMesh.create().bakeRoot(), false);
			ModelPart root = this.root();
			this.muscles = new ModelPart[WebHulkMesh.PARTS.length];
			this.order = WebHulkMesh.ORDER;
			for (int i = 0; i < this.muscles.length; i++) {
				// down its path of parts (the forearms' and shins' muscles hang off the elbows and knees)
				ModelPart part = root;
				for (String name : WebHulkMesh.PARTS[i]) {
					part = part.getChild(name);
				}
				this.muscles[i] = part;
			}
			this.jaw = this.head.getChild("jaw");
			this.elbowRight = this.rightArm.getChild("elbow");
			this.elbowLeft = this.leftArm.getChild("elbow");
			this.kneeRight = this.rightLeg.getChild("knee");
			this.kneeLeft = this.leftLeg.getChild("knee");
		}

		/** {@code g}: how far it's been built (0 nothing, 1 all of it); each muscle swells on in its turn. */
		void build(float g) {
			for (int i = 0; i < this.muscles.length; i++) {
				float at = Mth.clamp((g - BUILD_FROM - BUILD_SPAN * this.order[i] / (float)WebHulkMesh.LAST_ORDER) / BUILD_EACH, 0.0F, 1.0F);
				float size = at >= 1.0F ? 1.0F : swell(at);
				ModelPart part = this.muscles[i];
				part.xScale = size;
				part.yScale = size;
				part.zScale = size;
				part.visible = size > 0.02F;
			}
		}

		/** The jaw hangs open, breathing, and draws back in a snarl now and then (and opens for the tongue's flicks). */
		void breathe(AvatarRenderState state) {
			this.jaw.xRot = jaw(state);
		}

		/**
		 * The elbows and knees, set outright from the render state after the limbs are posed (so twice changes nothing):
		 * walking, each forearm trails its arm's swing, bending most just after the arm comes forward, and each knee
		 * folds as its leg swings through and straightens to land; galloping, they work with the arms and legs together;
		 * standing, the elbows hang a little bent and breathe while the arms rock. A raised thigh folds its shin back.
		 */
		void limbs(AvatarRenderState s) {
			float pos = s.walkAnimationPos;
			float speed = Mth.clamp(s.walkAnimationSpeed, 0.0F, 1.0F);
			float age = s.ageInTicks;
			float idle = 1.0F - speed;
			// the beast's own walk (WebFeral) swings each arm with the leg on its side, the game's with the other one
			boolean beast = !(s.isCrouching || s.isPassenger || s.isFallFlying || s.swimAmount > 0.0F);
			WebSymbioteClient.Tendrils tendrils = s.getData(WebSymbioteClient.TENDRILS);
			float run = beast && tendrils != null && tendrils.armor() ? tendrils.run() * Mth.clamp(0.4F + speed, 0.0F, 1.0F) : 0.0F;
			float walk = pos * 0.6662F, gallop = pos * 0.45F;
			for (int side = 0; side < 2; side++) {
				boolean right = side == 0;
				ModelPart arm = right ? this.rightArm : this.leftArm;
				// where the arm is in its swing (cos 1 at the back of it): the forearm bends most a little after the front
				float armPhase = walk + (right ? 0.0F : Mth.PI) + (beast ? 0.0F : Mth.PI);
				float stride = 0.5F - 0.5F * Mth.cos(armPhase - ELBOW_LAG);
				float galloping = 0.5F - 0.5F * Mth.cos(gallop - Mth.HALF_PI - ELBOW_LAG);
				float bend = ELBOW_REST + ELBOW_SWING * speed * Mth.lerp(run, stride, galloping)
					+ ELBOW_IDLE * idle * (0.5F + 0.5F * Mth.sin(age * IDLE_RATE * 1.3F + side * 1.7F));
				if (busy(s, right)) {
					bend *= ELBOW_HOLDING;
				} else {
					arm.xRot += IDLE_SWAY * idle * Mth.sin(age * IDLE_RATE + side * 2.1F);
				}
				// forward (and up): the forearm's bottom swings ahead
				(right ? this.elbowRight : this.elbowLeft).xRot = -bend;

				ModelPart leg = right ? this.rightLeg : this.leftLeg;
				// the game's walk: each leg cos(phase), back at 1, swinging forward while sin(phase) > 0
				float legPhase = walk + (right ? 0.0F : Mth.PI);
				float through = (float)Math.pow(Math.max(0.0F, Mth.sin(legPhase + KNEE_LEAD)), 1.5);
				// the gallop drives both legs together, -sin(gallop): forward while cos(gallop) > 0
				float gallopThrough = (float)Math.pow(Math.max(0.0F, Mth.cos(gallop + KNEE_LEAD)), 1.5);
				float knee = speed * (KNEE_WALK + KNEE_SWING * Mth.lerp(run, through, gallopThrough));
				// how far the thigh is raised forward beyond its stride: a crouch, the coil, knees drawn up, a seat
				float swing = Mth.lerp(run, Mth.cos(legPhase) * 1.4F * speed, -Mth.sin(gallop) * 0.95F * speed + 0.2F);
				float raised = Math.max(0.0F, swing - leg.xRot);
				knee = Math.max(knee, raised * (s.isPassenger ? KNEE_SIT : KNEE_CROUCH));
				// back: the shin's bottom swings behind
				(right ? this.kneeRight : this.kneeLeft).xRot = Math.min(knee, KNEE_MAX);
			}
		}
	}

	/** This arm is holding something, punching or forming a weapon: it keeps its elbow nearly straight. */
	private static boolean busy(AvatarRenderState s, boolean right) {
		boolean main = (s.mainArm == HumanoidArm.RIGHT) == right;
		if (main && s.swingAnimation > 0.0F) {
			return true;
		}
		if ((right ? s.rightArmPose : s.leftArmPose) != HumanoidModel.ArmPose.EMPTY) {
			return true;
		}
		WebMorphLayer.Strike strike = s.getData(WebMorphLayer.KEY);
		WebMorphLayer.Gather gather = s.getData(WebMorphLayer.GATHER);
		int side = strike != null && !strike.tendril() ? strike.side() : gather != null && !gather.tendril() ? gather.side() : 0;
		return side != 0 && side > 0 == right;
	}

	/** When the build starts (share of the change), how long it takes to reach the last muscle, and how long each one takes. */
	private static final float BUILD_FROM = 0.3F, BUILD_SPAN = 0.52F, BUILD_EACH = 0.16F;

	private WebHulk() {
	}

	/** 0 → 1, overshooting first: a muscle slapped on and settling. */
	private static float swell(float g) {
		float eased = 1.0F - (1.0F - g) * (1.0F - g);
		return eased + Mth.sin(g * Mth.PI) * 0.18F;
	}

	static float jaw(AvatarRenderState state) {
		float age = state.ageInTicks;
		float breath = Mth.sin(age * 0.11F) * JAW_BREATH;
		float snarl = (float)Math.pow(Math.max(0.0F, Mth.sin(age * 0.045F)), 16.0) * JAW_SNARL;
		float flick = flick(state);
		return JAW_REST + breath + snarl + (flick < 0.0F ? 0.0F : Mth.sin(flick * Mth.PI) * JAW_FLICK);
	}

	/** Entity id → {1 while it's eating (0 after), the age it finished at}: for the flick just after a meal. */
	private static final Map<Integer, float[]> MEALS = new HashMap<>();

	/** Notes whether this player is eating (call it every frame the big body is drawn, eating or not). */
	static void noteMeal(AvatarRenderState state) {
		boolean eating = state.getData(WebSymbioteClient.EATING) != null;
		float[] meal = MEALS.get(state.id);
		if (eating) {
			if (meal == null) {
				MEALS.put(state.id, new float[] {1.0F, 0.0F});
			} else {
				meal[0] = 1.0F;
			}
		} else if (meal != null) {
			if (meal[0] > 0.0F) {
				meal[0] = 0.0F;
				meal[1] = state.ageInTicks;
			} else if (state.ageInTicks - meal[1] > MEAL_FLICK_DELAY + MEAL_FLICKS * MEAL_FLICK_GAP || state.ageInTicks < meal[1]) {
				MEALS.remove(state.id);
			}
		}
		if (MEALS.size() > 256) {
			MEALS.clear();
		}
	}

	/** How far through a flick of the tongue it is (0-1), or -1 if it's in the mouth. */
	static float flick(AvatarRenderState state) {
		float age = state.ageInTicks;
		float don = donLash(state);
		if (don >= 0.0F) {
			return don;
		}
		float meal = mealFlick(state);
		if (meal != -2.0F) {
			// licking its chops, just after a meal (and no idle flick over it)
			return meal;
		}
		float cycle = (age + (state.id & 0xFFFF) * 37.0F) % TONGUE_EVERY;
		return cycle < FLICK_TICKS ? cycle / FLICK_TICKS : -1.0F;
	}

	/**
	 * How far through one of the flicks just after a meal it is (0-1), -1 between them, or -2 when no meal was just
	 * eaten.
	 */
	static float mealFlick(AvatarRenderState state) {
		float[] meal = MEALS.get(state.id);
		if (meal == null || meal[0] != 0.0F) {
			return -2.0F;
		}
		float since = state.ageInTicks - meal[1] - MEAL_FLICK_DELAY;
		if (since < 0.0F) {
			return -1.0F;
		}
		if (since >= MEAL_FLICKS * MEAL_FLICK_GAP) {
			return -2.0F;
		}
		float in = since % MEAL_FLICK_GAP;
		return in < FLICK_TICKS ? in / FLICK_TICKS : -1.0F;
	}

	/** How far through the lash it gives when put on by hand (0-1), or -1 if not now. */
	static float donLash(AvatarRenderState state) {
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		if (minecraft.level == null || !(minecraft.level.getEntity(state.id) instanceof net.minecraft.world.entity.player.Player player)) {
			return -1.0F;
		}
		Long donned = player.getAttached(com.jaymospowers.power.web.WebSymbiote.DONNED);
		if (donned == null) {
			return -1.0F;
		}
		float since = minecraft.level.getGameTime() - donned - DON_LASH_AT;
		return since >= 0.0F && since < DON_LASH_TICKS ? since / DON_LASH_TICKS : -1.0F;
	}

	/** How far out the tongue is in a flick {@code f} through (0-1): shot out fast, held a moment, drawn back in. */
	static float flickOut(float f) {
		if (f < 0.0F) {
			return 0.0F;
		}
		float out = f < FLICK_OUT ? f / FLICK_OUT : f > FLICK_BACK ? 1.0F - (f - FLICK_BACK) / (1.0F - FLICK_BACK) : 1.0F;
		out = Mth.clamp(out, 0.0F, 1.0F);
		return out * out * (3.0F - 2.0F * out);
	}

	/** What the big body's pieces were last moved to (so moving them twice in one frame moves them once). */
	private static final Map<ModelPart, float[]> MOVED = new WeakHashMap<>();

	/**
	 * Spreads a model out to the big body's frame, {@code k} of the way (0 none): the hips up {@code LEG_LIFT} and apart,
	 * the neck up along the torso (however it's bent) so the torso is {@code TORSO} long, the shoulders out with it, and
	 * the head forward of the traps in the torso's frame, its turn held in on the neck ({@link #neck}). Safe to call more
	 * than once a frame.
	 */
	static void shift(HumanoidModel<?> h, float k) {
		if (k <= 0.0F) {
			return;
		}
		// the torso's frame: lifting the neck along it keeps the shoulders and head on a bent torso
		org.joml.Quaternionf torso = new org.joml.Quaternionf().rotationZYX(h.body.zRot, h.body.yRot, h.body.xRot);
		org.joml.Vector3f along = torso.transform(new org.joml.Vector3f(0.0F, -(WebHulkMesh.BODY_LIFT - WebHulkMesh.LEG_LIFT), 0.0F));
		float nx = along.x * k, ny = (along.y - WebHulkMesh.LEG_LIFT) * k, nz = along.z * k;
		org.joml.Vector3f out = torso.transform(new org.joml.Vector3f(ARM_OUT, 0.0F, 0.0F));
		org.joml.Vector3f head = torso.transform(new org.joml.Vector3f(0.0F, HEAD_DOWN, -HEAD_FORWARD));
		move(h.body, nx, ny, nz);
		move(h.rightArm, nx - out.x * k, ny - out.y * k, nz - out.z * k);
		move(h.leftArm, nx + out.x * k, ny + out.y * k, nz + out.z * k);
		move(h.rightLeg, -LEG_OUT * k, -WebHulkMesh.LEG_LIFT * k, 0.0F);
		move(h.leftLeg, LEG_OUT * k, -WebHulkMesh.LEG_LIFT * k, 0.0F);
		move(h.head, nx + head.x * k, ny + head.y * k, nz + head.z * k);
		neck(h.head, h.body, k);
	}

	/** What the heads were last turned to (so turning one twice in a frame turns it once). */
	private static final Map<ModelPart, float[]> NECKED = new WeakHashMap<>();

	/**
	 * The head held on its neck, {@code k} of the way: its turn and roll limited, its pitch following the torso's a
	 * little and kept within {@code HEAD_BACK_MAX} back and {@code HEAD_FORWARD_MAX} forward of it, so the big skull can't
	 * swing into the shoulders or sink into the traps.
	 */
	private static void neck(ModelPart head, ModelPart body, float k) {
		float[] was = NECKED.get(head);
		if (was != null && was[0] == head.xRot && was[1] == head.yRot && was[2] == head.zRot) {
			return;
		}
		float yaw = Mth.lerp(k, Mth.PI, HEAD_YAW_MAX), roll = Mth.lerp(k, Mth.PI, HEAD_ROLL_MAX);
		head.yRot = Mth.clamp(head.yRot, -yaw, yaw);
		head.zRot = Mth.clamp(head.zRot, -roll, roll);
		float pitch = head.xRot + HEAD_FOLLOW * k * body.xRot;
		head.xRot = Mth.clamp(pitch, body.xRot - Mth.lerp(k, Mth.PI, HEAD_BACK_MAX), body.xRot + Mth.lerp(k, Mth.PI, HEAD_FORWARD_MAX));
		NECKED.put(head, new float[] {head.xRot, head.yRot, head.zRot});
	}

	/** What the suit's parts were last stretched to, and on which frame (so stretching one twice in a frame stretches it once). */
	private static final Map<ModelPart, float[]> STRETCHED = new WeakHashMap<>();

	/**
	 * Stretches a model laid under the big body (the suit, the goo) to its longer torso and legs, {@code k} of the way,
	 * after it's been laid (its scales set). Not the big body itself (its mesh is built long), nor the wearer's own
	 * (hidden inside it; its parts carry the layers drawn off them).
	 */
	static void stretch(HumanoidModel<?> h, float k, float frame) {
		if (k <= 0.0F || h instanceof WebHulk.Model) {
			return;
		}
		stretch(h.body, Mth.lerp(k, 1.0F, TORSO_STRETCH), frame);
		stretch(h.rightLeg, Mth.lerp(k, 1.0F, LEG_STRETCH), frame);
		stretch(h.leftLeg, Mth.lerp(k, 1.0F, LEG_STRETCH), frame);
	}

	private static void stretch(ModelPart part, float by, float frame) {
		float[] was = STRETCHED.get(part);
		if (was != null && was[0] == frame && was[1] == part.yScale) {
			return;
		}
		part.yScale *= by;
		STRETCHED.put(part, new float[] {frame, part.yScale});
	}

	private static void move(ModelPart part, float dx, float dy, float dz) {
		float[] was = MOVED.get(part);
		if (was != null && was[0] == part.x && was[1] == part.y && was[2] == part.z) {
			return;
		}
		part.x += dx;
		part.y += dy;
		part.z += dz;
		MOVED.put(part, new float[] {part.x, part.y, part.z});
	}

	/**
	 * The tongue: in the mouth, and every so often ({@link #flick}) flicked out between the jaws over the fangs, its tip
	 * fluttering, and drawn back in. Drawn off the head ({@code head}, already posed and moved to the big body's frame),
	 * {@code grown} of its length (it comes in with the head as the big body is built).
	 */
	static void submitTongue(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state, PlayerModel parent, float grown) {
		float flick = flick(state);
		float out = flickOut(flick) * grown;
		if (out <= 0.02F) {
			// in the mouth
			return;
		}
		float age = state.ageInTicks;
		float jawAngle = jaw(state);
		float whip = Mth.sin(flick * Mth.PI);
		poseStack.pushPose();
		parent.root().translateAndRotate(poseStack);
		parent.head.translateAndRotate(poseStack);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebTubes.TEXTURE), (pose, buffer) ->
			// out from between the jaws, riding down with the lower one (sliding back into the mouth as it's drawn in)
			tongue(buffer, pose, new Vec3(0.0, 0.9 + Mth.sin(jawAngle) * 2.0, -3.0 + (1.0F - out) * 3.0), 0.15F + jawAngle * 0.6F, TONGUE_PIECES,
				TONGUE_PIECE, TONGUE_WIDTH, TONGUE_THICK, age, out, whip, TONGUE_DROOP, state.lightCoords));
		poseStack.popPose();
	}

	/**
	 * The plain suit's tongue: it has none, except just after a meal, when it flicks out of the mouth a few times
	 * ({@link #mealFlick}) as the big body's does. Drawn off the head ({@code head}, already posed).
	 */
	static void submitSuitTongue(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state, PlayerModel parent) {
		float flick = mealFlick(state);
		float out = flickOut(flick);
		if (out <= 0.02F) {
			return;
		}
		float age = state.ageInTicks;
		float whip = Mth.sin(flick * Mth.PI);
		poseStack.pushPose();
		parent.root().translateAndRotate(poseStack);
		parent.head.translateAndRotate(poseStack);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebTubes.TEXTURE), (pose, buffer) ->
			// out of the mouth at the front of the head, sliding back in as it's drawn in
			tongue(buffer, pose, new Vec3(0.0, -1.6, -3.4 + (1.0F - out) * 1.5), 0.2F, SUIT_TONGUE_PIECES, SUIT_TONGUE_PIECE,
				SUIT_TONGUE_WIDTH, SUIT_TONGUE_THICK, age, out, whip, TONGUE_DROOP, state.lightCoords));
		poseStack.popPose();
	}

	/**
	 * Lays a tongue from {@code at} (model pixels: -Z ahead, +Y down) heading out at {@code pitch} (down from straight
	 * ahead): {@code pieces} pieces of {@code piece} pixels, each {@code out} of its length (0 drawn right in, 1 all the
	 * way out), drooping {@code droop} more each piece, a ripple running down it, and its tip whipping from side to side
	 * with {@code whip} (0-1). Shared with the maw's tongue (WebMouthLayer).
	 */
	static void tongue(VertexConsumer buffer, PoseStack.Pose pose, Vec3 at, float pitch, int pieces, float piece, float width, float thick,
		float age, float out, float whip, float droop, int light) {
		for (int i = 0; i < pieces; i++) {
			float f = i / (float)pieces;
			// straighter while it's thrust out, drooping more toward the tip, a ripple running down it
			pitch += droop * (1.0F - whip * 0.5F) + Mth.sin(age * 0.23F - i * 0.6F) * TONGUE_RIPPLE * f;
			float yaw = Mth.sin(age * 0.13F - i * 0.45F) * TONGUE_SWAY * (0.3F + f) * 0.5F
				+ Mth.sin(age * 1.9F - i * 0.7F) * TONGUE_FLUTTER * f * whip;
			Vec3 dir = new Vec3(Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), -Mth.cos(yaw) * Mth.cos(pitch));
			Vec3 next = at.add(dir.scale(piece * out));
			float w0 = width * (1.0F - 0.55F * f), w1 = width * (1.0F - 0.55F * (i + 1) / pieces);
			float t0 = thick * (1.0F - 0.4F * f), t1 = thick * (1.0F - 0.4F * (i + 1) / pieces);
			if (i == pieces - 1) {
				// a pointed tip
				w1 = 0.15F;
				t1 = 0.1F;
			}
			WebTubes.prism(buffer, pose, at.scale(1.0 / 16.0), next.scale(1.0 / 16.0), new Vec3(1.0, 0.0, 0.0), w0 / 16.0, t0 / 16.0, w1 / 16.0,
				t1 / 16.0, WebTubes.RED, light);
			at = next;
		}
	}
}

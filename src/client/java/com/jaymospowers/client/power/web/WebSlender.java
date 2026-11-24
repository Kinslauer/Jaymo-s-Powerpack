package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The red strain's own body (Andy, 2026-11-24: "place our mechanics tendrils blades and scripts onto the narrow taller
 * slender body"): where the black strain raises the big body ({@link WebHulk}), the red one draws itself up into a narrow,
 * taller frame, all sinew, a head of fangs on a long neck and loose strands whipping off every limb (the red form of Andy's
 * own Symbiote mod, {@link WebSlenderMesh}, {@code tools/gen_web_slender.py}). It is a body, not new abilities: it is up
 * whenever the big body would be ({@link WebSuitGrowth#slender}), our tendrils grow out of its back and
 * shoulders ({@link WebTendrilLayer}, each its own size: {@link #TENDRIL_REST}, among more thin strands: {@link #STRANDS}), the weapon arm forms on its hands ({@link WebMorphLayer})
 * and every pose and execution moves it, since it keeps the player model's joints: {@link #shift} moves them to its own
 * frame (the neck and the shoulders higher, the hips higher on longer legs) for every model drawn on that body. It flows on
 * the way the big body's goo does, the chest first and the head last, its forearms and shins bending at their own elbows
 * and knees ({@link WebHulk#limbs}), its jaw hanging and its tongue flicking on the big body's beat.
 */
final class WebSlender {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_slender.png");
	static final Identifier GLOW = JaymosPowers.id("textures/entity/web_symbiote_slender_glow.png");
	/** The original's parts are drawn this much of their size (its frame then stands {@link #HEIGHT} pixels tall, a player 32). */
	static final float SCALE = 0.6F;
	/**
	 * Our tendrils on it (Andy: "there should be a visible abundance of tendrils and they should differ in size thickness and
	 * length ... they should stay on the back/rear of the model and shoulders, never out the front"): each roots on the back
	 * or a shoulder (body space, pixels: x, y down from the neck, z back), rises out at its own turn ({@code yaw}: 0 straight
	 * back, ±π/2 straight out to the side, never further round) and lift ({@code pitch}, up; down if negative), every segment
	 * curling {@code curl} (back down if negative), and is {@code thick} as thick and {@code length} as long as the big
	 * body's: a crown of them arching up and over off the shoulders, fewer and thinner hanging off the small of the back,
	 * no two alike. Rows in WebTendrilLayer's order (right, left). {x, y, z, yaw, pitch, curl, thick, length}.
	 */
	static final float[][] TENDRIL_REST = {
		{-3.2F, 0.3F, 1.0F, -0.95F, 1.0F, -0.26F, 0.95F, 1.15F},
		{3.0F, 0.5F, 1.0F, 1.05F, 0.9F, -0.3F, 0.85F, 1.05F},
		{-1.4F, 0.8F, 2.0F, -0.3F, 1.3F, -0.2F, 0.8F, 1.35F},
		{1.6F, 1.0F, 2.0F, 0.45F, 1.15F, -0.24F, 0.7F, 1.2F},
		{-2.6F, 3.5F, 2.0F, -1.25F, 0.4F, -0.32F, 0.6F, 0.95F},
		{2.4F, 3.2F, 2.0F, 1.15F, 0.55F, -0.28F, 0.65F, 1.0F},
		{-1.8F, 7.0F, 2.0F, -0.65F, -0.5F, 0.16F, 0.5F, 0.85F},
		{1.5F, 7.4F, 2.0F, 0.5F, -0.65F, 0.12F, 0.45F, 0.75F},
	};
	/**
	 * On the move (its favourite way about, with climbing; Andy), the tendrils from {@code SKITTER_FIRST} on (the low ones off
	 * the small of the back) stop hanging and walk it: once the stride is {@code SKITTER_FROM} of a run, each plants its tip
	 * on the ground {@code SKITTER_WIDE} out to its side and {@code SKITTER_BACK} behind, strides {@code SKITTER_STRIDE}
	 * either way with the leg on its side ({@code SKITTER_LAG} of the cycle behind it), lifting {@code SKITTER_LIFT} as it
	 * swings through (pixels). The ground is {@code GROUND_BELOW} under the body's pivot (its neck) standing.
	 */
	static final int SKITTER_FIRST = 6;
	static final float SKITTER_FROM = 0.3F, SKITTER_WIDE = 5.0F, SKITTER_BACK = 7.0F, SKITTER_STRIDE = 6.0F, SKITTER_LAG = 0.9F, SKITTER_LIFT = 4.0F;
	/**
	 * More strands than tendrils: thin ones that do nothing but sway and curl, off the shoulders and the back between the
	 * tendrils (drawn with them, WebSlender.Strands). Same columns as {@link #TENDRIL_REST}, the thickness and length of a
	 * whole grown tendril's.
	 */
	static final float[][] STRANDS = {
		{-3.8F, 0.6F, 0.5F, -1.5F, 0.7F, -0.45F, 0.35F, 0.6F},
		{3.7F, 0.4F, 0.6F, 1.45F, 0.85F, -0.4F, 0.3F, 0.65F},
		{-2.9F, 1.6F, 1.6F, -1.1F, 0.25F, -0.5F, 0.3F, 0.5F},
		{3.0F, 1.8F, 1.6F, 1.2F, 0.15F, -0.55F, 0.25F, 0.45F},
		{-0.7F, 1.4F, 2.0F, -0.12F, 0.95F, -0.42F, 0.4F, 0.85F},
		{0.6F, 2.2F, 2.0F, 0.2F, 0.75F, -0.38F, 0.35F, 0.7F},
		{-1.2F, 4.6F, 2.0F, -0.8F, 0.6F, -0.35F, 0.3F, 0.7F},
		{1.3F, 5.0F, 2.0F, 0.95F, 0.45F, -0.4F, 0.28F, 0.6F},
		{-2.5F, 6.0F, 1.8F, -1.4F, -0.2F, 0.3F, 0.25F, 0.5F},
		{2.6F, 5.6F, 1.8F, 1.35F, -0.1F, 0.25F, 0.22F, 0.45F},
		{-0.5F, 8.5F, 2.0F, -0.3F, -0.9F, 0.2F, 0.3F, 0.75F},
		{0.7F, 9.0F, 2.0F, 0.4F, -1.0F, 0.25F, 0.25F, 0.65F},
		{-3.4F, 0.2F, 1.8F, -0.6F, 1.35F, -0.5F, 0.22F, 0.55F},
		{3.3F, 0.1F, 1.8F, 0.7F, 1.4F, -0.45F, 0.2F, 0.5F},
	};
	/**
	 * {@code rows} (columns as {@link #TENDRIL_REST}'s) for a bigger body: the roots {@code wide} times as far out and
	 * {@code deep} pixels further back, each {@code thick} times as thick and {@code length} times as long (the big body's
	 * crown is this one's, grown to its frame: WebHulk).
	 */
	static float[][] grown(float[][] rows, float wide, float deep, float thick, float length) {
		float[][] out = new float[rows.length][];
		for (int i = 0; i < rows.length; i++) {
			float[] r = rows[i].clone();
			r[0] *= wide;
			r[2] += deep;
			r[6] *= thick;
			r[7] *= length;
			out[i] = r;
		}
		return out;
	}

	/** The weapon arm's hand, this far down the arm from the shoulder (pixels), and the arm's half-width there. */
	static final float HAND_Y = 15.0F, ARM_HALF = 1.2F;
	/** Flowing on: the chest first, then the arms, the legs, and the head last (WebHulk's order, 0 to WebHulkMesh.LAST_ORDER). */
	static final int ORDER_BODY = 0, ORDER_ARMS = 2, ORDER_LEGS = 5, ORDER_HEAD = 8;

	/** Its frame (pixels, y down, the feet on 24): the neck, the hips and the shoulders, from the original's joints. */
	static final float NECK = WebSlenderMesh.GROUND + (WebSlenderMesh.NECK_Y - WebSlenderMesh.GROUND) * SCALE;
	static final float HIPS = WebSlenderMesh.GROUND + (WebSlenderMesh.HIP_Y - WebSlenderMesh.GROUND) * SCALE;
	static final float HEIGHT = WebSlenderMesh.GROUND - NECK + 15.0F * SCALE + 8.0F;
	/** How far its neck and hips sit above a player's, how far out its shoulders and hips sit (pixels; negative: in). */
	static final float BODY_LIFT = -NECK, LEG_LIFT = 12.0F - HIPS;
	static final float ARM_OUT = WebSlenderMesh.SHOULDER_X * SCALE - 5.0F, LEG_OUT = WebSlenderMesh.HIP_X * SCALE - 1.9F;
	/** The ground under the body's pivot (its neck), standing. */
	static final float GROUND_BELOW = WebSlenderMesh.GROUND - NECK;
	/** The suit and the goo under it stretch to its torso (neck to hips) and its legs. */
	static final float TORSO_STRETCH = (HIPS - NECK) / 12.0F, LEG_STRETCH = (WebSlenderMesh.GROUND - HIPS) / 12.0F;

	private WebSlender() {
	}

	/**
	 * Moves a model to the slender frame, {@code k} of the way (0 none): the hips up, the neck up along the torso (however
	 * it's bent), the shoulders with it. Safe to call more than once a frame (WebHulk#move).
	 */
	static void shift(HumanoidModel<?> h, float k) {
		if (k <= 0.0F) {
			return;
		}
		org.joml.Quaternionf torso = new org.joml.Quaternionf().rotationZYX(h.body.zRot, h.body.yRot, h.body.xRot);
		org.joml.Vector3f along = torso.transform(new org.joml.Vector3f(0.0F, -(BODY_LIFT - LEG_LIFT), 0.0F));
		float nx = along.x * k, ny = (along.y - LEG_LIFT) * k, nz = along.z * k;
		org.joml.Vector3f out = torso.transform(new org.joml.Vector3f(ARM_OUT, 0.0F, 0.0F));
		WebHulk.move(h.body, nx, ny, nz);
		WebHulk.move(h.rightArm, nx - out.x * k, ny - out.y * k, nz - out.z * k);
		WebHulk.move(h.leftArm, nx + out.x * k, ny + out.y * k, nz + out.z * k);
		WebHulk.move(h.rightLeg, -LEG_OUT * k, -LEG_LIFT * k, 0.0F);
		WebHulk.move(h.leftLeg, LEG_OUT * k, -LEG_LIFT * k, 0.0F);
		WebHulk.move(h.head, nx, ny, nz);
	}

	/**
	 * Its stance (Andy, from his reference of it crouched: "the idle pose needs more of an exaggerated hunch like the
	 * reference, and even more curled while using tendrils, retaining the slight ragdoll on movement"): it stands hunched
	 * right over, the back curled {@code HUNCH} forward from the hips (radians) and the head craned back up {@code HEAD_UP} of that to
	 * look ahead, the arms hanging forward {@code ARMS_FORWARD} and the thighs bent {@code THIGHS} (the knees fold with them,
	 * WebHulk#limbs); reared up out of it to {@code HUNCH_TENDRILS} (his later stills of it rearing, its tendrils high: see
	 * {@code REAR_LIFT}) while its tendrils strike, hold, stab or walk it, easing between the two over {@code CURL_TICKS}. On
	 * the move it bends only {@code HUNCH_MOVING} as far (the beast pose, WebFeral, already gallops bent over), and stays
	 * loose: the head and the arms lag and loll with the stride ({@code LOLL}, radians), the slight ragdoll it always had.
	 */
	static final float HUNCH = 0.7F, HUNCH_TENDRILS = -0.15F, HUNCH_MOVING = 0.3F, HEAD_UP = 0.85F, ARMS_FORWARD = 0.35F, THIGHS = 0.45F;
	/**
	 * Never stiff (Andy: "the arms and knees should bend too"): the elbows crooked {@code ELBOWS} more than the big body's
	 * (radians, the claws held out in front), the knees folding with the bent thighs (WebHulk#limbs folds a raised thigh's
	 * shin back twice as far), the whole of it sunk {@code CROUCH_DROP} pixels so the feet stay on the ground.
	 */
	static final float ELBOWS = 0.7F, CROUCH_DROP = 1.8F;
	/**
	 * Rearing (Andy's stills of it in the second film's last fight): while its tendrils work it straightens up out of the
	 * hunch, leaning back a little ({@code HUNCH_TENDRILS} is negative), and the tendrils not striking rise
	 * {@code REAR_LIFT} higher (radians) and curl {@code REAR_CURL} further, reaching up and over it like claws.
	 */
	static final float REAR_LIFT = 0.6F, REAR_CURL = -0.15F;

	/**
	 * A body's stance (WebSlender#hunch): how far bent from the hips standing, with its tendrils at work and on the move
	 * (a share), the head craned back up (a share of the bend), the arms forward, the thighs raised, how far it's sunk on its
	 * knees (pixels), its elbows crooked, and its legs spread apart (radians).
	 */
	record Stance(float hunch, float working, float moving, float headUp, float armsForward, float thighs, float drop, float elbows, float apart) {
	}

	/** The slender body's: hunched right over, rearing up when its tendrils work. */
	static final Stance STANCE = new Stance(HUNCH, HUNCH_TENDRILS, HUNCH_MOVING, HEAD_UP, ARMS_FORWARD, THIGHS, CROUCH_DROP, ELBOWS, 0.0F);
	static final float CURL_TICKS = 6.0F, LOLL = 0.12F;
	/**
	 * The pounce (Andy's reference: the two strains' last fight in the second film; only its feel is taken): off the ground
	 * (its hunt's lunge, a leap, a drop) it springs out of the hunch, the back arching to {@code POUNCE_ARCH} of it, the claws
	 * thrown forward {@code POUNCE_ARMS} and wide {@code POUNCE_SPREAD} (radians), the legs tucked up {@code POUNCE_TUCK}, and
	 * its tendrils flare out behind it, {@code FLARE_WIDE} wider and lifted {@code FLARE_LIFT} (WebTendrilLayer), easing in
	 * and out over {@code AIR_TICKS}.
	 */
	static final float POUNCE_ARCH = 0.3F, POUNCE_ARMS = 1.5F, POUNCE_SPREAD = 0.45F, POUNCE_TUCK = 0.8F, FLARE_WIDE = 0.5F, FLARE_LIFT = 0.35F,
		AIR_TICKS = 4.0F;
	/** On a red-strain body's render state: how far into the pounce it is (0-1), or null on the ground. */
	public static final net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey<Float> AIR =
		net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey.create(() -> "jaymospowers:web_slender_air");
	private static final WebEase AIRBORNE = new WebEase();
	/** A pounce lasts at most this long off the ground (ticks); a longer flight (a swing, a glide, a fall) isn't one. */
	static final int POUNCE_MAX_TICKS = 14;
	/** Entity id → the tick it left the ground. */
	private static final java.util.Map<Integer, Integer> AIR_SINCE = new java.util.HashMap<>();

	/** From LivingEntityRenderer.extractRenderState (WebHostBody): how far a red-strain body is into a pounce. */
	static @org.jspecify.annotations.Nullable Float air(net.minecraft.world.entity.LivingEntity entity) {
		if (com.jaymospowers.power.web.WebSymbioteStrains.of(entity) != com.jaymospowers.power.web.WebSymbioteStrain.RED) {
			return null;
		}
		boolean up = !entity.onGround() && !entity.isInWater() && !entity.isPassenger() && !entity.onClimbable() && !entity.isFallFlying()
			&& !(entity instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().flying);
		// (a spring, not a whole swing, glide or fall: only the first POUNCE_MAX_TICKS of being off the ground)
		if (AIR_SINCE.size() > 256) {
			AIR_SINCE.clear();
		}
		if (!up) {
			AIR_SINCE.remove(entity.getId());
		} else {
			int since = AIR_SINCE.computeIfAbsent(entity.getId(), id -> entity.tickCount);
			up = entity.tickCount - since < POUNCE_MAX_TICKS;
		}
		float a = AIRBORNE.to(entity.getId(), up ? 1.0F : 0.0F, AIR_TICKS);
		return a > 0.0F ? a : null;
	}

	/** Entity id → how far curled over for its tendrils (0-1). */
	private static final WebEase CURL = new WebEase();
	/** What each body was last hunched to (so hunching it twice in a frame hunches it once). */
	private static final java.util.Map<ModelPart, Float> HUNCHED = new java.util.WeakHashMap<>();
	/** Where each body's legs were last sunk to. */
	private static final java.util.Map<ModelPart, Float> SUNK = new java.util.WeakHashMap<>();

	/** Hunches a model drawn for a body in its stance, {@code k} of the way (before the frame shift, so the shoulders and neck follow). */
	static void hunch(HumanoidModel<?> h, AvatarRenderState s, float k, Stance st) {
		if (k <= 0.0F || s.isPassenger || s.isFallFlying || s.swimAmount > 0.0F) {
			return;
		}
		// (once a pose: the layers that pose the body again (WebFeral...) reset the upper body but not the legs, so each half
		// has its own guard; the legs are done once sunk, and stay done once the frame shift has moved them on)
		Float was = HUNCHED.get(h.body);
		boolean upper = was == null || was != h.body.xRot;
		Float sunk = SUNK.get(h.rightLeg);
		boolean legs = (sunk == null || sunk != h.rightLeg.y) && !WebHulk.moved(h.rightLeg);
		if (!upper && !legs) {
			return;
		}
		if (st == WebHulk.STANCE) {
			// (the big body's beast pose crouches it already with its tendrils out, WebFeral: this is the upright brute's)
			k *= WebFeral.uprightness(s);
			if (k <= 0.0F) {
				return;
			}
		}
		WebSymbioteClient.Tendrils tendrils = s.getData(WebSymbioteClient.TENDRILS);
		boolean working = tendrils != null && (tendrils.strike() >= 0.0F || tendrils.combo() >= 0 || tendrils.held() != 0 || tendrils.legs() != null
			|| tendrils.tethers() != null);
		float curl = CURL.to(s.id, working ? 1.0F : 0.0F, CURL_TICKS);
		float speed = Mth.clamp(s.walkAnimationSpeed, 0.0F, 1.0F);
		Float airborne = s.getData(AIR);
		float air = airborne == null || working ? 0.0F : airborne;
		float bend = Mth.lerp(curl, st.hunch(), st.working()) * Mth.lerp(speed, 1.0F, st.moving()) * Mth.lerp(air, 1.0F, POUNCE_ARCH) * k;
		float loll = Mth.sin(s.walkAnimationPos * 0.6662F - 0.8F) * LOLL * speed * k;
		// the back curled over from the hips (the head and arms carried with it, PoseBlend.bend), the head craned up again,
		// the arms let hang (straight down, a little forward) and lolling with the stride
		if (upper) {
			com.jaymospowers.client.PoseBlend.bend(h, bend, 0.0F, 0.0F, st.drop() * k, 0.0F);
			h.head.xRot -= bend * st.headUp() - loll * 0.5F;
			h.head.zRot += loll * 0.6F;
			h.rightArm.xRot -= bend + st.armsForward() * k - loll;
			h.leftArm.xRot -= bend + st.armsForward() * k + loll;
			h.rightArm.zRot += loll * 0.5F;
			h.leftArm.zRot += loll * 0.5F;
			if (air > 0.0F) {
				// pouncing: claws thrown forward and wide
				h.rightArm.xRot -= POUNCE_ARMS * air * k;
				h.leftArm.xRot -= POUNCE_ARMS * air * k;
				h.rightArm.zRot += POUNCE_SPREAD * air * k;
				h.leftArm.zRot -= POUNCE_SPREAD * air * k;
			}
			HUNCHED.put(h.body, h.body.xRot);
		}
		if (legs) {
			h.rightLeg.xRot -= (st.thighs() + POUNCE_TUCK * air) * k;
			h.leftLeg.xRot -= (st.thighs() + POUNCE_TUCK * air) * k;
			h.rightLeg.zRot += st.apart() * k;
			h.leftLeg.zRot -= st.apart() * k;
			// (sunk on its bent knees)
			h.rightLeg.y += st.drop() * k;
			h.leftLeg.y += st.drop() * k;
			SUNK.put(h.rightLeg, h.rightLeg.y);
		}
	}

	/** Stretches a model laid under it (the suit, the goo) to its torso and legs, {@code k} of the way (not the body itself). */
	static void stretch(HumanoidModel<?> h, float k, float frame) {
		if (k <= 0.0F || h instanceof Model) {
			return;
		}
		WebHulk.stretch(h.body, Mth.lerp(k, 1.0F, TORSO_STRETCH), frame);
		WebHulk.stretch(h.rightLeg, Mth.lerp(k, 1.0F, LEG_STRETCH), frame);
		WebHulk.stretch(h.leftLeg, Mth.lerp(k, 1.0F, LEG_STRETCH), frame);
	}

	/**
	 * Thin strands ({@link #STRANDS} on this body, WebHulk's on the big body), drawn off the body as the tendrils are (WebTendrilLayer, the same mesh and
	 * texture): grown in with the tendrils, swaying and rippling, streaming back as it runs. They never strike or hold.
	 */
	static final class Strands extends net.minecraft.client.model.Model<AvatarRenderState> {
		/** How fast they sway, and how far (radians), and how hard each segment ripples. */
		static final float SWAY_RATE = 0.09F, SWAY = 0.22F, RIPPLE = 0.16F;
		private final float[][] rows;
		private final ModelPart[][] parts;

		/** The strands of {@code rows} (columns as {@link #TENDRIL_REST}'s): the slender body's {@link #STRANDS}, or the big body's. */
		Strands(float[][] rows) {
			super(mesh(rows).bakeRoot(), net.minecraft.client.renderer.rendertype.RenderTypes::entityCutout);
			this.rows = rows;
			this.parts = new ModelPart[rows.length][WebTendrilLayer.SEGMENTS];
			for (int t = 0; t < rows.length; t++) {
				ModelPart part = this.root().getChild("strand" + t);
				this.parts[t][0] = part;
				for (int k = 1; k < WebTendrilLayer.SEGMENTS; k++) {
					part = part.getChild("s" + k);
					this.parts[t][k] = part;
				}
			}
		}

		/** WebTendrilLayer's tendril mesh, once for each strand. */
		private static net.minecraft.client.model.geom.builders.LayerDefinition mesh(float[][] rows) {
			net.minecraft.client.model.geom.builders.MeshDefinition mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
			net.minecraft.client.model.geom.builders.PartDefinition root = mesh.getRoot();
			int segments = WebTendrilLayer.SEGMENTS, tips = WebTendrilLayer.TIP.length;
			float length = WebTendrilLayer.SEGMENT_LENGTH;
			for (int t = 0; t < rows.length; t++) {
				net.minecraft.client.model.geom.builders.PartDefinition parent = root.addOrReplaceChild("strand" + t,
					net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, length),
					net.minecraft.client.model.geom.PartPose.offset(rows[t][0], rows[t][1], rows[t][2]));
				for (int k = 1; k < segments; k++) {
					float thick = k < segments - tips ? 3.0F : WebTendrilLayer.TIP[k - (segments - tips)];
					parent = parent.addOrReplaceChild("s" + k, net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(0, (k % 2) * 8)
						.addBox(-thick / 2.0F, -thick / 2.0F, 0.0F, thick, thick, length), net.minecraft.client.model.geom.PartPose.offset(0.0F, 0.0F, length - 0.4F));
				}
			}
			return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 32, 32);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			WebSymbioteClient.Tendrils tendrils = state.getData(WebSymbioteClient.TENDRILS);
			float growth = tendrils == null ? 0.0F : tendrils.growth();
			float time = tendrils == null ? state.ageInTicks : tendrils.time();
			// grown in as the tendrils are (WebTendrilLayer: 0.3 to 1.3 thick, nothing to 1.3 long)
			float eased = growth * growth * (3.0F - 2.0F * growth);
			float thick = Mth.lerp(Mth.sqrt(growth), 0.3F, 1.3F), length = Mth.lerp(eased, 0.04F, 1.3F);
			// running, they stream back
			float drag = Mth.clamp(state.walkAnimationSpeed / 0.4F, 0.0F, 1.0F);
			for (int t = 0; t < this.rows.length; t++) {
				float[] r = this.rows[t];
				ModelPart base = this.parts[t][0];
				base.visible = growth > 0.02F;
				float sway = Mth.sin(time * SWAY_RATE + t * 1.7F) * SWAY;
				base.yRot = r[3] * (1.0F - 0.4F * drag) + sway * 0.5F;
				base.xRot = r[4] * (1.0F - 0.6F * drag) + sway;
				base.xScale = thick * r[6];
				base.yScale = thick * r[6];
				base.zScale = length * r[7];
				for (int k = 1; k < WebTendrilLayer.SEGMENTS; k++) {
					float wave = Mth.sin(time * (0.14F + 0.2F * drag) + t * 0.9F - k * 0.8F) * (0.15F + 0.06F * k);
					this.parts[t][k].xRot = Mth.lerp(drag, r[5], 0.1F) + wave * RIPPLE * 3.0F;
					this.parts[t][k].yRot = wave * RIPPLE * 2.0F;
				}
			}
		}
	}

	/** The slender body: the player model's joints, each holding the original's parts for it ({@link WebSlenderMesh}). */
	static final class Model extends PlayerModel {
		/** The holders under the head, body, arms and legs, and when each flows on. */
		private final ModelPart[] holders;
		private final int[] order = {ORDER_HEAD, ORDER_BODY, ORDER_ARMS, ORDER_ARMS, ORDER_LEGS, ORDER_LEGS};
		private final ModelPart elbowRight, elbowLeft, kneeRight, kneeLeft;
		private final ModelPart jaw;
		/** Each hand's fingers and thumb (right, left): they sink away when that arm becomes the weapon. */
		private final ModelPart[][] fingers = new ModelPart[2][5];
		private final ModelPart[] tongue = new ModelPart[3];

		Model() {
			super(WebSlenderMesh.create().bakeRoot(), false);
			this.holders = new ModelPart[] {this.head.getChild("slender"), this.body.getChild("slender"), this.rightArm.getChild("slender"),
				this.leftArm.getChild("slender"), this.rightLeg.getChild("slender"), this.leftLeg.getChild("slender")};
			this.elbowRight = this.holders[2].getChild("right_arm").getChild("right_forearm");
			this.elbowLeft = this.holders[3].getChild("left_arm").getChild("left_forearm");
			this.kneeRight = this.holders[4].getChild("right_leg").getChild("right_shin");
			this.kneeLeft = this.holders[5].getChild("left_leg").getChild("left_shin");
			this.jaw = this.holders[0].getChild("head").getChild("jaw");
			for (int f = 0; f < 4; f++) {
				this.fingers[0][f] = this.elbowRight.getChild("r_fin" + f);
				this.fingers[1][f] = this.elbowLeft.getChild("l_fin" + f);
			}
			this.fingers[0][4] = this.elbowRight.getChild("r_thumb");
			this.fingers[1][4] = this.elbowLeft.getChild("l_thumb");
			ModelPart segment = this.jaw;
			for (int i = 0; i < this.tongue.length; i++) {
				segment = segment.getChild("tongue" + i);
				this.tongue[i] = segment;
			}
		}

		/**
		 * Lays it on: {@code g} how far it's built, {@code hold} how much still holds you as you fight a takeover
		 * (WebSuitGrowth.peeledHulk), {@code k} how far the joints are in its frame. Each joint's parts flow on in their
		 * turn (grown from nothing to full size); until the frame is its own, the torso and legs are squashed to the wearer's.
		 */
		void build(float g, float hold, float k) {
			float flowed = WebHulk.flowed(g, hold);
			for (int i = 0; i < this.holders.length; i++) {
				float f = WebHulk.flow(this.order[i], flowed);
				ModelPart holder = this.holders[i];
				holder.visible = f > 0.02F;
				holder.xScale = SCALE * f;
				holder.yScale = SCALE * f;
				holder.zScale = SCALE * f;
			}
			this.body.yScale = Mth.lerp(k, 1.0F / TORSO_STRETCH, 1.0F);
			this.rightLeg.yScale = Mth.lerp(k, 1.0F / LEG_STRETCH, 1.0F);
			this.leftLeg.yScale = this.rightLeg.yScale;
		}

		/** The jaw hangs and breathes as the big body's does, the tongue flicks out on its beat, and the elbows and knees bend. */
		void animate(AvatarRenderState state, float g) {
			float jawAngle = WebHulk.jaw(state);
			this.jaw.xRot = jawAngle;
			boolean maw = state.getData(WebSymbioteClient.EATING) != null || state.getData(WebSymbioteClient.COUNTER_MAW) != null
				|| state.getData(WebSymbioteClient.ROAR) != null;
			float flick = WebHulk.flick(state);
			float out = maw ? 0.0F : WebHulk.flickOut(flick) * Mth.clamp((g - 0.85F) / 0.15F, 0.0F, 1.0F);
			this.tongue[0].visible = out > 0.02F;
			if (out > 0.02F) {
				this.tongue[0].zScale = out;
				this.tongue[0].xRot = WebBigHead.TONGUE_REST - jawAngle * WebBigHead.TONGUE_LEAD;
				float whip = Mth.sin(flick * Mth.PI);
				for (int i = 1; i < this.tongue.length; i++) {
					float f = i / (float)(this.tongue.length - 1);
					this.tongue[i].xRot = WebHulk.TONGUE_DROOP * (1.0F - whip * 0.5F) + Mth.sin(state.ageInTicks * 0.23F - i * 0.6F) * WebHulk.TONGUE_RIPPLE * f;
					this.tongue[i].yRot = Mth.sin(state.ageInTicks * 1.9F - i * 0.7F) * WebHulk.TONGUE_FLUTTER * f * whip;
				}
			}
			WebHulk.limbs(state, this, this.elbowRight, this.elbowLeft, this.kneeRight, this.kneeLeft);
			// the weapon arm (WebMorphLayer) isn't held in a hand: the arm becomes it (Andy's concept-art reference of the two
			// strains' fight), so that hand's fingers sink away into the forearm the blade grows out of, its elbow held straight
			WebMorphLayer.Strike strike = state.getData(WebMorphLayer.KEY);
			WebMorphLayer.Gather gather = state.getData(WebMorphLayer.GATHER);
			int side = strike != null && !strike.tendril() ? strike.side() : gather != null && !gather.tendril() ? gather.side() : 0;
			for (int hand = 0; hand < 2; hand++) {
				boolean weapon = hand == 0 ? side > 0 : side < 0;
				for (ModelPart finger : this.fingers[hand]) {
					finger.visible = !weapon;
				}
				// (crooked further otherwise: never stiff, ELBOWS)
				if (!weapon) {
					(hand == 0 ? this.elbowRight : this.elbowLeft).xRot -= ELBOWS;
				}
			}
		}
	}
}

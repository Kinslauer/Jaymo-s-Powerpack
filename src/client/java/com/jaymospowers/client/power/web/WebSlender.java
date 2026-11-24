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
	 * The thin strands ({@link #STRANDS}), drawn off the body as the tendrils are (WebTendrilLayer, the same mesh and
	 * texture): grown in with the tendrils, swaying and rippling, streaming back as it runs. They never strike or hold.
	 */
	static final class Strands extends net.minecraft.client.model.Model<AvatarRenderState> {
		/** How fast they sway, and how far (radians), and how hard each segment ripples. */
		static final float SWAY_RATE = 0.09F, SWAY = 0.22F, RIPPLE = 0.16F;
		private final ModelPart[][] parts = new ModelPart[STRANDS.length][WebTendrilLayer.SEGMENTS];

		Strands() {
			super(mesh().bakeRoot(), net.minecraft.client.renderer.rendertype.RenderTypes::entityCutout);
			for (int t = 0; t < STRANDS.length; t++) {
				ModelPart part = this.root().getChild("strand" + t);
				this.parts[t][0] = part;
				for (int k = 1; k < WebTendrilLayer.SEGMENTS; k++) {
					part = part.getChild("s" + k);
					this.parts[t][k] = part;
				}
			}
		}

		/** WebTendrilLayer's tendril mesh, once for each strand. */
		private static net.minecraft.client.model.geom.builders.LayerDefinition mesh() {
			net.minecraft.client.model.geom.builders.MeshDefinition mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
			net.minecraft.client.model.geom.builders.PartDefinition root = mesh.getRoot();
			int segments = WebTendrilLayer.SEGMENTS, tips = WebTendrilLayer.TIP.length;
			float length = WebTendrilLayer.SEGMENT_LENGTH;
			for (int t = 0; t < STRANDS.length; t++) {
				net.minecraft.client.model.geom.builders.PartDefinition parent = root.addOrReplaceChild("strand" + t,
					net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, length),
					net.minecraft.client.model.geom.PartPose.offset(STRANDS[t][0], STRANDS[t][1], STRANDS[t][2]));
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
			for (int t = 0; t < STRANDS.length; t++) {
				float[] r = STRANDS[t];
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
		}
	}
}

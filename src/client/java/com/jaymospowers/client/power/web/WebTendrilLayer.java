package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebSymbiote;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Living Armor's tendrils: thick black cords growing out of the wearer's back and shoulders, arching up and out and
 * swaying on their own. The longer the armour is up, the thicker and longer they get (to {@link #GROWN_TICKS}); every
 * swing of the wearer's arm, they whip forward over the shoulders. Everyone sees them.
 *
 * <p>A strike is a slam (reared high over the head, then crashing straight down onto the target) or a swipe (drawn out
 * wide to one side, then swept across through the target), as the server picked it (see {@link WebSymbiote.Strike});
 * swinging held debris it's slower and reaches much further. In first person (your own body drawn by First-person
 * Model, or {@link #submitFirstPerson} next to the hands without it) they reach forward over the shoulders into view.
 */
final class WebTendrilLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_tendril.png");
	/** Ticks of Living Armor until the tendrils are fully grown. */
	static final float GROWN_TICKS = 80.0F;
	/** Segments per tendril, and each one's length (model pixels). */
	private static final int SEGMENTS = 7;
	private static final float SEGMENT_LENGTH = 4.5F;
	/** Where each tendril roots (model space: +Y down, +Z back), its outward lean and its upward tilt (radians). */
	private static final float[][] ROOTS = {
		{-3.0F, 1.5F, 2.0F, -0.6F, -1.0F},
		{3.0F, 1.5F, 2.0F, 0.6F, -1.0F},
		{-1.5F, 0.5F, 2.0F, -0.22F, -1.35F},
		{1.5F, 0.5F, 2.0F, 0.22F, -1.35F},
		{-3.5F, 4.5F, 2.0F, -1.0F, -0.55F},
		{3.5F, 4.5F, 2.0F, 1.0F, -0.55F},
		{-2.0F, 8.0F, 2.0F, -1.25F, -0.15F},
		{2.0F, 8.0F, 2.0F, 1.25F, -0.15F},
	};
	/** Roots higher than this (model y) are the upper ones: the strikers, and the ones seen in first person. */
	private static final float UPPER_ROOT_Y = 6.0F;

	// ---- strikes ----
	/** A slam: reared to this elevation (radians; past straight up, so it's over the head), and it comes down this far below the target. */
	private static final float SLAM_HIGH = 1.95F, SLAM_DIP = 0.25F;
	/** A swipe: drawn out this far to its side (radians of yaw past the target), a little above it. */
	private static final float SWIPE_WIDE = 1.4F, SWIPE_LIFT = 0.15F;
	/** After the hit it follows through this much further (share of the swing), then eases back to rest. */
	private static final float FOLLOW_THROUGH = 0.35F;
	/** Each segment's curl: reared (back over), striking (whipping down), and a swipe's trailing bend. */
	private static final float CURL_REARED = 0.15F, CURL_STRIKE = -0.12F, CURL_SWIPE = 0.1F;
	/** How much longer a striking tendril gets: a plain lash, swinging debris, and extra from your own eyes. */
	private static final float STRETCH = 0.35F, STRETCH_HEAVY = 0.9F, STRETCH_FIRST_PERSON = 0.4F;

	// ---- first person ----
	/** Your own upper tendrils: out to the side this far (yaw), rising this steeply, curling forward and inward into view. */
	private static final float FP_YAW = 1.1F, FP_PITCH = 0.95F, FP_CURL_DOWN = -0.22F, FP_CURL_IN = 0.12F;
	/** They reach this much further than in third person, and sway this much at the edges of the screen. */
	private static final float FP_LENGTH = 1.35F, FP_SWAY = 0.12F;
	/** Without First-person Model: where they root (camera space: right, up, back, in blocks) and how big they're drawn. */
	private static final float FP_ROOT_UP = -0.28F, FP_ROOT_BACK = 0.05F, FP_SCALE = 0.6F;

	private final TendrilModel model;
	private static TendrilModel firstPersonModel;

	WebTendrilLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		this.model = new TendrilModel(createLayer().bakeRoot());
	}

	private static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int t = 0; t < ROOTS.length; t++) {
			float[] r = ROOTS[t];
			PartDefinition parent = root.addOrReplaceChild("tendril" + t, CubeListBuilder.create().texOffs(0, 0)
				.addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, SEGMENT_LENGTH), PartPose.offset(r[0], r[1], r[2]));
			for (int k = 1; k < SEGMENTS; k++) {
				parent = parent.addOrReplaceChild("s" + k, CubeListBuilder.create().texOffs(0, (k % 2) * 8)
					.addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, SEGMENT_LENGTH), PartPose.offset(0.0F, 0.0F, SEGMENT_LENGTH - 0.4F));
			}
		}
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		WebSymbioteClient.Tendrils tendrils = state.getData(WebSymbioteClient.TENDRILS);
		if (tendrils == null || state.isInvisible) {
			return;
		}
		poseStack.pushPose();
		// rooted to the torso, so they lean with a crouch (or the hunch)
		WebFeral.applyModel(this.getParentModel(), state);
		this.getParentModel().body.translateAndRotate(poseStack);
		collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY,
			state.outlineColor);
		poseStack.popPose();
	}

	/**
	 * Without First-person Model: your own tendrils drawn from your eyes, after the hands (camera space), rising from
	 * behind your shoulders and curling in at the edges of the screen; strikes and grabs reach out in front.
	 */
	static void submitFirstPerson(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState avatar) {
		WebSymbioteClient.Tendrils tendrils = avatar.getData(WebSymbioteClient.TENDRILS);
		if (tendrils == null || avatar.isInvisible || FirstPersonCompat.bodyShown()) {
			return;
		}
		if (firstPersonModel == null) {
			firstPersonModel = new TendrilModel(createLayer().bakeRoot());
		}
		// the strike's aim is worked out against the body; here everything is against the view
		float yaw = tendrils.aimYaw() - avatar.yRot * Mth.DEG_TO_RAD;
		float pitch = tendrils.aimPitch() + avatar.xRot * Mth.DEG_TO_RAD;
		float wallYaw = tendrils.wallYaw() - avatar.yRot * Mth.DEG_TO_RAD;
		float wallPitch = tendrils.wallPitch() + avatar.xRot * Mth.DEG_TO_RAD;
		AvatarRenderState own = new AvatarRenderState();
		own.setData(WebSymbioteClient.TENDRILS, new WebSymbioteClient.Tendrils(tendrils.growth(), tendrils.time(), tendrils.strike(), yaw, pitch,
			yaw >= 0.0F ? -1.0F : 1.0F, 0.0F, tendrils.held(), tendrils.armor(), tendrils.wall(), wallYaw, wallPitch, tendrils.heavy(),
			tendrils.variant(), true));
		poseStack.pushPose();
		poseStack.translate(0.0F, FP_ROOT_UP, FP_ROOT_BACK);
		// camera space to model space: model -Z is ahead, -Y up, +X the wearer's left
		poseStack.scale(-FP_SCALE, -FP_SCALE, FP_SCALE);
		collector.submitModel(firstPersonModel, own, poseStack, RenderTypes.entityCutout(TEXTURE), avatar.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	/** Eases 0-1. */
	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/**
	 * The base yaw that points a tendril {@code yaw} radians off straight ahead (+ to the wearer's right, as the strike's
	 * aim is measured), taken the short way round from {@code rest} (a right-hand tendril, rest below 0, doesn't swing
	 * round the left of the body). With it, the base's xRot is simply the elevation (+ up): the cords are built
	 * pointing back (+Z), and model space has +Y down and +X on the wearer's left.
	 */
	private static float aimYaw(float yaw, float rest) {
		float aim = Mth.PI + yaw;
		if (rest < 0.0F && aim > 0.0F) {
			aim -= Mth.TWO_PI;
		}
		return aim;
	}

	/** The tendrils' pose, worked out per wearer at draw time. */
	private static final class TendrilModel extends Model<AvatarRenderState> {
		private final ModelPart[][] parts = new ModelPart[ROOTS.length][SEGMENTS];

		TendrilModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			for (int t = 0; t < ROOTS.length; t++) {
				ModelPart part = root.getChild("tendril" + t);
				this.parts[t][0] = part;
				for (int k = 1; k < SEGMENTS; k++) {
					part = part.getChild("s" + k);
					this.parts[t][k] = part;
				}
			}
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			WebSymbioteClient.Tendrils tendrils = state.getData(WebSymbioteClient.TENDRILS);
			if (tendrils == null) {
				return;
			}
			float growth = tendrils.growth();
			float time = tendrils.time();
			boolean fp = tendrils.firstPerson();
			// they bud from tiny pods on the back and grow out to their full thickness and length
			float eased = growth * growth * (3.0F - 2.0F * growth);
			float thick = Mth.lerp(Mth.sqrt(growth), 0.3F, 1.3F);
			float length = Mth.lerp(eased, 0.04F, 1.3F);

			// the strike's swing (the same for every striker)
			float strike = tendrils.strike();
			boolean heavy = tendrils.heavy();
			int variant = tendrils.variant();
			float rearShare = heavy ? WebSymbiote.HEAVY_STRIKE_REAR : WebSymbiote.STRIKE_REAR;
			float hitShare = heavy ? WebSymbiote.HEAVY_STRIKE_HIT / (float)WebSymbiote.HEAVY_STRIKE_TICKS
				: WebSymbiote.STRIKE_HIT / (float)WebSymbiote.STRIKE_TICKS;
			float weight = 0.0F, swing = 0.0F;
			if (strike >= 0.0F) {
				if (strike < rearShare) {
					// rearing (or drawing out to the side)
					weight = smooth(strike / rearShare);
				} else if (strike < hitShare) {
					// coming in, faster and faster: lands at the hit
					weight = 1.0F;
					float k = (strike - rearShare) / (hitShare - rearShare);
					swing = k * k;
				} else {
					// following through, then easing back to rest
					float f = (strike - hitShare) / (1.0F - hitShare);
					weight = 1.0F - smooth(f * 1.4F - 0.4F);
					swing = 1.0F + FOLLOW_THROUGH * Math.min(1.0F, f * 3.0F);
				}
			}
			float strikeYaw, strikePitch, curlX, curlY;
			float sweepSide = variant == WebSymbiote.STRIKE_SWIPE_LEFT ? 1.0F : -1.0F;
			if (variant == WebSymbiote.STRIKE_SLAM) {
				strikeYaw = tendrils.aimYaw();
				strikePitch = Mth.lerp(swing, SLAM_HIGH, tendrils.aimPitch() - SLAM_DIP);
				curlX = Mth.lerp(Math.min(1.0F, swing), CURL_REARED, CURL_STRIKE);
				curlY = 0.0F;
			} else {
				strikeYaw = tendrils.aimYaw() - sweepSide * SWIPE_WIDE * (1.0F - swing);
				strikePitch = tendrils.aimPitch() + SWIPE_LIFT;
				curlX = CURL_STRIKE * 0.5F;
				// the cord trails behind the sweep
				curlY = -sweepSide * CURL_SWIPE * (swing > 1.0F ? -1.0F : 1.0F);
			}
			// a slam strikes with the tendrils on the target's side; a swipe with those on the side it sweeps from
			float strikers = variant == WebSymbiote.STRIKE_SLAM ? tendrils.side() : sweepSide;
			float stretch = 1.0F + (heavy ? STRETCH_HEAVY : STRETCH) * weight * Math.min(1.0F, swing) + (fp ? STRETCH_FIRST_PERSON * weight : 0.0F);

			for (int t = 0; t < ROOTS.length; t++) {
				float[] r = ROOTS[t];
				boolean upper = r[1] < UPPER_ROOT_Y;
				float side = Math.signum(r[0]);
				ModelPart base = this.parts[t][0];
				// the top two (right, left) are the ones that reach out and hold: while holding, they're drawn reaching
				base.visible = !(t == 0 && (tendrils.held() & 1) != 0 || t == 1 && (tendrils.held() & 2) != 0);
				float sway = Mth.sin(time * 0.07F + t * 1.3F) * 0.2F;
				float restYaw = r[3] + sway * 0.5F;
				float restPitch = r[4] + sway;
				float restCurlX = 0.28F;
				float restCurlY = 0.0F;
				float restLength = length;
				if (fp && upper) {
					// your own, from your eyes: forward over the shoulders, curling in at the edges of the view
					float fpSway = Mth.sin(time * 0.09F + t * 1.7F) * FP_SWAY;
					restYaw = aimYaw(-side * (FP_YAW + fpSway), side);
					restPitch = FP_PITCH + fpSway * 0.5F;
					restCurlX = FP_CURL_DOWN;
					restCurlY = side * FP_CURL_IN;
					restLength = length * FP_LENGTH;
				}
				boolean striking = weight > 0.0F && upper && side == strikers;
				float w = striking ? weight : 0.0F;
				base.yRot = Mth.lerp(w, restYaw, aimYaw(strikeYaw, restYaw));
				base.xRot = Mth.lerp(w, restPitch, strikePitch);
				base.xScale = thick;
				base.yScale = thick;
				base.zScale = restLength * (striking ? stretch : 1.0F);
				float segCurlX = Mth.lerp(w, restCurlX, curlX);
				float segCurlY = Mth.lerp(w, restCurlY, curlY);
				float calm = 1.0F - w;
				if (tendrils.wall() == 1) {
					// every one of them reaching for the wall, spread a little
					calm = 0.0F;
					segCurlX = CURL_STRIKE * 0.3F;
					segCurlY = 0.0F;
					base.yRot = aimYaw(tendrils.wallYaw() - r[3] * 0.25F, r[3]);
					base.xRot = tendrils.wallPitch() - (r[1] - 4.0F) * 0.04F;
					base.zScale = length * 1.6F * (fp ? 1.0F + STRETCH_FIRST_PERSON : 1.0F);
				} else if (tendrils.wall() == 2) {
					// back to the wall: fanned out straight behind, driven into it
					calm = 0.0F;
					segCurlX = 0.0F;
					segCurlY = 0.0F;
					base.yRot = r[3] * 0.85F;
					base.xRot = (r[1] - 4.0F) * 0.13F;
					base.zScale = length * 1.15F;
				}
				for (int k = 1; k < SEGMENTS; k++) {
					ModelPart segment = this.parts[t][k];
					float wave = Mth.sin(time * 0.12F + t * 0.9F + k * 0.7F) * (0.18F + 0.07F * k);
					// each segment curls further over, rippling; striking straightens them into a whip
					segment.xRot = segCurlX + wave * 0.5F * calm + wave * 0.1F * (1.0F - calm);
					segment.yRot = segCurlY + wave * 0.55F * calm;
					segment.xScale = 0.9F;
					segment.yScale = 0.9F;
				}
			}
		}
	}
}

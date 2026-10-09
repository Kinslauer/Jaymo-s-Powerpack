package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
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
 */
final class WebTendrilLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_tendril.png");
	/** Ticks of Living Armor until the tendrils are fully grown. */
	static final float GROWN_TICKS = 200.0F;
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
	/** A strike: rearing back until this share of it, lashing out until the next, then easing back. */
	private static final float STRIKE_REAR = 0.42F;
	private static final float STRIKE_OUT = 0.55F;

	private final TendrilModel model;

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
		// rooted to the torso, so they lean with a crouch
		this.getParentModel().body.translateAndRotate(poseStack);
		collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY,
			state.outlineColor);
		poseStack.popPose();
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
			// thicker and longer as they grow
			float thick = 0.6F + 0.7F * growth;
			float length = 0.6F + 0.7F * growth;
			for (int t = 0; t < ROOTS.length; t++) {
				float[] r = ROOTS[t];
				ModelPart base = this.parts[t][0];
				float sway = Mth.sin(time * 0.07F + t * 1.3F) * 0.2F;
				float restYaw = r[3] + sway * 0.5F;
				float restPitch = r[4] + sway;
				// the strikers: the upper tendrils on the target's side
				float strike = tendrils.strike() >= 0.0F && Math.signum(r[0]) == tendrils.side() && r[1] < 6.0F ? tendrils.strike() : -1.0F;
				float aim = 0.0F;
				float rear = 0.0F;
				if (strike >= 0.0F) {
					if (strike < STRIKE_REAR) {
						rear = Mth.sin(strike / STRIKE_REAR * Mth.HALF_PI);
					} else if (strike < STRIKE_OUT) {
						float p = (strike - STRIKE_REAR) / (STRIKE_OUT - STRIKE_REAR);
						rear = 1.0F - p;
						aim = p * p;
					} else {
						aim = 1.0F - (strike - STRIKE_OUT) / (1.0F - STRIKE_OUT);
						aim = aim * aim * (3.0F - 2.0F * aim);
					}
				}
				// rearing: lifted high and drawn back; striking: flung over the shoulder at the target
				float aimYaw = Mth.PI - tendrils.aimYaw();
				float aimPitch = -Mth.HALF_PI - 1.0F - tendrils.aimPitch();
				base.yRot = Mth.lerp(aim, restYaw + rear * r[3] * 0.4F, aimYaw);
				base.xRot = Mth.lerp(aim, restPitch - rear * 0.55F, aimPitch);
				base.xScale = thick;
				base.yScale = thick;
				base.zScale = length * (1.0F + 0.25F * aim);
				for (int k = 1; k < SEGMENTS; k++) {
					ModelPart segment = this.parts[t][k];
					float wave = Mth.sin(time * 0.12F + t * 0.9F + k * 0.7F) * (0.18F + 0.07F * k);
					// each segment curls further over, rippling; rearing coils them; striking straightens them into a whip
					segment.xRot = Mth.lerp(aim, 0.28F + wave * 0.5F + rear * 0.25F, wave * 0.15F);
					segment.yRot = wave * 0.55F * (1.0F - aim);
					segment.xScale = 0.9F;
					segment.yScale = 0.9F;
				}
			}
		}
	}
}

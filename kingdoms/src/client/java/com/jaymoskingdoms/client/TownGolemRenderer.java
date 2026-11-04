package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.TownGolem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * (0.7.477) The town golems ({@link TownGolem}): one rune golem of our own (texture 128x64, painted by
 * {@code kingdoms/tools/gen_town_golems.py}), in each kind's stone and size: a block of a head with a crest stone, a broad
 * chest with a carved core set in it, a narrow waist, arms to the knees, short legs. Its eyes and core glow (a lamp
 * warden's only at night, a scribe's only while it has ink); the porter wears a basket on its back while it carries.
 */
public final class TownGolemRenderer extends MobRenderer<TownGolem, TownGolemRenderer.State, TownGolemRenderer.Model> {
	private static final Identifier[] TEX = new Identifier[TownGolem.Kind.values().length];
	private static final Identifier[] GLOW = new Identifier[TEX.length];

	static {
		for (TownGolem.Kind kind : TownGolem.Kind.values()) {
			TEX[kind.ordinal()] = JaymosKingdoms.id("textures/entity/golem/" + kind.id + ".png");
			GLOW[kind.ordinal()] = JaymosKingdoms.id("textures/entity/golem/" + kind.id + "_glow.png");
		}
	}

	public static final class State extends LivingEntityRenderState {
		TownGolem.Kind kind = TownGolem.Kind.CLAY;
		boolean lit, carrying;
		int id;
	}

	public TownGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(Model.createBodyLayer().bakeRoot()), 0.6F);
		this.addLayer(new Glow(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TownGolem golem, State state, float partialTicks) {
		super.extractRenderState(golem, state, partialTicks);
		state.kind = golem.kind();
		state.lit = golem.lit();
		state.carrying = golem.carrying();
		state.id = golem.getId();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEX[state.kind.ordinal()];
	}

	/** Its eyes and core, bright in the dark. */
	private static final class Glow extends RenderLayer<State, Model> {
		Glow(TownGolemRenderer parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			boolean on = switch (state.kind) {
				case GILT, LAPIS -> state.lit;
				default -> true;
			};
			if (!on) {
				return;
			}
			collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(GLOW[state.kind.ordinal()]), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
		}
	}

	static final class Model extends EntityModel<State> {
		private final ModelPart body, head, rightArm, leftArm, rightLeg, leftLeg, pack;

		Model(ModelPart root) {
			super(root);
			this.body = root.getChild("body");
			this.head = this.body.getChild("head");
			this.rightArm = this.body.getChild("right_arm");
			this.leftArm = this.body.getChild("left_arm");
			this.pack = this.body.getChild("pack");
			this.rightLeg = root.getChild("right_leg");
			this.leftLeg = root.getChild("left_leg");
		}

		static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 16).addBox(-7, -16, -4, 14, 10, 8)
				.texOffs(0, 34).addBox(-5, -6, -3, 10, 6, 6)
				.texOffs(34, 0).addBox(-2, -14, -5, 4, 4, 1), PartPose.offset(0, 14, 0));
			body.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8)
				.texOffs(46, 0).addBox(-1, -10, -1, 2, 2, 2), PartPose.offset(0, -16, -1));
			body.addOrReplaceChild("pack", CubeListBuilder.create().texOffs(64, 46).addBox(-5, 0, 0, 10, 9, 4), PartPose.offset(0, -15, 4));
			body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(64, 0).addBox(-2, -2, -2.5F, 4, 18, 5), PartPose.offset(-9, -14, 0));
			body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(64, 0).mirror().addBox(-2, -2, -2.5F, 4, 18, 5), PartPose.offset(9, -14, 0));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(64, 24).addBox(-2.5F, 0, -2.5F, 5, 10, 5), PartPose.offset(-3, 14, 0));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(64, 24).mirror().addBox(-2.5F, 0, -2.5F, 5, 10, 5), PartPose.offset(3, 14, 0));
			return LayerDefinition.create(mesh, 128, 64);
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float swing = state.walkAnimationPos * 0.6F, amount = Math.min(state.walkAnimationSpeed, 1.0F);
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
			this.rightLeg.xRot = Mth.cos(swing) * 1.1F * amount;
			this.leftLeg.xRot = Mth.cos(swing + Mth.PI) * 1.1F * amount;
			this.rightArm.xRot = Mth.cos(swing + Mth.PI) * 0.8F * amount;
			this.leftArm.xRot = Mth.cos(swing) * 0.8F * amount;
			this.body.zRot = Mth.cos(swing) * 0.05F * amount;
			// a slow breath, each on its own clock; the scribe holds its hands out a little, as over a page
			float breath = Mth.cos((state.ageInTicks + state.id * 13) * 0.05F);
			this.rightArm.zRot = 0.06F + breath * 0.03F;
			this.leftArm.zRot = -this.rightArm.zRot;
			if (state.kind == TownGolem.Kind.LAPIS) {
				this.rightArm.xRot = this.leftArm.xRot = -0.5F + breath * 0.04F;
			}
			// the porter carries in both arms, held out before it
			if (state.carrying) {
				this.rightArm.xRot = this.leftArm.xRot = -0.9F;
			}
			this.pack.visible = state.kind == TownGolem.Kind.CLAY;
		}
	}
}

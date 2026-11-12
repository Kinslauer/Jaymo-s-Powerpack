package com.jaymospowers.client.eden;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.eden.Diskwing;
import com.jaymospowers.eden.Eden;
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
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Draws the diskwing: Eden Ring's shape (a flat body, four eyes, two crescent wings, a two-jointed finned tail) with its lit markings over it. */
public final class DiskwingClient {
	/** How far the body tips with its climb or dive, the tail's sway and the wings' beat (radians), and how fast they go. */
	private static final float TIP = 0.6F, TAIL_SWAY = 0.2F, WING_BEAT = 0.25F, TAIL_RATE = 0.1F, WING_RATE = 0.2F;
	private static final float SHADOW = 0.6F;

	private DiskwingClient() {
	}

	public static void init() {
		EntityRenderers.register(Eden.DISKWING, Renderer::new);
	}

	static final class State extends LivingEntityRenderState {
		int kind;
		/** Blocks a tick it is rising (falling below 0), within -1 to 1. */
		float climb;
	}

	static final class Model extends EntityModel<State> {
		private final ModelPart body, tail1, tail2, wingRight, wingLeft;

		Model(ModelPart root) {
			super(root);
			this.body = root.getChild("model");
			this.tail1 = this.body.getChild("tail_pivot_1");
			this.tail2 = this.tail1.getChild("tail_pivot_2");
			this.wingRight = this.body.getChild("wing_right");
			this.wingLeft = this.body.getChild("wing_left");
		}

		static LayerDefinition layer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition body = mesh.getRoot().addOrReplaceChild("model", CubeListBuilder.create().texOffs(0, 24).addBox(-4.0F, -1.0F, -8.0F, 8, 2, 16),
				PartPose.offset(0.0F, 22.0F, 0.0F));
			body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(-24, 0).addBox(-12.0F, 0.0F, -6.0F, 14, 0, 24),
				PartPose.offset(-4.0F, 0.0F, 0.0F));
			body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(-24, 0).mirror().addBox(-2.0F, 0.0F, -6.0F, 14, 0, 24),
				PartPose.offset(4.0F, 0.0F, 0.0F));
			body.addOrReplaceChild("eye_1", CubeListBuilder.create().texOffs(0, 24).addBox(-3.0F, -3.0F, -1.0F, 2, 2, 2),
				PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, 0.8727F, 0.5236F, 0.0F));
			body.addOrReplaceChild("eye_2", CubeListBuilder.create().texOffs(0, 28).addBox(-3.0F, 1.0F, -1.0F, 2, 2, 2),
				PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, -0.8727F, 0.5236F, 0.0F));
			body.addOrReplaceChild("eye_3", CubeListBuilder.create().texOffs(0, 24).mirror().addBox(1.0F, -3.0F, -1.0F, 2, 2, 2),
				PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, 0.8727F, -0.5236F, 0.0F));
			body.addOrReplaceChild("eye_4", CubeListBuilder.create().texOffs(0, 28).mirror().addBox(1.0F, 1.0F, -1.0F, 2, 2, 2),
				PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, -0.8727F, -0.5236F, 0.0F));
			PartDefinition tail = body.addOrReplaceChild("tail_pivot_1", CubeListBuilder.create().texOffs(0, 42).addBox(-2.0F, -1.0F, 0.0F, 4, 2, 8),
				PartPose.offset(0.0F, 0.0F, 8.0F));
			tail.addOrReplaceChild("tail_pivot_2", CubeListBuilder.create().texOffs(0, 52).addBox(-1.0F, -1.0F, 0.0F, 2, 2, 8)
				.texOffs(20, 0).addBox(-6.0F, 0.0F, 6.0F, 12, 0, 8), PartPose.offset(0.0F, 0.0F, 8.0F));
			return LayerDefinition.create(mesh, 64, 64);
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float sway = Mth.sin(state.ageInTicks * TAIL_RATE) * TAIL_SWAY;
			this.body.xRot = -state.climb * TIP;
			this.tail1.xRot = sway;
			this.tail2.xRot = sway;
			this.wingRight.zRot = Mth.sin(state.ageInTicks * WING_RATE) * WING_BEAT;
			this.wingLeft.zRot = -this.wingRight.zRot;
		}
	}

	static final class Renderer extends MobRenderer<Diskwing, State, Model> {
		private static final Identifier[] SKINS = new Identifier[Diskwing.KINDS.length];
		private static final RenderType[] GLOWS = new RenderType[Diskwing.KINDS.length];

		static {
			for (int i = 0; i < SKINS.length; i++) {
				SKINS[i] = JaymosPowers.id("textures/entity/eden/diskwing_" + Diskwing.KINDS[i] + ".png");
				GLOWS[i] = RenderTypes.eyes(JaymosPowers.id("textures/entity/eden/diskwing_" + Diskwing.KINDS[i] + "_glow.png"));
			}
		}

		Renderer(EntityRendererProvider.Context context) {
			super(context, new Model(Model.layer().bakeRoot()), SHADOW);
			this.addLayer(new Glow(this));
		}

		@Override
		public State createRenderState() {
			return new State();
		}

		@Override
		public void extractRenderState(Diskwing mob, State state, float partialTicks) {
			super.extractRenderState(mob, state, partialTicks);
			state.kind = mob.kind();
			state.climb = (float)Mth.clamp(mob.getDeltaMovement().y * 4.0, -1.0, 1.0);
		}

		@Override
		public Identifier getTextureLocation(State state) {
			return SKINS[Math.floorMod(state.kind, SKINS.length)];
		}
	}

	/** The lit markings, drawn at full brightness as a spider's eyes are. */
	static final class Glow extends RenderLayer<State, Model> {
		Glow(RenderLayerParent<State, Model> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			collector.order(1).submitModel(this.getParentModel(), state, poseStack, Renderer.GLOWS[Math.floorMod(state.kind, Renderer.GLOWS.length)],
				lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		}
	}
}

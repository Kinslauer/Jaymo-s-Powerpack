package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FieldMortar;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The field mortar: the Mortar of cleannrooster's Frontier Armaments (MIT), its boxes and its sheet as the maker drew
 * them ({@code tools/bbmodels/frontier-armaments/mortar.geo.json}, built here as a vanilla model): a plate with four
 * splayed legs and their feet, two cheeks, and the tube between them. The tube leans forward to its lay and kicks back
 * down its own line when it fires. Drawn in pixels of 1/16 block, the ground at 24, forward +z.
 */
public final class FieldMortarRenderer extends EntityRenderer<FieldMortar, FieldMortarRenderer.State> {
	/** How far the tube kicks back down its line when it fires (pixels). */
	private static final float KICK = 2.5F;
	private static final float DEG = Mth.DEG_TO_RAD;
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/field_mortar.png");

	public static final class State extends EntityRenderState {
		float yaw;
		/** The tube's lean forward from upright (radians), and how far it is kicked back (pixels). */
		float lean;
		float kick;
	}

	private static Model model;

	public FieldMortarRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.6F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FieldMortar entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.lean = (90.0F - entity.elevation()) * DEG;
		float t = entity.sinceFired + partialTicks;
		state.kick = t >= FieldMortar.KICK_TICKS ? 0.0F : KICK * (1.0F - t / FieldMortar.KICK_TICKS);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (model == null) {
			model = new Model(build().bakeRoot());
		}
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		// the model is drawn the vanilla way up: y down, its ground at 24 pixels
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.5F, 0.0F);
		collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
			state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static CubeListBuilder box(int u, int v, float x, float y, float z, float w, float h, float d, float grow) {
		return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d, new CubeDeformation(grow));
	}

	/** The maker's boxes: a Bedrock box at (x, y, z) of (w, h, d) is ours at (x, 24 - y - h, z), its turns unchanged. */
	static LayerDefinition build() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition stand = mesh.getRoot().addOrReplaceChild("stand", CubeListBuilder.create(), PartPose.ZERO);
		stand.addOrReplaceChild("hub", box(32, 0, -2.5F, 21.5F, -2.5F, 5.0F, 1.5F, 5.0F, 0.1F), PartPose.ZERO);
		stand.addOrReplaceChild("plate_low", box(0, 0, -4.0F, 23.0F, -4.0F, 8.0F, 1.0F, 8.0F, 0.5F), PartPose.ZERO);
		stand.addOrReplaceChild("plate", box(0, 9, -4.0F, 22.0F, -4.0F, 8.0F, 1.0F, 8.0F, 0.0F), PartPose.ZERO);
		// the four legs, each sloping a little down to its foot
		stand.addOrReplaceChild("leg_back", box(0, 18, -1.0F, -1.0F, -12.0F, 2.0F, 1.0F, 11.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, 5.0F * DEG, 0.0F, 0.0F));
		stand.addOrReplaceChild("leg_front", box(26, 18, -1.0F, -1.0F, 1.0F, 2.0F, 1.0F, 11.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, -5.0F * DEG, 0.0F, 0.0F));
		stand.addOrReplaceChild("leg_left", box(0, 30, -12.0F, -1.0F, -1.0F, 11.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, 0.0F, 0.0F, -5.0F * DEG));
		stand.addOrReplaceChild("leg_right", box(26, 30, 1.0F, -1.0F, -1.0F, 11.0F, 1.0F, 2.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, 0.0F, 0.0F, 5.0F * DEG));
		stand.addOrReplaceChild("foot_front", box(0, 40, -2.0F, -2.0F, 10.0F, 3.0F, 3.0F, 3.0F, 0.1F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, -5.0F * DEG, 0.0F, 0.0F));
		stand.addOrReplaceChild("foot_right", box(0, 40, 10.0F, -2.0F, -1.0F, 3.0F, 3.0F, 3.0F, 0.1F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, 0.0F, 0.0F, 5.0F * DEG));
		stand.addOrReplaceChild("foot_left", box(0, 40, -13.0F, -1.0F, -2.0F, 3.0F, 3.0F, 3.0F, 0.1F), PartPose.offsetAndRotation(0.0F, 22.0F, 0.0F, 0.0F, 0.0F, -5.0F * DEG));
		stand.addOrReplaceChild("foot_back", box(0, 40, -2.0F, -2.0F, -13.0F, 3.0F, 3.0F, 3.0F, 0.1F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, 5.0F * DEG, 0.0F, 0.0F));
		// the cheeks, and the tube turning between them
		stand.addOrReplaceChild("cheek_left", box(34, 47, -2.5F, 17.5F, -1.5F, 1.0F, 4.5F, 3.0F, 0.5F), PartPose.ZERO);
		stand.addOrReplaceChild("cheek_right", box(19, 43, 1.5F, 17.5F, -1.5F, 1.0F, 4.5F, 3.0F, 0.5F), PartPose.ZERO);
		stand.addOrReplaceChild("tube", box(32, 6, -1.5F, -10.25F, -1.5F, 3.0F, 8.5F, 3.0F, 2.0F), PartPose.offset(0.0F, 18.75F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** The mortar, its tube laid and kicked by the state. */
	static final class Model extends EntityModel<State> {
		private final ModelPart tube;

		Model(ModelPart root) {
			super(root);
			this.tube = root.getChild("stand").getChild("tube");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			// (forward is +z and up is -y: a lean forward is a turn back about x; the kick runs down the tube's own line)
			this.tube.xRot = -state.lean;
			this.tube.y = 18.75F + state.kick * Mth.cos(state.lean);
			this.tube.z = -state.kick * Mth.sin(state.lean);
		}
	}
}

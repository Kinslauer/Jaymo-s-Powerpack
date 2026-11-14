package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mangonel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
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
import net.minecraft.world.phys.AABB;

/**
 * A mangonel, our own model, in the field cannon's timber and iron ({@code cannon.png}): two long rails on four spoked
 * wheels, two uprights braced back to the rails with a padded stop beam across their tops, a skein of rope between the
 * rails, a winch drum at the back, and the throwing arm with its cup. Cocked, the arm lies back over the winch with a
 * stone in the cup; thrown, it flies up against the stop, rests there, and is winched down again. Its wheels turn as it
 * is pushed. Drawn in pixels of 1/16 block, the ground at 24.
 */
public final class MangonelRenderer extends EntityRenderer<Mangonel, MangonelRenderer.State> {
	/** The arm's angle back from upright when cocked (radians), and how far past upright the stop lets it go. */
	private static final float COCKED = 1.31F, STOPPED = -0.06F;
	/** The wheels' radius (pixels). */
	private static final float WHEEL = 5.0F;
	/** A stone is back in the cup this long before the winch is home (ticks). */
	private static final int LOADED_BEFORE = 20;
	/** The cannon sheet's three materials: cast iron above; timber at the lower left; the bands at the lower right. */
	private static final int[] IRON = {0, 0}, TIMBER = {0, 64}, BAND = {80, 64};
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/cannon.png");

	public static final class State extends EntityRenderState {
		float yaw;
		/** The arm's angle back from upright (radians), whether a stone lies in the cup, and how far it has rolled (blocks). */
		float arm;
		boolean loaded;
		float rolled;
	}

	private static Model model;

	public MangonelRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.2F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Mangonel entity, float partialTicks) {
		return entity.getBoundingBox().inflate(2.0);
	}

	@Override
	public void extractRenderState(Mangonel entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.rolled = Mth.lerp(partialTicks, entity.rolledO, entity.rolled);
		float t = entity.sinceThrown + partialTicks;
		float whole = Mangonel.THROW_TICKS + Mangonel.REST_TICKS + Mangonel.WINCH_TICKS;
		if (t < Mangonel.THROW_TICKS) {
			float up = t / Mangonel.THROW_TICKS;
			state.arm = Mth.lerp(up * up, COCKED, STOPPED);
		} else if (t < Mangonel.THROW_TICKS + Mangonel.REST_TICKS) {
			state.arm = STOPPED;
		} else if (t < whole) {
			state.arm = Mth.lerp((t - Mangonel.THROW_TICKS - Mangonel.REST_TICKS) / Mangonel.WINCH_TICKS, STOPPED, COCKED);
		} else {
			state.arm = COCKED;
		}
		state.loaded = t >= whole - LOADED_BEFORE;
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

	// ---- the model ----------------------------------------------------------------------------------------------------

	private static CubeListBuilder cube(int[] sheet, float x, float y, float z, float w, float h, float d) {
		return CubeListBuilder.create().texOffs(sheet[0], sheet[1]).addBox(x, y, z, w, h, d);
	}

	/** The mangonel: z forward, y down, x across; the ground at y 24; the arm turns about (0, 13, 4). */
	static LayerDefinition build() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition frame = root.addOrReplaceChild("frame", CubeListBuilder.create(), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "l" : "r";
			float x = side < 0 ? -12.0F : 9.0F;
			frame.addOrReplaceChild("rail_" + s, cube(TIMBER, x, 15.0F, -16.0F, 3.0F, 4.0F, 32.0F), PartPose.ZERO);
			frame.addOrReplaceChild("upright_" + s, cube(TIMBER, x, -9.0F, 5.5F, 3.0F, 24.0F, 3.0F), PartPose.ZERO);
			// the brace from the upright's top back down to the rail's rear
			frame.addOrReplaceChild("brace_" + s, cube(TIMBER, -1.25F, 0.0F, -1.0F, 2.5F, 27.5F, 2.0F),
				PartPose.offsetAndRotation(x + 1.5F, -6.0F, 5.0F, -0.708F, 0.0F, 0.0F));
			// iron shoes where the upright meets the rail and the stop
			frame.addOrReplaceChild("shoe_" + s, cube(BAND, x - 0.3F, 12.5F, 5.2F, 3.6F, 3.0F, 3.6F), PartPose.ZERO);
			frame.addOrReplaceChild("cap_" + s, cube(BAND, x - 0.3F, -9.3F, 5.2F, 3.6F, 2.0F, 3.6F), PartPose.ZERO);
			for (int end = -1; end <= 1; end += 2) {
				// a wheel: a hub, and four rim-and-spoke slabs set at 45 degrees apiece
				PartDefinition wheel = frame.addOrReplaceChild("wheel_" + s + (end < 0 ? "_back" : "_front"), CubeListBuilder.create(),
					PartPose.offset(side * 14.0F, 24.0F - WHEEL, end * 11.0F));
				for (int i = 0; i < 4; i++) {
					wheel.addOrReplaceChild("slab_" + i, cube(TIMBER, -1.0F, -WHEEL, -1.9F, 2.0F, WHEEL * 2.0F, 3.8F),
						PartPose.rotation(i * Mth.PI / 4.0F, 0.0F, 0.0F));
				}
				wheel.addOrReplaceChild("hub", cube(IRON, -1.5F, -1.6F, -1.6F, 3.0F, 3.2F, 3.2F), PartPose.ZERO);
			}
		}
		// the beams across: front, back, the padded stop over the uprights; the axles; the skein and the winch
		frame.addOrReplaceChild("beam_front", cube(TIMBER, -9.0F, 15.5F, 12.0F, 18.0F, 3.0F, 3.0F), PartPose.ZERO);
		frame.addOrReplaceChild("beam_back", cube(TIMBER, -9.0F, 15.5F, -15.0F, 18.0F, 3.0F, 3.0F), PartPose.ZERO);
		frame.addOrReplaceChild("stop", cube(TIMBER, -9.0F, -8.0F, 5.5F, 18.0F, 3.5F, 3.0F), PartPose.ZERO);
		frame.addOrReplaceChild("pad", cube(BAND, -4.0F, -8.4F, 4.6F, 8.0F, 4.3F, 1.0F), PartPose.ZERO);
		frame.addOrReplaceChild("axle_front", cube(IRON, -14.0F, 18.0F, 10.0F, 28.0F, 2.0F, 2.0F), PartPose.ZERO);
		frame.addOrReplaceChild("axle_back", cube(IRON, -14.0F, 18.0F, -12.0F, 28.0F, 2.0F, 2.0F), PartPose.ZERO);
		frame.addOrReplaceChild("skein", cube(BAND, -9.0F, 11.0F, 2.0F, 18.0F, 4.0F, 4.0F), PartPose.ZERO);
		frame.addOrReplaceChild("winch", cube(TIMBER, -9.0F, 11.5F, -13.0F, 18.0F, 3.0F, 3.0F), PartPose.ZERO);
		frame.addOrReplaceChild("winch_bar", cube(IRON, -13.5F, 12.5F, -12.0F, 27.0F, 1.0F, 1.0F), PartPose.ZERO);

		// the arm, drawn upright from its pivot in the skein; its cup opens forward (upward, when it lies cocked)
		PartDefinition arm = frame.addOrReplaceChild("arm", cube(TIMBER, -1.5F, -30.0F, -1.5F, 3.0F, 32.0F, 3.0F), PartPose.offset(0.0F, 13.0F, 4.0F));
		arm.addOrReplaceChild("arm_band", cube(BAND, -1.8F, -20.0F, -1.8F, 3.6F, 2.0F, 3.6F), PartPose.ZERO);
		arm.addOrReplaceChild("arm_foot", cube(BAND, -1.8F, -2.5F, -1.8F, 3.6F, 4.0F, 3.6F), PartPose.ZERO);
		arm.addOrReplaceChild("cup_back", cube(TIMBER, -3.5F, -33.0F, 1.5F, 7.0F, 7.0F, 1.0F), PartPose.ZERO);
		arm.addOrReplaceChild("cup_top", cube(BAND, -3.5F, -33.0F, 2.5F, 7.0F, 1.0F, 2.0F), PartPose.ZERO);
		arm.addOrReplaceChild("cup_bottom", cube(BAND, -3.5F, -27.0F, 2.5F, 7.0F, 1.0F, 2.0F), PartPose.ZERO);
		arm.addOrReplaceChild("cup_l", cube(BAND, -3.5F, -32.0F, 2.5F, 1.0F, 5.0F, 2.0F), PartPose.ZERO);
		arm.addOrReplaceChild("cup_r", cube(BAND, 2.5F, -32.0F, 2.5F, 1.0F, 5.0F, 2.0F), PartPose.ZERO);
		arm.addOrReplaceChild("stone", cube(IRON, -2.0F, -31.5F, 2.5F, 4.0F, 4.0F, 3.5F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 128);
	}

	/** The mangonel, its arm cocked or thrown, a stone in its cup or not, its wheels turned, by the state. */
	static final class Model extends EntityModel<State> {
		private final ModelPart arm, stone;
		private final ModelPart[] wheels;

		Model(ModelPart root) {
			super(root);
			ModelPart frame = root.getChild("frame");
			this.arm = frame.getChild("arm");
			this.stone = this.arm.getChild("stone");
			this.wheels = new ModelPart[] {frame.getChild("wheel_l_back"), frame.getChild("wheel_l_front"), frame.getChild("wheel_r_back"),
				frame.getChild("wheel_r_front")};
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			this.arm.xRot = state.arm;
			this.stone.visible = state.loaded;
			for (ModelPart wheel : this.wheels) {
				wheel.xRot = -state.rolled * 16.0F / WHEEL;
			}
		}
	}
}

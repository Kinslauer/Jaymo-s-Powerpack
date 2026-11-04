package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Cannon;
import com.jaymoskingdoms.JaymosKingdoms;
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
 * (0.7.490) A field cannon, our own model and texture (it was iexogen's "Canon" until now): a cast barrel of eight faces with
 * reinforcing bands, a swelling muzzle and a cascabel, laid on trunnions in a wooden carriage with two cheeks, a transom,
 * a trail to the ground and two spoked wheels. It swings round to its mark; firing, the barrel runs back along its line
 * and the whole carriage rolls back on its wheels, then is hauled up again slowly. (Smoke, flame and the flash are the
 * server's: {@code Cannon.fire}.) Drawn in pixels of 1/16 block, the trunnions {@link Cannon#PIVOT} off the ground and
 * the muzzle {@link Cannon#MUZZLE} out from them.
 */
public final class CannonRenderer extends EntityRenderer<Cannon, CannonRenderer.State> {
	/** How far the barrel runs back along the carriage, and the carriage along the ground, at the worst of the kick (blocks). */
	private static final float SLIDE = 0.22F, ROLL = 0.3F;
	/** The share of the kick's time the gun takes to run back; it's hauled forward for the rest. */
	private static final float RUN_BACK = 0.15F;
	/** The wheels' radius and the ground's height in the model (pixels). */
	private static final float WHEEL = 6.0F;
	/** Where the sheet's three materials lie: cast iron above; timber at the lower left; the iron-and-brass bands at the lower right. */
	private static final int[] IRON = {0, 0}, TIMBER = {0, 64}, BAND = {80, 64};
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/cannon.png");

	public static final class State extends EntityRenderState {
		float yaw;
		float pitch;
		/** How far through the kick: 0 at rest, 1 at the worst of it. */
		float kick;
	}

	private static Model model;

	public CannonRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.8F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Cannon entity, float partialTicks) {
		return entity.getBoundingBox().inflate(2.0);
	}

	@Override
	public void extractRenderState(Cannon entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.pitch = Mth.lerp(partialTicks, entity.pitchO, entity.barrelPitch());
		float t = (entity.sinceFired + partialTicks) / Cannon.RECOIL_TICKS;
		if (t >= 1.0F) {
			state.kick = 0.0F;
		} else if (t < RUN_BACK) {
			state.kick = t / RUN_BACK;
		} else {
			float back = 1.0F - (t - RUN_BACK) / (1.0F - RUN_BACK);
			state.kick = back * back;
		}
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

	/**
	 * A regular octagon of apothem {@code p} along z from {@code z0} for {@code len}, about the barrel's line: two slabs
	 * across and two boxes turned 45 degrees for the cut corners.
	 */
	private static void octagon(PartDefinition parent, String name, int[] sheet, float p, float z0, float len) {
		float q = p * 0.41421356F;
		parent.addOrReplaceChild(name + "_h", cube(sheet, -p, -q, z0, p * 2.0F, q * 2.0F, len), PartPose.ZERO);
		parent.addOrReplaceChild(name + "_v", cube(sheet, -q, -p, z0, q * 2.0F, p * 2.0F, len), PartPose.ZERO);
		parent.addOrReplaceChild(name + "_a", cube(sheet, -q, -p, z0, q * 2.0F, p * 2.0F, len), PartPose.rotation(0.0F, 0.0F, Mth.PI / 4.0F));
		parent.addOrReplaceChild(name + "_b", cube(sheet, -q, -p, z0, q * 2.0F, p * 2.0F, len), PartPose.rotation(0.0F, 0.0F, -Mth.PI / 4.0F));
	}

	/** The cannon: z forward, y down, x across; the ground at y 24, the trunnions at y 12 on the middle line. */
	static LayerDefinition build() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition carriage = root.addOrReplaceChild("carriage", CubeListBuilder.create(), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "l" : "r";
			float x = side < 0 ? -7.5F : 4.5F;
			// the cheek, high at the breech end of the barrel's bed, and the trail running back and down to the ground
			carriage.addOrReplaceChild("cheek_" + s, cube(TIMBER, x, 8.5F, -5.0F, 3.0F, 11.0F, 15.0F), PartPose.ZERO);
			carriage.addOrReplaceChild("trail_a_" + s, cube(TIMBER, x + 0.25F, 15.5F, -12.0F, 2.5F, 3.5F, 8.0F), PartPose.ZERO);
			carriage.addOrReplaceChild("trail_b_" + s, cube(TIMBER, x + 0.25F, 19.0F, -20.0F, 2.5F, 3.5F, 8.5F), PartPose.ZERO);
			// iron straps round the cheek, and a cap-square over each trunnion
			carriage.addOrReplaceChild("strap_a_" + s, cube(BAND, x - 0.2F, 8.3F, 5.0F, 3.4F, 11.4F, 1.6F), PartPose.ZERO);
			carriage.addOrReplaceChild("strap_b_" + s, cube(BAND, x - 0.2F, 8.3F, -4.2F, 3.4F, 11.4F, 1.6F), PartPose.ZERO);
			carriage.addOrReplaceChild("cap_" + s, cube(BAND, x - 0.4F, 10.0F, -2.0F, 3.8F, 4.0F, 4.0F), PartPose.ZERO);
			// the wheel: a hub, and four rim-and-spoke slabs set at 45 degrees apiece
			PartDefinition wheel = carriage.addOrReplaceChild("wheel_" + s, CubeListBuilder.create(), PartPose.offset(side * 10.0F, 24.0F - WHEEL, 3.0F));
			for (int i = 0; i < 4; i++) {
				wheel.addOrReplaceChild("slab_" + i, cube(TIMBER, -1.0F, -WHEEL, -2.2F, 2.0F, WHEEL * 2.0F, 4.4F),
					PartPose.rotation(i * Mth.PI / 4.0F, 0.0F, 0.0F));
			}
			wheel.addOrReplaceChild("hub", cube(IRON, -1.6F, -1.8F, -1.8F, 3.2F, 3.6F, 3.6F), PartPose.ZERO);
		}
		// the axle through the cheeks, the transom between them behind, and a bolster under the breech
		carriage.addOrReplaceChild("axle", cube(IRON, -10.0F, 17.0F, 2.0F, 20.0F, 2.0F, 2.0F), PartPose.ZERO);
		carriage.addOrReplaceChild("transom", cube(TIMBER, -4.5F, 16.5F, -9.0F, 9.0F, 3.0F, 3.0F), PartPose.ZERO);
		carriage.addOrReplaceChild("transom_front", cube(TIMBER, -4.5F, 16.0F, 5.5F, 9.0F, 3.0F, 3.0F), PartPose.ZERO);
		carriage.addOrReplaceChild("bolster", cube(TIMBER, -2.5F, 15.4F, -7.0F, 5.0F, 1.8F, 6.0F), PartPose.ZERO);
		carriage.addOrReplaceChild("trail_spike", cube(IRON, -1.0F, 21.5F, -21.5F, 2.0F, 2.5F, 2.0F), PartPose.ZERO);

		// the barrel on its trunnions: it runs from the cascabel at -10 to the muzzle at 21.6, and swings about (0, 12, 0)
		PartDefinition barrel = carriage.addOrReplaceChild("barrel", CubeListBuilder.create(), PartPose.offset(0.0F, 12.0F, 0.0F));
		octagon(barrel, "breech", IRON, 3.6F, -8.0F, 8.0F);
		octagon(barrel, "chase", IRON, 3.1F, 0.0F, 12.0F);
		octagon(barrel, "taper", IRON, 2.6F, 12.0F, 6.0F);
		octagon(barrel, "swell", IRON, 3.2F, 18.0F, 3.6F);
		// reinforcing bands: at the breech, the chase's two ends and middle, the taper and the muzzle's lip
		float[] ring = {-6.0F, 0.0F, 5.0F, 10.5F, 14.5F, 19.0F, 20.4F};
		float[] size = {4.0F, 3.5F, 3.3F, 3.3F, 2.9F, 3.6F, 3.5F};
		for (int i = 0; i < ring.length; i++) {
			octagon(barrel, "band_" + i, BAND, size[i], ring[i], 1.4F);
		}
		barrel.addOrReplaceChild("trunnion", cube(IRON, -7.6F, -1.5F, -1.5F, 15.2F, 3.0F, 3.0F), PartPose.ZERO);
		barrel.addOrReplaceChild("cascabel", cube(IRON, -1.6F, -1.6F, -10.5F, 3.2F, 3.2F, 2.5F), PartPose.ZERO);
		barrel.addOrReplaceChild("knob", cube(IRON, -1.0F, -1.0F, -12.0F, 2.0F, 2.0F, 1.5F), PartPose.ZERO);
		// the vent field's priming pan
		barrel.addOrReplaceChild("pan", cube(BAND, -1.0F, -4.4F, -5.0F, 2.0F, 1.0F, 2.5F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 128);
	}

	/** The cannon, its barrel raised and run back, its carriage rolled back with its wheels turning, by the state. */
	static final class Model extends EntityModel<State> {
		private final ModelPart carriage, barrel, wheelL, wheelR;

		Model(ModelPart root) {
			super(root);
			this.carriage = root.getChild("carriage");
			this.barrel = this.carriage.getChild("barrel");
			this.wheelL = this.carriage.getChild("wheel_l");
			this.wheelR = this.carriage.getChild("wheel_r");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			// the barrel laid to its mark (up is positive)
			this.barrel.xRot = state.pitch * Mth.DEG_TO_RAD;
			float roll = state.kick * ROLL * 16.0F;
			float slide = state.kick * SLIDE * 16.0F;
			// the whole carriage runs back along the ground; the wheels turn the way that rolls them
			this.carriage.z = -roll;
			this.wheelL.xRot = roll / WHEEL;
			this.wheelR.xRot = roll / WHEEL;
			// and the barrel slides back along its own line in the cheeks
			float raise = state.pitch * Mth.DEG_TO_RAD;
			this.barrel.z = -slide * Mth.cos(raise);
			this.barrel.y = 12.0F + slide * Mth.sin(raise);
		}
	}
}

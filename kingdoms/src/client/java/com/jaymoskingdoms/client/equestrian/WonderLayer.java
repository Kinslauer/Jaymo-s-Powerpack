package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.equestrian.WonderHorses;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * The wings and horn of a winged or horned horse ({@link WonderHorses}), drawn over whatever horse it is: the wings are
 * the six boxes of Mo' Creatures' horse model (GPL-3, see {@code licenses/mocreatures}), folded along its sides on the
 * ground, spread and beating in the air, in the feathers of its pegasus; the horn a short spiral of three boxes on the
 * brow, tilting with the head. One texture ({@code textures/entity/wonder_horse.png}, {@code tools/gen_moc_beasts.py}).
 */
public final class WonderLayer extends RenderLayer<EquineRenderState, EntityModel<EquineRenderState>> {
	/** What the horse has (WonderHorses.KIND), and whether it is off the ground, and climbing. */
	static final RenderStateDataKey<int[]> LOOK = RenderStateDataKey.create(() -> "jaymoskingdoms:wonder_horse");
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/wonder_horse.png");
	private static final float DEG = Mth.DEG_TO_RAD;
	/** The head's pitch at rest (as MoC's horse model holds it). */
	private static final float HEAD_TILT = 30.0F * DEG;

	private final Model model = new Model(build().bakeRoot());

	private WonderLayer(RenderLayerParent<EquineRenderState, EntityModel<EquineRenderState>> parent) {
		super(parent);
	}

	/** On the render state: what a wonder horse has, for the layer. Called from the horse renderer mixin. */
	public static void extract(AbstractHorse horse, EquineRenderState state) {
		int kind = WonderHorses.kind(horse);
		if (kind != WonderHorses.NONE) {
			boolean air = !horse.onGround() && !horse.isInWater();
			state.setData(LOOK, new int[] {kind, air ? 1 : 0, air && horse.getDeltaMovement().y > 0.0 ? 1 : 0});
		}
	}

	@SuppressWarnings("unchecked")
	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AbstractHorseRenderer<?, ?, ?>) {
				helper.register(new WonderLayer((RenderLayerParent<EquineRenderState, EntityModel<EquineRenderState>>)renderer));
			}
		});
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, EquineRenderState state, float yRot, float xRot) {
		int[] look = state.getData(LOOK);
		if (look == null || state.isInvisible) {
			return;
		}
		this.model.look = look;
		collector.order(2).submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
			state.outlineColor);
	}

	/** The wings' six boxes and the horn's three, texture offsets as in the original (128 by 128, drawn at 256). */
	private static LayerDefinition build() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		wing(root, "inner_wing", 0, 96, 0F, 7F, 5F, -0.3490659F);
		wing(root, "mid_wing", 82, 68, 1F, 12F, 5F, 0.0872665F);
		wing(root, "outer_wing", 0, 68, 0F, 22F, 17F, -0.3228859F);
		wing(root, "inner_wing_r", 0, 110, -7F, 7F, -5F, 0.3490659F);
		wing(root, "mid_wing_r", 82, 82, -13F, 12F, -5F, -0.0872665F);
		wing(root, "outer_wing_r", 0, 82, -22F, 22F, -17F, 0.3228859F);
		PartDefinition horn = root.addOrReplaceChild("horn", CubeListBuilder.create(), PartPose.offset(0F, 4F, -10F));
		horn.addOrReplaceChild("horn_a", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -12.5F, -0.2F, 3F, 3F, 3F), PartPose.ZERO);
		horn.addOrReplaceChild("horn_b", CubeListBuilder.create().texOffs(0, 8).addBox(-1F, -14.5F, 0.3F, 2F, 2F, 2F), PartPose.rotation(-0.15F, 0F, 0F));
		horn.addOrReplaceChild("horn_c", CubeListBuilder.create().texOffs(0, 16).addBox(-0.5F, -16.5F, 0.8F, 1F, 2F, 1F), PartPose.rotation(-0.3F, 0F, 0F));
		return LayerDefinition.create(mesh, 128, 128);
	}

	private static void wing(PartDefinition root, String name, int u, int v, float x, float length, float pivotX, float yaw) {
		root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, name.startsWith("mid") ? 0.1F : 0F, name.startsWith("mid") ? 1F : 0F,
			length, 2F, 11F), PartPose.offsetAndRotation(pivotX, 3F, -6F, 0F, yaw, 0F));
	}

	private static final class Model extends EntityModel<EquineRenderState> {
		private final ModelPart innerWing, midWing, outerWing, innerWingR, midWingR, outerWingR, horn;
		int[] look = {0, 0, 0};

		Model(ModelPart root) {
			super(root);
			this.innerWing = root.getChild("inner_wing");
			this.midWing = root.getChild("mid_wing");
			this.outerWing = root.getChild("outer_wing");
			this.innerWingR = root.getChild("inner_wing_r");
			this.midWingR = root.getChild("mid_wing_r");
			this.outerWingR = root.getChild("outer_wing_r");
			this.horn = root.getChild("horn");
		}

		@Override
		public void setupAnim(EquineRenderState state) {
			super.setupAnim(state);
			boolean wings = this.look[0] == WonderHorses.WINGED, air = this.look[1] == 1, flapping = this.look[2] == 1;
			for (ModelPart part : new ModelPart[] {this.innerWing, this.midWing, this.outerWing, this.innerWingR, this.midWingR, this.outerWingR}) {
				part.visible = wings;
			}
			this.horn.visible = this.look[0] == WonderHorses.HORNED;
			// the horn follows the head's pitch
			this.horn.xRot = HEAD_TILT + state.xRot * DEG;
			if (!wings) {
				return;
			}
			float wingRot = flapping ? Mth.cos(state.ageInTicks * 0.3F + Mth.PI) * 1.2F : Mth.cos(state.walkAnimationPos * 0.5F) * 0.1F;
			if (air) {
				this.outerWing.yRot = -0.3228859F + wingRot / 2.0F;
				this.outerWingR.yRot = 0.3228859F - wingRot / 2.0F;
			} else {
				wingRot = 60.0F * DEG;
				this.outerWing.yRot = -90.0F * DEG;
				this.outerWingR.yRot = 90.0F * DEG;
			}
			float y = this.innerWing.getInitialPose().y();
			this.innerWingR.y = this.innerWing.y = y;
			this.outerWing.x = this.innerWing.x + Mth.cos(wingRot) * 12.0F;
			this.outerWingR.x = this.innerWingR.x - Mth.cos(wingRot) * 12.0F;
			this.midWing.y = this.midWingR.y = y;
			this.outerWing.y = y + Mth.sin(wingRot) * 12.0F;
			this.outerWingR.y = y + Mth.sin(wingRot) * 12.0F;
			this.midWing.zRot = this.innerWing.zRot = this.outerWing.zRot = wingRot;
			this.innerWingR.zRot = this.midWingR.zRot = this.outerWingR.zRot = -wingRot;
		}
	}
}

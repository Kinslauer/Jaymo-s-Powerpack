package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FrostTech;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a villager wears of the Frostborn's work ({@link FrostTech}), for everyone to see: a scouter over one eye, a made
 * arm, a made leg; and the wooden or iron limb a smith fitted one with after they lost their own (Jaymo's Powers' own
 * wooden and iron limb sheets, the same box). The scouter is Jaymo's Powers' own, drawn from its armour sheet on a box just off the head (so
 * a resource pack that repaints the scouter repaints these too; without Powers there is no scouter to draw). A made
 * limb is plating of our own ({@code tools/gen_frost_tech.py}, in the scouter's greys and green light) drawn over the
 * arm or leg, just proud of the sleeve, with its lamps unlit by the dark. Both are worn models ({@link WornModel}), so they
 * take the body's pose as it is drawn, a ragdoll's or a downed body's too. The scouter is hidden under a helmet or a
 * hood, a limb under a costume, and nothing is drawn on the invisible.
 */
public final class FrostTechLayer {
	/** How far a made limb stands off the bare limb: just past MCA's clothes (their outer layer sits at 0.3125). */
	static final float LIMB_INFLATE = 0.36F;
	/** The scouter's box off the head: just past the hat layer (0.5) and MCA's hair, close to the face, not a helmet's full 1.0. */
	static final float SCOUTER_INFLATE = 0.6F;

	/** On a living body's render state: {@link FrostTech#of}, with {@link FrostTech#kinds} above it (shifted 8), or null for none. */
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:frost_tech");
	private static final Identifier SCOUTER = Identifier.fromNamespaceAndPath("jaymospowers", "textures/entity/equipment/humanoid/scouter.png");
	private static final Identifier LIMB = JaymosKingdoms.id("textures/entity/frost_tech/limb.png");
	private static final Identifier WOOD = Identifier.fromNamespaceAndPath("jaymospowers", "textures/entity/wounds/wooden_limb.png");
	private static final Identifier IRON = Identifier.fromNamespaceAndPath("jaymospowers", "textures/entity/wounds/iron_limb.png");
	private static final Identifier LIMB_GLOW = JaymosKingdoms.id("textures/entity/frost_tech/limb_glow.png");
	private static final boolean POWERS = FabricLoader.getInstance().isModLoaded("jaymospowers");

	private FrostTechLayer() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?> && !(renderer.getModel() instanceof PlayerModel)) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> create(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: what this body wears (nothing for anyone but a grown villager or a patrolman). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		int worn = FrostTech.of(entity);
		state.setData(KEY, worn == 0 ? null : worn | FrostTech.kinds(entity) << 8);
	}

	/**
	 * The humanoid's bones (posed from the wearer each frame, WornModel), with boxes on the head for the scouter, or on the
	 * four limbs for the plating.
	 */
	private static ModelPart bones(boolean head, float inflate, int sheetW, int sheetH) {
		CubeDeformation off = new CubeDeformation(inflate);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", head ? CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, off) : CubeListBuilder.create(),
			PartPose.ZERO).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		// each in its bone's own space, as the humanoid model's boxes are
		root.addOrReplaceChild("right_arm", limb(!head, -3.0F, -2.0F, off), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", limb(!head, -1.0F, -2.0F, off), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", limb(!head, -2.0F, 0.0F, off), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", limb(!head, -2.0F, 0.0F, off), PartPose.offset(1.9F, 12.0F, 0.0F));
		return LayerDefinition.create(mesh, sheetW, sheetH).bakeRoot();
	}

	private static CubeListBuilder limb(boolean box, float x, float y, CubeDeformation off) {
		return box ? CubeListBuilder.create().texOffs(0, 0).addBox(x, y, -2.0F, 4.0F, 12.0F, 4.0F, off) : CubeListBuilder.create();
	}

	/**
	 * Right after a model is posed to be drawn, after every other pose and hide (FrostTechModelFeatureRendererMixin): a
	 * stand-in limb is drawn where its own limb is gone. Jaymo's Powers' lost-limb hide (and its stump) and Kitchen's
	 * butchered corpse hide the parts by name, these with them; the fitted limbs are put back. A severed piece (only one
	 * part drawn) is left as Powers made it.
	 */
	public static void reshow(Object model, Object state) {
		if (model instanceof Limbs limbs && state instanceof HumanoidRenderState humanoid) {
			limbs.show(humanoid);
		}
	}

	/**
	 * The scouter, or one make's limbs: worn models (WornModel), so drawn in the body's own pose at the moment it is drawn,
	 * the ragdoll's, the downed body's and any other laid over it then. They were boxes drawn on the bones as the layers
	 * ran, before those poses: a dead or downed villager's fitted leg stood up on its own while the body lay (Andy,
	 * 2026-11-19).
	 */
	private static final class Scouter extends WornModel {
		Scouter(Supplier<HumanoidModel<?>> wearer) {
			super(bones(true, SCOUTER_INFLATE, 64, 32), wearer);
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			this.hat.visible = false;
			this.body.visible = false;
		}
	}

	/** One make's limbs ({@link FrostTech#WOOD}, IRON or MADE): the limbs of that make show, the rest don't. */
	private static final class Limbs extends WornModel {
		private final int make;
		/** Each limb's length as the wearer's own (Powers' stump shortens a lost one's after). */
		private final float[] length = new float[4];

		Limbs(Supplier<HumanoidModel<?>> wearer, int make) {
			super(bones(false, LIMB_INFLATE, 16, 16), wearer);
			this.make = make;
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			this.head.visible = false;
			this.body.visible = false;
			ModelPart[] limbs = limbs();
			for (int i = 0; i < limbs.length; i++) {
				this.length[i] = limbs[i].yScale;
			}
			show(state);
		}

		/** The limbs of this make shown, at the wearer's own length. (Not asked whether the wearer's limb shows: a lost limb's is hidden, and this stands in for it.) */
		void show(HumanoidRenderState state) {
			Integer data = state.getData(KEY);
			int kinds = data == null ? 0 : data >> 8;
			ModelPart[] limbs = limbs();
			for (int i = 0; i < limbs.length; i++) {
				limbs[i].visible = (kinds >> 2 * i & 3) == this.make;
				limbs[i].yScale = this.length[i];
			}
		}

		private ModelPart[] limbs() {
			return new ModelPart[] {this.rightArm, this.leftArm, this.rightLeg, this.leftLeg};
		}
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		private final Scouter scouter = new Scouter(this::getParentModel);
		private final Limbs wood = new Limbs(this::getParentModel, FrostTech.WOOD);
		private final Limbs iron = new Limbs(this::getParentModel, FrostTech.IRON);
		private final Limbs made = new Limbs(this::getParentModel, FrostTech.MADE);

		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Integer data = state.getData(KEY);
			if (data == null || state.isInvisible || state.getData(BioForms.KEY) != null) {
				return;
			}
			int worn = data & 0xFF, kinds = data >> 8;
			if (POWERS && (worn & FrostTech.SCOUTER_BIT) != 0 && state.headEquipment.isEmpty() && !PowersCover.head(state)) {
				collector.order(1).submitModel(this.scouter, state, poseStack, RenderTypes.entityCutout(SCOUTER), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
			}
			if ((worn & FrostTech.LIMBS) == 0 || PowersCover.body(state)) {
				return;
			}
			boolean[] has = new boolean[4];
			for (int i = 0; i < 4; i++) {
				int make = kinds >> 2 * i & 3;
				if (make != FrostTech.NONE) {
					has[make] = true;
				}
			}
			if (has[FrostTech.WOOD]) {
				collector.order(1).submitModel(this.wood, state, poseStack, RenderTypes.entityCutout(WOOD), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
			}
			if (has[FrostTech.IRON]) {
				collector.order(1).submitModel(this.iron, state, poseStack, RenderTypes.entityCutout(IRON), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
			}
			if (has[FrostTech.MADE]) {
				collector.order(1).submitModel(this.made, state, poseStack, RenderTypes.entityCutout(LIMB), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
				collector.order(2).submitModel(this.made, state, poseStack, RenderTypes.eyes(LIMB_GLOW), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
			}
		}
	}
}

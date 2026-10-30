package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Valor;
import com.jaymoskingdoms.ValorWings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.jaymoskingdoms.client.bb.BbModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.410) A Skyward Valor's wings ({@link ValorWings}), drawn on whoever has them out: the Valor's own look (iron plates,
 * stone, scales, ash feathers, a heat haze, black lacquer; {@code tools/gen_valor_wings.py}), fading as the pool runs down,
 * the King's coating's soul colour beating along their ribs. They move the way Xenoform Wyrm's do: in flight they beat, and
 * fold back at speed; on the ground, coated, they wrap round the body like a cloak, and swing out of the way of your own
 * blows; when they catch a blow they snap open and flap.
 * <p>
 * Each Valor's wings are one of the pack's dragons' ({@link ValorWings#DESIGNS}, {@code tools/gen_valor_wing_designs.py}):
 * the ender dragon's shape (Xenoform Wyrm's), Notus and Boreas's fire dragon wings (nyxaris's "Fire Dragon", CC BY 4.0),
 * Zephyrus's short ragged wings, or Eurus and Aeolus's wyvern wings (eanncrod's "Kintsugi", CC BY 4.0), all laid out the same
 * way (a wing along x with its membrane trailing back, and a tip) so the same poses move them, recoloured into the look.
 */
public final class ValorWingsLook {
	/** The wings' least show as the pool empties (alpha share). */
	static final float FADE_FLOOR = 0.25F;
	/** In flight: the beat's speed and depth; folded at speed, the yaw they sweep back to and their lift; how fast is fast (blocks a tick). */
	static final float FLAP_RATE = 0.55F, FLAP_DEPTH = 0.95F, FOLDED_YAW = 1.35F, FOLDED_LIFT = 0.15F, FAST_FROM = 0.3F, FAST_FULL = 0.6F;
	/** How far the tips bend behind the beat. */
	static final float TIP_FLEX = 0.6F;
	/** Wrapped round the body (by design): hanging down, swung forward round the sides, the tips folded in across the chest. */
	static final float[] WRAP_PITCH = {-Mth.HALF_PI, -Mth.HALF_PI, -Mth.HALF_PI, -0.67F}, WRAP_FOLD = {-2.6F, -2.6F, -2.6F, -2.0F};
	static final float WRAP_YAW = -0.7F;
	/** Each design's size (its wings are drawn from its dragon's at this much). */
	static final float[] SCALE = {1.0F, 0.36F, 0.6F, 0.115F};
	/** A swing: how far the wrapped wings open out of its way; a flap's speed and depth. */
	static final float SWING_CLEAR = 0.85F, COUNTER_RATE = 1.3F, COUNTER_DEPTH = 1.1F;
	/** Where each wing roots on the back (model pixels from the neck); the bone's and the tip's length. */
	static final float ROOT_X = 2.0F, ROOT_Y = 2.0F, ROOT_Z = 2.5F;
	static final int BONE = 13, TIP = 12;

	public static final RenderStateDataKey<Wings> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:valor_wings");

	/** Wings out: the look, flying, a flap just now, how full the pool is, King's coating and its soul colour, how fast. */
	public record Wings(int design, int look, boolean flying, boolean flap, float fade, boolean kings, int soul, float speed) {
	}

	private static final Identifier[][] TEXTURES = new Identifier[ValorWings.DESIGNS.length][Valor.Look.values().length];
	private static final Identifier[] VEINS = new Identifier[ValorWings.DESIGNS.length];

	private ValorWingsLook() {
	}

	/** wings_<look> for the wyrm's (the first), wings_<design>_<look> for the rest. */
	static Identifier texture(int design, int look) {
		int i = Mth.clamp(look, 0, Valor.Look.values().length - 1);
		if (TEXTURES[design][i] == null) {
			TEXTURES[design][i] = JaymosKingdoms.id("textures/entity/valor/wings_" + prefix(design) + Valor.Look.values()[i].key() + ".png");
		}
		return TEXTURES[design][i];
	}

	static Identifier veins(int design) {
		if (VEINS[design] == null) {
			VEINS[design] = JaymosKingdoms.id("textures/entity/valor/wings_" + prefix(design) + "veins.png");
		}
		return VEINS[design];
	}

	private static String prefix(int design) {
		return design == 0 ? "" : ValorWings.DESIGNS[design] + "_";
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> || renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(layer(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: the wings, while they're out. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		if (!ValorWings.out(entity) || entity.isInvisible()) {
			state.setData(KEY, null);
			return;
		}
		Valor.Data data = Valor.of(entity);
		if (data == null) {
			state.setData(KEY, null);
			return;
		}
		int bits = Valor.state(entity);
		state.setData(KEY, new Wings(data.wingDesign(), data.look(), (bits & Valor.FLYING) != 0, (bits & Valor.FLAP) != 0, Valor.poolShare(entity),
			(bits & Valor.KINGS) != 0, data.soulColor(), (float) entity.getDeltaMovement().horizontalDistance()));
	}

	/** On every humanoid (players, MCA's villagers, our own folk): the wings over the body. */
	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		/** By design, made when first drawn. */
		private final WingsModel[] models = new WingsModel[ValorWings.DESIGNS.length];

		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Wings wings = state.getData(KEY);
			if (wings == null || state.isInvisible) {
				return;
			}
			int design = Mth.clamp(wings.design(), 0, this.models.length - 1);
			if (this.models[design] == null) {
				this.models[design] = new WingsModel(this::getParentModel, design);
			}
			WingsModel model = this.models[design];
			float fade = FADE_FLOOR + (1.0F - FADE_FLOOR) * Mth.clamp(wings.fade(), 0.0F, 1.0F);
			collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(texture(design, wings.look())), lightCoords,
				OverlayTexture.NO_OVERLAY, ARGB.white(fade), null, state.outlineColor);
			if (wings.kings()) {
				collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(veins(design)), lightCoords, OverlayTexture.NO_OVERLAY,
					ARGB.scaleRGB(ValorLook.glow(wings.soul(), state.ageInTicks), fade), null, state.outlineColor);
			}
		}
	}

	/** The wings: a humanoid with nothing on it but them, so they ride the wearer's body through every pose. */
	static final class WingsModel extends WornModel {
		private final int design;
		private final ModelPart rightWing, leftWing, rightTip, leftTip;

		WingsModel(Supplier<HumanoidModel<?>> wearer, int design) {
			super(root(design), wearer);
			this.design = design;
			this.rightWing = this.body.getChild("right_wing");
			this.leftWing = this.body.getChild("left_wing");
			this.rightTip = this.rightWing.getChild("right_wing_tip");
			this.leftTip = this.leftWing.getChild("left_wing_tip");
		}

		/** A humanoid with nothing on it but the wings: the wyrm's and Zephyrus's built here, Notus's and Eurus's baked. */
		private static ModelPart root(int design) {
			if (design == 1 || design == 3) {
				BbModel bb = BbModel.load(JaymosKingdoms.MOD_ID, "valor_wings_" + ValorWings.DESIGNS[design]);
				ModelPart right = bb.part("right_wing"), left = bb.part("left_wing");
				PartPose r = PartPose.offset(-ROOT_X, ROOT_Y, ROOT_Z).withScale(SCALE[design]), l = PartPose.offset(ROOT_X, ROOT_Y, ROOT_Z).withScale(SCALE[design]);
				right.setInitialPose(r);
				right.loadPose(r);
				left.setInitialPose(l);
				left.loadPose(l);
				Map<String, ModelPart> wings = new HashMap<>();
				wings.put("right_wing", right);
				wings.put("left_wing", left);
				Map<String, ModelPart> parts = new HashMap<>();
				parts.put("head", new ModelPart(List.of(), new HashMap<>(Map.of("hat", empty()))));
				parts.put("body", new ModelPart(List.of(), wings));
				for (String limb : new String[] {"right_arm", "left_arm", "right_leg", "left_leg"}) {
					parts.put(limb, empty());
				}
				return new ModelPart(List.of(), parts);
			}
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
			if (design == 2) {
				zephyrus(body);
				return LayerDefinition.create(mesh, 256, 256).bakeRoot();
			}
			wyrm(body);
			return LayerDefinition.create(mesh, 64, 64).bakeRoot();
		}

		private static ModelPart empty() {
			return new ModelPart(List.of(), new HashMap<>());
		}

		/** Zephyrus's wings (ZephyrusDragonModel's own boxes, on its sheet), small. */
		private static void zephyrus(PartDefinition body) {
			float s = SCALE[2];
			PartDefinition left = body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(94, 95).addBox(0.0F, -1.5F, -1.5F, 18, 3, 3)
				.texOffs(112, 0).addBox(0.0F, 0.0F, 1.5F, 18, 0, 26), PartPose.offset(ROOT_X, ROOT_Y, ROOT_Z).withScale(s));
			left.addOrReplaceChild("left_wing_tip", CubeListBuilder.create().texOffs(0, 103).addBox(0.0F, -1.0F, -1.0F, 20, 2, 2)
				.texOffs(0, 44).addBox(0.0F, 0.0F, 1.0F, 20, 0, 22), PartPose.offset(17.0F, 0.0F, 0.0F));
			PartDefinition right = body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(94, 95).mirror().addBox(-18.0F, -1.5F, -1.5F, 18, 3, 3)
				.texOffs(112, 0).addBox(-18.0F, 0.0F, 1.5F, 18, 0, 26), PartPose.offset(-ROOT_X, ROOT_Y, ROOT_Z).withScale(s));
			right.addOrReplaceChild("right_wing_tip", CubeListBuilder.create().texOffs(0, 103).mirror().addBox(-20.0F, -1.0F, -1.0F, 20, 2, 2)
				.texOffs(0, 44).addBox(-20.0F, 0.0F, 1.0F, 20, 0, 22), PartPose.offset(-17.0F, 0.0F, 0.0F));
		}

		/**
		 * The wyrm's: each wing a bone with its membrane trailing behind, and a tip with its own (texture: membrane 0,0; the
		 * tip's 0,14; the bone 0,26; the tip's bone 0,32).
		 */
		private static void wyrm(PartDefinition body) {
			PartDefinition left = body.addOrReplaceChild("left_wing", CubeListBuilder.create().mirror()
				.addBox("bone", 0.0F, -1.0F, -1.0F, BONE, 2, 2, 0, 26).addBox("skin", 0.0F, 0.0F, 0.0F, BONE, 0, 12, 0, 0),
				PartPose.offset(ROOT_X, ROOT_Y, ROOT_Z));
			left.addOrReplaceChild("left_wing_tip", CubeListBuilder.create().mirror()
				.addBox("bone", 0.0F, -0.5F, -0.5F, TIP, 1, 1, 0, 32).addBox("skin", 0.0F, 0.0F, 0.0F, TIP, 0, 10, 0, 14),
				PartPose.offset(BONE, 0.0F, 0.0F));
			PartDefinition right = body.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.addBox("bone", -BONE, -1.0F, -1.0F, BONE, 2, 2, 0, 26).addBox("skin", -BONE, 0.0F, 0.0F, BONE, 0, 12, 0, 0),
				PartPose.offset(-ROOT_X, ROOT_Y, ROOT_Z));
			right.addOrReplaceChild("right_wing_tip", CubeListBuilder.create()
				.addBox("bone", -TIP, -0.5F, -0.5F, TIP, 1, 1, 0, 32).addBox("skin", -TIP, 0.0F, 0.0F, TIP, 0, 10, 0, 14),
				PartPose.offset(-BONE, 0.0F, 0.0F));
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			Wings wings = state.getData(KEY);
			if (wings == null) {
				return;
			}
			float t = state.ageInTicks;
			float fly = wings.flying() ? 1.0F : 0.0F;
			float fold = fly * smooth(Mth.clamp((wings.speed() - FAST_FROM) / (FAST_FULL - FAST_FROM), 0.0F, 1.0F));
			// a blow of your own: the wrapped wings swing out of its way, swept back; a flap snaps them open
			float swing = state.swingAnimation > 0.0F ? Mth.sin(state.swingAnimation * Mth.PI) : 0.0F;
			float flap = wings.flap() ? 1.0F : 0.0F;
			float wrap = (1.0F - fly) * (1.0F - SWING_CLEAR * swing) * (1.0F - flap);
			float sweep = Math.max(fold, (1.0F - fly) * swing * (1.0F - flap));

			// open: a slow stir, the deep strokes of flight dying away as they fold at speed, or a flap's sharp beats
			float idle = Mth.sin(t * 0.25F) * 0.05F;
			float beat = Mth.lerp(fly, idle, Mth.sin(t * FLAP_RATE) * FLAP_DEPTH * (1.0F - fold));
			beat += flap * Mth.sin(t * COUNTER_RATE) * COUNTER_DEPTH;
			float openZ = -0.3F + beat + fold * FOLDED_LIFT;
			float openY = Mth.lerp(sweep, 0.6F + Mth.cos(t * 0.25F) * 0.1F, FOLDED_YAW);
			float openTip = -(Mth.sin(t * FLAP_RATE - 2.0F) + 0.5F) * TIP_FLEX * fly * (1.0F - fold);

			// wrapped: the cloak sways with each step
			float walk = state.walkAnimationSpeed;
			float step = Mth.sin(state.walkAnimationPos * 0.6662F) * walk;
			float wrapX = WRAP_PITCH[this.design] + walk * 0.15F;
			float wrapY = WRAP_YAW + Mth.abs(step) * 0.06F;
			float wrapFold = WRAP_FOLD[this.design] + walk * 0.25F;

			this.rightWing.xRot = Mth.lerp(wrap, 0.0F, wrapX);
			this.leftWing.xRot = Mth.lerp(wrap, 0.0F, wrapX);
			this.rightWing.yRot = Mth.lerp(wrap, openY, wrapY + step * 0.08F);
			this.leftWing.yRot = Mth.lerp(wrap, -openY, -wrapY + step * 0.08F);
			this.rightWing.zRot = Mth.lerp(wrap, openZ, 0.0F);
			this.leftWing.zRot = Mth.lerp(wrap, -openZ, 0.0F);
			this.rightTip.zRot = Mth.lerp(wrap, openTip, wrapFold);
			this.leftTip.zRot = Mth.lerp(wrap, -openTip, -wrapFold);
		}

		private static float smooth(float x) {
			return x * x * (3.0F - 2.0F * x);
		}
	}
}

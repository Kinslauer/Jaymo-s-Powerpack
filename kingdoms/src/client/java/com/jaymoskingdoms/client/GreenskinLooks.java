package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Greenskins;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.292) A goblin's or an orc's look as an MCA villager ({@link Greenskins}): their green is MCA's skin
 * ({@code McaVerdantSkinMixin}); on it, drawn here and tinted by the same green, their features and marks
 * (textures from {@code tools/gen_greenskins.py}):
 * <ul>
 * <li><b>Goblins</b>, in one of two shapes: long ears out to the sides and a hooked nose, or tall bat ears and a needle
 * nose on a warty bulb; and one of four sets of warts, wrinkles and blotches.</li>
 * <li><b>Orcs</b>: tusks up from the jaw, a heavy brow and small swept ears, or an underbite with long tusks, a heavier
 * brow and one ear torn short; one of four sets of marks, and one of four of scars (three with war paint: soot, blood
 * or bone-white). Tusks, scars and paint keep their own colours.</li>
 * </ul>
 * Each one's shape and marks are fixed by its UUID. Clothes drawn over the body hide the marks under them.
 */
public final class GreenskinLooks {
	/** On a villager's render state: its brood, which shape, which marks (and an orc's scars), and its green. */
	record Look(Heritage.Kind kind, int shape, int marks, int scars, int color) {
	}

	static final RenderStateDataKey<Look> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:greenskin_look");
	/** The shapes and the sets of marks (and of scars) there are of each. */
	static final int SHAPES = 2, MARKS = 4;
	/** How far a goblin's long ears lift and its bat ears lean out, and an orc's ears sweep back (radians). */
	static final float EAR_LIFT = 0.2F, BAT_LEAN = 0.45F, EAR_SWEEP = 0.45F;

	private static final Identifier GOBLIN_FEATURES = JaymosKingdoms.id("textures/entity/greenskin/goblin_features.png");
	private static final Identifier ORC_FEATURES = JaymosKingdoms.id("textures/entity/greenskin/orc_features.png");
	private static final Identifier[] GOBLIN_MARKS = new Identifier[MARKS], ORC_MARKS = new Identifier[MARKS], ORC_SCARS = new Identifier[MARKS];
	/** Each brood's shapes: its flesh (tinted) and its bone (tusks, as they are), made the first time they're drawn. */
	private static final ModelPart[][] FLESH = new ModelPart[2][SHAPES], BONE = new ModelPart[2][SHAPES];

	static {
		for (int i = 0; i < MARKS; i++) {
			GOBLIN_MARKS[i] = JaymosKingdoms.id("textures/entity/greenskin/goblin_marks_" + i + ".png");
			ORC_MARKS[i] = JaymosKingdoms.id("textures/entity/greenskin/orc_marks_" + i + ".png");
			ORC_SCARS[i] = JaymosKingdoms.id("textures/entity/greenskin/orc_scars_" + i + ".png");
		}
	}

	private GreenskinLooks() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (!(renderer instanceof AvatarRenderer<?>) && renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> create(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState ({@link VillagerLooks#extract}): a goblin's or an orc's look, or none. True if it's one. */
	static boolean extract(LivingEntity entity, LivingEntityRenderState state) {
		Heritage.Kind kind = entity instanceof Player || entity instanceof com.jaymoskingdoms.Folk ? null : Greenskins.of(entity);
		if (kind == null) {
			state.setData(KEY, null);
			return false;
		}
		long bits = entity.getUUID().getLeastSignificantBits();
		state.setData(KEY, new Look(kind, (int)Math.floorMod(bits >>> 3, (long)SHAPES), (int)Math.floorMod(bits >>> 7, (long)MARKS),
			(int)Math.floorMod(bits >>> 11, (long)MARKS), Greenskins.skin(entity)));
		return true;
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Look look = state.getData(KEY);
			if (look == null || state.isInvisible) {
				return;
			}
			HumanoidModel<S> model = this.getParentModel();
			HumanoidModel<S> marks = model;
			if (McaCompat.LOADED) {
				HumanoidModel<S> mca = mcaMarks(model);
				if (mca != null) {
					marks = mca;
				}
			}
			boolean orc = look.kind() == Heritage.Kind.ORC;
			Identifier markTex = (orc ? ORC_MARKS : GOBLIN_MARKS)[look.marks()];
			collector.order(1).submitModel(marks, state, poseStack, RenderTypes.entityCutout(markTex), lightCoords, OverlayTexture.NO_OVERLAY,
				look.color(), null, state.outlineColor);
			if (orc) {
				collector.order(1).submitModel(marks, state, poseStack, RenderTypes.entityCutout(ORC_SCARS[look.scars()]), lightCoords,
					OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			}
			if (!model.head.visible) {
				return;
			}
			Identifier features = orc ? ORC_FEATURES : GOBLIN_FEATURES;
			int brood = orc ? 1 : 0;
			bake(brood, look.shape());
			poseStack.pushPose();
			model.head.translateAndRotate(poseStack);
			collector.order(1).submitModelPart(FLESH[brood][look.shape()], poseStack, RenderTypes.entityCutout(features), lightCoords,
				OverlayTexture.NO_OVERLAY, null, look.color());
			if (BONE[brood][look.shape()] != null) {
				collector.order(1).submitModelPart(BONE[brood][look.shape()], poseStack, RenderTypes.entityCutout(features), lightCoords,
					OverlayTexture.NO_OVERLAY, null, -1);
			}
			poseStack.popPose();
		}
	}

	@SuppressWarnings("unchecked")
	private static <S extends HumanoidRenderState> @Nullable HumanoidModel<S> mcaMarks(HumanoidModel<S> parent) {
		return (HumanoidModel<S>) McaLookModels.marks(parent);
	}

	/**
	 * Brood {@code brood}'s (0 goblin, 1 orc) shape {@code shape}, in head space (the head is -4..4 across, -8..0 up, its face
	 * at z -4), texture offsets as {@code tools/gen_greenskins.py} lays them out (64x32).
	 */
	private static void bake(int brood, int shape) {
		if (FLESH[brood][shape] != null) {
			return;
		}
		MeshDefinition flesh = new MeshDefinition(), bone = new MeshDefinition();
		PartDefinition f = flesh.getRoot(), b = bone.getRoot();
		boolean tusks = false;
		if (brood == 0 && shape == 0) {
			// long ears out to the sides, tips lifted, and a hooked nose
			f.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -1.5F, -0.5F, 5.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(-4.0F, -4.5F, 0.0F, 0.0F, 0.0F, EAR_LIFT));
			f.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(0, 4).mirror().addBox(0.0F, -1.5F, -0.5F, 5.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(4.0F, -4.5F, 0.0F, 0.0F, 0.0F, -EAR_LIFT));
			f.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(12, 0).addBox(-1.0F, -4.0F, -6.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
			f.addOrReplaceChild("hook", CubeListBuilder.create().texOffs(12, 4).addBox(-0.5F, -2.0F, -6.5F, 1.0F, 1.0F, 1.0F), PartPose.ZERO);
		} else if (brood == 0) {
			// tall bat ears leaning out, a needle nose on a warty bulb
			f.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(20, 0).addBox(-1.0F, -6.0F, -0.5F, 2.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(-4.0F, -4.0F, 0.0F, 0.0F, 0.0F, -BAT_LEAN));
			f.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(26, 0).mirror().addBox(-1.0F, -6.0F, -0.5F, 2.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(4.0F, -4.0F, 0.0F, 0.0F, 0.0F, BAT_LEAN));
			f.addOrReplaceChild("needle", CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, -3.5F, -7.0F, 1.0F, 1.0F, 3.0F), PartPose.ZERO);
			f.addOrReplaceChild("bulb", CubeListBuilder.create().texOffs(40, 0).addBox(-1.0F, -4.0F, -5.0F, 2.0F, 2.0F, 1.0F), PartPose.ZERO);
		} else if (shape == 0) {
			// tusks up from the jaw, a heavy brow, small ears swept back
			tusks = true;
			b.addOrReplaceChild("right_tusk", CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, -3.0F, -5.0F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);
			b.addOrReplaceChild("left_tusk", CubeListBuilder.create().texOffs(4, 16).addBox(2.0F, -3.0F, -5.0F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);
			f.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -6.0F, -5.0F, 8.0F, 1.0F, 1.0F), PartPose.ZERO);
			f.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 4).addBox(-1.0F, -1.5F, 0.0F, 1.0F, 3.0F, 2.0F),
				PartPose.offsetAndRotation(-4.0F, -4.5F, -0.5F, 0.0F, -EAR_SWEEP, 0.0F));
			f.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(6, 4).mirror().addBox(0.0F, -1.5F, 0.0F, 1.0F, 3.0F, 2.0F),
				PartPose.offsetAndRotation(4.0F, -4.5F, -0.5F, 0.0F, EAR_SWEEP, 0.0F));
		} else {
			// an underbite with long tusks out of it, a heavier brow, the right ear torn short
			tusks = true;
			f.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 10).addBox(-3.0F, -1.5F, -5.0F, 6.0F, 2.0F, 1.0F), PartPose.ZERO);
			b.addOrReplaceChild("right_tusk", CubeListBuilder.create().texOffs(8, 16).addBox(-3.0F, -4.0F, -5.5F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
			b.addOrReplaceChild("left_tusk", CubeListBuilder.create().texOffs(12, 16).addBox(2.0F, -4.0F, -5.5F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
			f.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(18, 0).addBox(-4.0F, -6.5F, -5.0F, 8.0F, 2.0F, 1.0F), PartPose.ZERO);
			f.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(12, 4).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 2.0F, 2.0F),
				PartPose.offsetAndRotation(-4.0F, -4.5F, -0.5F, 0.0F, -EAR_SWEEP, 0.0F));
			f.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(6, 4).mirror().addBox(0.0F, -1.5F, 0.0F, 1.0F, 3.0F, 2.0F),
				PartPose.offsetAndRotation(4.0F, -4.5F, -0.5F, 0.0F, EAR_SWEEP, 0.0F));
		}
		FLESH[brood][shape] = LayerDefinition.create(flesh, 64, 32).bakeRoot();
		BONE[brood][shape] = tusks ? LayerDefinition.create(bone, 64, 32).bakeRoot() : null;
	}
}

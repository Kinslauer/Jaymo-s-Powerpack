package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.costume.CostumeModels;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Prosthetics;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.82) A wooden or iron limb ({@link Prosthetics}) drawn on the player where the lost arm or leg would be: a plain
 * vanilla-style box the size of the limb it stands in for, in pegged planks or riveted iron
 * ({@code tools/gen_prosthetics.py}). Worn the way {@link DressingsLayer}'s bandages are ({@link CostumeModels#worn}), so
 * it takes whatever pose the body is drawn in. The missing limb itself stays hidden by {@link com.jaymospowers.client.Dismembered}.
 */
public final class ProstheticLayer {
	/** On a living body's render state: the fitted limbs ({@link Dismember} bits) and, above them (shifted 8), which are iron. */
	public static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymospowers:prosthetics");
	private static final Identifier WOOD = JaymosPowers.id("textures/entity/wounds/wooden_limb.png");
	private static final Identifier IRON = JaymosPowers.id("textures/entity/wounds/iron_limb.png");
	private static final String PIECE = "prosthetic";
	/** The worn models, so {@link com.jaymospowers.client.Dismembered} doesn't hide the limb they draw. */
	private static final Set<Model<?>> OWNED = Collections.newSetFromMap(new IdentityHashMap<>());

	private ProstheticLayer() {
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer.getModel() instanceof PlayerModel) {
				helper.register(layer(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent)renderer);
	}

	/** Whether {@code model} is one of ours (it draws a limb that Dismember would otherwise hide). */
	public static boolean owns(Model<?> model) {
		return OWNED.contains(model);
	}

	/** From every living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, EntityRenderState state) {
		int fitted = Prosthetics.fitted(entity);
		state.setData(KEY, fitted == 0 ? null : fitted | Prosthetics.ironMask(entity) << 8);
	}

	private static LayerDefinition limbs(boolean slimArms) {
		MeshDefinition mesh = CostumeModels.bareBones(slimArms);
		PartDefinition root = mesh.getRoot();
		float arm = slimArms ? 3.0F : 4.0F;
		// as the player model's own boxes: an arm hangs from its shoulder (-2 to 10), the right one's inner side at x 1, the left's at -1
		root.getChild("right_arm").addOrReplaceChild(PIECE, CubeListBuilder.create().texOffs(0, 0).addBox(1.0F - arm, -2.0F, -2.0F, arm, 12.0F, 4.0F),
			PartPose.ZERO);
		root.getChild("left_arm").addOrReplaceChild(PIECE, CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.0F, -2.0F, arm, 12.0F, 4.0F),
			PartPose.ZERO);
		root.getChild("right_leg").addOrReplaceChild(PIECE, CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
			PartPose.ZERO);
		root.getChild("left_leg").addOrReplaceChild(PIECE, CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
			PartPose.ZERO);
		return LayerDefinition.create(mesh, 16, 16);
	}

	/** One renderer's worn models, for wide arms and slim: wood and iron. */
	private static final class Worn {
		private final PlayerModel[] wide = new PlayerModel[2], slim = new PlayerModel[2];
		private final java.util.function.Supplier<PlayerModel> wearer;

		Worn(java.util.function.Supplier<PlayerModel> wearer) {
			this.wearer = wearer;
		}

		PlayerModel get(boolean iron, boolean slimArms) {
			PlayerModel[] models = slimArms ? this.slim : this.wide;
			int kind = iron ? 1 : 0;
			if (models[kind] == null) {
				models[kind] = CostumeModels.worn(limbs(slimArms), slimArms, this.wearer, (model, state) -> show(model, state, iron));
				OWNED.add(models[kind]);
			}
			return models[kind];
		}
	}

	/** As a worn model animates, for the player it's drawn on: only the limbs of its own material that are fitted. */
	private static void show(PlayerModel model, AvatarRenderState state, boolean iron) {
		int data = fitted(state);
		int ironBits = data >> 8 & data;
		int mask = iron ? ironBits : data & ~ironBits;
		piece(model.rightArm, mask & Dismember.RIGHT_ARM);
		piece(model.leftArm, mask & Dismember.LEFT_ARM);
		piece(model.rightLeg, mask & Dismember.RIGHT_LEG);
		piece(model.leftLeg, mask & Dismember.LEFT_LEG);
	}

	private static void piece(ModelPart bone, int shown) {
		if (bone.hasChild(PIECE)) {
			bone.getChild(PIECE).visible = shown != 0;
			if (shown != 0) {
				bone.visible = true;
			}
		}
	}

	/** The fitted limbs on this body just now, with the iron flags above them, or 0 for nothing (invisible too). */
	private static int fitted(LivingEntityRenderState living) {
		Integer data = living.getData(KEY);
		return data == null || living.isInvisible ? 0 : data;
	}

	static final class Layer<S extends LivingEntityRenderState> extends RenderLayer<S, EntityModel<S>> {
		private @Nullable Worn worn;

		Layer(RenderLayerParent<S, EntityModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S living, float yRot, float xRot) {
			int data = fitted(living);
			if (data == 0 || !(living instanceof AvatarRenderState state) || !(this.getParentModel() instanceof PlayerModel parent)) {
				return;
			}
			if (this.worn == null) {
				this.worn = new Worn(() -> (PlayerModel)this.getParentModel());
			}
			CostumeModels.wear(state, parent);
			boolean slimArms = CostumeModels.slim(parent, state);
			int iron = data >> 8 & data;
			if ((data & ~iron & 0xFF) != 0) {
				renderColoredCutoutModel(this.worn.get(false, slimArms), WOOD, poseStack, collector, lightCoords, state, -1, 1);
			}
			if (iron != 0) {
				renderColoredCutoutModel(this.worn.get(true, slimArms), IRON, poseStack, collector, lightCoords, state, -1, 1);
			}
		}
	}
}

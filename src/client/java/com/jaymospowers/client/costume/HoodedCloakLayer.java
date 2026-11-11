package com.jaymospowers.client.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.HiddenArmor;
import com.jaymospowers.costume.Costume;
import com.jaymospowers.costume.HoodedCloak;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
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
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;

/**
 * (0.21.41) The cloak a hood from {@link HoodedCloak#HOODS} (the Archers' archer hood) is joined to, drawn on every player
 * who wears one: a collar and shoulder mantle the hood's hem meets at the neck, and a fitted back panel to mid-calf in two
 * hinged halves that lift and flutter as you go. With the hood lowered ({@link HoodedCloak#down}) the hood itself isn't
 * drawn on the head; it lies bunched on the upper back as a cowl, its point hanging down the cloak.
 * <p>Our own boxes (below) and paint ({@code tools/gen_hooded_cloak.py}); see docs/Hooded-Cloak-Spec.md. The boxes are
 * built for a wearer with chest armour on (the Archers' tunic stands out a pixel from the body) and pulled in about each
 * part's middle when there's none.
 */
public final class HoodedCloakLayer {
	// ===== the knobs ================================================================================================
	/** The back panel's tilt (radians): at rest, added at a full run, flutter at a run, idle sway. */
	static final float REST = 0.05F, RUN_LIFT = 0.32F, FLUTTER = 0.05F, IDLE = 0.02F;
	/** The lower half's extra tilt over the upper's at a full run, and its flutter. */
	static final float HEM_LIFT = 0.14F, HEM_FLUTTER = 0.06F;
	/** Tilt added while crouching: the body already leans, so the cloak drops back a little toward hanging straight. */
	static final float CROUCH_LIFT = -0.15F;
	/** How far the collar, mantles and back are pulled in without chest armour (x and z scale; z shift of the back). */
	static final float BARE_COLLAR_X = 0.82F, BARE_COLLAR_Z = 0.8F, BARE_MANTLE = 0.8F, BARE_BACK_Z = -0.9F;
	// ================================================================================================================

	static final int TEX_W = 64, TEX_H = 64;
	/** On a player's render state: null for no cloak, else the cloak texture to draw (per hood). */
	static final RenderStateDataKey<Identifier> KEY = RenderStateDataKey.create(() -> "jaymospowers:hooded_cloak");

	private HoodedCloakLayer() {
	}

	static ModelLayerLocation layer(boolean slim) {
		return new ModelLayerLocation(JaymosPowers.id("hooded_cloak"), slim ? "slim" : "wide");
	}

	public static void init() {
		for (boolean slim : new boolean[] {false, true}) {
			ModelLayerRegistry.registerModelLayer(layer(slim), () -> build(slim));
		}
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar, context.getModelSet()));
			}
		});
	}

	/**
	 * From the avatar renderer's extractRenderState: whether this player shows the cloak, and with the hood down the hood
	 * comes off the head (the armour layer skips it). A costume over everything, or the hood hidden with the armour eye,
	 * shows no cloak. Detects hoods in the vanilla head armour slot and picks the cloak texture by which hood is worn.
	 */
	public static void extract(Avatar entity, AvatarRenderState state) {
		if (!(entity instanceof Player player) || Costume.worn(player) != null
			|| HiddenArmor.hidden(player, EquipmentSlot.HEAD)) {
			state.setData(KEY, null);
			return;
		}
		Identifier texture = null;
		ItemStack headStack = player.getItemBySlot(EquipmentSlot.HEAD);
		if (headStack.is(HoodedCloak.HOODS)) {
			switch (BuiltInRegistries.ITEM.getKey(headStack.getItem()).getPath()) {
				case "ranger_armor_head" ->
					texture = JaymosPowers.id("textures/entity/worn/hooded_cloak_ranger.png");
				case "netherite_ranger_armor_head" ->
					texture = JaymosPowers.id("textures/entity/worn/hooded_cloak_netherite_ranger.png");
				default ->
					texture = JaymosPowers.id("textures/entity/worn/hooded_cloak.png");
			}
		}
		if (texture == null) {
			state.setData(KEY, null);
			return;
		}
		boolean down = HoodedCloak.down(player);
		state.setData(KEY, texture);
		state.showCape = false;
		if (down) {
			state.headEquipment = ItemStack.EMPTY;
		}
	}

	// ---- the boxes (texture offsets must match tools/gen_hooded_cloak.py) --------------------------------------------

	static LayerDefinition build(boolean slim) {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);
		PartDefinition root = mesh.getRoot().clearRecursively();
		PartDefinition body = root.getChild("body");
		// the collar: a yoke round the top of the chest and back, where the hood's hem comes down to meet it
		body.addOrReplaceChild("collar", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-5.5F, -0.6F, -3.0F, 11, 3, 6)
			.texOffs(12, 31).addBox(-1.0F, 0.4F, -3.6F, 2, 2, 1), PartPose.ZERO);
		// the back: two halves hinged at the shoulders and at the small of the back; the hood's point hangs on the upper
		PartDefinition back = body.addOrReplaceChild("back", CubeListBuilder.create()
			.texOffs(0, 9).addBox(-5.5F, 0.0F, 0.0F, 11, 10, 1), PartPose.offset(0.0F, 0.6F, 3.0F));
		back.addOrReplaceChild("hem", CubeListBuilder.create()
			.texOffs(24, 9).addBox(-6.0F, 0.0F, 0.0F, 12, 10, 1), PartPose.offset(0.0F, 10.0F, 0.0F));
		back.addOrReplaceChild("point", CubeListBuilder.create()
			.texOffs(0, 31).addBox(-2.5F, 2.0F, 1.0F, 5, 4, 1), PartPose.ZERO);
		// the lowered hood: bunched on the upper back, its mouth open at the top
		body.addOrReplaceChild("cowl", CubeListBuilder.create()
			.texOffs(26, 20).addBox(-4.5F, -1.4F, 2.6F, 9, 4, 4), PartPose.ZERO);
		// the mantle over each shoulder, on the arm so it rides every swing; pivoted on the arm's middle
		float mid = slim ? -0.5F : -1.0F;
		root.getChild("right_arm").addOrReplaceChild("mantle", CubeListBuilder.create()
			.texOffs(0, 20).addBox(-3.0F, -2.6F, -3.5F, 6, 4, 7), PartPose.offset(mid, 0.0F, 0.0F));
		root.getChild("left_arm").addOrReplaceChild("mantle", CubeListBuilder.create().mirror()
			.texOffs(0, 20).addBox(-3.0F, -2.6F, -3.5F, 6, 4, 7), PartPose.offset(-mid, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	// ---- the model and the layer --------------------------------------------------------------------------------

	static final class Model extends PlayerModel {
		final ModelPart collar, back, hem, point, cowl, rightMantle, leftMantle;

		Model(ModelPart root, boolean slim) {
			super(root, slim);
			this.collar = this.body.getChild("collar");
			this.back = this.body.getChild("back");
			this.hem = this.back.getChild("hem");
			this.point = this.back.getChild("point");
			this.cowl = this.body.getChild("cowl");
			this.rightMantle = this.rightArm.getChild("mantle");
			this.leftMantle = this.leftArm.getChild("mantle");
		}

		void pose(AvatarRenderState state, boolean down) {
			for (ModelPart part : new ModelPart[] {collar, back, hem, point, cowl, rightMantle, leftMantle}) {
				part.resetPose();
			}
			point.visible = down;
			cowl.visible = down;
			boolean armour = !state.chestEquipment.isEmpty();
			if (!armour) {
				collar.xScale = BARE_COLLAR_X;
				collar.zScale = BARE_COLLAR_Z;
				for (ModelPart mantle : new ModelPart[] {rightMantle, leftMantle}) {
					mantle.xScale = BARE_MANTLE;
					mantle.zScale = BARE_MANTLE;
				}
				back.z += BARE_BACK_Z;
				cowl.z += BARE_BACK_Z;
			}
			float stride = state.walkAnimationPos * 0.6662F;
			float pace = Math.min(1.0F, state.walkAnimationSpeed);
			float idle = Mth.sin(state.ageInTicks * 0.09F);
			back.xRot = REST + RUN_LIFT * pace + FLUTTER * pace * Mth.sin(stride * 2.0F) + IDLE * idle
				+ (state.isCrouching ? CROUCH_LIFT : 0.0F);
			// (the hem is cloth: it lags the back, swings on turns, billows in a fall)
			if (ClothCape.sample(state, 2, 1) >= 0.0F) {
				hem.xRot += ClothCape.REL_PITCH[1];
				hem.zRot += ClothCape.REL_ROLL[1];
			}
			hem.xRot += HEM_LIFT * pace + HEM_FLUTTER * pace * Mth.sin(stride * 2.0F + 1.2F) + IDLE * Mth.sin(state.ageInTicks * 0.09F + 1.2F);
		}
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final EntityModelSet models;
		private Model wide, slim;

		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityModelSet models) {
			super(renderer);
			this.models = models;
		}

		private Model model(boolean isSlim) {
			if (isSlim) {
				return slim == null ? slim = new Model(models.bakeLayer(layer(true)), true) : slim;
			}
			return wide == null ? wide = new Model(models.bakeLayer(layer(false)), false) : wide;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Identifier texture = state.getData(KEY);
			if (texture == null || state.isInvisible) {
				return;
			}
			// hood down when the head slot was cleared in extract()
			boolean down = state.headEquipment.isEmpty();
			Model model = model(state.skin != null && state.skin.model() == PlayerModelType.SLIM);
			model.setupAnim(state);
			model.pose(state, down);
			collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(texture), lightCoords, OverlayTexture.NO_OVERLAY, -1,
				null, state.outlineColor);
		}
	}
}

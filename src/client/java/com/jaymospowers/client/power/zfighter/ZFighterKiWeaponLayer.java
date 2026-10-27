package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.costume.CostumeModels;
import com.jaymospowers.power.zfighter.ZFighterKiWeapon;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.45, Andy's NOEA picks) The ki weapon ({@link ZFighterKiWeapon}) in his ki colour: a blade, a scythe or a clawed
 * lance standing out of the right fist, along the forearm so it follows every punch and pose. Plain boxes drawn added
 * onto what is behind them (the ki glow's energy texture), a gentle flicker, nothing else.
 */
public final class ZFighterKiWeaponLayer {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_ki_glow.png");
	/** How bright it burns, and how much it flickers. */
	static final float LIGHT = 0.85F, FLICKER = 0.12F, FLICKER_SPEED = 0.4F;

	private ZFighterKiWeaponLayer() {
	}

	/** The three shapes as children of the right arm (the hand is at y = 10 in the arm's own space), {@code cx} the arm's middle. */
	private static LayerDefinition definition(boolean slim) {
		MeshDefinition mesh = CostumeModels.bareBones(slim);
		PartDefinition arm = mesh.getRoot().getChild("right_arm");
		float cx = slim ? -0.5F : -1.0F;
		// the blade: broad at the fist, tapering to a point
		arm.addOrReplaceChild("weapon0", CubeListBuilder.create().texOffs(0, 0)
			.addBox(cx - 1.0F, 9.0F, -0.5F, 2.0F, 8.0F, 1.0F)
			.addBox(cx - 0.7F, 17.0F, -0.5F, 1.4F, 4.0F, 1.0F)
			.addBox(cx - 0.3F, 21.0F, -0.4F, 0.6F, 3.0F, 0.8F), PartPose.ZERO);
		// the scythe: a long haft, a curved blade swept out to the side and hooked back down at its end
		arm.addOrReplaceChild("weapon1", CubeListBuilder.create().texOffs(0, 0)
			.addBox(cx - 0.5F, 9.0F, -0.5F, 1.0F, 12.0F, 1.0F)
			.addBox(cx - 0.5F, 21.0F, -0.5F, 1.0F, 1.5F, 1.0F)
			.addBox(cx - 9.0F, 19.5F, -0.4F, 9.0F, 1.2F, 0.8F)
			.addBox(cx - 9.8F, 16.5F, -0.4F, 1.2F, 4.2F, 0.8F), PartPose.ZERO);
		// the clawed lance: a shaft, a leaf head with a long point, two claws curving off it
		arm.addOrReplaceChild("weapon2", CubeListBuilder.create().texOffs(0, 0)
			.addBox(cx - 0.6F, 9.0F, -0.6F, 1.2F, 12.0F, 1.2F)
			.addBox(cx - 0.9F, 21.0F, -0.5F, 1.8F, 3.0F, 1.0F)
			.addBox(cx - 0.4F, 24.0F, -0.4F, 0.8F, 4.0F, 0.8F)
			.addBox(cx - 2.6F, 14.5F, -0.4F, 5.2F, 0.8F, 0.8F)
			.addBox(cx - 2.6F, 15.3F, -0.4F, 0.8F, 5.5F, 0.8F)
			.addBox(cx + 1.8F, 15.3F, -0.4F, 0.8F, 5.5F, 0.8F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	public static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private PlayerModel wide, slim;

		public Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		private PlayerModel model(boolean slimArms) {
			PlayerModel made = slimArms ? this.slim : this.wide;
			if (made == null) {
				made = CostumeModels.worn(definition(slimArms), slimArms, this::getParentModel, (model, state) -> {
					Player player = player(state);
					int shape = player == null ? -1 : ZFighterKiWeapon.out(player);
					for (int i = 0; i < ZFighterKiWeapon.SHAPES; i++) {
						model.rightArm.getChild("weapon" + i).visible = i == shape;
					}
				});
				if (slimArms) {
					this.slim = made;
				} else {
					this.wide = made;
				}
			}
			return made;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Player player = player(state);
			if (player == null || state.isInvisible || ZFighterKiWeapon.out(player) < 0 || !player.getMainHandItem().isEmpty()) {
				return;
			}
			PlayerModel parent = this.getParentModel();
			CostumeModels.wear(state, parent);
			boolean slimArms = CostumeModels.slim(parent, state);
			Integer form = player.getAttached(ZFighterPower.FORM);
			int color = ZFighterKiGlow.color(player, form == null ? 0 : form);
			float light = Mth.clamp(LIGHT + FLICKER * Mth.sin(state.ageInTicks * FLICKER_SPEED), 0.0F, 1.0F);
			int tint = ARGB.color(255, (int)(ARGB.red(color) * light), (int)(ARGB.green(color) * light), (int)(ARGB.blue(color) * light));
			collector.order(5).submitModel(model(slimArms), state, poseStack, RenderTypes.energySwirl(TEXTURE, 0.0F, -(state.ageInTicks * 0.01F) % 1.0F),
				lightCoords, OverlayTexture.NO_OVERLAY, tint, null, 0);
		}

		private static Player player(AvatarRenderState state) {
			var level = Minecraft.getInstance().level;
			return level != null && level.getEntity(state.id) instanceof Player found ? found : null;
		}
	}
}

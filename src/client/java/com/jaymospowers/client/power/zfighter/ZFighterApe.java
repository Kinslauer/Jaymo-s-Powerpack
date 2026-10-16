package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Great Ape's body, drawn in the player's place on everyone's screen (as Xenoform draws its aliens): the player is
 * hidden and a shaggy ape in his pose stands there instead, at his (giant) size. A big furred head with a jutting
 * muzzle, a heavy brow and red eyes; a barrel chest with a shoulder hump; long arms hanging to the knees; short thick
 * legs; a tail. Its skin is {@code textures/entity/zfighter_great_ape.png} ({@code tools/gen_great_ape.py}).
 */
public final class ZFighterApe {
	/** Whether this player is the Great Ape, on his render state. */
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_great_ape");
	/** (0.12.3) Whether the Great Ape lies crippled (flat on his back), on his render state. */
	public static final RenderStateDataKey<Boolean> CRIPPLED = RenderStateDataKey.create(() -> "jaymospowers:zfighter_ape_crippled");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_great_ape.png");

	private static Model model;

	private ZFighterApe() {
	}

	/** From the avatar renderer's extractRenderState: the Great Ape hides the player's own body, armour and held items. */
	static void extract(Avatar entity, AvatarRenderState state) {
		boolean ape = entity instanceof Player player && ZFighterLineage.ape(player) && !player.isInvisible();
		state.setData(KEY, ape);
		if (!ape) {
			return;
		}
		state.setData(CRIPPLED, ZFighterLineage.crippled((Player)entity));
		state.isInvisible = true;
		state.isInvisibleToPlayer = true;
		state.headEquipment = ItemStack.EMPTY;
		state.chestEquipment = ItemStack.EMPTY;
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
		state.headItem.clear();
		state.wornHeadType = null;
		state.wornHeadProfile = null;
		state.heldOnHead.clear();
		state.rightHandItemState.clear();
		state.leftHandItemState.clear();
		state.leftHandItemStack = ItemStack.EMPTY;
		state.showCape = false;
		state.showExtraEars = false;
		state.parrotOnLeftShoulder = null;
		state.parrotOnRightShoulder = null;
		state.arrowCount = 0;
		state.stingerCount = 0;
	}

	static Model model() {
		if (model == null) {
			model = new Model();
		}
		return model;
	}

	/** The ape: a humanoid model, so it walks, swings and looks round as he does. */
	static final class Model extends HumanoidModel<AvatarRenderState> {
		private final ModelPart tail;

		Model() {
			super(LayerDefinition.create(mesh(), 128, 128).bakeRoot());
			this.tail = this.body.getChild("tail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-5.0F, -9.0F, -5.0F, 10.0F, 9.0F, 9.0F)
				.texOffs(40, 0).addBox(-3.5F, -4.5F, -8.0F, 7.0F, 5.0F, 3.0F)
				.texOffs(64, 0).addBox(-5.0F, -9.5F, -6.0F, 10.0F, 2.0F, 2.0F)
				.texOffs(90, 0).addBox(-7.0F, -7.0F, -1.0F, 2.0F, 3.0F, 1.0F)
				.texOffs(90, 0).mirror().addBox(5.0F, -7.0F, -1.0F, 2.0F, 3.0F, 1.0F), PartPose.ZERO);
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 20).addBox(-6.0F, -1.0F, -3.5F, 12.0F, 13.0F, 7.0F)
				.texOffs(40, 20).addBox(-5.0F, -2.0F, 0.0F, 10.0F, 4.0F, 4.0F), PartPose.ZERO);
			body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 70).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 12.0F),
				PartPose.offsetAndRotation(0.0F, 10.5F, 3.0F, -0.6F, 0.0F, 0.0F));
			root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 44).addBox(-5.0F, -2.0F, -2.5F, 5.0F, 17.0F, 5.0F),
				PartPose.offset(-6.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(24, 44).addBox(0.0F, -2.0F, -2.5F, 5.0F, 17.0F, 5.0F),
				PartPose.offset(6.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(48, 44).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 12.0F, 6.0F),
				PartPose.offset(-3.0F, 12.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(76, 44).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 12.0F, 6.0F),
				PartPose.offset(3.0F, 12.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float time = state.ageInTicks;
			float walk = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			// the arms hang a little out from the bulk of him and sway heavily
			this.rightArm.zRot += 0.12F + 0.03F * Mth.sin(time * 0.07F);
			this.leftArm.zRot -= 0.12F + 0.03F * Mth.sin(time * 0.07F);
			// the tail swings, more as he lumbers along
			this.tail.yRot = (0.25F + 0.35F * walk) * Mth.sin(time * (0.08F + 0.12F * walk));
			this.tail.xRot = -0.6F + 0.1F * Mth.sin(time * 0.05F);
		}
	}

	/** Draws the ape over the hidden player. Registered after the tail and hair layers, which the hidden body skips. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			if (!Boolean.TRUE.equals(state.getData(KEY))) {
				return;
			}
			boolean down = Boolean.TRUE.equals(state.getData(CRIPPLED));
			if (down) {
				// crippled: laid flat on his back, about his feet (model space: y down, the feet 1.5 below the origin)
				poseStack.pushPose();
				poseStack.translate(0.0F, 1.5F, 0.0F);
				poseStack.rotateDegrees(com.mojang.math.Axis.XP, -90.0F);
				poseStack.translate(0.0F, -1.5F, -0.25F);
			}
			// not the "copy layer" call: that skips invisible states, and the body was made invisible
			renderColoredCutoutModel(model(), TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
			if (down) {
				poseStack.popPose();
			}
			// let his shadow fall (the renderer skips it for invisible states, after the layers)
			state.isInvisible = false;
		}
	}
}

package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterAfterlife;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.62, Andy's NOEA picks) The halo of the dead over the head of every player who woke in the Other World
 * ({@link ZFighterAfterlife#STAY}, synced to everyone). (0.17.143, Andy: "remove the particle halo and replace it with divine
 * born's for everyone") It is the Divine-born's ring now (Kingdoms' {@code HeritageLooks} halo: a flat gold ring of four
 * thin bars, lit, bobbing over the head), no longer dust. A Divine-born already wears theirs, so they get no second one.
 */
public final class ZFighterHalo {
	/** Whether this player wears the halo of the dead. */
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_halo");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_halo.png");
	static final int FULL_BRIGHT = 0xF000F0;
	/** How far above the top of the head it floats (model pixels), how far it bobs, and how fast (radians a tick). */
	static final float RISE = 4.0F, BOB = 0.5F, SPEED = 0.08F;

	private static ModelPart halo;

	private ZFighterHalo() {
	}

	/** The ring, made the first time it's drawn (head space: the top of the head at y -8). */
	private static ModelPart halo() {
		if (halo == null) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("ring", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-5.0F, 0.0F, -5.0F, 10.0F, 0.0F, 1.0F)
				.texOffs(0, 2).addBox(-5.0F, 0.0F, 4.0F, 10.0F, 0.0F, 1.0F)
				.texOffs(0, 4).addBox(-5.0F, 0.0F, -4.0F, 1.0F, 0.0F, 8.0F)
				.texOffs(0, 14).addBox(4.0F, 0.0F, -4.0F, 1.0F, 0.0F, 8.0F), PartPose.ZERO);
			halo = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		}
		return halo;
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		boolean dead = entity instanceof Player player && player.isAlive() && !player.isSpectator() && ZFighterAfterlife.among(player)
			&& !ZFighterLineage.divine(player);
		state.setData(KEY, dead ? Boolean.TRUE : null);
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			if (state.getData(KEY) == null || state.isInvisible || !this.getParentModel().head.visible) {
				return;
			}
			poseStack.pushPose();
			this.getParentModel().head.translateAndRotate(poseStack);
			poseStack.translate(0.0F, (-8.0F - RISE + Mth.sin(state.ageInTicks * SPEED) * BOB) / 16.0F, 0.0F);
			collector.order(2).submitModelPart(halo(), poseStack, RenderTypes.eyes(TEXTURE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, -1);
			poseStack.popPose();
		}
	}
}

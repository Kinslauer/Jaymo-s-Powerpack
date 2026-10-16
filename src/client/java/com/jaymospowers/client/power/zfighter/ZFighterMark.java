package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterCorruption;
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
 * A corrupted Z Fighter's mark ({@code textures/entity/zfighter_mark.png}): a crimson rune on his forehead, glowing, in
 * every form; in front of Super hair's fringe when he has it. In Corrupted mode it burns brighter and pulses.
 */
public final class ZFighterMark {
	/** 0 no mark, 1 the mark, 2 the mark burning (Corrupted mode). */
	public static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_mark");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_mark.png");
	static final int FULL_BRIGHT = 0xF000F0;
	/** How far in front of the face (pixels): over the skin's hat layer, or over Super hair's fringe. */
	static final float OVER_HAT = 4.56F;
	static final float OVER_HAIR = 4.75F;

	private static ModelPart mark;

	private ZFighterMark() {
	}

	private static ModelPart mark() {
		if (mark == null) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("mark", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -6.6F, 0.0F, 5.0F, 3.0F, 0.0F),
				PartPose.ZERO);
			mark = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("mark");
		}
		return mark;
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		int mark = 0;
		if (entity instanceof Player player && ZFighterCorruption.corrupted(player) && !ZFighterLineage.ape(player)) {
			mark = Boolean.TRUE.equals(player.getAttached(ZFighterCorruption.MODE)) ? 2 : 1;
		}
		state.setData(KEY, mark);
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Integer mark = state.getData(KEY);
			if (mark == null || mark == 0 || state.isInvisible || com.jaymospowers.client.BodyCover.face(state)) {
				return;
			}
			boolean hair = state.getData(ZFighterHair.KEY) != null;
			poseStack.pushPose();
			this.getParentModel().head.translateAndRotate(poseStack);
			poseStack.translate(0.0F, 0.0F, -(hair ? OVER_HAIR : OVER_HAT) / 16.0F);
			float glow = mark == 2 ? 0.8F + 0.2F * Mth.sin(state.ageInTicks * 0.5F) : 0.75F;
			int alpha = (int)(glow * 255.0F);
			collector.submitModelPart(mark(), poseStack, RenderTypes.eyes(TEXTURE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
				alpha << 24 | 0xFFFFFF);
			poseStack.popPose();
		}
	}
}

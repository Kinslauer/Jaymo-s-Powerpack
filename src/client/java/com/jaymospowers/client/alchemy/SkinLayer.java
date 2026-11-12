package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.picks.SkinDraughts;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;

/**
 * A skin draught's look: the whole body drawn over, once more, in the colour of the block that was touched
 * ({@link SkinDraughts#WORN}'s colour), a little see-through. Everyone's client draws everyone's.
 */
final class SkinLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** How much of the colour shows over the skin (0-255). */
	static final int ALPHA = 0x8C;

	SkinLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new SkinLayer(avatar));
			}
		});
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (state.isInvisible || minecraft.level == null || !(minecraft.level.getEntity(state.id) instanceof Player player)) {
			return;
		}
		SkinDraughts.Worn worn = player.getAttached(SkinDraughts.WORN);
		if (worn == null || state.skin == null) {
			return;
		}
		int tint = ALPHA << 24 | worn.rgb() & 0xFFFFFF;
		collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityTranslucent(state.skin.body().texturePath()), lightCoords,
			OverlayTexture.NO_OVERLAY, tint, null, EntityRenderState.NO_OUTLINE);
	}
}

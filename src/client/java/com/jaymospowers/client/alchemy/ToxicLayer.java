package com.jaymospowers.client.alchemy;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.toxic.HunterEyes;
import com.jaymospowers.alchemy.toxic.Toxicity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * What a body shows on any skin, drawn over it as a skin-shaped texture that is clear everywhere but the face: dark veins
 * round the eyes that darken as the load climbs past half ({@link Toxicity#GREEN_AT}), and, for good after a first monster
 * brew, hunter's eyes ({@link HunterEyes}): bright slit pupils, drawn lit. Everyone's client draws everyone's.
 */
final class ToxicLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier VEINS = JaymosPowers.id("textures/entity/toxic/veins.png");
	private static final Identifier EYES = JaymosPowers.id("textures/entity/toxic/hunter_eyes.png");
	/** The veins' greatest opacity (0-255) at a full load. */
	static final int VEIN_ALPHA = 0xE0;

	ToxicLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new ToxicLayer(avatar));
			}
		});
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (state.isInvisible || minecraft.level == null || !(minecraft.level.getEntity(state.id) instanceof Player player)) {
			return;
		}
		int total = Toxicity.total(player);
		if (total > Toxicity.GREEN_AT) {
			float t = Mth.clamp((total - Toxicity.GREEN_AT) / (float) (Toxicity.CAPACITY - Toxicity.GREEN_AT), 0.0F, 1.0F);
			int alpha = Math.max(40, Math.round(VEIN_ALPHA * t));
			collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityTranslucent(VEINS), lightCoords,
				OverlayTexture.NO_OVERLAY, alpha << 24 | 0xFFFFFF, null, EntityRenderState.NO_OUTLINE);
		}
		if (HunterEyes.has(player)) {
			collector.order(2).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(EYES), lightCoords,
				OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, EntityRenderState.NO_OUTLINE);
		}
	}
}

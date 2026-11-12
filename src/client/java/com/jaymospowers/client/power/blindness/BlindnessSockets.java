package com.jaymospowers.client.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.BodyCover;
import com.jaymospowers.client.McaLooks;
import com.jaymospowers.power.blindness.Blind;
import com.jaymospowers.power.blindness.BlindnessDevotion;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Andy: "your vision is gone even after they heal. leaving sockets where your eyes were." Whoever has lost an eye or
 * both ({@link Blind#eyesLost}) has a dark, empty socket there, for everyone to see, unless a blindfold or anything else
 * covers the face. Laid at the stock eye spots (face row 4), not found on each skin.
 */
final class BlindnessSockets extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/blindness_sockets.png");
	private static final Identifier LEFT = JaymosPowers.id("textures/entity/blindness_socket_left.png");
	private static final Identifier RIGHT = JaymosPowers.id("textures/entity/blindness_socket_right.png");

	private BlindnessSockets(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new BlindnessSockets(avatar));
			}
		});
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (state.isInvisible || minecraft.level == null || BodyCover.face(state)) {
			return;
		}
		Entity entity = minecraft.level.getEntity(state.id);
		if (!(entity instanceof Player player) || BlindnessDevotion.blindfolded(player)) {
			return;
		}
		int lost = Blind.eyesLost(player);
		if (lost == 0) {
			return;
		}
		Identifier texture = lost == Blind.BOTH ? TEXTURE : lost == Blind.LEFT ? LEFT : RIGHT;
		collector.order(1).submitModel(McaLooks.skinBody(this.getParentModel()), state, poseStack, RenderTypes.entityTranslucent(texture), lightCoords,
			OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
	}
}

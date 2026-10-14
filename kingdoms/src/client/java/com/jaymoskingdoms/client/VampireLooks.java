package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Vampires;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.22) What everyone sees of a vampire player: ashen skin on the face and hands always; at night, fangs and red
 * eyes that glow in the dark. Textures from {@code tools/gen_vampire_looks.py}, laid over the player's own skin.
 */
public final class VampireLooks extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** On a player's render state: 1 = a vampire by day, 2 = by night; absent for anyone else. */
	public static final RenderStateDataKey<Integer> LOOK = RenderStateDataKey.create(() -> "jaymoskingdoms:vampire_look");

	private static final Identifier PALE = JaymosKingdoms.id("textures/entity/vampire/pale.png");
	private static final Identifier FANGS = JaymosKingdoms.id("textures/entity/vampire/fangs.png");
	private static final Identifier EYES = JaymosKingdoms.id("textures/entity/vampire/eyes.png");

	private VampireLooks(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new VampireLooks(avatar));
			}
		});
	}

	/** Called from the render-state mixin: what {@code entity} shows this frame. */
	public static Integer look(LivingEntity entity) {
		if (!(entity instanceof Player) || !Vampires.isVampire(entity)) {
			return null;
		}
		return Vampires.night(entity.level().getOverworldClockTime()) ? 2 : 1;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Integer look = state.getData(LOOK);
		if (look == null || state.isInvisible) {
			return;
		}
		PlayerModel model = this.getParentModel();
		collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(PALE), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
			state.outlineColor);
		if (look == 2) {
			collector.order(2).submitModel(model, state, poseStack, RenderTypes.entityCutout(FANGS), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
			collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(EYES), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
		}
	}
}

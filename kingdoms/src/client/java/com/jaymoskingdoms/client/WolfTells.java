package com.jaymoskingdoms.client;

import com.jaymoskingdoms.HiddenChange;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * (0.7.323) What everyone sees of a werewolf player's hidden change ({@link HiddenChange}): coarse dark fur over the hands
 * and forearms, and the eyes flashing wolf yellow for a few ticks now and then, glowing in the dark. The eyes go where the
 * player's own skin has its eyes, found the way a vampire's red ones are ({@link VampireLooks#glowingEyes}). Textures from
 * {@code tools/gen_wolf_tells.py}.
 */
public final class WolfTells extends RenderLayer<AvatarRenderState, PlayerModel> {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The eyes flash for this many ticks at a time, and in about one window in this many. */
	static final int FLASH_TICKS = 5, FLASH_ONE_IN = 20;
	/** The eyes' wolf yellow (opaque ARGB). */
	static final int EYE_GLOW = 0xFFFFD424;

	private static final Identifier HANDS = JaymosKingdoms.id("textures/entity/werewolf/hairy_hands.png");
	private static final Identifier EYES = JaymosKingdoms.id("textures/entity/werewolf/eyes.png");
	/** Per skin texture: its own yellow-eyes texture (or the stock one). */
	private static final Map<Identifier, Identifier> EYES_BY_SKIN = new HashMap<>();

	private WolfTells(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new WolfTells(avatar));
			}
		});
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || Minecraft.getInstance().level == null) {
			return;
		}
		Entity who = Minecraft.getInstance().level.getEntity(state.id);
		int tells = who == null ? 0 : HiddenChange.tells(who);
		if (tells == 0 || PowersCover.face(state)) {
			// (nothing to show, or under a mask or the symbiote's suit)
			return;
		}
		PlayerModel model = this.getParentModel();
		if ((tells & HiddenChange.HAIRY_HANDS) != 0) {
			collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityCutout(HANDS), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
		}
		if ((tells & HiddenChange.FLASHING_EYES) != 0 && flashing(state)) {
			Identifier eyes = VampireLooks.glowingEyes(state.skin.body().texturePath(), EYE_GLOW, EYES, "wolf_eyes/", EYES_BY_SKIN);
			collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(eyes), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
		}
	}

	/** Whether the eyes are flashing now: a window of a few ticks, in a scattering of windows that differs for each player. */
	private static boolean flashing(AvatarRenderState state) {
		int window = (int)(state.ageInTicks / FLASH_TICKS);
		return Math.floorMod(Mth.murmurHash3Mixer(window * 31 + state.id * 7919), FLASH_ONE_IN) == 0;
	}
}

package com.jaymospowers.client.body;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.BodyCover;
import com.jaymospowers.client.McaLooks;
import com.jaymospowers.client.power.zfighter.ZFighterDancePose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * Faces that move (Punchy Moves' idea, our own code), for every body drawn with the player renderer, MCA's villagers
 * (and the players MCA draws as villagers) first: Andy: "mostly for MCA".
 * <ul>
 * <li><b>Blinking</b> is MCA's own for its villagers (its blink face, swapped in at random by {@code VillagerVisuals#isBlinking}),
 * and PlayerFaces' for players on ordinary skins; neither is made again here.</li>
 * <li><b>Wince:</b> for a few ticks after a hit the eyes shut (MCA's blink face again, through McaWinceBlinkMixin; the
 * closed eyes PlayerFaces already has for a blink on an ordinary skin), and the brows come down and the mouth tightens,
 * a small overlay on the face's own pixels ({@code face_wince.png}) laid over MCA's face layer or the skin.</li>
 * <li><b>Grin</b> while the body is doing a celebratory move (the fusion dance's), the mouth a toothy smile ({@code face_grin.png}).</li>
 * </ul>
 * MCA's faces are the 8x8 front of the head, 64x64 like the player's skin, so one overlay fits both; it is laid on MCA's
 * own body when MCA draws the player ({@link McaLooks#skinBody}) and not at all where a mask or helmet covers the face.
 */
public final class BodyFaces {
	// ===== the knobs ================================================================================================
	/** A hit's wince lasts this many ticks from the blow (the hurt flash is 10). */
	static final int WINCE_TICKS = 6;
	// ================================================================================================================
	private static final Identifier WINCE = JaymosPowers.id("textures/entity/face_wince.png");
	private static final Identifier GRIN = JaymosPowers.id("textures/entity/face_grin.png");

	/** On a render state: whether the face winces now. */
	public static final RenderStateDataKey<Boolean> WINCING = RenderStateDataKey.create(() -> "jaymospowers:face_wince");

	private BodyFaces() {
	}

	/** In the first few ticks after a hit. */
	public static boolean wincing(LivingEntity entity) {
		return entity.isAlive() && entity.hurtTime > 0 && entity.hurtDuration - entity.hurtTime < WINCE_TICKS;
	}

	/** Whether the entity with this id winces now (for MCA's blink face, which asks by id). */
	public static boolean wincing(int entityId) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level != null && minecraft.level.getEntity(entityId) instanceof LivingEntity living && wincing(living);
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(WINCING, wincing(entity) ? Boolean.TRUE : null);
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
	}

	/** The overlay: the wince's brows and mouth, or the grin's. */
	private static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			if (state.isInvisible || state.deathTime > 0.0F || BodyCover.face(state)) {
				return;
			}
			Identifier texture = state.getData(WINCING) != null ? WINCE : state.getData(ZFighterDancePose.KEY) != null ? GRIN : null;
			if (texture == null) {
				return;
			}
			collector.order(2).submitModel(McaLooks.skinBody(this.getParentModel()), state, poseStack, RenderTypes.entityTranslucent(texture), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
		}
	}
}

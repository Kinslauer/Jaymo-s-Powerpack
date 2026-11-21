package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.BodyCover;
import com.jaymospowers.client.McaLooks;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.power.web.WebSymbioteMend;
import com.jaymospowers.power.web.WebSymbioteStrain;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * A part the symbiote has corded back ({@link WebSymbioteMend#MENDED}) is the symbiote's own colour, pulsing, laid over
 * the skin (MCA's too); then it fades back to the skin. Each limb's texture holds just that limb's skin regions of the
 * suit (tools/gen_web_mend.py), so one model draw shows one limb. The eyes are MCA's own eye pixels where MCA draws
 * the player, else the stock eye spots.
 */
final class WebSymbioteMendLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier RIGHT_ARM = JaymosPowers.id("textures/entity/web_symbiote_mend_right_arm.png");
	private static final Identifier LEFT_ARM = JaymosPowers.id("textures/entity/web_symbiote_mend_left_arm.png");
	private static final Identifier RIGHT_LEG = JaymosPowers.id("textures/entity/web_symbiote_mend_right_leg.png");
	private static final Identifier LEFT_LEG = JaymosPowers.id("textures/entity/web_symbiote_mend_left_leg.png");
	private static final Identifier EYES = JaymosPowers.id("textures/entity/web_symbiote_mend_eyes.png");
	/** The eyes' colour on a player MCA draws, by strain (RGB). */
	private static final int BLACK_EYES = 0x0E0C10, WHITE_EYES = 0xE8E5DC, RED_EYES = 0x6E0C14;
	/** The pulse: this many ticks a beat, and how dark it gets between beats (1 = not at all). */
	static final float PULSE_TICKS = 24.0F, PULSE_LOW = 0.55F;

	WebSymbioteMendLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (state.isInvisible || minecraft.level == null) {
			return;
		}
		Entity entity = minecraft.level.getEntity(state.id);
		if (!(entity instanceof Player player)) {
			return;
		}
		List<WebSymbioteMend.Piece> pieces = WebSymbioteMend.mended(player);
		if (pieces.isEmpty()) {
			return;
		}
		long now = minecraft.level.getGameTime();
		WebSymbioteStrain strain = WebStrainLook.of(state);
		// a heartbeat: a quick swell, a slow ebb
		float beat = (state.ageInTicks % PULSE_TICKS) / PULSE_TICKS;
		float swell = beat < 0.2F ? beat / 0.2F : 1.0F - (beat - 0.2F) / 0.8F;
		float shade = Mth.lerp(swell * swell, PULSE_LOW, 1.0F);
		for (WebSymbioteMend.Piece piece : pieces) {
			float black = WebSymbioteMend.black(piece, now);
			if (black <= 0.0F) {
				continue;
			}
			int grey = Math.round(shade * 255.0F);
			int color = Math.round(black * 255.0F) << 24 | grey << 16 | grey << 8 | grey;
			if (piece.part() == WebSymbioteMend.EYES) {
				if (BodyCover.face(state)) {
					continue;
				}
				int eyes = strain == WebSymbioteStrain.WHITE ? WHITE_EYES : strain == WebSymbioteStrain.RED ? RED_EYES : BLACK_EYES;
				Identifier texture = McaLooks.faceEyes(state, this.getParentModel(), eyes, () -> WebStrainLook.texture(EYES, strain));
				collector.order(2).submitModel(McaLooks.glowBody(this.getParentModel()), state, poseStack, RenderTypes.entityTranslucent(texture),
					lightCoords, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor);
				continue;
			}
			Identifier limb = limb(piece.part());
			if (limb == null) {
				continue;
			}
			collector.order(1).submitModel(McaLooks.skinBody(this.getParentModel()), state, poseStack,
				RenderTypes.entityTranslucent(WebStrainLook.texture(limb, strain)), lightCoords, OverlayTexture.NO_OVERLAY, color, null,
				state.outlineColor);
		}
	}

	private static Identifier limb(int part) {
		return switch (part) {
			case Dismember.RIGHT_ARM -> RIGHT_ARM;
			case Dismember.LEFT_ARM -> LEFT_ARM;
			case Dismember.RIGHT_LEG -> RIGHT_LEG;
			case Dismember.LEFT_LEG -> LEFT_LEG;
			default -> null;
		};
	}
}

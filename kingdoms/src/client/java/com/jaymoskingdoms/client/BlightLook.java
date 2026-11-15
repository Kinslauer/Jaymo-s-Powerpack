package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BlightSigns;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The Hollow's blight as everyone sees it on a player (Andy's pick "Signs others can see"; the server's
 * {@link BlightSigns#SIGN}): the skin drawn over again in a grey wash leaning toward the Hollow being made, faint once it
 * takes hold and heavy near the end, and at the end flies about them (tiny dark specks, like those about rotten blood).
 * A full mask (or the symbiote's suit) hides the greying, as it hides blood.
 */
public final class BlightLook extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** On a blighted player's render state: the wash's colour (ARGB, its alpha the stage's strength). */
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:blight_look");

	/** The wash's strength by stage (alpha, 0-255), and how much of the Hollow's colour is mixed into the grey. */
	static final int[] ALPHA = {0, 70, 130};
	static final float TINT_SHARE = 0.35F;
	static final int GREY = 0x8C9088;
	/** Flies in the last stage: one look in this many ticks, at most this many specks, and how far about the body. */
	static final int FLY_EVERY = 4, FLIES = 2;
	static final double ROAM = 0.55;
	private static final DustParticleOptions FLY = new DustParticleOptions(0x0E0E0C, 0.32F);

	private BlightLook(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new BlightLook(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(BlightLook::tick);
	}

	/** From the living renderer's extractRenderState: a blighted player's wash. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		Integer sign = entity instanceof Player ? entity.getAttached(BlightSigns.SIGN) : null;
		state.setData(KEY, sign == null ? null : wash(sign));
	}

	/** The wash for a sign: the stage's strength, grey leaning toward the Hollow's colour. */
	static int wash(int sign) {
		int stage = Math.min(ALPHA.length - 1, Math.max(0, sign >>> 24));
		int tint = sign & 0xFFFFFF;
		int r = mix(GREY >> 16 & 0xFF, tint >> 16 & 0xFF), g = mix(GREY >> 8 & 0xFF, tint >> 8 & 0xFF), b = mix(GREY & 0xFF, tint & 0xFF);
		return ALPHA[stage] << 24 | r << 16 | g << 8 | b;
	}

	private static int mix(int grey, int tint) {
		return Math.round(grey + (tint - grey) * TINT_SHARE);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Integer wash = state.getData(KEY);
		if (wash == null || (wash >>> 24) == 0 || state.isInvisible || PowersCover.face(state)) {
			return;
		}
		PlayerModel model = VampireLooks.glowBody(this.getParentModel());
		collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(state.skin.body().texturePath()), lightCoords,
			OverlayTexture.NO_OVERLAY, wash, null, state.outlineColor);
	}

	/** Flies about anyone in the blight's last stage. */
	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused() || level.getGameTime() % FLY_EVERY != 0) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			Integer sign = player.getAttached(BlightSigns.SIGN);
			if (sign == null || (sign >>> 24) < 2 || player.isInvisible() || player.isSpectator()
				|| player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
				continue;
			}
			RandomSource random = player.getRandom();
			for (int i = random.nextInt(FLIES + 1); i > 0; i--) {
				level.addParticle(FLY, player.getX() + (random.nextDouble() - 0.5) * 2.0 * ROAM, player.getY() + 0.4 + random.nextDouble() * 1.4,
					player.getZ() + (random.nextDouble() - 0.5) * 2.0 * ROAM, (random.nextDouble() - 0.5) * 0.06, 0.0,
					(random.nextDouble() - 0.5) * 0.06);
			}
		}
	}
}

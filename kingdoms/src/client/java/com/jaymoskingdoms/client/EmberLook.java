package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.75) What everyone sees of an Emberborn player: embers under the skin of the hands and forearms and eyes of ember
 * gold, both glowing in the dark (drawn emissive over their own skin; textures from {@code tools/gen_ember_look.py}, the
 * eyes found on each skin the way a vampire's are, {@link VampireLooks#glowingEyes}), and small flames rising off their
 * hands now and then. Not in water or rain, and not round your own first-person camera.
 */
public final class EmberLook extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** On a player's render state: present for an Emberborn. */
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:emberborn");

	private static final Identifier MARKS = JaymosKingdoms.id("textures/entity/ember/marks.png");
	private static final Identifier EYES = JaymosKingdoms.id("textures/entity/ember/eyes.png");
	/** The eyes' ember gold (opaque ARGB). */
	static final int EYE_GLOW = 0xFFFFB43C;
	/** One flame off a hand in this many ticks, and a wisp of smoke with one in this many of those. */
	static final int FLAME_EVERY = 12, SMOKE_ONE_IN = 3;
	/** Per skin texture: its own ember eyes (or the stock ones). */
	private static final Map<Identifier, Identifier> EYES_BY_SKIN = new HashMap<>();

	private EmberLook(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new EmberLook(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(EmberLook::tick);
	}

	/** From the living renderer's extractRenderState: marks an Emberborn player's render state. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(KEY, entity instanceof Player player && Heritage.is(player, Heritage.Kind.EMBERBORN) ? Boolean.TRUE : null);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.getData(KEY) == null || state.isInvisible || PowersCover.face(state)) {
			// (0.7.81) under a mask or the symbiote's suit
			return;
		}
		PlayerModel model = this.getParentModel();
		collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(MARKS), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
			state.outlineColor);
		Identifier eyes = VampireLooks.glowingEyes(state.skin.body().texturePath(), EYE_GLOW, EYES, "ember_eyes/", EYES_BY_SKIN);
		collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(eyes), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
			state.outlineColor);
	}

	/** Every client tick: now and then a small flame off an Emberborn's hand, drifting up. */
	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			RandomSource random = player.getRandom();
			if (random.nextInt(FLAME_EVERY) != 0 || !Heritage.is(player, Heritage.Kind.EMBERBORN) || player.isInvisible() || player.isSpectator()
				|| player.isInWaterOrRain() || player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
				continue;
			}
			// a hand hangs at the side, a little below the waist
			float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
			double side = (random.nextBoolean() ? 1.0 : -1.0) * player.getBbWidth() * 0.6;
			double x = player.getX() - Mth.cos(yaw) * side, z = player.getZ() - Mth.sin(yaw) * side;
			double y = player.getY() + player.getBbHeight() * 0.4;
			level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0.0, 0.02, 0.0);
			if (random.nextInt(SMOKE_ONE_IN) == 0) {
				level.addParticle(ParticleTypes.SMOKE, x, y + 0.1, z, 0.0, 0.03, 0.0);
			}
		}
	}
}

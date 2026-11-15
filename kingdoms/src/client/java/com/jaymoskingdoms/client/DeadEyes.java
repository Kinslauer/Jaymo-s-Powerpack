package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Moons;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Eyes that glow (docs/Blight-Hordes-Plan.md, patch 5: etf-eyes): at night a cold light sits in the sockets of every zombie
 * and husk, brighter, and gone to the moon's red, under a blood moon ({@link Moons}). Drawn over the body in the game's
 * eyes render type, so it shows in the dark and moves with the head (Fresh Animations' too, through Entity Model
 * Features). The light is our own texture: the sockets of our old-dead skins (gen_old_dead.py's eyes.png), of the game's
 * baby layout (eyes_baby.png), and of Jaymo's Powers' future dead (eyes_future.png). A future zombie left to Tissou's
 * pack has its pack's own lit eyes, so it gets none of ours.
 */
public final class DeadEyes {
	/** How brightly they burn (alpha, 0..255) on an ordinary night and under a blood moon. */
	static final int NIGHT_GLOW = 150, BLOOD_GLOW = 255;
	/** Under a blood moon the light is multiplied toward this (the moon's red). */
	static final int BLOOD_TINT = 0xFF5A50;
	static final Identifier EYES = JaymosKingdoms.id("textures/entity/old_dead/eyes.png");
	static final Identifier BABY = JaymosKingdoms.id("textures/entity/old_dead/eyes_baby.png");
	static final Identifier FUTURE = JaymosKingdoms.id("textures/entity/old_dead/eyes_future.png");
	/** On a zombie or husk whose eyes are lit: the light's texture and colour. */
	static final RenderStateDataKey<Identifier> TEXTURE = RenderStateDataKey.create(() -> "jaymoskingdoms:dead_eyes");
	static final RenderStateDataKey<Integer> COLOUR = RenderStateDataKey.create(() -> "jaymoskingdoms:dead_eyes_colour");

	private DeadEyes() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if ((type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK) && renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(layer(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From OldDeadSkins.extract: a zombie's or husk's eyes, lit at night. */
	static void extract(LivingEntity zombie, LivingEntityRenderState state, boolean future, boolean tissou) {
		Level level = zombie.level();
		if (tissou || !level.isDarkOutside()) {
			return;
		}
		float blood = level.dimension() == Level.OVERWORLD
			&& (Moons.clientShown == Moons.Moon.BLOOD || Moons.clientShown == Moons.Moon.SUPER_BLOOD) ? Moons.clientFade : 0.0F;
		int alpha = Math.round(Mth.lerp(blood, NIGHT_GLOW, BLOOD_GLOW));
		int tint = ARGB.color(255, Math.round(Mth.lerp(blood, 255, BLOOD_TINT >> 16 & 0xFF)),
			Math.round(Mth.lerp(blood, 255, BLOOD_TINT >> 8 & 0xFF)), Math.round(Mth.lerp(blood, 255, BLOOD_TINT & 0xFF)));
		state.setData(TEXTURE, zombie.isBaby() ? BABY : future ? FUTURE : EYES);
		state.setData(COLOUR, ARGB.color(alpha, ARGB.red(tint), ARGB.green(tint), ARGB.blue(tint)));
	}

	static final class Layer<S extends LivingEntityRenderState> extends RenderLayer<S, EntityModel<S>> {
		Layer(RenderLayerParent<S, EntityModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Identifier texture = state.getData(TEXTURE);
			Integer colour = state.getData(COLOUR);
			if (texture == null || colour == null || state.isInvisible) {
				return;
			}
			collector.order(3).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(texture), lightCoords,
				OverlayTexture.NO_OVERLAY, colour, null, state.outlineColor);
		}
	}
}

package com.jaymospowers.client.blood;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.wet.ArmourGrimeTextures;
import com.jaymospowers.core.blood.Bloodied;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.20.41) Andy liked Hematic's blood on creatures (ideas only). A body splashed with blood ({@link Bloodied#COAT}) is
 * drawn with it over its skin: splotches from our own mask ({@code textures/blood/body_blood.png}), more as it gets
 * bloodier, laid only where the skin has pixels ({@link ArmourGrimeTextures#skin}), in its own blood's colour. It wears
 * off by itself and washes off in water.
 * <p>
 * (0.20.66) Andy: the blood "seemed to be under the skin". A body in the player's layout (players, MCA folk) has it on
 * its outer layer too (hat, jacket, sleeves, trousers), wherever that layer has pixels, so it lies on top of the clothes;
 * a player's downloaded skin is read for this (before, it was painted as a plain whole skin, base layer only).
 * <p>
 * (0.20.55) A corpse keeps the blood it died in ({@link Bloodied#CORPSE_BLOOD}), drying darker: Jaymo's Kitchen hands
 * each body's state to {@link #corpseBody} as it's drawn (its {@code CorpseDressing.BODY} hook, by name), and a dead
 * player drawn in their plain skin gets it through {@code EXTRACT} and {@code SUBMIT}.
 */
public final class BloodCoatLayer {
	static final RenderStateDataKey<Bloodied.Coat> KEY = RenderStateDataKey.create(() -> "jaymospowers:blood_coat");
	/** (0.20.55) A corpse's blood, drawn in place of a coat. */
	static final RenderStateDataKey<Bloodied.Blood> CORPSE = RenderStateDataKey.create(() -> "jaymospowers:corpse_blood");
	private static final String DRESSING = "com.jaymoskitchen.client.corpse.CorpseDressing";

	private BloodCoatLayer() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> helper.register((RenderLayer) layer(renderer)));
		hookCorpses();
	}

	/** (0.20.55) Kitchen's corpse drawing hooks; an older Kitchen just draws bodies clean. */
	@SuppressWarnings("unchecked")
	private static void hookCorpses() {
		if (!FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			return;
		}
		try {
			Class<?> dressing = Class.forName(DRESSING);
			((List<BiConsumer<Entity, EntityRenderState>>) dressing.getField("BODY").get(null)).add(BloodCoatLayer::corpseBody);
			((List<BiConsumer<Entity, AvatarRenderState>>) dressing.getField("EXTRACT").get(null)).add(BloodCoatLayer::corpseBody);
			((List<BiConsumer<AvatarRenderState, Object[]>>) dressing.getField("SUBMIT").get(null)).add(BloodCoatLayer::submitPlayerCorpse);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosPowers.LOGGER.warn("Jaymo's Kitchen has no body hook (older Kitchen?): corpses are drawn without their blood", e);
		}
	}

	/** (0.20.55) The body {@code corpse} is drawn as: its own blood, not whatever coat the stand-in body has. */
	private static void corpseBody(Entity corpse, EntityRenderState state) {
		state.setData(KEY, null);
		state.setData(CORPSE, Bloodied.corpseBlood(corpse));
	}

	/** (0.20.55) Just after a dead player's plain body: its blood over it, in its pose. */
	private static void submitPlayerCorpse(AvatarRenderState avatar, Object[] args) {
		Bloodied.Blood blood = avatar.getData(CORPSE);
		if (blood == null || args.length < 4 || !(args[0] instanceof PlayerModel body) || !(args[1] instanceof PoseStack poseStack)
			|| !(args[2] instanceof SubmitNodeCollector collector) || !(args[3] instanceof Integer light) || avatar.skin == null) {
			return;
		}
		Identifier painted = ArmourGrimeTextures.skin(avatar.skin.body().texturePath(), blood, true);
		if (painted != null) {
			collector.order(1).submitModel(body, avatar, poseStack, RenderTypes.entityCutout(painted), light, OverlayTexture.NO_OVERLAY,
				avatar.outlineColor);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static RenderLayer layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer(renderer);
	}

	/** From every living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(KEY, Bloodied.coat(entity));
		state.setData(CORPSE, null);
	}

	static final class Layer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
		private final LivingEntityRenderer<?, S, M> renderer;

		Layer(LivingEntityRenderer<?, S, M> renderer) {
			super(renderer);
			this.renderer = renderer;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Bloodied.Coat coat = state.getData(KEY);
			Bloodied.Blood dead = state.getData(CORPSE);
			if (coat == null && dead == null || state.isInvisible) {
				return;
			}
			Identifier skin;
			try {
				skin = this.renderer.getTextureLocation(state);
			} catch (RuntimeException e) {
				return;
			}
			// (0.20.66) a body in the player's layout wears the blood over its outer layer too
			boolean layered = this.getParentModel() instanceof PlayerModel;
			Identifier painted = skin == null ? null
				: dead != null ? ArmourGrimeTextures.skin(skin, dead, layered) : ArmourGrimeTextures.skin(skin, coat, layered);
			if (painted != null) {
				renderColoredCutoutModel(this.getParentModel(), painted, poseStack, collector, lightCoords, state, -1, 1);
			}
		}
	}
}

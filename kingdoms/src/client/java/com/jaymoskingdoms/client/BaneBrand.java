package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Banes;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.619, Rivals patch 4) The Bane's brand ({@link Banes#BRAND}): a faint red rune on a foe's brow, drawn glowing (the eyes
 * render type) for the one player whose brand it is, on MCA's body when MCA draws it ({@code tools/gen_brand.py}); and now and
 * then a wisp off it, so it can be seen from further off. Nobody else sees a thing.
 */
public final class BaneBrand {
	static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:bane_brand");
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/captain/brand.png");
	/** A wisp off each brand you see, every this many ticks. */
	static final int WISP_EVERY = 12;

	private BaneBrand() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(BaneBrand::wisps);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> create(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** Whether {@code entity} carries the brand of the player at this screen. */
	static boolean mine(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		String brand = entity.getAttached(Banes.BRAND);
		return brand != null && mc.player != null && brand.equals(mc.player.getStringUUID());
	}

	/** From the living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(KEY, mine(entity) ? Boolean.TRUE : null);
	}

	private static void wisps(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.getGameTime() % WISP_EVERY != 0) {
			return;
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.isAlive() && e.distanceToSqr(mc.player) < 48.0 * 48.0 && mine(e)) {
				mc.level.addParticle(ParticleTypes.SCULK_SOUL, e.getX(), e.getEyeY() + 0.2, e.getZ(), 0.0, 0.02, 0.0);
			}
		}
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			if (state.getData(KEY) == null || state.isInvisible || !this.getParentModel().head.visible) {
				return;
			}
			HumanoidModel<S> model = this.getParentModel();
			if (McaCompat.LOADED) {
				HumanoidModel<S> mca = mcaMarks(model);
				if (mca != null) {
					model = mca;
				}
			}
			collector.order(3).submitModel(model, state, poseStack, RenderTypes.eyes(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
		}
	}

	@SuppressWarnings("unchecked")
	private static <S extends HumanoidRenderState> HumanoidModel<S> mcaMarks(HumanoidModel<S> parent) {
		return (HumanoidModel<S>)McaLookModels.marks(parent);
	}
}

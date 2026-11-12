package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.Possession;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Red eyes on one turned for a while, steady ({@link Possession#EYES_RED}), or flickering on one with the Murmur
 * ({@link Possession#EYES_FLICKER}): docs/Pawn-Plan.md H1 and H2. An MCA villager's own eyes lit red ({@link McaEyes}),
 * anyone else's the stock red spots.
 */
public final class DarkEyes {
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:dark_eyes");
	static final int RED = 0xFFB0141A;
	/** A flicker: lit this many ticks out of each {@link #FLICKER_EVERY}, at a different moment for each body. */
	static final int FLICKER_EVERY = 40, FLICKER_LIT = 8;
	private static final Identifier STOCK = JaymosKingdoms.id("textures/entity/heritage/nightborn_blood_eyes.png");

	private DarkEyes() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (!(renderer instanceof AvatarRenderer<?>) && renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> create(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		Integer eyes = entity.getAttached(Possession.EYES);
		if (eyes != null && eyes == Possession.EYES_FLICKER && (state.ageInTicks + (entity.getId() * 7)) % FLICKER_EVERY >= FLICKER_LIT) {
			eyes = null;
		}
		state.setData(KEY, eyes);
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Integer eyes = state.getData(KEY);
			if (eyes == null || state.isInvisible) {
				return;
			}
			HumanoidModel<S> model = this.getParentModel();
			Identifier tex = STOCK;
			if (McaCompat.LOADED) {
				HumanoidModel<S> glow = mcaGlow(model);
				Identifier mca = McaEyes.glowing(state, RED);
				if (glow != null && mca != null) {
					model = glow;
					tex = mca;
				}
			}
			collector.order(3).submitModel(model, state, poseStack, RenderTypes.eyes(tex), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
		}
	}

	@SuppressWarnings("unchecked")
	private static <S extends HumanoidRenderState> @Nullable HumanoidModel<S> mcaGlow(HumanoidModel<S> parent) {
		return (HumanoidModel<S>) McaLookModels.glow(parent);
	}
}

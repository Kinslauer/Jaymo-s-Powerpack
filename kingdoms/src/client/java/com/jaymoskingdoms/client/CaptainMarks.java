package com.jaymoskingdoms.client;

import com.jaymoskingdoms.CaptainWays;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.616, Rivals patch 1) The marks a captain carries back from how he nearly died ({@link CaptainWays#MARKS}): a burned
 * face, an eye patch, an iron jaw, a stitched cheek, a pale eye, claw rakes. Painted in vanilla's style over his head
 * ({@code tools/gen_scars.py}), on MCA's body when MCA draws him (as {@link GreenskinLooks} lays its scars), on anything
 * humanoid. Illagers' bodies aren't humanoid models, so their marks don't show.
 */
public final class CaptainMarks {
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:captain_marks");
	private static final Identifier[] TEXTURES = new Identifier[CaptainWays.MARKS.size()];

	static {
		for (int i = 0; i < TEXTURES.length; i++) {
			TEXTURES[i] = JaymosKingdoms.id("textures/entity/captain/mark_" + CaptainWays.MARKS.get(i) + ".png");
		}
	}

	private CaptainMarks() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> create(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: the marks {@code entity} shows, if any. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		int bits = entity.getAttachedOrElse(CaptainWays.SHOWN, 0);
		state.setData(KEY, bits == 0 ? null : bits);
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Integer bits = state.getData(KEY);
			if (bits == null || state.isInvisible || !this.getParentModel().head.visible) {
				return;
			}
			HumanoidModel<S> model = this.getParentModel();
			if (McaCompat.LOADED) {
				HumanoidModel<S> mca = mcaMarks(model);
				if (mca != null) {
					model = mca;
				}
			}
			for (int i = 0; i < TEXTURES.length; i++) {
				if ((bits & (1 << i)) != 0) {
					collector.order(2).submitModel(model, state, poseStack, RenderTypes.entityCutout(TEXTURES[i]), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
						state.outlineColor);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <S extends HumanoidRenderState> @Nullable HumanoidModel<S> mcaMarks(HumanoidModel<S> parent) {
		return (HumanoidModel<S>)McaLookModels.marks(parent);
	}
}

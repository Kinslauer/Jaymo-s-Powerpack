package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.Webbed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Webbed up, as everyone sees it ({@link Webbed}; the medieval picks' de-wrap): a web skin over the whole body, its
 * model drawn again a little proud of itself in our own web (sparse, medium, dense by the layers it wears), and tufts of
 * web standing out of it, one a layer. The skin's way, the body drawn again just outside itself, and the tufts round
 * it are after Derelict's SpiderWebModelFeature (mim1q, MIT); the textures are our own ({@code tools/gen_webbed.py}).
 * Every living renderer gets it, players and Kingdoms' mobs among them; GeckoLib's bodies get it through
 * {@link WebbedGeoLayer}.
 */
public final class WebbedLook {
	static final RenderStateDataKey<Look> KEY = RenderStateDataKey.create(() -> "jaymospowers:webbed");
	/** How far proud of the body the web skin sits (blocks, along each face). */
	static final float PUFF = 0.035F;
	/** Tufts: how big (a share of the body's height, within these bounds, blocks), and how high each stands on it (shares). */
	static final float TUFT_SHARE = 0.45F, TUFT_LEAST = 0.35F, TUFT_MOST = 0.9F;
	static final float[] TUFT_HEIGHTS = {0.25F, 0.65F, 0.45F};
	/** Bodies lower than this (blocks) get no tufts; nor ones wider than this. */
	static final float TUFT_MIN_HEIGHT = 0.5F, TUFT_MAX_WIDTH = 4.0F;

	static final Identifier[] SKINS = {
		JaymosPowers.id("textures/entity/webbed/web_sparse.png"),
		JaymosPowers.id("textures/entity/webbed/web_medium.png"),
		JaymosPowers.id("textures/entity/webbed/web_dense.png"),
	};
	static final Identifier TUFT = JaymosPowers.id("textures/entity/webbed/web_tuft.png");

	/** What's drawn this frame: the layers, a turn of its own for the tufts, and the body's size (blocks). */
	record Look(int layers, float turn, float width, float height, float scale) {
	}

	/** Draws the model just outside itself: each face pushed out along its normal. */
	static final UvMapping PUFFED = new UvMapping() {
		@Override
		public float getU(float offset) {
			return offset;
		}

		@Override
		public float getV(float offset) {
			return offset;
		}

		@Override
		public VertexConsumer wrap(VertexConsumer buffer) {
			return new Puffed(buffer);
		}
	};

	private WebbedLook() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> helper.register((RenderLayer) new Layer(renderer)));
	}

	/** From every living renderer's extractRenderState (and GeckoLib's). */
	public static void extract(LivingEntity entity, EntityRenderState state) {
		int layers = Mth.clamp(Webbed.shown(entity), 0, Webbed.MOST);
		if (layers <= 0) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Look(layers, (entity.getId() * 777 % 360) * Mth.DEG_TO_RAD, entity.getBbWidth(), entity.getBbHeight(),
			Math.max(0.05F, entity.getScale())));
	}

	static Identifier skin(int layers) {
		return SKINS[Mth.clamp(layers, 1, SKINS.length) - 1];
	}

	/**
	 * Tufts of web standing out of the body, one a layer, round it a third of a turn apart. {@code yUp}: the pose is
	 * GeckoLib's (blocks, from the feet, up is up); else a living renderer's model space (flipped, the feet 1.501 down).
	 */
	static void tufts(PoseStack poseStack, SubmitNodeCollector collector, Look look, int light, boolean yUp) {
		if (look.height() < TUFT_MIN_HEIGHT || look.width() > TUFT_MAX_WIDTH) {
			return;
		}
		float unit = yUp ? 1.0F : 1.0F / look.scale();
		float size = Mth.clamp(look.height() * TUFT_SHARE, TUFT_LEAST, TUFT_MOST) * unit;
		float out = look.width() * 0.5F * unit;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TUFT), (pose, buffer) -> {
			for (int i = 0; i < look.layers(); i++) {
				float a = look.turn() + i * Mth.TWO_PI / 3.0F;
				float cos = Mth.cos(a), sin = Mth.sin(a);
				float up = look.height() * TUFT_HEIGHTS[i % TUFT_HEIGHTS.length] * unit;
				float y = yUp ? up : 1.501F - up;
				float cx = cos * out, cz = sin * out;
				// two crossed sheets, one along the way out of the body, one across it
				sheet(buffer, pose, cx, y, cz, cos, sin, size, yUp, light);
				sheet(buffer, pose, cx, y, cz, -sin, cos, size, yUp, light);
			}
		});
	}

	/** One square sheet of tuft centred on ({@code x}, {@code y}, {@code z}), lying along ({@code dx}, {@code dz}). */
	private static void sheet(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float dx, float dz, float size, boolean yUp,
		int light) {
		float h = size * 0.5F;
		float top = yUp ? y + h : y - h, bottom = yUp ? y - h : y + h;
		float ax = x - dx * h, az = z - dz * h, bx = x + dx * h, bz = z + dz * h;
		float nx = -dz, nz = dx;
		vertex(buffer, pose, ax, top, az, 0.0F, 0.0F, nx, nz, light);
		vertex(buffer, pose, ax, bottom, az, 0.0F, 1.0F, nx, nz, light);
		vertex(buffer, pose, bx, bottom, bz, 1.0F, 1.0F, nx, nz, light);
		vertex(buffer, pose, bx, top, bz, 1.0F, 0.0F, nx, nz, light);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float nz, int light) {
		buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, 0.0F, nz);
	}

	static final class Layer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
		Layer(RenderLayerParent<S, M> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Look look = state.getData(KEY);
			if (look == null || state.isInvisible) {
				return;
			}
			collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityCutout(skin(look.layers())), lightCoords,
				LivingEntityRenderer.getOverlayCoords(state, 0.0F), -1, PUFFED, state.outlineColor);
			tufts(poseStack, collector, look, lightCoords, false);
		}
	}

	/** Passes everything on, each of the model's vertices moved {@link #PUFF} out along its face's normal. */
	private record Puffed(VertexConsumer inner) implements VertexConsumer {
		@Override
		public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz) {
			this.inner.addVertex(x + nx * PUFF, y + ny * PUFF, z + nz * PUFF, color, u, v, overlayCoords, lightCoords, nx, ny, nz);
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			this.inner.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			this.inner.setColor(r, g, b, a);
			return this;
		}

		@Override
		public VertexConsumer setColor(int color) {
			this.inner.setColor(color);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			this.inner.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			this.inner.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			this.inner.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv3(float u, float v) {
			this.inner.setUv3(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			this.inner.setNormal(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			this.inner.setLineWidth(width);
			return this;
		}
	}
}

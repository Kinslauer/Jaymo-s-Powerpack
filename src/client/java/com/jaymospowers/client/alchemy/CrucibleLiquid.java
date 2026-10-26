package com.jaymospowers.client.alchemy;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;

/**
 * Draws the liquid in a crucible or a jar: a box of one grey ripple texture ({@code block/alchemy/liquid}) tinted with
 * the colour of what is in it, slightly see-through.
 */
final class CrucibleLiquid {
	/** How solid the liquid is (0..255). */
	static final int ALPHA = 0xD8;
	private static final RenderType TYPE = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);

	private CrucibleLiquid() {
	}

	static TextureAtlasSprite sprite(SpriteGetter sprites) {
		return sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, JaymosPowers.id("block/alchemy/liquid")));
	}

	/** A box of liquid in block units, relative to the pose, tinted {@code rgb}; the sides only when {@code sides}. */
	static void box(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, float x0, float y0, float z0, float x1,
		float y1, float z1, int rgb, int light, boolean sides) {
		int argb = ALPHA << 24 | rgb & 0xFFFFFF;
		collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
			float u0 = sprite.getU(0.0F), u1 = sprite.getU(1.0F), v0 = sprite.getV(0.0F), v1 = sprite.getV(1.0F);
			quad(pose, buffer, argb, light, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, u0, v0, u1, v1);
			if (sides) {
				quad(pose, buffer, argb, light, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, -1, u0, v0, u1, v1);
				quad(pose, buffer, argb, light, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0, 0, 1, u0, v0, u1, v1);
				quad(pose, buffer, argb, light, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1, 0, 0, u0, v0, u1, v1);
				quad(pose, buffer, argb, light, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1, 0, 0, u0, v0, u1, v1);
			}
		});
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int argb, int light, float ax, float ay, float az, float bx, float by,
		float bz, float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz, float u0, float v0, float u1,
		float v1) {
		vertex(pose, buffer, argb, light, ax, ay, az, u0, v0, nx, ny, nz);
		vertex(pose, buffer, argb, light, bx, by, bz, u0, v1, nx, ny, nz);
		vertex(pose, buffer, argb, light, cx, cy, cz, u1, v1, nx, ny, nz);
		vertex(pose, buffer, argb, light, dx, dy, dz, u1, v0, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int argb, int light, float x, float y, float z, float u, float v,
		float nx, float ny, float nz) {
		buffer.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}
}

package com.jaymospowers.client.tinkering;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.smeltery.Molten;
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

/** Draws molten metal as boxes of its (animated) texture, glowing, tiled a block at a time. */
final class MoltenQuads {
	static final int FULL_BRIGHT = 0xF000F0;
	private static final RenderType TYPE = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);

	private MoltenQuads() {
	}

	static TextureAtlasSprite sprite(SpriteGetter sprites, Molten molten) {
		return sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, JaymosPowers.id("block/tinkering/molten/" + molten.id)));
	}

	/** A box of liquid (block units, relative to the pose); the bottom face is left out. */
	static void box(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, float x0, float y0, float z0,
		float x1, float y1, float z1, boolean top) {
		collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
			if (top) {
				for (float x = x0; x < x1; x += 1.0F) {
					for (float z = z0; z < z1; z += 1.0F) {
						float xe = Math.min(x1, x + 1.0F), ze = Math.min(z1, z + 1.0F);
						quad(pose, buffer, sprite, x, y1, z, x, y1, ze, xe, y1, ze, xe, y1, z, 0, 1, 0, xe - x, ze - z);
					}
				}
			}
			for (float y = y0; y < y1; y += 1.0F) {
				float ye = Math.min(y1, y + 1.0F);
				for (float x = x0; x < x1; x += 1.0F) {
					float xe = Math.min(x1, x + 1.0F);
					quad(pose, buffer, sprite, x, ye, z0, xe, ye, z0, xe, y, z0, x, y, z0, 0, 0, -1, xe - x, ye - y);
					quad(pose, buffer, sprite, xe, ye, z1, x, ye, z1, x, y, z1, xe, y, z1, 0, 0, 1, xe - x, ye - y);
				}
				for (float z = z0; z < z1; z += 1.0F) {
					float ze = Math.min(z1, z + 1.0F);
					quad(pose, buffer, sprite, x0, ye, ze, x0, ye, z, x0, y, z, x0, y, ze, -1, 0, 0, ze - z, ye - y);
					quad(pose, buffer, sprite, x1, ye, z, x1, ye, ze, x1, y, ze, x1, y, z, 1, 0, 0, ze - z, ye - y);
				}
			}
		});
	}

	/** A quad, corners in order, with the sprite's top-left share (u, v up to 1) mapped across it. */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, float ax, float ay, float az,
		float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz,
		float uSpan, float vSpan) {
		float u0 = sprite.getU(0.0F), u1 = sprite.getU(uSpan), v0 = sprite.getV(0.0F), v1 = sprite.getV(vSpan);
		vertex(pose, buffer, ax, ay, az, u0, v0, nx, ny, nz);
		vertex(pose, buffer, bx, by, bz, u0, v1, nx, ny, nz);
		vertex(pose, buffer, cx, cy, cz, u1, v1, nx, ny, nz);
		vertex(pose, buffer, dx, dy, dz, u1, v0, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, float nx, float ny,
		float nz) {
		buffer.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
			.setNormal(pose, nx, ny, nz);
	}
}

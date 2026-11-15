package com.jaymoskitchen.client.mill;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.mill.power.WindsockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a windsock's sock (GT New Horizons picks, patch 8): a tapering tube of cloth in red and white bands on a swivel
 * at the top of the pole. It points the way the wind blows, hangs limp in a calm and stands out level in a gale, and
 * ripples down its length, faster and tighter the harder it blows. No text: the look is the reading.
 */
public class WindsockRenderer implements BlockEntityRenderer<WindsockEntity, WindsockRenderer.State> {
	/** Bands along the sock, its length (blocks), and its half-width at the mouth and the tail. */
	private static final int BANDS = 5;
	private static final float LENGTH = 1.1F, MOUTH = 0.15F, TAIL = 0.06F;
	/** Where the swivel is (blocks up the pole). */
	private static final float SWIVEL = 15.0F / 16.0F;
	/** Wind strength at which it starts to lift, and where it stands out level; how far it hangs in a calm (degrees). */
	private static final float LIFT_FROM = 0.2F, LIFT_FULL = 1.1F, HANG = 80.0F;
	/** The ripple down its length (degrees at the tail): in a calm, and less in a hard wind. */
	private static final float RIPPLE_CALM = 14.0F, RIPPLE_HARD = 5.0F;
	private static final int RED = 0xFFC8342A, WHITE = 0xFFF2EEE6;
	private static final RenderType TYPE = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);

	public static class State extends BlockEntityRenderState {
		float angle;
		float strength;
		float flutter;
	}

	private final SpriteGetter sprites;

	public WindsockRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WindsockEntity sock, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(sock, state, partialTicks, camera, breakProgress);
		state.angle = Mth.lerp(partialTicks, sock.lastAngle, sock.shownAngle);
		state.strength = Mth.lerp(partialTicks, sock.lastStrength, sock.shownStrength);
		state.flutter = Mth.lerp(partialTicks, sock.lastFlutter, sock.flutter);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		TextureAtlasSprite sprite = sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, JaymosKitchen.id("block/windsock_cloth")));
		int light = state.lightCoords;
		float lift = Mth.clamp((state.strength - LIFT_FROM) / (LIFT_FULL - LIFT_FROM), 0.0F, 1.0F);
		float ripple = Mth.lerp(lift, RIPPLE_CALM, RIPPLE_HARD);
		poseStack.pushPose();
		poseStack.translate(0.5F, SWIVEL, 0.5F);
		// +x turned to the way the wind blows toward, then let down by how little it blows
		poseStack.rotateDegrees(Axis.YP, -state.angle * Mth.RAD_TO_DEG);
		poseStack.rotateDegrees(Axis.ZP, -(1.0F - lift) * HANG);
		float step = LENGTH / BANDS;
		for (int band = 0; band < BANDS; band++) {
			float along = (float) band / BANDS;
			float h0 = Mth.lerp(along, MOUTH, TAIL), h1 = Mth.lerp(along + 1.0F / BANDS, MOUTH, TAIL);
			int colour = band % 2 == 0 ? RED : WHITE;
			collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> band(pose, buffer, sprite, light, colour, step, h0, h1));
			poseStack.translate(step, 0.0F, 0.0F);
			// each band swings a little off the last, the wave running down to the tail
			poseStack.rotateDegrees(Axis.YP, Mth.sin(state.flutter - band * 0.9F) * ripple * along);
			poseStack.rotateDegrees(Axis.ZP, Mth.cos(state.flutter * 0.7F - band) * ripple * 0.4F * along);
		}
		poseStack.popPose();
	}

	/** One band: four faces of a square tube from x 0 to {@code length}, half-width {@code h0} to {@code h1}. */
	private static void band(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light, int colour, float length,
		float h0, float h1) {
		float u0 = sprite.getU(0.0F), u1 = sprite.getU(1.0F), v0 = sprite.getV(0.0F), v1 = sprite.getV(0.5F);
		// top, bottom, and the two sides, each seen from both ways (it's thin cloth)
		face(pose, buffer, u0, u1, v0, v1, colour, light, 0, h0, -h0, 0, h0, h0, length, h1, h1, length, h1, -h1, 0, 1, 0);
		face(pose, buffer, u0, u1, v0, v1, colour, light, 0, -h0, h0, 0, -h0, -h0, length, -h1, -h1, length, -h1, h1, 0, -1, 0);
		face(pose, buffer, u0, u1, v0, v1, colour, light, 0, -h0, -h0, 0, h0, -h0, length, h1, -h1, length, -h1, -h1, 0, 0, -1);
		face(pose, buffer, u0, u1, v0, v1, colour, light, 0, h0, h0, 0, -h0, h0, length, -h1, h1, length, h1, h1, 0, 0, 1);
	}

	private static void face(PoseStack.Pose pose, VertexConsumer buffer, float u0, float u1, float v0, float v1, int colour, int light,
		float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
		float nx, float ny, float nz) {
		vertex(pose, buffer, ax, ay, az, u0, v0, colour, light, nx, ny, nz);
		vertex(pose, buffer, bx, by, bz, u1, v0, colour, light, nx, ny, nz);
		vertex(pose, buffer, cx, cy, cz, u1, v1, colour, light, nx, ny, nz);
		vertex(pose, buffer, dx, dy, dz, u0, v1, colour, light, nx, ny, nz);
		// the inside
		vertex(pose, buffer, dx, dy, dz, u0, v1, colour, light, -nx, -ny, -nz);
		vertex(pose, buffer, cx, cy, cz, u1, v1, colour, light, -nx, -ny, -nz);
		vertex(pose, buffer, bx, by, bz, u1, v0, colour, light, -nx, -ny, -nz);
		vertex(pose, buffer, ax, ay, az, u0, v0, colour, light, -nx, -ny, -nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int colour, int light,
		float nx, float ny, float nz) {
		buffer.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		// the sock reaches past its block
		return true;
	}
}

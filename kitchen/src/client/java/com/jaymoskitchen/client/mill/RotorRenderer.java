package com.jaymoskitchen.client.mill;

import com.jaymoskitchen.mill.power.PowerPartBlock.Part;
import com.jaymoskitchen.mill.power.Power;
import com.jaymoskitchen.mill.power.Rotor;
import com.jaymoskitchen.mill.power.RotorEntity;
import com.jaymoskitchen.JaymosKitchen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a windmill or waterwheel turning (after Better With Mods, MIT; our own code and models): the hub, then four
 * sails or eight paddles on spokes, all spun round the shaft. A sail is a spar with a cloth blade beside it, pitched
 * into the wind like Better With Mods' and dyed its own colour (its sailcloth texture is Better With Mods', MIT). The
 * hub, spars and paddles are block models held by {@link Part}.
 */
public class RotorRenderer implements BlockEntityRenderer<RotorEntity, RotorRenderer.State> {
	private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
	/** Sails on a windmill, paddles on a waterwheel. */
	private static final int SAILS = 4, PADDLES = 8;

	public static class State extends BlockEntityRenderState {
		Rotor rotor = Rotor.WINDMILL;
		float yaw;
		float angle;
		final BlockModelRenderState hub = new BlockModelRenderState();
		final BlockModelRenderState spar = new BlockModelRenderState();
		final BlockModelRenderState blade = new BlockModelRenderState();
		/** Each sail's cloth colour (ARGB). */
		final int[] cloth = new int[SAILS];
	}

	/** A sail's pitch about its spar (degrees, Better With Mods' 0.26 rad) and its cloth's thickness (blocks). */
	private static final float PITCH = 15.0F, CLOTH = 1.0F / 16.0F;
	private static final RenderType CLOTH_TYPE = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
	private final SpriteGetter sprites;

	private final BlockModelResolver blocks;

	public RotorRenderer(BlockEntityRendererProvider.Context context) {
		this.blocks = context.blockModelResolver();
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RotorEntity rotor, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(rotor, state, partialTicks, camera, breakProgress);
		state.rotor = rotor.rotor();
		// turn the model's -z (where its shaft is) to the shaft
		state.yaw = 180.0F - rotor.facing().toYRot();
		state.angle = Mth.lerp(partialTicks, rotor.lastAngle, rotor.angle);
		boolean windmill = state.rotor == Rotor.WINDMILL;
		blocks.update(state.hub, Power.PART.part(windmill ? Part.WINDMILL_HUB : Part.WATERWHEEL_HUB), DISPLAY);
		blocks.update(state.spar, Power.PART.part(Part.WINDMILL_SPAR), DISPLAY);
		blocks.update(state.blade, Power.PART.part(windmill ? Part.WINDMILL_SAIL : Part.WATERWHEEL_PADDLE), DISPLAY);
		for (int sail = 0; sail < SAILS; sail++) {
			state.cloth[sail] = 0xFF000000 | rotor.sail(sail).getTextureDiffuseColor();
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = state.lightCoords;
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.rotateDegrees(Axis.YP, state.yaw);
		poseStack.rotateDegrees(Axis.ZP, state.angle);
		block(state.hub, poseStack, collector, light, 0.0F);
		if (state.rotor == Rotor.WINDMILL) {
			for (int sail = 0; sail < SAILS; sail++) {
				poseStack.pushPose();
				poseStack.rotateDegrees(Axis.ZP, sail * 90.0F);
				// the spar out to the tip, and the cloth beside it from a block out
				for (int length = 0; length < Rotor.WINDMILL.radius; length++) {
					block(state.spar, poseStack, collector, light, 0.5F + length);
				}
				cloth(poseStack, collector, light, state.cloth[sail]);
				poseStack.popPose();
			}
		} else {
			for (int paddle = 0; paddle < PADDLES; paddle++) {
				poseStack.pushPose();
				poseStack.rotateDegrees(Axis.ZP, paddle * 360.0F / PADDLES);
				// the paddle model's block sits a block out; its spokes reach back in to the hub (its y -16)
				block(state.blade, poseStack, collector, light, 1.0F);
				poseStack.popPose();
			}
		}
		poseStack.popPose();
	}

	/** A sail's cloth: a thin blade one block wide along the spar (up this spoke), turned about it into the wind. */
	private void cloth(PoseStack poseStack, SubmitNodeCollector collector, int light, int colour) {
		TextureAtlasSprite sprite = sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, JaymosKitchen.id("block/windmill_cloth")));
		float y0 = 1.0F, y1 = Rotor.WINDMILL.radius + 0.5F, x0 = 1.0F / 16.0F, x1 = x0 + 1.0F, z0 = -CLOTH / 2.0F, z1 = CLOTH / 2.0F;
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, PITCH);
		collector.submitCustomGeometry(poseStack, CLOTH_TYPE, (pose, buffer) -> {
			for (float y = y0; y < y1; y += 1.0F) {
				float ye = Math.min(y1, y + 1.0F);
				// both broad faces, a block of cloth texture to each block of blade
				quad(pose, buffer, sprite, colour, light, x0, ye, z0, x1, ye, z0, x1, y, z0, x0, y, z0, 0, 0, -1, ye - y);
				quad(pose, buffer, sprite, colour, light, x1, ye, z1, x0, ye, z1, x0, y, z1, x1, y, z1, 0, 0, 1, ye - y);
			}
		});
		poseStack.popPose();
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int colour, int light, float ax, float ay,
		float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz,
		float vSpan) {
		float u0 = sprite.getU(0.0F), u1 = sprite.getU(1.0F), v0 = sprite.getV(0.0F), v1 = sprite.getV(vSpan);
		vertex(pose, buffer, ax, ay, az, u0, v0, colour, light, nx, ny, nz);
		vertex(pose, buffer, bx, by, bz, u1, v0, colour, light, nx, ny, nz);
		vertex(pose, buffer, cx, cy, cz, u1, v1, colour, light, nx, ny, nz);
		vertex(pose, buffer, dx, dy, dz, u0, v1, colour, light, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int colour, int light,
		float nx, float ny, float nz) {
		buffer.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	/** One piece, its model's block set {@code out} blocks up the current spoke (0: centred on the hub). */
	private static void block(BlockModelRenderState part, PoseStack poseStack, SubmitNodeCollector collector, int light, float out) {
		poseStack.pushPose();
		poseStack.translate(-0.5F, out == 0.0F ? -0.5F : out, -0.5F);
		part.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		// the sails reach far past the hub's block
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}
}

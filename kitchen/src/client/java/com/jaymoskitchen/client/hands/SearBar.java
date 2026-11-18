package com.jaymoskitchen.client.hands;

import com.jaymoskitchen.cooking.Sear;
import com.jaymoskitchen.cooking.hands.Geometry;
import com.jaymoskitchen.cooking.hands.HandsOn;
import com.jaymoskitchen.cooking.hands.Pick;
import com.jaymoskitchen.net.Payloads.HandsSync;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The searing marker, drawn in the world: a bar floating over the pan (the track, the gold target with its pale core,
 * the sweeping marker), turned to face whoever looks at it. It is drawn from the numbers the server sends for the
 * station ({@code SearRun#fill}), so every player near who can see the pan sees the same sweep.
 */
final class SearBar {
	/** How far off (blocks) the bar is still drawn. */
	static final double RANGE = 24.0;
	/** The bar's width and height (blocks), and how far over the pan's middle it floats. */
	static final float WIDTH = 1.0F, HEIGHT = 0.12F, LIFT = 0.6F;
	/** The marker's width (blocks), and how far it stands out above and below the bar. */
	static final float MARKER = 0.03F, MARKER_OVER = 0.04F;
	/** How far each layer stands in front of the one behind it (blocks), so they draw in order. */
	static final float LAYER = 0.004F;
	/** The colours of the heat bar, as the screen's: the track, the target, its core, the marker. */
	static final int TRACK = 0xFF3A1810, ZONE = 0xFFE0A030, CORE = 0xFFFFF0B4, NEEDLE = 0xFFFFFFFF;

	private SearBar() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!HandsOn.clientOn || minecraft.level == null || HandsClient.VIEWS.isEmpty()) {
			return;
		}
		CameraRenderState cameraState = context.levelState().cameraRenderState;
		float time = HandsClient.clock + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		// across the viewer's sight, up it, and toward the viewer
		Vector3f right = cameraState.orientation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
		Vector3f up = cameraState.orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
		Vector3f near = cameraState.orientation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
		for (HandsClient.View view : HandsClient.VIEWS.values()) {
			HandsSync sync = view.sync();
			float[] f = sync.floats();
			if (sync.phase() != HandsSync.PHASE_RUNNING || sync.kind() != Pick.Kind.SEAR.ordinal() || f.length < 3) {
				continue;
			}
			Vec3 at = Geometry.potCenter(minecraft.level, sync.pos()).add(0.0, LIFT, 0.0).subtract(cameraState.pos);
			if (at.lengthSqr() > RANGE * RANGE) {
				continue;
			}
			// where the marker has got to since the server last said: it turns back at the ends, as it does there
			float marker = f[0] + f[2] * Math.max(0.0F, time - view.seen());
			marker = Mth.clamp(marker > 1.0F ? 2.0F - marker : marker < 0.0F ? -marker : marker, 0.0F, 1.0F);
			float zone = f[1];
			float needle = marker;
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
				span(pose, buffer, at, right, up, near, 0, 0.0F, 1.0F, 0.0F, TRACK);
				span(pose, buffer, at, right, up, near, 1, zone - Sear.ZONE_WIDTH / 2, zone + Sear.ZONE_WIDTH / 2, 0.0F, ZONE);
				span(pose, buffer, at, right, up, near, 2, zone - Sear.CORE_WIDTH / 2, zone + Sear.CORE_WIDTH / 2, 0.0F, CORE);
				float half = MARKER / 2 / WIDTH;
				span(pose, buffer, at, right, up, near, 3, needle - half, needle + half, MARKER_OVER, NEEDLE);
			});
		}
	}

	/**
	 * One stretch of the bar, {@code from} to {@code to} along it (0 to 1; cut off at the bar's ends unless it stands
	 * {@code over} the bar, as the marker does), on layer {@code layer} (0 is the back).
	 */
	private static void span(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, Vector3f right, Vector3f up, Vector3f near, int layer, float from,
		float to, float over, int color) {
		if (over == 0.0F) {
			from = Mth.clamp(from, 0.0F, 1.0F);
			to = Mth.clamp(to, 0.0F, 1.0F);
		}
		float x0 = (from - 0.5F) * WIDTH, x1 = (to - 0.5F) * WIDTH;
		float y = HEIGHT / 2 + over;
		float d = layer * LAYER;
		corner(pose, buffer, at, right, up, near, x0, -y, d, color);
		corner(pose, buffer, at, right, up, near, x0, y, d, color);
		corner(pose, buffer, at, right, up, near, x1, y, d, color);
		corner(pose, buffer, at, right, up, near, x1, -y, d, color);
	}

	private static void corner(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, Vector3f right, Vector3f up, Vector3f near, float x, float y, float d,
		int color) {
		buffer.addVertex(pose, (float) at.x + right.x * x + up.x * y + near.x * d, (float) at.y + right.y * x + up.y * y + near.y * d,
			(float) at.z + right.z * x + up.z * y + near.z * d).setColor(color);
	}
}

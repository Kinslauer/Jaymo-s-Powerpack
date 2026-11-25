package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.future.CarKind;
import com.jaymospowers.power.chrono.future.FutureCar;
import com.jaymospowers.power.chrono.future.FutureCars;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * A future car: the body is the ported mesh ({@code car_meshes/<name>.quads}, written by {@code tools/car_port.py}; big-endian,
 * a count then four vertices of position, uv and normal per quad), the four wheels are boxes that spin with the car's speed
 * and whose front pair turns with the steering.
 */
public final class FutureCarRenderer extends EntityRenderer<FutureCar, FutureCarRenderer.State> {
	public static final class State extends EntityRenderState {
		CarKind kind = CarKind.RUNABOUT;
		float yaw, speed, steer, roll;
	}

	/** A wheel's thickness (blocks) and how far out from the middle its centre sits (blocks). */
	private static final float TYRE = 0.26F, TRACK = 0.74F;
	/** Degrees of front wheel turn at full steer. */
	private static final float LOCK = 28.0F;
	private static final Map<CarKind, float[]> MESHES = new EnumMap<>(CarKind.class);
	private static final Identifier WHEEL = JaymosPowers.id("textures/entity/future/car_wheel.png");

	public FutureCarRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.9F;
	}

	public static void register() {
		for (CarKind kind : CarKind.values()) {
			EntityRendererRegistry.register(FutureCars.TYPES.get(kind), FutureCarRenderer::new);
		}
	}

	private static float[] mesh(CarKind kind) {
		return MESHES.computeIfAbsent(kind, k -> {
			try (InputStream in = FutureCarRenderer.class.getResourceAsStream("/assets/jaymospowers/car_meshes/" + k.mesh + ".quads")) {
				DataInputStream data = new DataInputStream(new java.io.BufferedInputStream(in));
				int quads = data.readInt();
				float[] floats = new float[quads * 32];
				for (int i = 0; i < floats.length; i++) {
					floats[i] = data.readFloat();
				}
				return floats;
			} catch (Exception e) {
				return new float[0];
			}
		});
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FutureCar entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = entity.kind();
		state.yaw = entity.getYRot();
		state.speed = entity.speed();
		state.steer = entity.steer();
		// the wheels' turn so far: the car's travel over the tyre's circumference
		state.roll = (entity.tickCount + partialTicks) * entity.speed() * 360.0F / (2.0F * (float) Math.PI * state.kind.wheel);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		CarKind kind = state.kind;
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		Identifier texture = JaymosPowers.id("textures/entity/future/car_" + kind.mesh + ".png");
		float[] quads = mesh(kind);
		int light = state.lightCoords;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, buffer) -> {
			for (int i = 0; i + 7 < quads.length; i += 8) {
				vertex(pose, buffer, quads, i, light);
			}
		});
		wheel(poseStack, collector, kind, TRACK, kind.front, state.steer * LOCK, state.roll, light);
		wheel(poseStack, collector, kind, -TRACK, kind.front, state.steer * LOCK, state.roll, light);
		wheel(poseStack, collector, kind, TRACK, kind.rear, 0.0F, state.roll, light);
		wheel(poseStack, collector, kind, -TRACK, kind.rear, 0.0F, state.roll, light);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float[] q, int i, int light) {
		buffer.addVertex(pose, q[i], q[i + 1], q[i + 2]).setColor(-1).setUv(q[i + 3], q[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light).setNormal(pose, q[i + 5], q[i + 6], q[i + 7]);
	}

	/** One wheel: a box turned about its axle, the steered ones first turned about the vertical. */
	private static void wheel(PoseStack poseStack, SubmitNodeCollector collector, CarKind kind, float x, float z, float turn, float roll, int light) {
		poseStack.pushPose();
		poseStack.translate(x, kind.wheel, z);
		poseStack.rotateDegrees(Axis.YP, -turn);
		poseStack.rotateDegrees(Axis.XP, roll);
		float r = kind.wheel, t = TYRE / 2.0F;
		RenderType type = RenderTypes.entityCutout(WHEEL);
		collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
			// six faces of the box, corners counter-clockwise from outside
			face(pose, buffer, light, -t, -r, -r, -t, r, -r, -t, r, r, -t, -r, r, -1, 0, 0);
			face(pose, buffer, light, t, -r, r, t, r, r, t, r, -r, t, -r, -r, 1, 0, 0);
			face(pose, buffer, light, -t, r, -r, t, r, -r, t, r, r, -t, r, r, 0, 1, 0);
			face(pose, buffer, light, -t, -r, r, t, -r, r, t, -r, -r, -t, -r, -r, 0, -1, 0);
			face(pose, buffer, light, t, -r, -r, t, r, -r, -t, r, -r, -t, -r, -r, 0, 0, -1);
			face(pose, buffer, light, -t, -r, r, -t, r, r, t, r, r, t, -r, r, 0, 0, 1);
		});
		poseStack.popPose();
	}

	private static void face(PoseStack.Pose pose, VertexConsumer buffer, int light, float ax, float ay, float az, float bx, float by, float bz,
		float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
		float[][] corners = {{ax, ay, az, 0, 0}, {bx, by, bz, 0, 1}, {cx, cy, cz, 1, 1}, {dx, dy, dz, 1, 0}};
		for (float[] c : corners) {
			buffer.addVertex(pose, c[0], c[1], c[2]).setColor(-1).setUv(c[3], c[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, nx, ny, nz);
		}
	}
}

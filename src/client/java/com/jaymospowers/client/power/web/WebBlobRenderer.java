package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebSymbioteBlob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A symbiote with no host: a lumpy black blob, heaving, that drags itself along on tendrils. Each tendril plants on the
 * ground ahead and stays put while the blob is hauled past it, then lets go and reaches forward again, a couple at a
 * time; standing still, one probes the air. The tendrils use the same texture as Living Armor's.
 */
public class WebBlobRenderer extends EntityRenderer<WebSymbioteBlob, WebBlobRenderer.State> {
	// ---- tuning ----
	/** The body: half-widths (blocks) and how hard it heaves. */
	private static final float BODY_WIDTH = 0.42F, BODY_HEIGHT = 0.3F, WOBBLE = 0.13F;
	private static final int RINGS = 7, SEGMENTS = 12;
	/** Tendrils: how many, where they plant (ahead of the body, spread over this many degrees each side), how far before one lets go. */
	private static final int LEGS = 5;
	private static final float LEG_SPREAD = 110.0F;
	private static final double PLANT_REACH = 0.95, PLANT_AHEAD = 0.45, LET_GO = 1.55;
	/** A step: ticks to reach its new spot, and how high it arcs. */
	private static final float STEP_TICKS = 5.0F;
	private static final double STEP_ARC = 0.45;
	private static final double LEG_ROOT_RADIUS = 0.085, LEG_TIP_RADIUS = 0.025;
	private static final int LEG_PIECES = 8;

	public static class State extends EntityRenderState {
		/** Each tendril's tip, relative to the body. */
		final Vec3[] feet = new Vec3[LEGS];
		/** Which way it's heading (horizontal, unit). */
		Vec3 heading = new Vec3(0.0, 0.0, 1.0);
		boolean moving;
	}

	/** Per blob: where each tendril has planted, where it came from, and when it set off. */
	private static final class Feet {
		final Vec3[] at = new Vec3[LEGS];
		final Vec3[] from = new Vec3[LEGS];
		final float[] stepAt = new float[LEGS];
	}

	private final Map<Integer, Feet> feet = new HashMap<>();

	public WebBlobRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WebSymbioteBlob blob, State state, float partialTicks) {
		super.extractRenderState(blob, state, partialTicks);
		Vec3 body = blob.getPosition(partialTicks);
		Vec3 motion = blob.getDeltaMovement().horizontal();
		state.moving = motion.lengthSqr() > 1.0E-4;
		float yaw = Mth.lerp(partialTicks, blob.yBodyRotO, blob.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 heading = state.moving ? motion.normalize() : new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		state.heading = heading;
		float time = blob.tickCount + partialTicks;
		Feet legs = this.feet.computeIfAbsent(blob.getId(), id -> new Feet());
		int stepping = 0;
		for (int i = 0; i < LEGS; i++) {
			if (legs.at[i] != null && time - legs.stepAt[i] < STEP_TICKS) {
				stepping++;
			}
		}
		for (int i = 0; i < LEGS; i++) {
			float angle = (i / (float)(LEGS - 1) * 2.0F - 1.0F) * LEG_SPREAD * Mth.DEG_TO_RAD;
			Vec3 dir = new Vec3(heading.x * Mth.cos(angle) - heading.z * Mth.sin(angle), 0.0, heading.x * Mth.sin(angle) + heading.z * Mth.cos(angle));
			Vec3 want = body.add(dir.scale(PLANT_REACH)).add(heading.scale(state.moving ? PLANT_AHEAD : 0.0));
			boolean far = legs.at[i] == null || legs.at[i].distanceTo(body) > LET_GO || Math.abs(legs.at[i].y - body.y) > 1.5;
			boolean behind = state.moving && legs.at[i] != null && legs.at[i].subtract(body).dot(heading) < -0.35;
			boolean busy = legs.at[i] != null && time - legs.stepAt[i] < STEP_TICKS;
			// a couple at a time, so it always has a grip
			if (!busy && (far || behind) && (legs.at[i] == null || stepping < 2)) {
				legs.from[i] = legs.at[i] == null ? body : shown(legs, i, time);
				legs.at[i] = ground(blob.level(), want);
				legs.stepAt[i] = time;
				stepping++;
			}
			Vec3 tip = shown(legs, i, time);
			if (!state.moving && i == LEGS / 2) {
				// standing still: the middle one rises and feels the air
				float probe = Mth.sin(time * 0.07F + blob.getId()) * 0.5F + 0.5F;
				tip = tip.add(Mth.sin(time * 0.21F) * 0.25, probe * 0.9, Mth.cos(time * 0.17F) * 0.25);
			}
			state.feet[i] = tip.subtract(body);
		}
		if (this.feet.size() > 64) {
			this.feet.keySet().removeIf(id -> Minecraft.getInstance().level == null || Minecraft.getInstance().level.getEntity(id) == null);
		}
	}

	/** Where tendril {@code i}'s tip shows now: planted, or arcing over to its new spot. */
	private static Vec3 shown(Feet legs, int i, float time) {
		float k = Mth.clamp((time - legs.stepAt[i]) / STEP_TICKS, 0.0F, 1.0F);
		if (k >= 1.0F || legs.from[i] == null) {
			return legs.at[i];
		}
		float eased = k * k * (3.0F - 2.0F * k);
		return legs.from[i].lerp(legs.at[i], eased).add(0.0, Math.sin(k * Math.PI) * STEP_ARC, 0.0);
	}

	/** The top of the ground at or a little below {@code at} (or {@code at} itself, over a drop). */
	private static Vec3 ground(Level level, Vec3 at) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + 1.0, at.z).mutable();
		for (int i = 0; i < 4; i++) {
			BlockState block = level.getBlockState(pos);
			VoxelShape shape = block.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				return new Vec3(at.x, pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), at.z);
			}
			pos.move(0, -1, 0);
		}
		return at;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float time = state.ageInTicks;
		int light = state.lightCoords;
		// it leans and bulges the way it's hauling itself
		Vec3 lean = state.moving ? state.heading.scale(0.08) : Vec3.ZERO;
		Vec3 center = new Vec3(lean.x, BODY_HEIGHT * 0.95, lean.z);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebLineRenderer.TENDRIL_TEXTURE), (pose, buffer) -> {
			body(buffer, pose, center, time, state.moving ? 1.6F : 1.0F, light);
			for (int i = 0; i < LEGS; i++) {
				Vec3 tip = state.feet[i];
				if (tip == null) {
					continue;
				}
				Vec3 flat = new Vec3(tip.x, 0.0, tip.z);
				Vec3 out = flat.lengthSqr() < 1.0E-4 ? state.heading : flat.normalize();
				Vec3 root = center.add(out.scale(BODY_WIDTH * 0.8)).add(0.0, -BODY_HEIGHT * 0.2, 0.0);
				// up and over, then down onto its grip
				Vec3 mid = root.lerp(tip, 0.5).add(0.0, 0.25 + 0.15 * Math.max(0.0, root.distanceTo(tip) - 0.6), 0.0);
				Vec3 prev = root;
				for (int k = 1; k <= LEG_PIECES; k++) {
					double t = k / (double)LEG_PIECES;
					Vec3 p = root.lerp(mid, t).lerp(mid.lerp(tip, t), t);
					double r0 = Mth.lerp((k - 1) / (double)LEG_PIECES, LEG_ROOT_RADIUS, LEG_TIP_RADIUS);
					double r1 = Mth.lerp(t, LEG_ROOT_RADIUS, LEG_TIP_RADIUS);
					WebLineRenderer.tube(buffer, pose, prev, p, r0, r1, k + i, light);
					prev = p;
				}
			}
		});
		super.submit(state, poseStack, collector, camera);
	}

	/** The lumpy, heaving body: a squashed ball whose surface swells and sinks in slow waves. */
	private static void body(VertexConsumer buffer, PoseStack.Pose pose, Vec3 center, float time, float speed, int light) {
		Vec3[][] p = new Vec3[RINGS + 1][SEGMENTS + 1];
		Vec3[][] n = new Vec3[RINGS + 1][SEGMENTS + 1];
		for (int r = 0; r <= RINGS; r++) {
			double lat = Math.PI * r / RINGS - Math.PI / 2.0;
			for (int s = 0; s <= SEGMENTS; s++) {
				double lon = Math.PI * 2.0 * (s % SEGMENTS) / SEGMENTS;
				Vec3 dir = new Vec3(Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon));
				double swell = 1.0 + WOBBLE * Math.sin(time * 0.17 * speed + lat * 2.3 + lon * 3.0)
					+ WOBBLE * 0.6 * Math.sin(time * 0.29 * speed - lon * 2.0 + lat);
				// flat underneath, where it presses on the ground
				double squash = lat < 0.0 ? 0.55 : 1.0;
				p[r][s] = center.add(dir.x * BODY_WIDTH * swell, dir.y * BODY_HEIGHT * swell * squash, dir.z * BODY_WIDTH * swell);
				n[r][s] = dir;
			}
		}
		float u0 = 3.0F / 32.0F, u1 = 6.0F / 32.0F, v0 = 3.0F / 32.0F, v1 = 7.5F / 32.0F;
		for (int r = 0; r < RINGS; r++) {
			for (int s = 0; s < SEGMENTS; s++) {
				WebLineRenderer.vertex(buffer, pose, p[r][s], u0, v0, n[r][s], light);
				WebLineRenderer.vertex(buffer, pose, p[r + 1][s], u0, v1, n[r + 1][s], light);
				WebLineRenderer.vertex(buffer, pose, p[r + 1][s + 1], u1, v1, n[r + 1][s + 1], light);
				WebLineRenderer.vertex(buffer, pose, p[r][s + 1], u1, v0, n[r][s + 1], light);
			}
		}
	}
}

package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoformState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.89) Pyre's comet (Andy: "the trail should be more like a comet of flame and less repeat particles"): while a
 * Pyre flies ({@link XenoformState#jet}) and moves, one long tail of fire streams back along the path it took, wide and
 * white-hot at the body and narrowing to a dark red wisp, with a round glowing head round the body. Three ribbons laid
 * over each other (an outer red glow, orange flame, a yellow-white core over the front of it), turned to face the camera,
 * flickering in width; only a few embers fall off it. When the flight stops, the tail draws in behind it. Drawn on every
 * client for every Pyre in view.
 */
public final class PyreComet {
	// ---- tuning ----
	/** The tail starts above this speed (blocks a tick) and is full at {@code FULL_SPEED}. */
	static final double MIN_SPEED = 0.15, FULL_SPEED = 1.0;
	/** Points kept along the path (one a tick): the tail's length in ticks. Sunheart's is longer. */
	static final int POINTS = 14, POINTS_SUN = 20;
	/** How much of the tail is drawn (0-1) rises and falls this fast a tick. */
	static final float RISE = 0.25F, FALL = 0.12F;
	/** The ribbons: width at the head (blocks), colour, and alpha at the head (0-255); the core covers the front share. */
	static final float GLOW_WIDTH = 1.25F, FLAME_WIDTH = 0.8F, CORE_WIDTH = 0.32F, CORE_SHARE = 0.45F;
	static final int GLOW_RGB = 0xC8320A, FLAME_RGB = 0xFF8A2A, CORE_RGB = 0xFFF2B8;
	static final int GLOW_ALPHA = 120, FLAME_ALPHA = 170, CORE_ALPHA = 230;
	/** Sunheart's comet is this much wider. */
	static final float SUN_WIDTH = 1.35F;
	/** The width's flicker (share) and speed (radians a tick). */
	static final float FLICKER = 0.18F, FLICKER_SPEED = 1.3F;
	/** The round head: its radius over the head's width, and its segments. */
	static final float HEAD_SCALE = 0.55F;
	static final int HEAD_SEGMENTS = 10;
	/** One ember off the tail every this many ticks. */
	static final int EMBER_EVERY = 3;
	/** Nothing is drawn nearer the camera than this (your own comet in first person), nor farther than {@code VIEW}. */
	static final double NEAR_CAMERA = 1.3, VIEW = 128.0;

	private static final class Tail {
		final ArrayDeque<Vec3> points = new ArrayDeque<>();
		float amount, amountO;
		boolean sun;
	}

	private static final Int2ObjectMap<Tail> TAILS = new Int2ObjectOpenHashMap<>();

	private PyreComet() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(PyreComet::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(PyreComet::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TAILS.clear());
	}

	/** The middle of the body: where the tail starts. */
	private static Vec3 middle(Player player, float partialTicks) {
		return player.getPosition(partialTicks).add(0.0, player.getBbHeight() * 0.5, 0.0);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			TAILS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			int kind = XenoformState.flight(player);
			if (XenoformState.jet(kind) && !TAILS.containsKey(player.getId())) {
				TAILS.put(player.getId(), new Tail());
			}
		}
		RandomSource random = level.getRandom();
		TAILS.int2ObjectEntrySet().removeIf(entry -> {
			Tail tail = entry.getValue();
			if (!(level.getEntity(entry.getIntKey()) instanceof Player player)) {
				return true;
			}
			int kind = XenoformState.flight(player);
			Vec3 moved = player.position().subtract(player.xo, player.yo, player.zo);
			double speed = moved.length();
			boolean on = XenoformState.jet(kind) && !player.isInvisible() && speed > MIN_SPEED;
			tail.sun = kind == XenoformState.FLY_SUN;
			tail.amountO = tail.amount;
			float target = on ? (float)Mth.clamp((speed - MIN_SPEED) / (FULL_SPEED - MIN_SPEED), 0.35, 1.0) : 0.0F;
			tail.amount += (target - tail.amount) * (target > tail.amount ? RISE : FALL);
			int max = tail.sun ? POINTS_SUN : POINTS;
			if (on) {
				tail.points.addFirst(middle(player, 1.0F));
				while (tail.points.size() > max) {
					tail.points.removeLast();
				}
			} else if (!tail.points.isEmpty()) {
				// the tail draws in behind it
				tail.points.removeLast();
				if (!tail.points.isEmpty()) {
					tail.points.removeLast();
				}
			}
			if (on && tail.points.size() > 2 && player.tickCount % EMBER_EVERY == 0) {
				// a single ember falls off somewhere along the tail
				List<Vec3> list = new ArrayList<>(tail.points);
				Vec3 at = list.get(1 + random.nextInt(list.size() - 1));
				level.addParticle(random.nextBoolean() ? ParticleTypes.SMALL_FLAME : ParticleTypes.LAVA, at.x, at.y, at.z,
					random.nextGaussian() * 0.02, -0.02, random.nextGaussian() * 0.02);
			}
			return tail.points.isEmpty() && tail.amount < 0.01F && !XenoformState.jet(kind);
		});
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || TAILS.isEmpty()) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		float time = level.getGameTime() + partialTick;
		List<float[]> tails = new ArrayList<>();
		List<float[]> heads = new ArrayList<>();
		for (Int2ObjectMap.Entry<Tail> entry : TAILS.int2ObjectEntrySet()) {
			Tail tail = entry.getValue();
			float amount = Mth.lerp(partialTick, tail.amountO, tail.amount);
			if (tail.points.size() < 2 || amount < 0.01F || !(level.getEntity(entry.getIntKey()) instanceof Player player)
				|| player.distanceToSqr(camera) > VIEW * VIEW) {
				continue;
			}
			// the head where the body is this frame, then the path behind it
			List<Vec3> path = new ArrayList<>(tail.points.size() + 1);
			path.add(middle(player, partialTick));
			path.addAll(tail.points);
			float width = amount * (tail.sun ? SUN_WIDTH : 1.0F);
			float[] line = new float[path.size() * 5];
			for (int i = 0; i < path.size(); i++) {
				Vec3 p = path.get(i);
				float f = i / (float)(path.size() - 1);
				line[i * 5] = (float)p.x;
				line[i * 5 + 1] = (float)p.y;
				line[i * 5 + 2] = (float)p.z;
				// tapering to nothing, flickering
				float flicker = 1.0F + FLICKER * Mth.sin(time * FLICKER_SPEED + i * 0.9F + entry.getIntKey());
				line[i * 5 + 3] = width * (1.0F - f) * (1.0F - f * 0.3F) * flicker;
				line[i * 5 + 4] = f;
			}
			tails.add(line);
			boolean own = player == minecraft.getCameraEntity() && firstPerson;
			if (!own) {
				Vec3 head = path.get(0);
				heads.add(new float[] {(float)head.x, (float)head.y, (float)head.z, width * HEAD_SCALE * (1.0F + FLICKER * Mth.sin(time * 1.7F))});
			}
		}
		if (tails.isEmpty()) {
			return;
		}
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			for (float[] line : tails) {
				ribbon(buffer, pose, line, camera, GLOW_WIDTH, GLOW_RGB, GLOW_ALPHA, 1.0F);
				ribbon(buffer, pose, line, camera, FLAME_WIDTH, FLAME_RGB, FLAME_ALPHA, 0.8F);
				ribbon(buffer, pose, line, camera, CORE_WIDTH, CORE_RGB, CORE_ALPHA, CORE_SHARE);
			}
			for (float[] head : heads) {
				disc(buffer, pose, head, camera, GLOW_RGB, GLOW_ALPHA, 1.3F);
				disc(buffer, pose, head, camera, FLAME_RGB, FLAME_ALPHA, 0.9F);
				disc(buffer, pose, head, camera, CORE_RGB, CORE_ALPHA, 0.45F);
			}
		});
		poseStack.popPose();
	}

	/**
	 * One ribbon along the tail ({@code x, y, z, width, share along} per point), turned to face the camera, over the front
	 * {@code share} of it; its alpha falls off along it.
	 */
	private static void ribbon(VertexConsumer buffer, PoseStack.Pose pose, float[] line, Vec3 eye, float width, int rgb, int maxAlpha, float share) {
		for (int i = 0; i + 9 < line.length; i += 5) {
			float fa = line[i + 4], fb = line[i + 9];
			if (fa >= share) {
				break;
			}
			Vec3 a = new Vec3(line[i], line[i + 1], line[i + 2]);
			Vec3 b = new Vec3(line[i + 5], line[i + 6], line[i + 7]);
			if (a.distanceToSqr(eye) < NEAR_CAMERA * NEAR_CAMERA || b.distanceToSqr(eye) < NEAR_CAMERA * NEAR_CAMERA) {
				continue;
			}
			Vec3 dir = b.subtract(a);
			Vec3 side = dir.cross(eye.subtract(a.lerp(b, 0.5)));
			if (side.lengthSqr() < 1.0E-8) {
				continue;
			}
			side = side.normalize();
			Vec3 sa = side.scale(line[i + 3] * width * 0.5);
			Vec3 sb = side.scale(line[i + 8] * width * 0.5);
			int ca = ARGB.color(Mth.clamp(Math.round(maxAlpha * fade(fa / share)), 0, 255), rgb);
			int cb = ARGB.color(Mth.clamp(Math.round(maxAlpha * fade(Math.min(1.0F, fb / share))), 0, 255), rgb);
			vertex(buffer, pose, a.subtract(sa), ca);
			vertex(buffer, pose, a.add(sa), ca);
			vertex(buffer, pose, b.add(sb), cb);
			vertex(buffer, pose, b.subtract(sb), cb);
		}
	}

	/** A round glow facing the camera ({@code x, y, z, radius}), bright in the middle and gone at the rim. */
	private static void disc(VertexConsumer buffer, PoseStack.Pose pose, float[] head, Vec3 eye, int rgb, int maxAlpha, float scale) {
		Vec3 c = new Vec3(head[0], head[1], head[2]);
		Vec3 toEye = eye.subtract(c);
		if (toEye.lengthSqr() < NEAR_CAMERA * NEAR_CAMERA) {
			return;
		}
		Vec3 n = toEye.normalize();
		Vec3 u = n.cross(Math.abs(n.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 v = n.cross(u);
		float r = head[3] * scale;
		int middle = ARGB.color(maxAlpha, rgb), rim = ARGB.color(0, rgb);
		for (int i = 0; i < HEAD_SEGMENTS; i++) {
			double a0 = i * Mth.TWO_PI / HEAD_SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / HEAD_SEGMENTS;
			Vec3 p0 = c.add(u.scale(Math.cos(a0) * r)).add(v.scale(Math.sin(a0) * r));
			Vec3 p1 = c.add(u.scale(Math.cos(a1) * r)).add(v.scale(Math.sin(a1) * r));
			// a quad folded into a wedge: the middle twice, then the rim
			vertex(buffer, pose, c, middle);
			vertex(buffer, pose, p0, rim);
			vertex(buffer, pose, p1, rim);
			vertex(buffer, pose, c, middle);
		}
	}

	/** Alpha along the tail: full at the head, easing to nothing at the end. */
	private static float fade(float f) {
		f = Mth.clamp(f, 0.0F, 1.0F);
		return (1.0F - f) * (1.0F - f * 0.5F);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 v, int color) {
		buffer.addVertex(pose, (float)v.x, (float)v.y, (float)v.z).setColor(color);
	}
}

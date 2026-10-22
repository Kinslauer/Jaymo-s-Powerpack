package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoSpeed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.57) Overclock's speed-force streak, after the comic-book speedsters, drawn on every client for every
 * overclocked runner ({@link ChronoSpeed#FLASH} is synced):
 * <ul>
 * <li><b>Afterimages:</b> translucent golden copies of the runner where they were a moment ago, each frozen in the
 * stride it had then, fading as they fall behind.</li>
 * <li><b>Lightning:</b> jagged yellow-orange bolts along the recent path, crackling off the body; their shape jumps
 * every {@link #FLICKER_TICKS} and each one blinks in and out.</li>
 * <li><b>Glyphs:</b> enchanting-table glyphs drifting off the trail.</li>
 * </ul>
 * All of it scales with how fast they're going ({@link #MIN_SPEED} to {@link #FULL_SPEED}) and fades out as they slow.
 * The afterimages are the runner's own render states, kept from the frames the world drew them in (never the inventory
 * screen's), so they wear the run pose ({@link ChronoRunPose}) they had.
 *
 * <p>(0.15.97) Velox's Velocity leaves the same streak in light blue ({@link ChronoSpeed#SKY}: {@code FLASH}'s value picks
 * the colours), and its afterimages are the alien, not the player under it ({@code XenoformClient#submitGhost}).
 */
public final class ChronoStreak {
	// ---- tuning: speed ----
	/** The streak starts above this speed and is full at {@code FULL_SPEED} (blocks a tick; a vanilla sprint is ~0.28). */
	static final double MIN_SPEED = 0.3, FULL_SPEED = 0.8;
	/** How fast the streak and the run pose come up to speed and die away (share of the gap a tick). */
	static final float RISE = 0.4F, FALL = 0.15F;
	/** The run pose wants horizontal speed: in from this much (blocks a tick), full at {@code RUN_FULL_SPEED}. */
	static final double RUN_MIN_SPEED = 0.25, RUN_FULL_SPEED = 0.6;
	/** How many ticks of path the trail keeps, and how far away (blocks) other runners' streaks are drawn. */
	static final int TRAIL_TICKS = 10;
	static final double VIEW_DISTANCE = 64.0;

	// ---- tuning: afterimages ----
	/** An afterimage every this many ticks back along the trail, at most {@code GHOSTS} of them. */
	static final int GHOST_EVERY = 2;
	static final int GHOSTS = 4;
	/** Their tint (golden; light blue for Velox) and how opaque the nearest is at full speed (0-1). */
	static final int[] GHOST_RGB = {0xFFC860, 0x8FD8FF};
	static final float GHOST_ALPHA = 0.5F;
	/** Your own afterimages closer than this to the camera (blocks) are skipped, so they don't fill the view. */
	static final double GHOST_NEAR_CAMERA = 1.2;

	// ---- tuning: lightning ----
	/** Bolts along the trail, the heights they run at (blocks above the feet), and their wander up and down. */
	static final float[] BOLT_HEIGHTS = {0.35F, 0.95F, 1.45F};
	static final double BOLT_WANDER = 0.25;
	/** Each step of the path is broken into this many jags, each up to {@code JAG} blocks off the line. */
	static final int JAG_SPLITS = 3;
	static final double JAG = 0.2;
	/** The bolts take a new shape every this many ticks, and each is lit this share of the time. */
	static final int FLICKER_TICKS = 2;
	static final float BOLT_CHANCE = 0.7F;
	/** Short arcs crackling off the body: how many, and how long (blocks). */
	static final int BODY_ARCS = 2;
	static final double ARC_LENGTH = 0.7;
	/** The bolt's hot core and its glow: widths (blocks), colours (gold, then Velox's light blue), strongest alpha (0-255). */
	static final float CORE_WIDTH = 0.05F, GLOW_WIDTH = 0.18F;
	static final int[] CORE_RGB = {0xFFF4B0, 0xE8F8FF}, GLOW_RGB = {0xFF8A1E, 0x4FB8FF};
	static final int CORE_ALPHA = 230, GLOW_ALPHA = 120;
	/** Bolt points closer than this to the camera are left out (your own streak, in first person). */
	static final double BOLT_NEAR_CAMERA = 0.8;

	// ---- tuning: glyphs ----
	/** Enchanting glyphs spawned a tick at full speed (a fraction rounds randomly), and how far they drift off (blocks). */
	static final float GLYPHS_PER_TICK = 2.5F;
	static final double GLYPH_DRIFT = 1.1;

	private static final int FULL_BRIGHT = 0xF000F0;

	/** Set on a runner's render state when the world extracts it (the entity id), so its frames can be kept. */
	static final RenderStateDataKey<Integer> RUNNER = RenderStateDataKey.create(() -> "jaymospowers:chrono_runner");

	/** Where a runner was on a tick, how hard they were streaking, and the frame the world drew them in (if any). */
	private record Sample(long tick, Vec3 pos, float intensity, @Nullable AvatarRenderState frame) {
	}

	private static final class Trail {
		final ArrayDeque<Sample> samples = new ArrayDeque<>();
		float intensity, intensityO;
		float run, runO;
		/** Its colours ({@link ChronoSpeed#GOLD} or {@link ChronoSpeed#SKY}), kept as it fades. */
		int palette;
		@Nullable AvatarRenderState latest;
	}

	private static final Int2ObjectMap<Trail> TRAILS = new Int2ObjectOpenHashMap<>();

	private ChronoStreak() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ChronoStreak::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(ChronoStreak::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TRAILS.clear());
	}

	/** How much of the run pose {@code entityId} shows now (0-1). */
	static float run(int entityId, float partialTicks) {
		Trail trail = TRAILS.get(entityId);
		return trail == null ? 0.0F : Mth.lerp(partialTicks, trail.runO, trail.run);
	}

	/** Render-state extraction: marks an overclocked runner's frame so the world's copy of it can be kept. */
	public static void extract(Entity entity, AvatarRenderState state) {
		Trail trail = TRAILS.get(entity.getId());
		boolean mark = trail != null && trail.intensity > 0.0F && entity instanceof Player player
			&& player.getAttached(ChronoSpeed.WALL_RUN) == null && !player.isFallFlying() && !player.isSwimming() && !player.isPassenger();
		state.setData(RUNNER, mark ? entity.getId() : null);
	}

	private static float approach(float now, float target) {
		float next = now + (target - now) * (target > now ? RISE : FALL);
		return target <= 0.0F && next < 0.01F ? 0.0F : next;
	}

	private static float ramp(double speed, double min, double full) {
		return (float)Mth.clamp((speed - min) / (full - min), 0.0, 1.0);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			TRAILS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		long now = level.getGameTime();
		for (AbstractClientPlayer player : level.players()) {
			Trail trail = TRAILS.get(player.getId());
			boolean on = ChronoSpeed.flashing(player) && !player.isSpectator() && !player.isPassenger() && player.isAlive();
			if (trail == null && !on) {
				continue;
			}
			if (trail == null) {
				trail = new Trail();
				TRAILS.put(player.getId(), trail);
			}
			Integer palette = player.getAttached(ChronoSpeed.FLASH);
			if (palette != null) {
				trail.palette = Mth.clamp(palette, 0, GHOST_RGB.length - 1);
			}
			Vec3 was = new Vec3(player.xo, player.yo, player.zo);
			Vec3 moved = player.position().subtract(was);
			trail.intensityO = trail.intensity;
			trail.runO = trail.run;
			trail.intensity = approach(trail.intensity, on ? ramp(moved.length(), MIN_SPEED, FULL_SPEED) : 0.0F);
			trail.run = approach(trail.run, on && !player.isShiftKeyDown() ? ramp(moved.horizontalDistance(), RUN_MIN_SPEED, RUN_FULL_SPEED) : 0.0F);
			if (trail.intensity > 0.0F) {
				trail.samples.addFirst(new Sample(now, player.position(), trail.intensity, trail.latest));
				glyphs(level, player, was, moved, trail.intensity);
			}
			trail.latest = null;
		}
		for (Iterator<Int2ObjectMap.Entry<Trail>> it = TRAILS.int2ObjectEntrySet().iterator(); it.hasNext();) {
			Int2ObjectMap.Entry<Trail> entry = it.next();
			Trail trail = entry.getValue();
			while (!trail.samples.isEmpty() && now - trail.samples.peekLast().tick() > TRAIL_TICKS) {
				trail.samples.removeLast();
			}
			Entity entity = level.getEntity(entry.getIntKey());
			if (!(entity instanceof Player) || trail.intensity <= 0.0F && trail.run <= 0.0F && trail.samples.isEmpty()) {
				it.remove();
			}
		}
	}

	/** Enchanting glyphs peeling off the trail just behind the runner, drifting out and up. */
	private static void glyphs(ClientLevel level, Player player, Vec3 was, Vec3 moved, float intensity) {
		RandomSource random = level.getRandom();
		float want = GLYPHS_PER_TICK * intensity;
		int count = (int)want + (random.nextFloat() < want - (int)want ? 1 : 0);
		Vec3 back = moved.lengthSqr() > 1.0E-4 ? moved.normalize().scale(-1.0) : Vec3.ZERO;
		for (int i = 0; i < count; i++) {
			// somewhere on the stretch just run, a little behind it
			Vec3 from = was.add(moved.scale(random.nextDouble())).add(back.scale(random.nextDouble() * 0.6))
				.add(0.0, 0.2 + random.nextDouble() * 1.6, 0.0);
			double angle = random.nextDouble() * Mth.TWO_PI;
			Vec3 drift = new Vec3(Math.cos(angle), 0.4 + random.nextDouble() * 0.6, Math.sin(angle)).add(back).normalize()
				.scale(GLYPH_DRIFT * (0.5 + random.nextDouble() * 0.5));
			Vec3 to = from.add(drift);
			// the glyph starts at (position + motion) and flies to its position: from the trail, out to where it fades
			level.addParticle(ParticleTypes.ENCHANT, to.x, to.y, to.z, from.x - to.x, from.y - to.y, from.z - to.z);
		}
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || TRAILS.isEmpty()) {
			return;
		}
		// keep the world's frames of the runners (never a GUI's)
		for (EntityRenderState state : context.levelState().entityRenderStates) {
			if (state instanceof AvatarRenderState avatar) {
				Integer id = avatar.getData(RUNNER);
				Trail trail = id == null ? null : TRAILS.get(id.intValue());
				if (trail != null) {
					trail.latest = avatar;
				}
			}
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		float time = level.getGameTime() + partialTick;
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		for (Int2ObjectMap.Entry<Trail> entry : TRAILS.int2ObjectEntrySet()) {
			Trail trail = entry.getValue();
			if (trail.samples.isEmpty() || !(level.getEntity(entry.getIntKey()) instanceof Player player) || player.isInvisible()) {
				continue;
			}
			if (player.distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			boolean self = player == minecraft.player;
			float fade = Mth.lerp(partialTick, trail.intensityO, trail.intensity);
			ghosts(trail, camera, time, fade, self && firstPerson, poseStack, collector);
			lightning(entry.getIntKey(), trail, player, camera, time, partialTick, fade, self && firstPerson, poseStack, collector);
		}
	}

	private static void ghosts(Trail trail, Vec3 camera, float time, float fade, boolean firstPerson, PoseStack poseStack,
		SubmitNodeCollector collector) {
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		int drawn = 0;
		for (Sample sample : trail.samples) {
			if (drawn >= GHOSTS) {
				break;
			}
			float age = time - sample.tick();
			// every other tick's frame, so each afterimage stays where it was left and fades there
			if (sample.frame() == null || age < 1.0F || Math.floorMod(sample.tick(), GHOST_EVERY) != 0) {
				continue;
			}
			AvatarRenderState state = sample.frame();
			Vec3 at = new Vec3(state.x, state.y, state.z);
			if (firstPerson && at.add(0.0, 1.0, 0.0).distanceToSqr(camera) < GHOST_NEAR_CAMERA * GHOST_NEAR_CAMERA) {
				continue;
			}
			float alpha = GHOST_ALPHA * sample.intensity() * Math.min(1.0F, fade * 1.5F) * Mth.clamp(1.0F - age / (TRAIL_TICKS + 1.0F), 0.0F, 1.0F);
			if (alpha < 0.02F) {
				continue;
			}
			drawn++;
			AvatarRenderer<?> renderer = dispatcher.getRenderer(state);
			PlayerModel model = renderer.getModel();
			Identifier texture = renderer.getTextureLocation(state);
			poseStack.pushPose();
			// as LivingEntityRenderer.submit lays the body out
			poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
			poseStack.scale(state.scale, state.scale, state.scale);
			poseStack.rotateDegrees(Axis.YP, 180.0F - state.bodyRot);
			ChronoRunPose.rotate(state, poseStack);
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.scale(0.9375F, 0.9375F, 0.9375F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			int tint = ARGB.color(Math.round(alpha * 255.0F), GHOST_RGB[trail.palette]);
			if (!com.jaymospowers.client.power.xenoform.XenoformClient.submitGhost(state, poseStack, collector, FULL_BRIGHT, tint)) {
				collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(texture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint,
					null, EntityRenderState.NO_OUTLINE);
			}
			poseStack.popPose();
		}
	}

	/** A point on the bolt path, with how strong the streak is there. */
	private record Point(Vec3 pos, float alpha, long key) {
	}

	private static void lightning(int id, Trail trail, Player player, Vec3 camera, float time, float partialTick, float fade,
		boolean firstPerson, PoseStack poseStack, SubmitNodeCollector collector) {
		long period = (long)Math.floor(time / FLICKER_TICKS);
		// the path, newest first: where the runner is now (unless it's your own eyes), then where they were
		List<Point> path = new ArrayList<>(trail.samples.size() + 1);
		if (!firstPerson && fade > 0.0F) {
			path.add(new Point(player.getPosition(partialTick), fade, Long.MIN_VALUE));
		}
		for (Sample sample : trail.samples) {
			float age = time - sample.tick();
			float alpha = sample.intensity() * Mth.clamp(1.0F - age / TRAIL_TICKS, 0.0F, 1.0F) * Math.min(1.0F, fade * 2.0F + 0.3F);
			path.add(new Point(sample.pos(), alpha, sample.tick()));
		}
		if (path.size() < 2) {
			return;
		}
		List<Vec3> line = new ArrayList<>();
		List<Float> alphas = new ArrayList<>();
		List<float[]> bolts = new ArrayList<>();
		for (int b = 0; b < BOLT_HEIGHTS.length; b++) {
			long seed = hash(id, b, period);
			if (unit(seed) > BOLT_CHANCE) {
				continue;
			}
			line.clear();
			alphas.clear();
			for (int i = 0; i < path.size(); i++) {
				Point p = path.get(i);
				long k = hash(seed, p.key(), 0);
				Vec3 at = p.pos().add(jitter(k, JAG), BOLT_HEIGHTS[b] + (unit(hash(k, 1, 1)) - 0.5) * 2.0 * BOLT_WANDER, jitter(hash(k, 2, 2), JAG));
				if (i > 0) {
					Point q = path.get(i - 1);
					Vec3 prev = line.get(line.size() - 1);
					for (int j = 1; j < JAG_SPLITS; j++) {
						float f = j / (float)JAG_SPLITS;
						long kj = hash(k, j, 3);
						line.add(prev.lerp(at, f).add(jitter(kj, JAG), jitter(hash(kj, 4, 4), JAG), jitter(hash(kj, 5, 5), JAG)));
						alphas.add(Mth.lerp(f, q.alpha(), p.alpha()));
					}
				}
				line.add(at);
				alphas.add(p.alpha());
			}
			bolts.add(flatten(line, alphas));
		}
		if (!firstPerson && fade > 0.05F) {
			// short arcs leaping off the body, back and out
			Vec3 body = player.getPosition(partialTick);
			Vec3 back = trail.samples.size() > 1 ? trail.samples.getLast().pos().subtract(body) : Vec3.ZERO;
			back = back.lengthSqr() > 1.0E-4 ? back.normalize() : Vec3.ZERO;
			for (int a = 0; a < BODY_ARCS; a++) {
				long seed = hash(id, 100 + a, (long)Math.floor(time));
				if (unit(seed) > BOLT_CHANCE) {
					continue;
				}
				line.clear();
				alphas.clear();
				Vec3 start = body.add(jitter(hash(seed, 1, 0), 0.3), 0.2 + unit(hash(seed, 2, 0)) * 1.5, jitter(hash(seed, 3, 0), 0.3));
				Vec3 out = back.add(jitter(hash(seed, 4, 0), 1.0), jitter(hash(seed, 5, 0), 0.8), jitter(hash(seed, 6, 0), 1.0));
				out = out.lengthSqr() > 1.0E-4 ? out.normalize().scale(ARC_LENGTH) : new Vec3(0.0, ARC_LENGTH, 0.0);
				for (int j = 0; j <= JAG_SPLITS; j++) {
					float f = j / (float)JAG_SPLITS;
					long kj = hash(seed, j, 7);
					double off = j == 0 ? 0.0 : JAG * 0.6;
					line.add(start.add(out.scale(f)).add(jitter(kj, off), jitter(hash(kj, 1, 8), off), jitter(hash(kj, 2, 9), off)));
					alphas.add(fade * (1.0F - f * 0.8F));
				}
				bolts.add(flatten(line, alphas));
			}
		}
		if (bolts.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		Vec3 eye = camera;
		int glow = GLOW_RGB[trail.palette], core = CORE_RGB[trail.palette];
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			for (float[] bolt : bolts) {
				ribbon(buffer, pose, bolt, eye, GLOW_WIDTH, glow, GLOW_ALPHA);
				ribbon(buffer, pose, bolt, eye, CORE_WIDTH, core, CORE_ALPHA);
			}
		});
		poseStack.popPose();
	}

	/** x, y, z, alpha for each point. */
	private static float[] flatten(List<Vec3> line, List<Float> alphas) {
		float[] out = new float[line.size() * 4];
		for (int i = 0; i < line.size(); i++) {
			Vec3 v = line.get(i);
			out[i * 4] = (float)v.x;
			out[i * 4 + 1] = (float)v.y;
			out[i * 4 + 2] = (float)v.z;
			out[i * 4 + 3] = alphas.get(i);
		}
		return out;
	}

	/** A flat strip along the bolt, turned to face the camera, each point's alpha its own. */
	private static void ribbon(VertexConsumer buffer, PoseStack.Pose pose, float[] bolt, Vec3 eye, float width, int rgb, int maxAlpha) {
		for (int i = 0; i + 7 < bolt.length; i += 4) {
			Vec3 a = new Vec3(bolt[i], bolt[i + 1], bolt[i + 2]);
			Vec3 b = new Vec3(bolt[i + 4], bolt[i + 5], bolt[i + 6]);
			if (a.distanceToSqr(eye) < BOLT_NEAR_CAMERA * BOLT_NEAR_CAMERA || b.distanceToSqr(eye) < BOLT_NEAR_CAMERA * BOLT_NEAR_CAMERA) {
				continue;
			}
			Vec3 dir = b.subtract(a);
			Vec3 side = dir.cross(eye.subtract(a.lerp(b, 0.5)));
			if (side.lengthSqr() < 1.0E-8) {
				continue;
			}
			side = side.normalize().scale(width * 0.5);
			int ca = ARGB.color(Mth.clamp(Math.round(maxAlpha * bolt[i + 3]), 0, 255), rgb);
			int cb = ARGB.color(Mth.clamp(Math.round(maxAlpha * bolt[i + 7]), 0, 255), rgb);
			// counter-clockwise seen from the camera
			vertex(buffer, pose, a.subtract(side), ca);
			vertex(buffer, pose, a.add(side), ca);
			vertex(buffer, pose, b.add(side), cb);
			vertex(buffer, pose, b.subtract(side), cb);
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 v, int color) {
		buffer.addVertex(pose, (float)v.x, (float)v.y, (float)v.z).setColor(color);
	}

	private static long hash(long a, long b, long c) {
		long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + c * 0x165667B19E3779F9L;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		return h ^ h >>> 33;
	}

	/** 0-1 from a hash. */
	private static float unit(long h) {
		return (h >>> 40) / (float)(1L << 24);
	}

	/** -range..range from a hash. */
	private static double jitter(long h, double range) {
		return (unit(h) * 2.0 - 1.0) * range;
	}
}

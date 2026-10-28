package com.jaymoskingdoms.client;

import com.jaymoskingdoms.RubberStomach;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.306) What a Rubber-folk's stomach looks like, from outside and in ({@link RubberStomach}).
 * <ul>
 * <li><b>The mound</b> ({@link #mound}): a body brought back up lies under a dome of their pink rubber that wobbles,
 * then sags flat, spreads into a puddle and fades, until only the body is left ({@link RubberStomach#MASS}).</li>
 * <li><b>Inside</b> ({@link #inside}): a swallowed player's own screen is walled in by the fleshy inside of the body,
 * pulsing with the swallower's heartbeat, with four organs in it ({@link RubberStomach.Organ}): the heart ahead, a lung to
 * each side, the stomach below. Their attack key hits whichever organ is under the crosshair ({@link #tick}, sent as
 * {@link RubberStomach.Strike}); a struck organ flashes and quivers, and a beaten one sags, dark and limp.</li>
 * </ul>
 * Both reuse {@link RubberEngulf}'s rubber skin.
 */
public final class RubberStomachView {
	/** The mound: its height against the body's (at least, at most), how far it spreads as it melts (a share), and when it starts to fade (a share of the melt). */
	static final float MOUND_LOW = 0.35F, MOUND_HIGH = 0.9F, SPREAD = 0.45F, FADE_FROM = 0.45F;
	/** Inside: how far off the walls are (blocks round the eye), how fast the heart beats (a beat every this many ticks), and a struck organ's flash (ticks). */
	static final float WALL = 3.4F, BEAT = 16.0F, FLASH = 6.0F;
	/** The light inside: dim and red, never quite dark. */
	static final int INSIDE_LIGHT = LightCoordsUtil.pack(11, 0);
	/** Mesh: rings and slices of a dome or a ball. */
	static final int RINGS = 12, SLICES = 18;

	/**
	 * Where each organ sits (blocks from the eye, facing the way they were looking when swallowed: x right, y up, z ahead),
	 * its half-sizes, and its colour (ARGB), in {@link RubberStomach.Organ} order.
	 */
	static final float[][] AT = {{0.0F, 0.25F, 1.7F}, {-1.5F, 0.55F, 0.7F}, {1.5F, 0.55F, 0.7F}, {0.0F, -1.25F, 0.5F}};
	static final float[][] SIZE = {{0.38F, 0.42F, 0.34F}, {0.42F, 0.75F, 0.45F}, {0.42F, 0.75F, 0.45F}, {0.8F, 0.48F, 0.7F}};
	static final int[] COLOR = {0xFFD0304A, 0xFFF0A4B8, 0xFFF0A4B8, 0xFFE07A8C};
	static final int WALL_COLOR = 0xFFB4506A, BEATEN = 0xFF5A2430;

	/** The way they faced when they were swallowed (degrees), or NaN when not inside; the clock when each organ was last struck. */
	private static float facing = Float.NaN;
	private static final float[] STRUCK = new float[RubberStomach.Organ.values().length];

	private RubberStomachView() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(RubberStomachView::render);
		ClientTickEvents.START_CLIENT_TICK.register(RubberStomachView::tick);
	}

	/** The swallowed's own view of who holds them, or null if they aren't inside anyone. */
	private static RubberStomach.Inside inside(LocalPlayer player) {
		return player.getAttached(RubberStomach.INSIDE);
	}

	/** Before the keys are handled: inside, the attack key hits an organ (and nothing else gets used or hit), first person only. */
	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		RubberStomach.Inside inside = player == null ? null : inside(player);
		if (inside == null) {
			facing = Float.NaN;
			return;
		}
		if (Float.isNaN(facing)) {
			facing = player.getYRot();
			java.util.Arrays.fill(STRUCK, -100.0F);
		}
		if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
			minecraft.options.setCameraType(CameraType.FIRST_PERSON);
		}
		while (minecraft.options.keyUse.consumeClick()) {
			// (nothing to use in here)
		}
		while (minecraft.options.keyAttack.consumeClick()) {
			player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
			int organ = aimed(player, inside.organs());
			if (organ >= 0) {
				STRUCK[organ] = player.tickCount;
				ClientPlayNetworking.send(new RubberStomach.Strike(organ));
			}
		}
	}

	/** The organ under the crosshair (the nearest one the look runs into), or -1. */
	private static int aimed(LocalPlayer player, List<Float> organs) {
		Vec3 look = player.getViewVector(1.0F);
		int best = -1;
		double nearest = Double.MAX_VALUE;
		for (int i = 0; i < AT.length && i < organs.size(); i++) {
			if (organs.get(i) <= 0.0F) {
				continue;
			}
			Vec3 centre = place(AT[i]);
			// in the organ's own shape, squashed to a ball: where the look comes nearest its middle
			Vec3 r = new Vec3(SIZE[i][0], SIZE[i][1], SIZE[i][2]);
			Vec3 axisX = place(new float[] {1.0F, 0.0F, 0.0F}), axisZ = place(new float[] {0.0F, 0.0F, 1.0F});
			Vec3 c = new Vec3(centre.dot(axisX) / r.x, centre.y / r.y, centre.dot(axisZ) / r.z);
			Vec3 d = new Vec3(look.dot(axisX) / r.x, look.y / r.y, look.dot(axisZ) / r.z);
			double t = c.dot(d) / d.lengthSqr();
			if (t <= 0.0) {
				continue;
			}
			if (d.scale(t).distanceToSqr(c) <= 1.0 && t < nearest) {
				nearest = t;
				best = i;
			}
		}
		return best;
	}

	/** {@code at} (x right, y up, z ahead, as they faced when swallowed) as a direction in the world. */
	private static Vec3 place(float[] at) {
		float yaw = (Float.isNaN(facing) ? 0.0F : facing) * Mth.DEG_TO_RAD;
		double sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		// ahead is (-sin, 0, cos) at yaw; right is (-cos, 0, -sin)
		return new Vec3(-sin * at[2] - cos * at[0], at[1], cos * at[2] - sin * at[0]);
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float now = level.getGameTime() + partialTick;
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof AreaEffectCloud cloud && entity.position().distanceToSqr(camera) < 64.0 * 64.0) {
				RubberStomach.Mass mass = cloud.getAttached(RubberStomach.MASS);
				if (mass != null) {
					mound(context, level, camera, cloud.getPosition(partialTick), mass, now);
				}
			}
		}
		LocalPlayer player = minecraft.player;
		RubberStomach.Inside inside = player == null ? null : inside(player);
		if (inside != null && !Float.isNaN(facing)) {
			inside(context, player, inside, player.tickCount + partialTick);
		}
	}

	/** A pink mound over a body, {@code now} into its melt. */
	private static void mound(LevelRenderContext context, ClientLevel level, Vec3 camera, Vec3 at, RubberStomach.Mass mass, float now) {
		float t = Mth.clamp((now - mass.start()) / Math.max(1, mass.length()), 0.0F, 1.0F);
		if (t >= 1.0F) {
			return;
		}
		// the body lies flat under it: as long as it is tall, as thick as it is wide
		float reach = Math.max(mass.width(), Math.min(mass.height(), 2.2F)) * 0.6F + 0.15F;
		float rx = reach * (1.0F + SPREAD * ease(t));
		float high = Mth.clamp(mass.width() * 0.9F, MOUND_LOW, MOUND_HIGH);
		// it holds its shape a little, then sags into a puddle
		float ry = Math.max(0.03F, high * (float)Math.pow(1.0F - t, 1.6));
		float alpha = t < FADE_FROM ? 1.0F : 1.0F - (t - FADE_FROM) / (1.0F - FADE_FROM);
		int color = ((int)(alpha * 255.0F) << 24) | 0xFFFFFF;
		int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(at.add(0.0, 0.3, 0.0)));
		Vec3 centre = at.subtract(camera);
		float clock = now;
		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(RubberEngulf.TEXTURE), (pose, buffer) ->
			ball(buffer, pose, centre, rx, ry, rx, true, false, color, light, clock, 0.08F * (1.0F - t)));
	}

	/** The inside of the body round their eye: the walls, and the organs. */
	private static void inside(LevelRenderContext context, LocalPlayer player, RubberStomach.Inside inside, float clock) {
		// the heartbeat: a quick double throb every BEAT ticks
		float phase = (clock % BEAT) / BEAT;
		float beat = (float)(Math.exp(-Math.pow((phase - 0.1) * 14.0, 2.0)) + 0.6 * Math.exp(-Math.pow((phase - 0.28) * 14.0, 2.0)));
		float breath = 0.5F + 0.5F * Mth.sin(clock * 0.09F);
		List<Float> organs = inside.organs();
		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(RubberEngulf.TEXTURE), (pose, buffer) -> {
			int wall = shade(WALL_COLOR, 0.75F + 0.25F * beat);
			ball(buffer, pose, Vec3.ZERO, WALL * (1.0F + 0.03F * beat), WALL * 0.8F, WALL * (1.0F + 0.03F * beat), false, true, wall, INSIDE_LIGHT, clock, 0.05F);
			for (int i = 0; i < AT.length && i < organs.size(); i++) {
				float whole = Mth.clamp(organs.get(i), 0.0F, 1.0F);
				float since = clock - STRUCK[i];
				float flash = since >= 0.0F && since < FLASH ? 1.0F - since / FLASH : 0.0F;
				float sx = SIZE[i][0], sy = SIZE[i][1], sz = SIZE[i][2];
				if (i == RubberStomach.Organ.HEART.ordinal()) {
					float s = 1.0F + 0.12F * beat * whole;
					sx *= s;
					sy *= s;
					sz *= s;
				} else if (i != RubberStomach.Organ.STOMACH.ordinal()) {
					sy *= 0.9F + 0.15F * breath * whole;
				}
				// a beaten organ sags, smaller and dark
				float limp = 0.55F + 0.45F * whole;
				sx *= limp;
				sy *= limp * limp;
				sz *= limp;
				int color = lerpColor(BEATEN, COLOR[i], whole);
				if (flash > 0.0F) {
					color = lerpColor(color, 0xFFFFFFFF, flash * 0.7F);
				}
				Vec3 centre = place(AT[i]).add(0.0, -(1.0F - limp) * SIZE[i][1], 0.0);
				ball(buffer, pose, centre, sx, sy, sz, false, false, color, INSIDE_LIGHT, clock + i * 13.0F, 0.04F + 0.12F * flash);
			}
		});
	}

	/**
	 * A ball (half-sizes {@code rx}, {@code ry}, {@code rz}) round {@code centre}, or its top half ({@code dome}), seen from
	 * outside, or from within ({@code inward}); wobbling by {@code wobble} as rubber does.
	 */
	private static void ball(VertexConsumer buffer, PoseStack.Pose pose, Vec3 centre, float rx, float ry, float rz, boolean dome, boolean inward, int color,
		int light, float clock, float wobble) {
		double last = dome ? Math.PI / 2.0 : Math.PI;
		Vec3[][] point = new Vec3[RINGS + 1][SLICES + 1];
		Vec3[][] normal = new Vec3[RINGS + 1][SLICES + 1];
		for (int i = 0; i <= RINGS; i++) {
			double theta = last * i / RINGS;
			for (int j = 0; j <= SLICES; j++) {
				double phi = j * Math.PI * 2.0 / SLICES;
				Vec3 dir = new Vec3(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
				double w = 1.0 + wobble * Math.sin(clock * 0.45 + theta * 3.0 + phi * 2.0);
				if (dome && i == RINGS) {
					// the rim spreads unevenly, in fingers
					w += 0.1 * Math.sin(phi * 5.0 + clock * 0.05);
				}
				point[i][j] = centre.add(dir.x * rx * w, dir.y * ry * w, dir.z * rz * w);
				Vec3 n = new Vec3(dir.x / rx, dir.y / ry, dir.z / rz).normalize();
				normal[i][j] = inward ? n.scale(-1.0) : n;
			}
		}
		for (int i = 0; i < RINGS; i++) {
			for (int j = 0; j < SLICES; j++) {
				float u0 = j / (float)SLICES, u1 = (j + 1) / (float)SLICES, v0 = i / (float)RINGS, v1 = (i + 1) / (float)RINGS;
				if (inward) {
					vertex(buffer, pose, point[i][j], normal[i][j], u0, v0, color, light);
					vertex(buffer, pose, point[i + 1][j], normal[i + 1][j], u0, v1, color, light);
					vertex(buffer, pose, point[i + 1][j + 1], normal[i + 1][j + 1], u1, v1, color, light);
					vertex(buffer, pose, point[i][j + 1], normal[i][j + 1], u1, v0, color, light);
				} else {
					vertex(buffer, pose, point[i][j], normal[i][j], u0, v0, color, light);
					vertex(buffer, pose, point[i][j + 1], normal[i][j + 1], u1, v0, color, light);
					vertex(buffer, pose, point[i + 1][j + 1], normal[i + 1][j + 1], u1, v1, color, light);
					vertex(buffer, pose, point[i + 1][j], normal[i + 1][j], u0, v1, color, light);
				}
			}
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, Vec3 n, float u, float v, int color, int light) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light).setNormal(pose, (float)n.x, (float)n.y, (float)n.z);
	}

	private static int shade(int argb, float by) {
		int r = Math.min(255, (int)(((argb >> 16) & 0xFF) * by)), g = Math.min(255, (int)(((argb >> 8) & 0xFF) * by)), b = Math.min(255, (int)((argb & 0xFF) * by));
		return (argb & 0xFF000000) | r << 16 | g << 8 | b;
	}

	private static int lerpColor(int from, int to, float t) {
		int a = (int)Mth.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF), r = (int)Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
		int g = (int)Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF), b = (int)Mth.lerp(t, from & 0xFF, to & 0xFF);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static float ease(float x) {
		return x * x * (3.0F - 2.0F * x);
	}
}

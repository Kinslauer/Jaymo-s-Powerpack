package com.jaymospowers.client.armory;

import com.jaymospowers.armory.Lash;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.15.147) Draws every flail's chain and ball and every whip's lash that's out ({@link Lash.State}, a synced player
 * attachment): from the hand to where {@link Lash#head} puts the head this frame, bowed back the way the head has come
 * and sagging when slack, so it trails behind a whirl and snakes out on a crack. Solid tubes, like the symbiote's
 * cords, shaded by which way each face looks and darkened in the dark.
 */
public final class LashRenderer {
	/** The chain: the ball's reach plus a little; how thick, a link's colour and its glint. */
	private static final double CHAIN_LENGTH = Lash.WHIRL_RADIUS + 0.7, CHAIN_RADIUS = 0.032;
	private static final int CHAIN_DARK = 0x3A3A40, CHAIN_LIGHT = 0x9A9AA4;
	/** The lash: its length, thick at the handle and thin at the tip, leather brown with a pale popper at the end. */
	private static final double WHIP_LENGTH = Lash.WHIP_REACH + 0.6, WHIP_BASE = 0.045, WHIP_TIP = 0.012;
	private static final int LEATHER_DARK = 0x3B2414, LEATHER_LIGHT = 0x7A4E2C, POPPER = 0xC9B48E;
	/** How much a slack line hangs (share of its slack) and how far it bows back after the head (share of its motion). */
	private static final double SAG = 0.45, LAG = 0.9;
	private static final int SEGMENTS = 18, SIDES = 6;
	/** The ball: half its width, and its spikes' length. */
	private static final double BALL = 0.17, SPIKE = 0.13;

	private LashRenderer() {
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(LashRenderer::render);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (Lash.isFlail(stack)) {
				lines.add(Component.translatable("item.jaymospowers.flail.whirl").withStyle(ChatFormatting.GRAY));
			}
		});
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double time = level.getGameTime() + partialTick;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Player player : level.players()) {
			Lash.State state = player.getAttached(Lash.STATE);
			if (state == null || player.isRemoved() || player.position().distanceToSqr(camera) > 64.0 * 64.0) {
				continue;
			}
			Vec3 hand = handPos(minecraft, player, partialTick);
			Vec3 head = Lash.head(player, state, time, partialTick);
			Vec3 before = Lash.head(player, state, time - 1.5, partialTick);
			float light = Math.max(0.2F, level.getMaxLocalRawBrightness(BlockPos.containing(head)) / 15.0F);
			boolean flail = state.kind() == Lash.FLAIL;
			boolean whirl = state.phase() == Lash.WHIRL;
			Vec3[] line = curve(hand, head, before, flail ? CHAIN_LENGTH : WHIP_LENGTH, whirl ? 0.15 : 1.0);
			for (int i = 0; i < line.length; i++) {
				line[i] = line[i].subtract(camera);
			}
			if (flail) {
				chain(context, line, light);
				ball(context, head.subtract(camera), time * 0.4, tint(player), light);
			} else {
				lash(context, line, light);
			}
		}
	}

	/**
	 * The line from {@code from} to {@code to}: a curve bowed back toward where the head just was ({@code before}), and
	 * hanging by however much shorter than {@code length} the gap is ({@code sag} scales it: a whirl's chain is taut).
	 */
	private static Vec3[] curve(Vec3 from, Vec3 to, Vec3 before, double length, double sag) {
		Vec3 mid = from.add(to).scale(0.5);
		double slack = Math.max(0.0, length - from.distanceTo(to));
		Vec3 control = mid.add(before.subtract(to).scale(LAG)).add(0.0, -slack * SAG * sag, 0.0);
		Vec3[] points = new Vec3[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			double t = i / (double)SEGMENTS;
			double a = (1.0 - t) * (1.0 - t), b = 2.0 * (1.0 - t) * t, c = t * t;
			points[i] = from.scale(a).add(control.scale(b)).add(to.scale(c));
		}
		return points;
	}

	/** A chain: links of dark iron with a glint on every other one. */
	private static void chain(LevelRenderContext context, Vec3[] line, float light) {
		tube(context, line, i -> CHAIN_RADIUS, i -> dim(i % 2 == 0 ? CHAIN_DARK : CHAIN_LIGHT, light));
	}

	/** The lash: tapering to the tip, the popper paler. */
	private static void lash(LevelRenderContext context, Vec3[] line, float light) {
		tube(context, line, i -> Mth.lerp(i / (double)SEGMENTS, WHIP_BASE, WHIP_TIP),
			i -> dim(i >= SEGMENTS - 1 ? POPPER : i % 3 == 0 ? LEATHER_DARK : LEATHER_LIGHT, light));
	}

	private interface Radius {
		double at(int i);
	}

	private interface Colour {
		int at(int i);
	}

	/** A tube through {@code line}: a ring of {@link #SIDES} at each point, quads between, lit from above. */
	private static void tube(LevelRenderContext context, Vec3[] line, Radius radius, Colour colour) {
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			Vec3[] prev = null;
			for (int i = 0; i < line.length; i++) {
				Vec3 dir = line[Math.min(i + 1, line.length - 1)].subtract(line[Math.max(i - 1, 0)]);
				if (dir.lengthSqr() < 1.0E-8) {
					dir = new Vec3(0.0, 1.0, 0.0);
				}
				dir = dir.normalize();
				Vec3 u = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
				Vec3 v = dir.cross(u).normalize();
				double r = radius.at(i);
				Vec3[] ring = new Vec3[SIDES];
				for (int j = 0; j < SIDES; j++) {
					double a = j * Math.PI * 2.0 / SIDES;
					ring[j] = line[i].add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
				}
				if (prev != null) {
					int base = colour.at(i - 1);
					for (int j = 0; j < SIDES; j++) {
						int n = (j + 1) % SIDES;
						double a = (j + 0.5) * Math.PI * 2.0 / SIDES;
						double up = u.scale(Math.cos(a)).add(v.scale(Math.sin(a))).y;
						quad(buffer, pose, prev[j], prev[n], ring[n], ring[j], shade(base, up));
					}
				}
				prev = ring;
			}
		});
	}

	/** The flail's head: a spiked iron ball, turning as it goes. */
	private static void ball(LevelRenderContext context, Vec3 at, double turn, int metal, float light) {
		int base = dim(metal, light);
		double c = Math.cos(turn), s = Math.sin(turn);
		// the ball's axes, turned about the vertical and tipped a little
		Vec3 x = new Vec3(c, 0.25, s).normalize(), z = new Vec3(-s, 0.0, c), y = z.cross(x).normalize();
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			Vec3[] axes = {x, y, z};
			for (int k = 0; k < 3; k++) {
				Vec3 a = axes[k], b = axes[(k + 1) % 3], n = axes[(k + 2) % 3];
				for (int sign = -1; sign <= 1; sign += 2) {
					Vec3 face = at.add(n.scale(sign * BALL));
					Vec3 p0 = face.add(a.scale(-BALL)).add(b.scale(-BALL)), p1 = face.add(a.scale(BALL)).add(b.scale(-BALL));
					Vec3 p2 = face.add(a.scale(BALL)).add(b.scale(BALL)), p3 = face.add(a.scale(-BALL)).add(b.scale(BALL));
					int shade = shade(base, n.y * sign);
					if (sign > 0) {
						quad(buffer, pose, p0, p1, p2, p3, shade);
					} else {
						quad(buffer, pose, p3, p2, p1, p0, shade);
					}
					// a spike out of each face: four thin sides meeting at the point
					Vec3 tip = face.add(n.scale(sign * SPIKE));
					double w = BALL * 0.4;
					Vec3 q0 = face.add(a.scale(-w)).add(b.scale(-w)), q1 = face.add(a.scale(w)).add(b.scale(-w));
					Vec3 q2 = face.add(a.scale(w)).add(b.scale(w)), q3 = face.add(a.scale(-w)).add(b.scale(w));
					int spike = shade(dim(CHAIN_LIGHT, light), n.y * sign);
					quad(buffer, pose, q0, q1, tip, tip, spike);
					quad(buffer, pose, q1, q2, tip, tip, spike);
					quad(buffer, pose, q2, q3, tip, tip, spike);
					quad(buffer, pose, q3, q0, tip, tip, spike);
				}
			}
		});
	}

	/** The ball's metal, by the flail's tier (Balkon's ids: flail.wood, flail.iron, ...). */
	private static int tint(Player player) {
		String path = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath();
		if (path.endsWith("wood")) {
			return 0x6B4F2A;
		} else if (path.endsWith("stone")) {
			return 0x7A7A7A;
		} else if (path.endsWith("copper")) {
			return 0xB4683E;
		} else if (path.endsWith("gold")) {
			return 0xE2C04A;
		} else if (path.endsWith("diamond")) {
			return 0x3FC9B0;
		} else if (path.endsWith("netherite")) {
			return 0x4A4042;
		}
		return 0x8E8E96;
	}

	/** Where the line leaves the hand: in first person the hand's screen corner (as the fishing line does), else the hand. */
	private static Vec3 handPos(Minecraft minecraft, Player player, float partialTick) {
		if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
			float fov = minecraft.options.fov().get().floatValue();
			Camera camera = minecraft.gameRenderer.mainCamera();
			Vec3 offset = camera.getNearPlane(fov).getPointOnPlane(side * 0.525F, -0.1F).scale(960.0 / fov);
			return player.getEyePosition(partialTick).add(offset);
		}
		return Lash.hand(player, partialTick);
	}

	private static int dim(int rgb, float light) {
		int r = (int)((rgb >> 16 & 0xFF) * light), g = (int)((rgb >> 8 & 0xFF) * light), b = (int)((rgb & 0xFF) * light);
		return r << 16 | g << 8 | b;
	}

	/** {@code rgb} shaded by how much its face looks up (-1 down, 1 up). */
	private static int shade(int rgb, double up) {
		float f = (float)(0.65 + 0.35 * up);
		int r = Mth.clamp((int)((rgb >> 16 & 0xFF) * f), 0, 255), g = Mth.clamp((int)((rgb >> 8 & 0xFF) * f), 0, 255);
		int b = Mth.clamp((int)((rgb & 0xFF) * f), 0, 255);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
		buffer.addVertex(pose, (float)a.x, (float)a.y, (float)a.z).setColor(color);
		buffer.addVertex(pose, (float)b.x, (float)b.y, (float)b.z).setColor(color);
		buffer.addVertex(pose, (float)c.x, (float)c.y, (float)c.z).setColor(color);
		buffer.addVertex(pose, (float)d.x, (float)d.y, (float)d.z).setColor(color);
	}
}

package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Shackles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.584) A captive's collar and tether as everyone sees it ({@link Shackles#TETHER}): an iron collar with a chain of
 * links, or a rope noose with a rope, sagging from the neck to the stake beside them. In your own first person only the
 * line is drawn, falling away from under your chin.
 */
public final class ShackleTether {
	static final Identifier CHAIN = JaymosKingdoms.id("textures/entity/shackle/chain.png");
	static final Identifier IRON = JaymosKingdoms.id("textures/entity/shackle/iron.png");
	public static final Identifier ROPE = JaymosKingdoms.id("textures/entity/shackle/rope.png");
	/** A chain link's length, its width; the rope's radius; the collar's radius and thickness (blocks). */
	static final double LINK = 0.16, LINK_WIDTH = 0.11, ROPE_RADIUS = 0.035, COLLAR = 0.24, COLLAR_THICK = 0.05;
	/** How far below the eyes the neck is, and how much the line sags per block of length. */
	static final double NECK_DROP = 0.22, SAG = 0.18;
	/** Sides round the rope and collar, pieces round the collar, pieces along the rope. */
	static final int SIDES = 6, RING = 16, PIECES = 12;

	private ShackleTether() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(ShackleTether::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		CameraRenderState cameraState = context.levelState().cameraRenderState;
		Vec3 camera = cameraState.pos;
		for (Player player : level.players()) {
			Shackles.Tether tether = player.getAttached(Shackles.TETHER);
			if (tether == null || player.isSpectator() || player.position().distanceToSqr(camera) > 64.0 * 64.0) {
				continue;
			}
			boolean self = player == minecraft.player && cameraState.isFirstPerson;
			Vec3 neck = player.getEyePosition(partialTick).subtract(0.0, self ? NECK_DROP + 0.25 : NECK_DROP, 0.0);
			BlockPos stake = tether.stake();
			Vec3 post = new Vec3(stake.getX() + 0.5, stake.getY() + 0.55, stake.getZ() + 0.5);
			int light = net.minecraft.util.LightCoordsUtil.getLightCoords(level, BlockPos.containing(neck));
			PoseStack poseStack = context.poseStack();
			Vec3 from = neck.subtract(camera), to = post.subtract(camera);
			if (tether.rope()) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(ROPE), (pose, buffer) -> {
					rope(buffer, pose, from, to, light);
					if (!self) {
						collar(buffer, pose, from, COLLAR * 0.9, COLLAR_THICK * 0.8, light);
					}
				});
			} else {
				context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(CHAIN),
					(pose, buffer) -> chain(buffer, pose, from, to, light));
				if (!self) {
					context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(IRON),
						(pose, buffer) -> collar(buffer, pose, from, COLLAR, COLLAR_THICK, light));
				}
			}
		}
	}

	/** Where the line hangs {@code s} of the way from {@code from} to {@code to}. */
	public static Vec3 along(Vec3 from, Vec3 to, double s) {
		double length = to.subtract(from).length();
		return from.add(to.subtract(from).scale(s)).add(0.0, -Math.sin(s * Math.PI) * length * SAG, 0.0);
	}

	/** Links of chain, each two crossed quads, every other one turned a quarter. */
	private static void chain(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, int light) {
		double length = to.subtract(from).length();
		int links = Math.max(2, (int)Math.ceil(length * (1.0 + SAG) / LINK));
		for (int i = 0; i < links; i++) {
			Vec3 a = along(from, to, i / (double)links), b = along(from, to, (i + 1) / (double)links);
			Vec3 dir = b.subtract(a);
			if (dir.lengthSqr() < 1.0E-8) {
				continue;
			}
			dir = dir.normalize();
			Vec3 u = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
			Vec3 v = dir.cross(u).normalize();
			if ((i & 1) == 1) {
				Vec3 t = u;
				u = v;
				v = t.scale(-1.0);
			}
			double w = LINK_WIDTH * 0.5;
			// (a little past each end, so the links hook into each other)
			Vec3 a2 = a.subtract(dir.scale(LINK * 0.15)), b2 = b.add(dir.scale(LINK * 0.15));
			quad(buffer, pose, a2.add(u.scale(w)), b2.add(u.scale(w)), b2.subtract(u.scale(w)), a2.subtract(u.scale(w)), v, light);
			quad(buffer, pose, a2.subtract(u.scale(w)), b2.subtract(u.scale(w)), b2.add(u.scale(w)), a2.add(u.scale(w)), v.scale(-1.0), light);
			Vec3 o = (i & 1) == 1 ? u : v;
			double w2 = w * 0.55;
			quad(buffer, pose, a2.add(o.scale(w2)), b2.add(o.scale(w2)), b2.subtract(o.scale(w2)), a2.subtract(o.scale(w2)), (i & 1) == 1 ? v : u, light);
			quad(buffer, pose, a2.subtract(o.scale(w2)), b2.subtract(o.scale(w2)), b2.add(o.scale(w2)), a2.add(o.scale(w2)), ((i & 1) == 1 ? v : u).scale(-1.0), light);
		}
	}

	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 n, int light) {
		vertex(buffer, pose, a, n, 0.0F, 0.0F, light);
		vertex(buffer, pose, b, n, 0.0F, 1.0F, light);
		vertex(buffer, pose, c, n, 1.0F, 1.0F, light);
		vertex(buffer, pose, d, n, 1.0F, 0.0F, light);
	}

	/** A twisted rope, a tube sagging between the two. */
	public static void rope(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, int light) {
		Vec3[] centre = new Vec3[PIECES + 1];
		for (int i = 0; i <= PIECES; i++) {
			centre[i] = along(from, to, i / (double)PIECES);
		}
		for (int i = 0; i < PIECES; i++) {
			Vec3 dir = centre[i + 1].subtract(centre[i]);
			if (dir.lengthSqr() < 1.0E-8) {
				continue;
			}
			dir = dir.normalize();
			Vec3 u = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
			Vec3 v = dir.cross(u).normalize();
			tube(buffer, pose, centre[i], centre[i + 1], u, v, ROPE_RADIUS, light);
		}
	}

	private static void tube(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 u, Vec3 v, double r, int light) {
		for (int k = 0; k < SIDES; k++) {
			double a0 = k * Math.PI * 2.0 / SIDES, a1 = (k + 1) * Math.PI * 2.0 / SIDES;
			Vec3 n0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0))), n1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
			float u0 = k / (float)SIDES, u1 = (k + 1) / (float)SIDES;
			vertex(buffer, pose, a.add(n0.scale(r)), n0, u0, 0.0F, light);
			vertex(buffer, pose, b.add(n0.scale(r)), n0, u0, 1.0F, light);
			vertex(buffer, pose, b.add(n1.scale(r)), n1, u1, 1.0F, light);
			vertex(buffer, pose, a.add(n1.scale(r)), n1, u1, 0.0F, light);
		}
	}

	/** A ring flat round the neck. */
	private static void collar(VertexConsumer buffer, PoseStack.Pose pose, Vec3 neck, double radius, double thick, int light) {
		for (int i = 0; i < RING; i++) {
			double t0 = i * Math.PI * 2.0 / RING, t1 = (i + 1) * Math.PI * 2.0 / RING;
			Vec3 p0 = neck.add(Math.cos(t0) * radius, 0.0, Math.sin(t0) * radius);
			Vec3 p1 = neck.add(Math.cos(t1) * radius, 0.0, Math.sin(t1) * radius);
			Vec3 out = new Vec3(Math.cos((t0 + t1) * 0.5), 0.0, Math.sin((t0 + t1) * 0.5));
			tube(buffer, pose, p0, p1, out, new Vec3(0.0, 1.0, 0.0), thick, light);
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, Vec3 n, float u, float v, int light) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light).setNormal(pose, (float)n.x, (float)n.y, (float)n.z);
	}
}

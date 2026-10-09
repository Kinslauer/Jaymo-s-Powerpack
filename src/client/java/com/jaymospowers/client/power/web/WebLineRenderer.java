package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every web line in view (the local player's from their own state, so it shows the same tick it fires; other
 * players' from the synced line attachments), each from its own hand, to the anchor, with a little
 * splat of strands where it sticks. Hooked into Fabric's level render "collect submits" phase.
 */
final class WebLineRenderer {
	private static final int COLOR = 0xFFEDEDED;
	private static final int SPLAT_COLOR = 0xFFD8D8D8;
	private static final int SEGMENTS = 12;
	/** A symbiote's webs: near-black, with a purple sheen that runs along them. */
	private static final int DARK = 0xFF100C16;
	private static final int SHEEN = 0xFF6A3C92;
	/** How fast the sheen runs (cycles per tick) and how much of the line it lights. */
	private static final float SHEEN_SPEED = 0.03F;
	private static final float SHEEN_WIDTH = 0.18F;

	/** The colour at {@code t} (0-1) along a symbiote's web, right now. */
	private static int sheen(float t) {
		Minecraft minecraft = Minecraft.getInstance();
		float time = minecraft.level == null ? 0.0F : (minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)) * SHEEN_SPEED;
		float phase = time - (float)Math.floor(time);
		float d = Math.abs(t - phase);
		d = Math.min(d, 1.0F - d);
		float glow = Math.max(0.0F, 1.0F - d / SHEEN_WIDTH);
		return net.minecraft.util.ARGB.srgbLerp(glow * glow * 0.8F, DARK, SHEEN);
	}
	/** Lines are drawn this much thicker than vanilla's fishing line. */
	private static final float WIDTH_SCALE = 1.4F;

	/**
	 * Creatures hitched to a block, found once a tick rather than by checking every entity every frame (hitches start
	 * and end on the server's tick, so a tick-old list is never behind).
	 */
	private static final java.util.List<net.minecraft.world.entity.Entity> HITCHED = new java.util.ArrayList<>();

	private WebLineRenderer() {
	}

	/** End of every client tick: remember which creatures are hitched. */
	static void tick(Minecraft minecraft) {
		HITCHED.clear();
		if (minecraft.level == null) {
			return;
		}
		if (!minecraft.isPaused()) {
			drip(minecraft);
		}
		for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity.getAttached(com.jaymospowers.power.web.WebPower.HITCH) != null) {
				HITCHED.add(entity);
			}
		}
	}

	/** How much thicker a symbiote's line is than a web's, and how often goo drips off it (per line, per tick). */
	private static final float DARK_WIDTH = 2.6F;
	private static final float DRIP_CHANCE = 0.6F;
	private static final net.minecraft.core.particles.DustParticleOptions GOO = new net.minecraft.core.particles.DustParticleOptions(0x0B0910, 1.3F);

	/** Three thick black cords, from around the shoulder and arm, gathering on the same point. */
	private static void bundle(LevelRenderContext context, Vec3 from, Vec3 to, float width) {
		float w = width * DARK_WIDTH;
		draw(context.poseStack(), context, from, to, w, true);
		draw(context.poseStack(), context, from.add(0.0, 0.4, 0.0), to.add(0.0, 0.12, 0.0), w * 0.8F, true);
		draw(context.poseStack(), context, from.add(0.0, -0.3, 0.0), to.add(0.0, -0.1, 0.0), w * 0.7F, true);
	}

	/** Black goo dripping off a symbiote's lines, along their length. */
	private static void drip(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		net.minecraft.util.RandomSource random = level.getRandom();
		for (Player player : level.players()) {
			if (!com.jaymospowers.power.web.WebSymbiote.suitShown(player) || player.distanceToSqr(minecraft.player) > 64.0 * 64.0) {
				continue;
			}
			for (int handIndex = WebNet.MAIN; handIndex <= WebNet.OFF; handIndex++) {
				Vec3 end = WebArms.lineEnd(minecraft, level, player, handIndex, 1.0F);
				if (end == null || random.nextFloat() > DRIP_CHANCE) {
					continue;
				}
				Vec3 at = handPos(minecraft, player, handIndex, 1.0F).lerp(end, random.nextDouble());
				level.addParticle(GOO, at.x, at.y - 0.05, at.z, 0.0, -0.08, 0.0);
				if (random.nextInt(4) == 0) {
					level.addParticle(net.minecraft.core.particles.ParticleTypes.FALLING_OBSIDIAN_TEAR, at.x, at.y - 0.05, at.z, 0.0, 0.0, 0.0);
				}
			}
		}
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float width = minecraft.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth * WIDTH_SCALE;
		// creatures hitched to blocks: a line from each to where it's tied
		for (net.minecraft.world.entity.Entity entity : HITCHED) {
			Vec3 anchor = entity.getAttached(com.jaymospowers.power.web.WebPower.HITCH);
			if (anchor == null || entity.isRemoved() || entity.level() != level) {
				continue;
			}
			Vec3 from = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0);
			if (from.distanceToSqr(camera) < 96.0 * 96.0) {
				draw(context.poseStack(), context, from.subtract(camera), anchor.subtract(camera), width, false);
			}
		}
		// weblines: the same line, strung taut between their two ends
		java.util.List<com.jaymospowers.power.web.WebBridges.Span> spans = level.getAttached(com.jaymospowers.power.web.WebBridges.SPANS);
		if (spans != null) {
			for (com.jaymospowers.power.web.WebBridges.Span span : spans) {
				if (span.from().distanceToSqr(camera) < 128.0 * 128.0 || span.to().distanceToSqr(camera) < 128.0 * 128.0) {
					draw(context.poseStack(), context, span.from().subtract(camera), span.to().subtract(camera), width, false);
				}
			}
		}
		for (Player player : level.players()) {
			// a symbiote's webs are black, with a purple sheen running along them
			boolean dark = com.jaymospowers.power.web.WebSymbiote.suitShown(player);
			for (int handIndex = WebNet.MAIN; handIndex <= WebNet.OFF; handIndex++) {
				Vec3 end = WebArms.lineEnd(minecraft, level, player, handIndex, partialTick);
				if (end == null) {
					continue;
				}
				Vec3 hand = handPos(minecraft, player, handIndex, partialTick);
				if (hand.distanceToSqr(end) > 96.0 * 96.0) {
					continue;
				}
				// a symbiote's lines are thick, living cords: most of that side's tendrils at once
				if (dark) {
					bundle(context, hand.subtract(camera), end.subtract(camera), width);
				} else {
					draw(context.poseStack(), context, hand.subtract(camera), end.subtract(camera), width, false);
				}
			}
			// whatever the tendrils hold, held up on its cords
			com.jaymospowers.power.web.WebTendrils.Grips grips = player.getAttached(com.jaymospowers.power.web.WebTendrils.GRIPS);
			if (grips != null) {
				for (int handIndex = WebNet.MAIN; handIndex <= WebNet.OFF; handIndex++) {
					net.minecraft.world.entity.Entity held = grips.get(handIndex) < 0 ? null : level.getEntity(grips.get(handIndex));
					if (held != null) {
						Vec3 hand = handPos(minecraft, player, handIndex, partialTick);
						Vec3 at = held.getPosition(partialTick).add(0.0, held.getBbHeight() * 0.55, 0.0);
						bundle(context, hand.subtract(camera), at.subtract(camera), width);
					}
				}
			}
			float spread = WebArms.wings(player, partialTick);
			if (spread > 0.02F && !(player == minecraft.player && minecraft.options.getCameraType().isFirstPerson())) {
				for (int side = -1; side <= 1; side += 2) {
					drawWing(context, WebArms.wingShoulder(player, side, partialTick).subtract(camera),
						WebArms.wingTip(player, side, spread, partialTick).subtract(camera), WebArms.wingHip(player, side, partialTick).subtract(camera),
						width * 0.8F, dark);
				}
			}
		}
	}

	/**
	 * One web wing: a membrane of strands between the arm (shoulder to hand) and the side (hand to hip), with a few
	 * spokes from the armpit and a couple of cross threads, like a spider's web.
	 */
	private static void drawWing(LevelRenderContext context, Vec3 shoulder, Vec3 tip, Vec3 hip, float width, boolean dark) {
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), (pose, buffer) -> {
			line(buffer, pose, shoulder, tip, width, dark);
			line(buffer, pose, tip, hip, width, dark);
			Vec3 armpit = shoulder.lerp(hip, 0.15);
			Vec3 prevA = null;
			Vec3 prevB = null;
			for (int k = 1; k <= 4; k++) {
				double t = k / 5.0;
				// spokes from the armpit out to the wing's edge (arm, then hand-to-hip edge)
				Vec3 edge = k <= 2 ? shoulder.lerp(tip, t * 2.0) : tip.lerp(hip, (t - 0.4) * 1.6);
				line(buffer, pose, armpit, edge, width * 0.7F, dark);
				// cross threads between neighbouring spokes, at two distances out
				Vec3 a = armpit.lerp(edge, 0.45);
				Vec3 b = armpit.lerp(edge, 0.8);
				if (prevA != null) {
					line(buffer, pose, prevA, a, width * 0.6F, dark);
					line(buffer, pose, prevB, b, width * 0.6F, dark);
				}
				prevA = a;
				prevB = b;
			}
		});
	}

	private static void line(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, float width, boolean dark) {
		Vec3 d = b.subtract(a);
		double length = d.length();
		if (length < 1.0E-4) {
			return;
		}
		segment(buffer, pose, a, b, d.scale(1.0 / length), dark ? sheen(0.5F) : COLOR, width);
	}

	/**
	 * Where the web leaves the hand: in first person, the screen corner the hand is in (as the fishing line does it);
	 * otherwise the posed hand at the end of the arm (see WebArms), or roughly where the hand would be.
	 */
	private static Vec3 handPos(Minecraft minecraft, Player player, int handIndex, float partialTick) {
		boolean mainRight = player.getMainArm() == HumanoidArm.RIGHT;
		int side = (handIndex == WebNet.MAIN) == mainRight ? 1 : -1;
		if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			float fov = minecraft.options.fov().get().floatValue();
			Camera camera = minecraft.gameRenderer.mainCamera();
			Vec3 offset = camera.getNearPlane(fov).getPointOnPlane(side * 0.525F, -0.1F).scale(960.0 / fov);
			return player.getEyePosition(partialTick).add(offset);
		}
		Vec3 posed = WebArms.handPos(player, handIndex, partialTick);
		if (posed != null) {
			return posed;
		}
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		double sin = Mth.sin(bodyYaw);
		double cos = Mth.cos(bodyYaw);
		double right = side * 0.35 * player.getScale();
		double forward = 0.3 * player.getScale();
		return player.getEyePosition(partialTick)
			.add(-cos * right - sin * forward, -0.35 * player.getScale(), -sin * right + cos * forward);
	}

	private static void draw(PoseStack poseStack, LevelRenderContext context, Vec3 from, Vec3 to, float width, boolean dark) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 1.0E-3) {
			return;
		}
		Vec3 dir = delta.scale(1.0 / length);
		// two directions across the line, for the splat at the anchor
		Vec3 across = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 across2 = dir.cross(across).normalize();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
			for (int i = 0; i < SEGMENTS; i++) {
				Vec3 a = from.add(delta.scale((double)i / SEGMENTS));
				Vec3 b = from.add(delta.scale((double)(i + 1) / SEGMENTS));
				segment(buffer, pose, a, b, dir, dark ? sheen((i + 0.5F) / SEGMENTS) : COLOR, width);
			}
			// splat: six short strands fanning out where the web sticks
			for (int k = 0; k < 6; k++) {
				double angle = k * Math.PI / 3.0 + 0.4;
				Vec3 spoke = across.scale(Math.cos(angle)).add(across2.scale(Math.sin(angle))).scale(0.35).subtract(dir.scale(0.12));
				Vec3 tip = to.add(spoke);
				segment(buffer, pose, to, tip, spoke.normalize(), dark ? DARK : SPLAT_COLOR, width * 0.7F);
			}
		});
	}

	private static void segment(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 normal, int color, float width) {
		float nx = (float)normal.x;
		float ny = (float)normal.y;
		float nz = (float)normal.z;
		buffer.addVertex(pose, (float)a.x, (float)a.y, (float)a.z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
		buffer.addVertex(pose, (float)b.x, (float)b.y, (float)b.z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
	}
}

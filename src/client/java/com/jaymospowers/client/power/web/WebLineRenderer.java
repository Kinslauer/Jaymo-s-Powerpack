package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
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
	/** A symbiote's webs: black-purple, with a faint sheen that runs along them. */
	private static final int DARK = 0xFF0B0710;
	private static final int SHEEN = 0xFF1F1530;
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
		rope(context, from, to, ROPE_STRANDS, ROPE_STRAND_RADIUS, ROPE_TWIST_RADIUS);
		// where it holds on, the black web spreads
		splat(context, to, to.subtract(from).normalize(), width * DARK_WIDTH, true);
	}

	/** A symbiote's cord: strands this thick (blocks), twisted this far about the middle, a turn per this many blocks. */
	private static final int ROPE_STRANDS = 3;
	private static final double ROPE_STRAND_RADIUS = 0.055;
	private static final double ROPE_TWIST_RADIUS = 0.06;
	private static final double ROPE_TURN_LENGTH = 1.4;
	private static final int ROPE_SIDES = 6;
	private static final double ROPE_STEP = 0.2;
	private static final int ROPE_DARK = 0x080510;
	private static final int ROPE_SHEEN = 0x221634;

	/**
	 * Twisted strands of black, like corded rope or tentacles, as solid tubes. Each face is shaded by which way it
	 * faces, with a faint purple-black sheen along the top, so it reads as round without any lighting.
	 */
	static void rope(LevelRenderContext context, Vec3 from, Vec3 to, int strands, double radius, double twist) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 1.0E-3) {
			return;
		}
		Vec3 dir = delta.scale(1.0 / length);
		Vec3 u = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 v = dir.cross(u).normalize();
		int steps = Math.max(2, (int)Math.ceil(length / ROPE_STEP));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			for (int k = 0; k < strands; k++) {
				double phase = k * Math.PI * 2.0 / strands;
				Vec3[] prev = null;
				for (int i = 0; i <= steps; i++) {
					double t = i / (double)steps;
					double angle = phase + t * length / ROPE_TURN_LENGTH * Math.PI * 2.0;
					// tapering a little toward the far end, like a tentacle
					double r = radius * (1.0 - 0.35 * t);
					Vec3 center = from.add(delta.scale(t)).add(u.scale(Math.cos(angle) * twist)).add(v.scale(Math.sin(angle) * twist));
					Vec3[] ring = new Vec3[ROPE_SIDES];
					for (int j = 0; j < ROPE_SIDES; j++) {
						double a = j * Math.PI * 2.0 / ROPE_SIDES;
						ring[j] = center.add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
					}
					if (prev != null) {
						for (int j = 0; j < ROPE_SIDES; j++) {
							int n = (j + 1) % ROPE_SIDES;
							// lit from above: the side whose middle faces up gets the sheen
							double a = (j + 0.5) * Math.PI * 2.0 / ROPE_SIDES;
							double up = u.scale(Math.cos(a)).add(v.scale(Math.sin(a))).y;
							int color = shade(up);
							quad(buffer, pose, prev[j], prev[n], ring[n], ring[j], color);
						}
					}
					prev = ring;
				}
			}
		});
	}

	private static int shade(double up) {
		float light = (float)Math.max(0.0, up);
		int r = (int)Mth.lerp(light, ROPE_DARK >> 16 & 0xFF, ROPE_SHEEN >> 16 & 0xFF);
		int g = (int)Mth.lerp(light, ROPE_DARK >> 8 & 0xFF, ROPE_SHEEN >> 8 & 0xFF);
		int b = (int)Mth.lerp(light, ROPE_DARK & 0xFF, ROPE_SHEEN & 0xFF);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
		buffer.addVertex(pose, (float)a.x, (float)a.y, (float)a.z).setColor(color);
		buffer.addVertex(pose, (float)b.x, (float)b.y, (float)b.z).setColor(color);
		buffer.addVertex(pose, (float)c.x, (float)c.y, (float)c.z).setColor(color);
		buffer.addVertex(pose, (float)d.x, (float)d.y, (float)d.z).setColor(color);
	}

	/**
	 * Where a line sticks: a little cobweb, spokes fanning out across the line with two rings of thread between them.
	 */
	static void splat(LevelRenderContext context, Vec3 at, Vec3 dir, float width, boolean dark) {
		Vec3 across = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 across2 = dir.cross(across).normalize();
		int color = dark ? DARK : SPLAT_COLOR;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), (pose, buffer) -> {
			Vec3[] inner = new Vec3[SPLAT_SPOKES];
			Vec3[] outer = new Vec3[SPLAT_SPOKES];
			for (int k = 0; k < SPLAT_SPOKES; k++) {
				double angle = k * Math.PI * 2.0 / SPLAT_SPOKES + 0.3;
				// a little irregular, like a real web
				double reach = SPLAT_RADIUS * (0.8 + 0.2 * Math.sin(k * 2.7));
				Vec3 spoke = across.scale(Math.cos(angle)).add(across2.scale(Math.sin(angle)));
				Vec3 tip = at.add(spoke.scale(reach)).subtract(dir.scale(0.08));
				inner[k] = at.add(spoke.scale(reach * 0.45)).subtract(dir.scale(0.04));
				outer[k] = at.add(spoke.scale(reach * 0.85)).subtract(dir.scale(0.07));
				segment(buffer, pose, at, tip, spoke, color, width * 0.7F);
			}
			for (int k = 0; k < SPLAT_SPOKES; k++) {
				int n = (k + 1) % SPLAT_SPOKES;
				segment(buffer, pose, inner[k], inner[n], inner[n].subtract(inner[k]).normalize(), color, width * 0.5F);
				segment(buffer, pose, outer[k], outer[n], outer[n].subtract(outer[k]).normalize(), color, width * 0.5F);
			}
		});
	}

	private static final int SPLAT_SPOKES = 8;
	private static final double SPLAT_RADIUS = 0.42;

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
			java.util.List<com.jaymospowers.power.web.WebHitches.Line> lines = entity.getAttached(com.jaymospowers.power.web.WebPower.HITCH);
			if (lines == null || entity.isRemoved() || entity.level() != level) {
				continue;
			}
			Vec3 from = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0);
			if (from.distanceToSqr(camera) > 96.0 * 96.0) {
				continue;
			}
			for (com.jaymospowers.power.web.WebHitches.Line line : lines) {
				if (line.dark()) {
					bundle(context, from.subtract(camera), line.anchor().subtract(camera), width);
				} else {
					draw(context.poseStack(), context, from.subtract(camera), line.anchor().subtract(camera), width, false);
				}
			}
		}
		// weblines: the same line, strung taut between their two ends
		java.util.List<com.jaymospowers.power.web.WebBridges.Span> spans = level.getAttached(com.jaymospowers.power.web.WebBridges.SPANS);
		if (spans != null) {
			for (com.jaymospowers.power.web.WebBridges.Span span : spans) {
				if (span.from().distanceToSqr(camera) < 128.0 * 128.0 || span.to().distanceToSqr(camera) < 128.0 * 128.0) {
					Vec3 from = span.from().subtract(camera), to = span.to().subtract(camera);
					switch (span.style()) {
						case com.jaymospowers.power.web.WebBridgeBlock.ROPE -> {
							// the suit's: a black corded rope, the black web splayed at both ends
							bundle(context, from, to, width);
							splat(context, from, from.subtract(to).normalize(), width * DARK_WIDTH, true);
						}
						case com.jaymospowers.power.web.WebBridgeBlock.TENDRIL -> tendrilSpan(context, minecraft, level, span.from(), span.to(), camera);
						default -> draw(context.poseStack(), context, from, to, width, false);
					}
				}
			}
		}
		for (Player player : level.players()) {
			// a symbiote's webs are black, with a faint sheen running along them
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
				// shooting out to where it sticks (the symbiote's ropes slower than a web)
				float out = WebGrowth.fraction(player.getId() * 4L + handIndex, end, hand.distanceTo(end),
					dark ? WebGrowth.ROPE_SPEED : WebGrowth.WEB_SPEED, false);
				end = hand.lerp(end, out);
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
						Vec3 at = held.getPosition(partialTick).add(0.0, held.getBbHeight() * 0.55, 0.0);
						tentacle(context, minecraft, player, handIndex, at, held.getId(), camera, partialTick);
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
	 * A Living Armor tendril holding something: one of the top two on the back (its own is hidden meanwhile), reaching
	 * up over the shoulder and down to what it holds in front, so the hands stay free. Drawn with the tendrils' own
	 * texture, thick at the root and tapering.
	 */
	private static void tentacle(LevelRenderContext context, Minecraft minecraft, Player player, int handIndex, Vec3 to, int heldId,
		Vec3 camera, float partialTick) {
		boolean mainRight = player.getMainArm() == HumanoidArm.RIGHT;
		boolean right = (handIndex == WebNet.MAIN) == mainRight;
		float bend = WebFeral.bend(player, partialTick);
		// the root: where the top tendril on that side grows (model pixels, as WebTendrilLayer roots it)
		Vec3 root = WebFeral.bodyPoint(player, partialTick, bend, right ? -3.0F : 3.0F, 1.5F, 2.0F);
		Vec3 over = WebFeral.bodyPoint(player, partialTick, bend, right ? -7.0F : 7.0F, -9.0F, 0.0F);
		if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			// from your own back only the arc over the shoulder shows: bring it in at the edge of the view
			float yaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
			Vec3 side = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw)).scale(right ? -0.55 : 0.55);
			over = player.getEyePosition(partialTick).add(0.0, 0.3, 0.0).add(side);
		}
		double radius = 0.1 * player.getScale();
		int pieces = 10;
		Vec3[] points = new Vec3[pieces + 1];
		for (int i = 0; i <= pieces; i++) {
			double t = i / (double)pieces;
			points[i] = root.lerp(over, t).lerp(over.lerp(to, t), t).subtract(camera);
		}
		int light = minecraft.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
		// it reaches out over time (WebTendrils.TENDRIL_SPEED, as the server takes hold)
		double path = 0.0;
		for (int i = 0; i < pieces; i++) {
			path += points[i].distanceTo(points[i + 1]);
		}
		float out = WebGrowth.fraction(((long)player.getId() << 24) ^ ((long)heldId << 2) ^ handIndex ^ 0x4000_0000_0000L, to, path,
			com.jaymospowers.power.web.WebTendrils.TENDRIL_SPEED * path / Math.max(0.5, root.distanceTo(to)), true);
		double reach = out * pieces;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(TENDRIL_TEXTURE), (pose, buffer) -> {
			for (int i = 0; i < pieces && i < reach; i++) {
				double r0 = radius * (1.0 - 0.45 * i / pieces);
				double r1 = radius * (1.0 - 0.45 * (i + 1) / pieces);
				Vec3 tip = i + 1 <= reach ? points[i + 1] : points[i].lerp(points[i + 1], reach - i);
				tube(buffer, pose, points[i], tip, r0, r1, i, light);
			}
		});
	}

	/** Living Armor's webline: a thick tendril, drawn taut from end to end in the tendrils' own texture. */
	private static final double SPAN_TENDRIL_RADIUS = 0.16;

	private static void tendrilSpan(LevelRenderContext context, Minecraft minecraft, ClientLevel level, Vec3 a, Vec3 b, Vec3 camera) {
		double length = a.distanceTo(b);
		int pieces = Math.max(2, (int)Math.ceil(length / 0.7));
		Vec3 from = a.subtract(camera), to = b.subtract(camera);
		int light = net.minecraft.util.LightCoordsUtil.getLightCoords(level, BlockPos.containing(a.lerp(b, 0.5)));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(TENDRIL_TEXTURE), (pose, buffer) -> {
			for (int i = 0; i < pieces; i++) {
				// a little thicker at each end, where it has gripped on
				double t0 = i / (double)pieces, t1 = (i + 1) / (double)pieces;
				double r0 = SPAN_TENDRIL_RADIUS * (1.0 + 0.35 * Math.abs(2.0 * t0 - 1.0));
				double r1 = SPAN_TENDRIL_RADIUS * (1.0 + 0.35 * Math.abs(2.0 * t1 - 1.0));
				tube(buffer, pose, from.lerp(to, t0), from.lerp(to, t1), r0, r1, i, light);
			}
		});
	}

	private static final net.minecraft.resources.Identifier TENDRIL_TEXTURE =
		com.jaymospowers.JaymosPowers.id("textures/entity/web_symbiote_tendril.png");

	/** One square, textured length of tendril from {@code a} to {@code b} (the segments alternate texture rows, like the model's). */
	private static void tube(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, double ra, double rb, int index, int light) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0E-6) {
			return;
		}
		dir = dir.normalize();
		Vec3 u = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 v = dir.cross(u).normalize();
		Vec3[] su = {u, v, u.reverse(), v.reverse()};
		float v0 = (index % 2) * 8 / 32.0F + 3.0F / 32.0F;
		float v1 = v0 + 4.5F / 32.0F;
		for (int k = 0; k < 4; k++) {
			Vec3 n0 = su[k];
			Vec3 n1 = su[(k + 1) % 4];
			Vec3 normal = n0.add(n1).normalize();
			Vec3 p0 = a.add(n0.scale(ra)), p1 = a.add(n1.scale(ra)), p2 = b.add(n1.scale(rb)), p3 = b.add(n0.scale(rb));
			float u0 = 3.0F / 32.0F, u1 = 6.0F / 32.0F;
			vertex(buffer, pose, p0, u0, v0, normal, light);
			vertex(buffer, pose, p1, u1, v0, normal, light);
			vertex(buffer, pose, p2, u1, v1, normal, light);
			vertex(buffer, pose, p3, u0, v1, normal, light);
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, Vec3 n, int light) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(-1).setUv(u, v)
			.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float)n.x, (float)n.y, (float)n.z);
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
		});
		// a little cobweb where it sticks
		splat(context, to, dir, width, dark);
	}

	private static void segment(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 normal, int color, float width) {
		float nx = (float)normal.x;
		float ny = (float)normal.y;
		float nz = (float)normal.z;
		buffer.addVertex(pose, (float)a.x, (float)a.y, (float)a.z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
		buffer.addVertex(pose, (float)b.x, (float)b.y, (float)b.z).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
	}
}

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
 * splat of strands where it sticks. Hooked into Fabric's level render "collect submits" phase. Since 0.12.3 a web line
 * is a braided white rope (see {@link #braid}): the full rope from a web shooter, a thinner, plainer one from the
 * power's own organic webs.
 */
final class WebLineRenderer {
	private static final int COLOR = 0xFFEDEDED;
	private static final int SPLAT_COLOR = 0xFFD8D8D8;
	private static final int SEGMENTS = 20;
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
		if (minecraft.level != null) {
			for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
				if (entity.getAttached(com.jaymospowers.power.web.WebPower.HITCH) != null) {
					HITCHED.add(entity);
				}
			}
		}
		// (spent lines go by which creatures are hitched, to leave a broken hitch's line behind)
		WebSpentLines.tick(minecraft, HITCHED);
		if (minecraft.level == null) {
			return;
		}
		if (!minecraft.isPaused()) {
			drip(minecraft);
			WebRopeSim.tick(minecraft.level);
		}
	}

	/** How much thicker a symbiote's line is than a web's, and how often goo drips off it (per line, per tick). */
	private static final float DARK_WIDTH = 2.6F;
	private static final float DRIP_CHANCE = 0.6F;
	private static final net.minecraft.core.particles.DustParticleOptions GOO = new net.minecraft.core.particles.DustParticleOptions(0x0B0910, 1.3F);

	/** Three thick black cords, from around the shoulder and arm, gathering on the same point. */
	private static void bundle(LevelRenderContext context, Vec3 from, Vec3 to, float width) {
		bundle(context, WebLineShape.straight(from, to), from, to, width);
	}

	private static void bundle(LevelRenderContext context, WebLineShape.Shape shape, Vec3 from, Vec3 to, float width) {
		rope(context, shape, from, to, ROPE_STRANDS, ROPE_STRAND_RADIUS, ROPE_TWIST_RADIUS);
		// where it holds on, the black web spreads
		splat(context, to, to.subtract(shape.at(0.95)).normalize(), width * DARK_WIDTH, true);
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
		rope(context, WebLineShape.straight(from, to), from, to, strands, radius, twist);
	}

	/** {@link #rope}, following {@code shape} (sagging, twanging) instead of straight. */
	static void rope(LevelRenderContext context, WebLineShape.Shape shape, Vec3 from, Vec3 to, int strands, double radius, double twist) {
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
					Vec3 center = shape.at(t).add(u.scale(Math.cos(angle) * twist)).add(v.scale(Math.sin(angle) * twist));
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
		splat(context, at, dir, width, dark, 1.0);
	}

	/** {@link #splat}, {@code size} times as big. */
	static void splat(LevelRenderContext context, Vec3 at, Vec3 dir, float width, boolean dark, double size) {
		Vec3 across = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 across2 = dir.cross(across).normalize();
		int color = dark ? DARK : SPLAT_COLOR;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), (pose, buffer) -> {
			Vec3[] inner = new Vec3[SPLAT_SPOKES];
			Vec3[] outer = new Vec3[SPLAT_SPOKES];
			for (int k = 0; k < SPLAT_SPOKES; k++) {
				double angle = k * Math.PI * 2.0 / SPLAT_SPOKES + 0.3;
				// a little irregular, like a real web
				double reach = SPLAT_RADIUS * size * (0.8 + 0.2 * Math.sin(k * 2.7));
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
		WebBehemothClient.render(context, level, camera, partialTick);
		// spent zip lines, falling and fading where they were left
		WebSpentLines.render(context, camera, partialTick, width);
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
			// both webs on one catch (a Web Whip): one web, held in both hands
			boolean twoHanded = WebArms.twoHanded(player);
			for (int handIndex = WebNet.MAIN; handIndex <= (twoHanded ? WebNet.MAIN : WebNet.OFF); handIndex++) {
				Vec3 end = WebArms.lineEnd(minecraft, level, player, handIndex, partialTick);
				if (end == null) {
					continue;
				}
				// from a web shooter: the full braided rope, out of the nozzle on the wrist
				boolean nozzle = !dark && com.jaymospowers.power.web.WebShooters.nozzleLine(player, handIndex);
				Vec3 hand = twoHanded ? handPos(minecraft, player, WebNet.MAIN, partialTick).lerp(handPos(minecraft, player, WebNet.OFF, partialTick), 0.5)
					: nozzle ? nozzlePos(minecraft, player, handIndex, partialTick) : handPos(minecraft, player, handIndex, partialTick);
				if (hand.distanceToSqr(end) > 96.0 * 96.0) {
					continue;
				}
				// shooting out to where it sticks (the symbiote's ropes slower than a web)
				long key = player.getId() * 4L + handIndex;
				double length = hand.distanceTo(end);
				double speed = dark ? WebGrowth.ROPE_SPEED : WebGrowth.WEB_SPEED;
				// (one stuck to something that moves, a whip's catch or a yank, keeps growing however fast that flies: it
				// isn't a fresh web each time its end jumps between frames)
				float out = WebGrowth.fraction(key, end, length, speed, WebSpentLines.onEntity(minecraft, player, handIndex));
				end = hand.lerp(end, out);
				// how it hangs: slack sags, a fresh or just-caught line twangs, a stretched one hums and thins
				double slack = slack(minecraft, player, handIndex, hand, end);
				double time = level.getGameTime() + partialTick;
				double twang = out < 1.0F ? 0.0 : WebLineShape.stuckTwang(WebGrowth.ticks(key) - length / speed);
				Vec3 from = hand.subtract(camera), to = end.subtract(camera);
				// once it's stuck, the line is a simulated rope: it hangs, drapes over edges and lies on the ground
				WebLineShape.Shape rope = out < 1.0F ? null
					: WebRopeSim.shape(key, hand, end, hand.distanceTo(end) + Math.max(0.0, slack), partialTick, camera);
				WebLineShape.Shape shape = WebLineShape.of(rope, from, to, 0.0, Math.max(0.0, -slack),
					1.0 - out, twang, time, player.getId() * 31 + handIndex);
				float lineWidth = width * WebLineShape.thin(-slack);
				// a symbiote's lines are thick, living cords: most of that side's tendrils at once
				if (dark) {
					bundle(context, shape, from, to, lineWidth);
					// and the whole arm streams into it: the suit's arm comes apart into tendrils from the shoulder down,
					// which run out along the line and braid into it
					if (!twoHanded && !(player == minecraft.player && minecraft.options.getCameraType().isFirstPerson())) {
						armSheath(context, minecraft, player, handIndex, shape, from, to, time, partialTick, camera);
					}
				} else {
					braid(context, level, camera, shape, from, to, width, WebLineShape.thin(-slack), nozzle, out >= 1.0F, time,
						player.getId() * 31 + handIndex);
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
			// tendrils latched onto a wall to climb (walking you up it, drawn on the body: only cords from your own eyes, where
			// the body isn't drawn), or into a block they're struggling to tear out
			if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson() && !FirstPersonCompat.bodyShown()) {
				latchCords(context, minecraft, player, player.getAttached(com.jaymospowers.power.web.WebTendrils.CLIMB), camera, partialTick);
			}
			latchCords(context, minecraft, player, player.getAttached(com.jaymospowers.power.web.WebTendrils.TUG), camera, partialTick);
			float spread = WebArms.wings(player, partialTick);
			if (spread > 0.02F && !(player == minecraft.player && minecraft.options.getCameraType().isFirstPerson())) {
				for (int side = -1; side <= 1; side += 2) {
					drawWing(context, player, side, WebArms.wingShoulder(player, side, partialTick).subtract(camera),
						WebArms.wingTip(player, side, spread, partialTick).subtract(camera), WebArms.wingHip(player, side, partialTick).subtract(camera),
						width * 0.8F, dark, partialTick);
				}
			}
		}
	}

	private static void latchCords(LevelRenderContext context, Minecraft minecraft, Player player,
		com.jaymospowers.power.web.WebTendrils.Latch latch, Vec3 camera, float partialTick) {
		if (latch == null) {
			return;
		}
		for (int handIndex = WebNet.MAIN; handIndex <= WebNet.OFF; handIndex++) {
			if (latch.has(handIndex)) {
				Vec3 at = latch.get(handIndex);
				// a new seed for every new grip point, so each one reaches out afresh
				tentacle(context, minecraft, player, handIndex, at, java.util.Objects.hash(at.x, at.y, at.z), camera, partialTick);
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

	/**
	 * Swinging in the suit, the arm itself becomes the line: {@code SHEATH_STRANDS} tendrils peel off the suit's arm from
	 * the shoulder down (wrapped round it, {@code SHEATH_ARM} blocks out from its middle, {@code SHEATH_BIG_ARM} on the big
	 * body), flare out past the hand ({@code SHEATH_FLARE}) and braid into the cord {@code SHEATH_MERGE} blocks along it,
	 * writhing as they go. Each is {@code SHEATH_RADIUS} thick at the shoulder, thinning toward the cord.
	 */
	private static final int SHEATH_STRANDS = 7, SHEATH_ARM_PIECES = 4, SHEATH_LINE_PIECES = 8;
	private static final double SHEATH_ARM = 0.11, SHEATH_BIG_ARM = 0.2, SHEATH_FLARE = 0.32, SHEATH_MERGE = 2.6;
	private static final double SHEATH_RADIUS = 0.05, SHEATH_WRITHE = 0.04;

	private static void armSheath(LevelRenderContext context, Minecraft minecraft, Player player, int handIndex, WebLineShape.Shape shape,
		Vec3 from, Vec3 to, double time, float partialTick, Vec3 camera) {
		double length = from.distanceTo(to);
		if (length < 0.3) {
			return;
		}
		boolean big = com.jaymospowers.power.web.WebSymbiote.bulked(player);
		double scale = player.getScale();
		Vec3 shoulder = WebArms.shoulder(player, handIndex, partialTick).subtract(camera);
		Vec3 hand = from;
		Vec3 armDir = hand.subtract(shoulder);
		if (armDir.lengthSqr() < 1.0E-4) {
			return;
		}
		armDir = armDir.normalize();
		Vec3 au = Math.abs(armDir.y) < 0.95 ? armDir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : armDir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 av = armDir.cross(au).normalize();
		Vec3 lineDir = shape.at(0.05).subtract(from);
		lineDir = lineDir.lengthSqr() < 1.0E-6 ? armDir : lineDir.normalize();
		Vec3 lu = Math.abs(lineDir.y) < 0.95 ? lineDir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : lineDir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 lv = lineDir.cross(lu).normalize();
		double armR = (big ? SHEATH_BIG_ARM : SHEATH_ARM) * scale;
		double merge = Math.min(SHEATH_MERGE, length * 0.8) / length;
		int pieces = SHEATH_ARM_PIECES + SHEATH_LINE_PIECES;
		int light = minecraft.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
		Vec3[][] strands = new Vec3[SHEATH_STRANDS][pieces + 1];
		for (int k = 0; k < SHEATH_STRANDS; k++) {
			double phase = k * Math.PI * 2.0 / SHEATH_STRANDS + player.getId();
			for (int i = 0; i <= pieces; i++) {
				double writhe = Math.sin(time * 0.45 + i * 0.9 + k * 1.7) * SHEATH_WRITHE * scale;
				Vec3 p;
				if (i <= SHEATH_ARM_PIECES) {
					// wrapped round the arm, coiling a little as it runs down it
					double f = i / (double)SHEATH_ARM_PIECES;
					double a = phase + f * 1.2;
					double r = armR + writhe;
					p = shoulder.lerp(hand, f).add(au.scale(Math.cos(a) * r)).add(av.scale(Math.sin(a) * r));
				} else {
					// out along the line: flaring wide past the hand, then braiding back into the cord
					double f = (i - SHEATH_ARM_PIECES) / (double)SHEATH_LINE_PIECES;
					double a = phase + 1.2 + f * 2.4;
					double flare = Math.sin(Math.min(1.0, f * 1.6) * Math.PI);
					double r = Mth.lerp(f, armR, ROPE_TWIST_RADIUS) + flare * SHEATH_FLARE * scale + writhe * 2.0;
					p = shape.at(f * merge).add(lu.scale(Math.cos(a) * r)).add(lv.scale(Math.sin(a) * r));
				}
				strands[k][i] = p;
			}
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(TENDRIL_TEXTURE), (pose, buffer) -> {
			for (Vec3[] strand : strands) {
				for (int i = 0; i < pieces; i++) {
					double r0 = SHEATH_RADIUS * scale * (1.0 - 0.6 * i / pieces) * (big ? 1.5 : 1.0);
					double r1 = SHEATH_RADIUS * scale * (1.0 - 0.6 * (i + 1) / pieces) * (big ? 1.5 : 1.0);
					tube(buffer, pose, strand[i], strand[i + 1], r0, r1, i, light);
				}
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

	static final net.minecraft.resources.Identifier TENDRIL_TEXTURE =
		com.jaymospowers.JaymosPowers.id("textures/entity/web_symbiote_tendril.png");

	/** One square, textured length of tendril from {@code a} to {@code b} (the segments alternate texture rows, like the model's). */
	static void tube(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, double ra, double rb, int index, int light) {
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

	static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, Vec3 n, int light) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(-1).setUv(u, v)
			.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float)n.x, (float)n.y, (float)n.z);
	}

	/**
	 * The wings are faint (a cobweb skin, mostly see-through): this alpha over the web's colour. A cobweb of
	 * {@code WING_SPOKES} spokes from the armpit to the wing's edge, tied by {@code WING_RINGS} rings of sagging thread.
	 */
	private static final int WING_ALPHA = 0x66, WING_EDGE_ALPHA = 0x99;
	private static final int WING_SPOKES = 7, WING_RINGS = 4, WING_STEPS = 6;
	/**
	 * It flows in the wind: the free edge (hand to hip) and the threads ripple along the wing normal, {@code FLUTTER}
	 * blocks plus {@code FLUTTER_PER_SPEED} per block/tick of speed, at {@code FLUTTER_SPEED} radians a tick. Braking, it
	 * billows out {@code BILLOW} blocks at its middle and inflates: the hand and the hip end reach {@code INFLATE} further.
	 */
	private static final double FLUTTER = 0.05, FLUTTER_PER_SPEED = 0.05, FLUTTER_SPEED = 0.55, BILLOW = 0.55, INFLATE = 0.35;
	/** How far a ring of thread sags back toward the armpit between two spokes (share of its length). */
	private static final double RING_SAG = 0.08;

	/**
	 * One web wing: a cobweb membrane between the arm (shoulder to hand) and the side (hand to hip), spokes from the
	 * armpit and rings of thread across them, rippling in the wind and billowing out when the wings flare to brake.
	 */
	private static void drawWing(LevelRenderContext context, Player player, int side, Vec3 shoulder, Vec3 tipIn, Vec3 hipIn, float width,
		boolean dark, float partialTick) {
		float brake = WebArms.brake(player, partialTick);
		// inflated: the free corners reach further out from the shoulder as the wing fills
		Vec3 hand = shoulder.add(tipIn.subtract(shoulder).scale(1.0 + INFLATE * brake));
		Vec3 hip = shoulder.add(hipIn.subtract(shoulder).scale(1.0 + INFLATE * 0.6 * brake));
		Vec3 tip = hand;
		Vec3 normal = tip.subtract(shoulder).cross(hip.subtract(shoulder));
		normal = normal.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : normal.normalize();
		if (normal.y < 0.0) {
			// the air catches it from below: it bulges up and back
			normal = normal.scale(-1.0);
		}
		double time = player.tickCount + partialTick;
		double speed = player.getDeltaMovement().length();
		double flutter = FLUTTER + FLUTTER_PER_SPEED * Math.min(speed, 2.5);
		double billow = BILLOW * brake;
		Vec3 armpit = shoulder.lerp(hip, 0.15);
		Vec3 n = normal;
		int color = dark ? (WING_ALPHA << 24) | (DARK & 0xFFFFFF) : (WING_ALPHA << 24) | (COLOR & 0xFFFFFF);
		int edgeColor = dark ? (WING_EDGE_ALPHA << 24) | (DARK & 0xFFFFFF) : (WING_EDGE_ALPHA << 24) | (COLOR & 0xFFFFFF);
		// a point of the membrane: s along its spoke (0 armpit, 1 edge), e along the edge (0 shoulder, 1 hand, 2 hip)
		java.util.function.BiFunction<Double, Double, Vec3> at = (s, e) -> {
			Vec3 edge = e <= 1.0 ? shoulder.lerp(tip, e) : tip.lerp(hip, e - 1.0);
			Vec3 p = armpit.lerp(edge, s);
			// the arm holds its edge still; the free edge (hand to hip) flies loosest
			double free = e <= 1.0 ? 0.25 * e : 0.25 * (2.0 - e) + Math.sin(Math.PI * (e - 1.0));
			double ripple = flutter * s * free * Math.sin(time * FLUTTER_SPEED + e * 2.6 + s * 1.7 + side);
			double bulge = billow * Math.sin(Math.PI * s * 0.85) * Math.sin(Math.PI * Math.min(1.0, e * 0.5 + 0.05));
			return p.add(n.scale(ripple + bulge));
		};
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lines(), (pose, buffer) -> {
			// the edges: the arm, then the free edge from hand to hip
			Vec3 prev = at.apply(1.0, 0.0);
			for (int k = 1; k <= WING_STEPS * 2; k++) {
				Vec3 next = at.apply(1.0, k / (double)WING_STEPS);
				coloured(buffer, pose, prev, next, width, edgeColor);
				prev = next;
			}
			// spokes, each a gentle curve with the membrane
			for (int k = 1; k <= WING_SPOKES; k++) {
				double e = 2.0 * k / (WING_SPOKES + 1);
				prev = at.apply(0.0, e);
				for (int step = 1; step <= WING_STEPS; step++) {
					Vec3 next = at.apply(step / (double)WING_STEPS, e);
					coloured(buffer, pose, prev, next, width * 0.6F, color);
					prev = next;
				}
			}
			// rings of thread from spoke to spoke, each sagging in toward the armpit between them
			for (int ring = 1; ring <= WING_RINGS; ring++) {
				double s = ring / (WING_RINGS + 0.6);
				for (int k = 0; k <= WING_SPOKES; k++) {
					double e0 = 2.0 * k / (WING_SPOKES + 1);
					double e1 = 2.0 * (k + 1) / (WING_SPOKES + 1);
					Vec3 a = at.apply(s, e0);
					Vec3 mid = at.apply(s * (1.0 - RING_SAG), (e0 + e1) * 0.5);
					Vec3 b = at.apply(s, e1);
					coloured(buffer, pose, a, mid, width * 0.5F, color);
					coloured(buffer, pose, mid, b, width * 0.5F, color);
				}
			}
		});
	}

	private static void coloured(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, float width, int color) {
		Vec3 d = b.subtract(a);
		double length = d.length();
		if (length < 1.0E-4) {
			return;
		}
		segment(buffer, pose, a, b, d.scale(1.0 / length), color, width);
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
	 * The slack (blocks) on {@code player}'s line from {@code handIndex}, negative when it's stretched: the local
	 * player's own swing webs know their rope; other players' swing webs go by the rope length their game sends (see
	 * {@link com.jaymospowers.power.web.WebPower#ROPE}); a Yank web holding something is
	 * {@link com.jaymospowers.power.web.WebPower#TETHER_LENGTH} long. Other lines are drawn taut.
	 */
	private static double slack(Minecraft minecraft, Player player, int handIndex, Vec3 hand, Vec3 end) {
		if (player == minecraft.player && WebPowerClient.isActive(minecraft.player)) {
			WebSwing swing = WebPowerClient.INSTANCE.swing;
			if (swing.strands[handIndex].attached) {
				return swing.slack(handIndex);
			}
		}
		com.jaymospowers.power.web.WebLine line = player.getAttached(com.jaymospowers.power.web.WebPower.line(handIndex));
		Float rope = player.getAttached(com.jaymospowers.power.web.WebPower.rope(handIndex));
		if (line != null && line.kind() == com.jaymospowers.power.web.WebLine.SWING && rope != null) {
			// measured as the swinger's game does, from about hand height
			return rope - end.distanceTo(player.getPosition(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)).add(0.0, 1.2, 0.0));
		}
		if (handIndex == WebNet.MAIN && line == null
			&& player.getAttached(com.jaymospowers.power.web.WebPower.TETHER) != null) {
			return com.jaymospowers.power.web.WebPower.TETHER_LENGTH - hand.distanceTo(end);
		}
		return 0.0;
	}

	/**
	 * Where the web leaves the hand: in first person, the screen corner the hand is in (as the fishing line does it);
	 * otherwise the posed hand at the end of the arm (see WebArms), or roughly where the hand would be.
	 */
	static Vec3 handPos(Minecraft minecraft, Player player, int handIndex, float partialTick) {
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
		Minecraft minecraft = Minecraft.getInstance();
		if (!dark && minecraft.level != null) {
			// a hitch's line or a webline: the power's own web, braided
			Vec3 camera = context.levelState().cameraRenderState.pos;
			Vec3 seed = to.add(camera);
			braid(context, minecraft.level, camera, WebLineShape.straight(from, to), from, to, width, 1.0F, false, true,
				minecraft.level.getGameTime(), java.util.Objects.hash((int)Math.floor(seed.x * 4.0), (int)Math.floor(seed.y * 4.0), (int)Math.floor(seed.z * 4.0)));
			return;
		}
		draw(poseStack, context, WebLineShape.straight(from, to), from, to, width, dark, true);
	}

	/** A line along {@code shape} (sagging, snaking, twanging); {@code stuck}: it has reached its end, so splat there. */
	/** Where a web shooter's line leaves: the nozzle on the posed wrist (in first person, the hand's screen corner as ever). */
	static Vec3 nozzlePos(Minecraft minecraft, Player player, int handIndex, float partialTick) {
		if (!(player == minecraft.player && minecraft.options.getCameraType().isFirstPerson())) {
			Vec3 wrist = WebArms.wristPos(player, handIndex, partialTick);
			if (wrist != null) {
				return wrist;
			}
		}
		return handPos(minecraft, player, handIndex, partialTick);
	}

	private static void draw(PoseStack poseStack, LevelRenderContext context, WebLineShape.Shape shape, Vec3 from, Vec3 to, float width,
		boolean dark, boolean stuck) {
		if (to.distanceToSqr(from) < 1.0E-6) {
			return;
		}
		Vec3[] points = new Vec3[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			points[i] = shape.at((double)i / SEGMENTS);
		}
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
			for (int i = 0; i < SEGMENTS; i++) {
				Vec3 d = points[i + 1].subtract(points[i]);
				if (d.lengthSqr() < 1.0E-8) {
					continue;
				}
				segment(buffer, pose, points[i], points[i + 1], d.normalize(), dark ? sheen((i + 0.5F) / SEGMENTS) : COLOR, width);
			}
		});
		if (stuck) {
			// a little cobweb where it sticks, facing the way the line comes in
			Vec3 in = points[SEGMENTS].subtract(points[SEGMENTS - 1]);
			splat(context, to, in.lengthSqr() < 1.0E-8 ? to.subtract(from).normalize() : in.normalize(), width, dark);
		}
	}

	// ===== the braided web line (0.12.3; the look Andy liked in the swinging video, our own geometry) ================
	static final net.minecraft.resources.Identifier ROPE_TEXTURE = com.jaymospowers.JaymosPowers.id("textures/entity/web_rope.png");
	/**
	 * A web shooter's line: {@code SHOOTER_STRANDS} strands, each a ribbon {@code SHOOTER_STRAND_WIDTH} blocks wide
	 * (solid down its middle, see-through at its edges: textures/entity/web_rope.png), twisted round each other
	 * {@code SHOOTER_TWIST} blocks out from the middle, with a fibre fraying off it every {@code SHOOTER_FIBRE_EVERY}
	 * blocks and a splat {@code SHOOTER_SPLAT} times the usual where it sticks.
	 */
	private static final int SHOOTER_STRANDS = 3;
	private static final double SHOOTER_STRAND_WIDTH = 0.05, SHOOTER_TWIST = 0.032, SHOOTER_FIBRE_EVERY = 0.5, SHOOTER_SPLAT = 1.3;
	/** The power's own organic web: the same, thinner and plainer. */
	private static final int ORGANIC_STRANDS = 2;
	private static final double ORGANIC_STRAND_WIDTH = 0.03, ORGANIC_TWIST = 0.017, ORGANIC_FIBRE_EVERY = 1.4, ORGANIC_SPLAT = 1.0;
	/**
	 * The strands make a full turn every {@code BRAID_TURN} blocks, drawn in pieces of {@code BRAID_STEP} blocks (at most
	 * {@code BRAID_MAX_PIECES} a line), and gather into one over the last {@code BRAID_GATHER} blocks at each end. The
	 * strand round the back is shaded down to {@code BRAID_BACK_SHADE}. Further than {@code BRAID_FAR} from the camera a
	 * line is drawn as the plain line it was (it would be a pixel or two wide anyway).
	 */
	private static final double BRAID_TURN = 0.9, BRAID_STEP = 0.25, BRAID_GATHER = 0.35, BRAID_FAR = 72.0;
	private static final int BRAID_MAX_PIECES = 160;
	private static final float BRAID_BACK_SHADE = 0.7F;
	/** Fibres: about this long (blocks), swaying this much with the time, this see-through. */
	private static final double FIBRE_LENGTH = 0.2, FIBRE_SWAY = 0.04;
	private static final int FIBRE_ALPHA = 0x8C;

	/**
	 * A web line as a braided rope along {@code shape} ({@code from} to {@code to}, camera-relative): {@code nozzle} the
	 * full rope of a web shooter, else the power's thinner organic one. {@code thin}: share of its width kept (a
	 * stretched line thins); {@code stuck}: it has reached its end, so it splats there.
	 */
	static void braid(LevelRenderContext context, ClientLevel level, Vec3 camera, WebLineShape.Shape shape, Vec3 from, Vec3 to, float width,
		float thin, boolean nozzle, boolean stuck, double time, int seed) {
		double length = from.distanceTo(to);
		if (length < 1.0E-3) {
			return;
		}
		if (from.lerp(to, 0.5).lengthSqr() > BRAID_FAR * BRAID_FAR) {
			draw(context.poseStack(), context, shape, from, to, width * (nozzle ? 1.4F : 1.0F) * thin, false, stuck);
			return;
		}
		int strands = nozzle ? SHOOTER_STRANDS : ORGANIC_STRANDS;
		double half = (nozzle ? SHOOTER_STRAND_WIDTH : ORGANIC_STRAND_WIDTH) * 0.5 * thin;
		double twist = (nozzle ? SHOOTER_TWIST : ORGANIC_TWIST) * thin;
		int pieces = Mth.clamp((int)Math.ceil(length / BRAID_STEP), 4, BRAID_MAX_PIECES);
		Vec3 dir = to.subtract(from).scale(1.0 / length);
		Vec3 u = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 v = dir.cross(u).normalize();
		Vec3[] middle = new Vec3[pieces + 1];
		int[] light = new int[pieces + 1];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i <= pieces; i++) {
			middle[i] = shape.at(i / (double)pieces);
			// the world's light along it (looked up every few pieces), so it isn't bright white in the dark
			if (i % 4 == 0 || i == pieces) {
				Vec3 at = middle[i].add(camera);
				light[i] = net.minecraft.util.LightCoordsUtil.getLightCoords(level, pos.set(at.x, at.y, at.z));
			} else {
				light[i] = light[i - i % 4];
			}
		}
		double phase0 = (seed & 0xFF) * 0.37;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(ROPE_TEXTURE), (pose, buffer) -> {
			for (int k = 0; k < strands; k++) {
				double phase = phase0 + k * Math.PI * 2.0 / strands;
				Vec3 prev = null, prevSide = null;
				int prevColor = 0;
				for (int i = 0; i <= pieces; i++) {
					double along = i / (double)pieces * length;
					// gathered tight at each end: into the nozzle (or the palm) and into the splat
					double gather = Math.min(1.0, Math.min(along, length - along) / BRAID_GATHER);
					double angle = phase + along / BRAID_TURN * Math.PI * 2.0;
					Vec3 offset = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
					Vec3 point = middle[i].add(offset.scale(twist * gather));
					Vec3 tangent = middle[Math.min(pieces, i + 1)].subtract(middle[Math.max(0, i - 1)]);
					// a ribbon turned to face the camera (at the origin here), as wide as the strand
					Vec3 side = tangent.cross(point);
					if (side.lengthSqr() < 1.0E-10) {
						side = u;
					}
					side = side.normalize().scale(half);
					// the strand round the back of the braid is in its shadow
					double facing = point.lengthSqr() < 1.0E-8 ? 0.0 : -offset.dot(point.normalize());
					float shade = (float)Mth.lerp(0.5 + 0.5 * facing * gather, BRAID_BACK_SHADE, 1.0);
					int c = (int)(shade * 255.0F);
					int color = 0xFF000000 | c << 16 | c << 8 | Math.min(255, c + 4);
					if (prev != null) {
						// the texture's fibres run a quarter of it per piece (it repeats every quarter)
						float v0 = (i - 1) % 4 / 4.0F, v1 = v0 + 0.25F;
						ribbon(buffer, pose, prev.subtract(prevSide), prev.add(prevSide), point.add(side), point.subtract(side), v0, v1, prevColor, color,
							light[i - 1], light[i]);
					}
					prev = point;
					prevSide = side;
					prevColor = color;
				}
			}
		});
		fibres(context, shape, length, u, v, twist, width, nozzle ? SHOOTER_FIBRE_EVERY : ORGANIC_FIBRE_EVERY, time, seed);
		if (stuck) {
			Vec3 in = to.subtract(shape.at(Math.max(0.0, 1.0 - 0.5 / length)));
			splat(context, to, in.lengthSqr() < 1.0E-8 ? dir : in.normalize(), width * (nozzle ? 1.3F : 1.0F), false,
				nozzle ? SHOOTER_SPLAT : ORGANIC_SPLAT);
		}
	}

	private static void ribbon(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a0, Vec3 a1, Vec3 b1, Vec3 b0, float v0, float v1, int colorA,
		int colorB, int lightA, int lightB) {
		ropeVertex(buffer, pose, a0, 0.0F, v0, colorA, lightA);
		ropeVertex(buffer, pose, a1, 1.0F, v0, colorA, lightA);
		ropeVertex(buffer, pose, b1, 1.0F, v1, colorB, lightB);
		ropeVertex(buffer, pose, b0, 0.0F, v1, colorB, lightB);
	}

	private static void ropeVertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, int color, int light) {
		// lit from straight above, so it's evenly lit whichever way it runs
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(color).setUv(u, v)
			.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	/**
	 * Fine fibres fraying off a braided line: one about every {@code every} blocks, each starting on the rope's surface
	 * and curling away from it (a little back toward the hand, as if combed by the shot), swaying gently.
	 */
	private static void fibres(LevelRenderContext context, WebLineShape.Shape shape, double length, Vec3 u, Vec3 v, double twist, float width,
		double every, double time, int seed) {
		int count = (int)(length / every);
		if (count <= 0) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.linesTranslucent(), (pose, buffer) -> {
			int color = FIBRE_ALPHA << 24 | 0xF2F4F8;
			int faint = (FIBRE_ALPHA / 2) << 24 | 0xF2F4F8;
			for (int j = 0; j < count; j++) {
				// the same fibres every frame: placed by a hash, not the random source
				int h = (j * 0x9E3779B1) ^ (seed * 0x85EBCA6B);
				h ^= h >>> 15;
				double r1 = (h & 0xFFFF) / 65535.0, r2 = (h >>> 16 & 0xFFFF) / 65535.0;
				double t = Mth.clamp((j + 0.2 + 0.6 * r1) / count, 0.04, 0.96);
				double angle = r2 * Math.PI * 2.0;
				Vec3 outward = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
				Vec3 base = shape.at(t).add(outward.scale(twist));
				Vec3 back = shape.at(Math.max(0.0, t - 0.02)).subtract(shape.at(t));
				back = back.lengthSqr() < 1.0E-10 ? Vec3.ZERO : back.normalize();
				double reach = FIBRE_LENGTH * (0.5 + 0.8 * r1);
				double sway = Math.sin(time * 0.35 + j * 1.7) * FIBRE_SWAY;
				Vec3 mid = base.add(outward.scale(reach * 0.5)).add(back.scale(reach * 0.25));
				Vec3 tip = base.add(outward.scale(reach * 0.85)).add(back.scale(reach * 0.6)).add(v.scale(sway));
				coloured(buffer, pose, base, mid, width * 0.55F, color);
				coloured(buffer, pose, mid, tip, width * 0.4F, faint);
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

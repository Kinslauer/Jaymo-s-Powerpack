package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebLine;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebPower;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Spent web lines. When a swing web, a zip or a whip lets go, its line doesn't vanish: it stays stuck where it hit,
 * goes slack and falls, draping down from the anchor and swaying, then comes loose, fades and breaks up into wisps.
 * Purely visual, on each client: every player's lines (from the synced lines, or the local player's own webs and pull)
 * leave one behind. A line stuck to something that moves (a yank, a whip) falls free at once.
 */
final class WebSpentLines {
	/** Points along a spent line (the first is the stuck end). */
	private static final int POINTS = 16;
	/** It hangs from its anchor this long (ticks), then comes loose; it's gone at {@code LIFE}, fading over the rest. */
	private static final int HANG_TICKS = 40;
	private static final int LIFE = 80;
	/** Pull down per tick, velocity kept per tick, and constraint passes (verlet rope). */
	private static final double GRAVITY = 0.03;
	private static final double DRAG = 0.95;
	private static final int PASSES = 4;
	/** Most spent lines kept at once (the oldest goes first). */
	private static final int MAX_LINES = 32;
	/** Chance per line per tick of a wisp breaking off while it fades. */
	private static final float WISP_CHANCE = 0.35F;

	private static final class Spent {
		final Vec3[] pos = new Vec3[POINTS];
		final Vec3[] prev = new Vec3[POINTS];
		final Vec3[] drawnPrev = new Vec3[POINTS];
		final double segment;
		final boolean dark;
		int age;

		Spent(Vec3 anchor, Vec3 hand, boolean dark) {
			for (int i = 0; i < POINTS; i++) {
				pos[i] = anchor.lerp(hand, i / (double)(POINTS - 1));
				prev[i] = pos[i];
				drawnPrev[i] = pos[i];
			}
			// a little slack, so it sags as it falls rather than staying a rod
			segment = anchor.distanceTo(hand) / (POINTS - 1) * 1.05;
			this.dark = dark;
		}
	}

	/** A hand's line as last seen: where it was stuck (on something that moves: {@code loose}), and where it left the hand. */
	private record Seen(Vec3 anchor, Vec3 hand, boolean dark, boolean loose) {
	}

	private static final List<Spent> LINES = new ArrayList<>();
	/** Keyed by player id × 2 + hand. */
	private static final Map<Integer, Seen> SEEN = new HashMap<>();
	/** A line whose stuck end jumps further than this in a tick was let go and another cast (blocks). */
	private static final double RECAST_JUMP = 1.5;

	private WebSpentLines() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			LINES.clear();
			SEEN.clear();
			return;
		}
		// lines that just let go (swing webs, zips, whips) leave themselves behind
		Map<Integer, Seen> now = new HashMap<>();
		for (Player player : level.players()) {
			boolean dark = com.jaymospowers.power.web.WebSymbiote.suitShown(player);
			for (int hand = WebNet.MAIN; hand <= WebNet.OFF; hand++) {
				Vec3 end = WebArms.lineEnd(minecraft, level, player, hand, 1.0F);
				if (end == null) {
					continue;
				}
				int key = player.getId() * 2 + hand;
				Seen seen = new Seen(end, WebLineRenderer.handPos(minecraft, player, hand, 1.0F), dark, onEntity(minecraft, player, hand));
				Seen before = SEEN.get(key);
				if (before != null && !before.loose() && !seen.loose() && before.anchor().distanceToSqr(end) > RECAST_JUMP * RECAST_JUMP) {
					spend(before);
				}
				now.put(key, seen);
			}
		}
		for (Map.Entry<Integer, Seen> entry : SEEN.entrySet()) {
			if (!now.containsKey(entry.getKey())) {
				spend(entry.getValue());
			}
		}
		SEEN.clear();
		SEEN.putAll(now);
		if (minecraft.isPaused()) {
			return;
		}
		for (Iterator<Spent> it = LINES.iterator(); it.hasNext(); ) {
			Spent line = it.next();
			if (++line.age >= LIFE) {
				it.remove();
				continue;
			}
			step(level, line);
		}
	}

	/** That hand's line is stuck to an entity (a yank, a whip), not to a fixed point. */
	private static boolean onEntity(Minecraft minecraft, Player player, int hand) {
		WebLine line = player.getAttached(WebPower.line(hand));
		if (line != null) {
			return line.entityId() >= 0;
		}
		// the local player's own swing webs and zip pull are fixed points; else the Yank tether
		return hand == WebNet.MAIN && player.getAttached(WebPower.TETHER) != null
			&& !(player == minecraft.player && WebPowerClient.INSTANCE.swing.lineEnd(hand, 1.0F) != null);
	}

	private static void spend(Seen seen) {
		Spent line = new Spent(seen.anchor(), seen.hand(), seen.dark());
		if (seen.loose()) {
			// it was stuck to something moving: it falls free at once
			line.age = HANG_TICKS;
		}
		add(line);
	}

	private static void add(Spent line) {
		if (LINES.size() >= MAX_LINES) {
			LINES.remove(0);
		}
		LINES.add(line);
	}

	private static void step(ClientLevel level, Spent line) {
		boolean loose = line.age > HANG_TICKS;
		for (int i = 0; i < POINTS; i++) {
			line.drawnPrev[i] = line.pos[i];
			if (i == 0 && !loose) {
				continue;
			}
			Vec3 v = line.pos[i].subtract(line.prev[i]).scale(DRAG);
			line.prev[i] = line.pos[i];
			Vec3 next = line.pos[i].add(v).add(0.0, -GRAVITY, 0.0);
			// it settles on whatever it falls onto
			if (!level.getBlockState(BlockPos.containing(next)).getCollisionShape(level, BlockPos.containing(next)).isEmpty()) {
				next = new Vec3(next.x, line.pos[i].y, next.z);
				line.prev[i] = new Vec3(line.prev[i].x, next.y, line.prev[i].z);
			}
			line.pos[i] = next;
		}
		for (int pass = 0; pass < PASSES; pass++) {
			for (int i = 0; i < POINTS - 1; i++) {
				Vec3 a = line.pos[i], b = line.pos[i + 1];
				Vec3 d = b.subtract(a);
				double length = d.length();
				if (length <= line.segment || length < 1.0E-6) {
					continue;
				}
				Vec3 fix = d.scale((length - line.segment) / length);
				if (i == 0 && !loose) {
					line.pos[i + 1] = b.subtract(fix);
				} else {
					line.pos[i] = a.add(fix.scale(0.5));
					line.pos[i + 1] = b.subtract(fix.scale(0.5));
				}
			}
		}
		// fading: wisps of web (or goo) break off it
		if (loose && level.getRandom().nextFloat() < WISP_CHANCE) {
			Vec3 at = line.pos[level.getRandom().nextInt(POINTS)];
			if (line.dark) {
				level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, at.x, at.y, at.z, 0.0, -0.02, 0.0);
			} else {
				level.addParticle(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 0.0, -0.02, 0.0);
			}
		}
	}

	static void render(LevelRenderContext context, Vec3 camera, float partialTick, float width) {
		if (LINES.isEmpty()) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.linesTranslucent(), (pose, buffer) -> {
			for (Spent line : LINES) {
				float age = line.age + partialTick;
				float fade = age <= HANG_TICKS ? 1.0F : Mth.clamp(1.0F - (age - HANG_TICKS) / (LIFE - HANG_TICKS), 0.0F, 1.0F);
				int alpha = (int)(fade * 255.0F);
				if (alpha < 4) {
					continue;
				}
				int rgb = line.dark ? 0x0B0710 : 0xEDEDED;
				int color = alpha << 24 | rgb;
				float w = width * (line.dark ? 2.0F : 1.0F) * (0.5F + 0.5F * fade);
				// breaking up as it fades: gaps open along it
				int keepEvery = fade > 0.5F ? 1 : fade > 0.25F ? 2 : 3;
				for (int i = 0; i < POINTS - 1; i++) {
					if (i % keepEvery != 0) {
						continue;
					}
					Vec3 a = line.drawnPrev[i].lerp(line.pos[i], partialTick).subtract(camera);
					Vec3 b = line.drawnPrev[i + 1].lerp(line.pos[i + 1], partialTick).subtract(camera);
					Vec3 d = b.subtract(a);
					double length = d.length();
					if (length < 1.0E-4) {
						continue;
					}
					Vec3 n = d.scale(1.0 / length);
					buffer.addVertex(pose, (float)a.x, (float)a.y, (float)a.z).setColor(color).setNormal(pose, (float)n.x, (float)n.y, (float)n.z).setLineWidth(w);
					buffer.addVertex(pose, (float)b.x, (float)b.y, (float)b.z).setColor(color).setNormal(pose, (float)n.x, (float)n.y, (float)n.z).setLineWidth(w);
				}
			}
		});
	}
}

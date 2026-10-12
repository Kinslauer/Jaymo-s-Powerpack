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
 * Spent Web Zip lines. When a zip lets go, its line doesn't vanish: it stays stuck where it hit, goes slack and falls,
 * draping down from the anchor and swaying, then comes loose, fades and breaks up into wisps. Purely visual, on each
 * client: every player's zips (from the synced line, or the local player's own pull) leave one behind.
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

	/** A player's zip line as last seen: where it was stuck and where it left the hand(s). */
	private record Seen(Vec3 anchor, Vec3 main, Vec3 off, boolean dark) {
	}

	private static final List<Spent> LINES = new ArrayList<>();
	private static final Map<Integer, Seen> SEEN = new HashMap<>();

	private WebSpentLines() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			LINES.clear();
			SEEN.clear();
			return;
		}
		// zips that just let go leave their lines behind
		Map<Integer, Seen> now = new HashMap<>();
		for (Player player : level.players()) {
			if (!zipLine(minecraft, player)) {
				continue;
			}
			Vec3 end = WebArms.lineEnd(minecraft, level, player, WebNet.MAIN, 1.0F);
			if (end == null) {
				continue;
			}
			boolean both = WebArms.zipping(minecraft, player);
			now.put(player.getId(), new Seen(end, WebLineRenderer.handPos(minecraft, player, WebNet.MAIN, 1.0F),
				both ? WebLineRenderer.handPos(minecraft, player, WebNet.OFF, 1.0F) : null, com.jaymospowers.power.web.WebSymbiote.suitShown(player)));
		}
		for (Map.Entry<Integer, Seen> entry : SEEN.entrySet()) {
			if (!now.containsKey(entry.getKey())) {
				Seen seen = entry.getValue();
				add(new Spent(seen.anchor(), seen.main(), seen.dark()));
				if (seen.off() != null) {
					add(new Spent(seen.anchor(), seen.off(), seen.dark()));
				}
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

	/** A Web Zip's line is out for this player right now. */
	private static boolean zipLine(Minecraft minecraft, Player player) {
		if (player == minecraft.player && WebPowerClient.isActive(minecraft.player) && WebPowerClient.INSTANCE.swing.pull != null) {
			return true;
		}
		WebLine line = player.getAttached(WebPower.LINE);
		return line != null && line.kind() == WebLine.ZIP;
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

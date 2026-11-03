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
 * Spent web lines. A line that breaks (cut, burnt through, pulled apart, worn out, or what it was stuck to gone)
 * doesn't vanish: what's left goes slack and falls, draping down from where it was stuck and swaying, then comes loose,
 * fades and breaks up into wisps. A line that is only let go leaves nothing (0.19.70: it used to drop one every time).
 * A line cut or snapped partway parts there: two broken halves, one hanging from where it was stuck and one falling
 * from the slinger's (or the hitched creature's) end, each whipping back from the break. Purely visual, on each
 * client: the server says when and where a line broke ({@link WebNet.LineSnap}) and the line as last seen here is
 * what's left behind. A line stuck to something that moves (a yank, a whip) falls free at once.
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

	/** A line as last seen: where it was stuck (on something that moves: {@code loose}), and where it left the hand. */
	private record Seen(Vec3 anchor, Vec3 hand, boolean dark, boolean loose) {
	}

	/** Line identity: a slinger's hand ({@link WebNet#MAIN}/{@link WebNet#OFF}) or a hitch (with where it's tied). */
	private record Key(int entity, int line, Vec3 tie) {
		boolean matches(WebNet.LineSnap snap) {
			return snap.entity() == entity && snap.line() == line && (line != WebNet.HITCH_LINE || snap.anchor().distanceToSqr(tie) < 0.01);
		}
	}

	/** A line gone from sight, waiting (up to {@code WAIT_TICKS}) for word that it broke; none comes, it was let go. */
	private record Gone(Key key, Seen seen, int ticks) {
	}

	/** Word from the server that a line broke, kept {@code WAIT_TICKS} for its line to go from sight. */
	private record Note(WebNet.LineSnap snap, int ticks) {
	}

	private static final int WAIT_TICKS = 10;
	private static final List<Gone> GONE = new ArrayList<>();
	private static final List<Note> NOTES = new ArrayList<>();
	/** How fast a snapped end whips back toward where it's held (blocks per tick, at the break). */
	private static final double RECOIL = 0.22;
	/** Every hitched creature's lines as last seen (creature id → where each is tied, and where the creature was). */
	private static final Map<Integer, List<Seen>> HITCHES = new HashMap<>();

	private static final List<Spent> LINES = new ArrayList<>();
	/** Keyed by player id × 2 + hand. */
	private static final Map<Integer, Seen> SEEN = new HashMap<>();
	/** A line whose stuck end jumps further than this in a tick was let go and another cast (blocks). */
	private static final double RECAST_JUMP = 1.5;

	private WebSpentLines() {
	}

	static void tick(Minecraft minecraft, List<net.minecraft.world.entity.Entity> hitched) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			LINES.clear();
			SEEN.clear();
			GONE.clear();
			NOTES.clear();
			HITCHES.clear();
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
					gone(new Key(player.getId(), hand, Vec3.ZERO), before);
				}
				now.put(key, seen);
			}
		}
		for (Map.Entry<Integer, Seen> entry : SEEN.entrySet()) {
			if (!now.containsKey(entry.getKey())) {
				gone(new Key(entry.getKey() >> 1, entry.getKey() & 1, Vec3.ZERO), entry.getValue());
			}
		}
		SEEN.clear();
		SEEN.putAll(now);
		hitches(level, hitched);
		// lines gone from sight: spent if the server says they broke, else they were only let go
		for (int i = GONE.size() - 1; i >= 0; i--) {
			Gone gone = GONE.get(i);
			Note note = take(gone.key());
			if (note != null) {
				GONE.remove(i);
				spend(gone.seen(), note.snap());
			} else if (gone.ticks() + 1 >= WAIT_TICKS) {
				GONE.remove(i);
			} else {
				GONE.set(i, new Gone(gone.key(), gone.seen(), gone.ticks() + 1));
			}
		}
		for (int i = NOTES.size() - 1; i >= 0; i--) {
			Note note = NOTES.get(i);
			if (note.ticks() + 1 >= WAIT_TICKS) {
				NOTES.remove(i);
			} else {
				NOTES.set(i, new Note(note.snap(), note.ticks() + 1));
			}
		}
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

	/** The server says a line broke: it's spent once it goes from sight here (it may have already). */
	static void snapped(WebNet.LineSnap snap) {
		for (int i = 0; i < GONE.size(); i++) {
			if (GONE.get(i).key().matches(snap)) {
				spend(GONE.remove(i).seen(), snap);
				return;
			}
		}
		NOTES.add(new Note(snap, 0));
	}

	private static void gone(Key key, Seen seen) {
		Note note = take(key);
		if (note != null) {
			spend(seen, note.snap());
		} else {
			GONE.add(new Gone(key, seen, 0));
		}
	}

	private static @org.jspecify.annotations.Nullable Note take(Key key) {
		for (int i = 0; i < NOTES.size(); i++) {
			if (key.matches(NOTES.get(i).snap())) {
				return NOTES.remove(i);
			}
		}
		return null;
	}

	/**
	 * Hitch lines gone since last tick: one that broke (snapped, cut, worn out) is left behind, from where it was tied
	 * to where the creature was, and falls and fades like any other.
	 */
	private static void hitches(ClientLevel level, List<net.minecraft.world.entity.Entity> hitched) {
		Map<Integer, List<Seen>> now = new HashMap<>();
		for (net.minecraft.world.entity.Entity entity : hitched) {
			List<com.jaymospowers.power.web.WebHitches.Line> lines = entity.getAttached(WebPower.HITCH);
			if (lines == null || entity.isRemoved()) {
				continue;
			}
			Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
			List<Seen> seen = new ArrayList<>();
			for (com.jaymospowers.power.web.WebHitches.Line line : lines) {
				seen.add(new Seen(line.anchor(), center, line.dark(), false));
			}
			now.put(entity.getId(), seen);
		}
		for (Map.Entry<Integer, List<Seen>> entry : HITCHES.entrySet()) {
			List<Seen> still = now.getOrDefault(entry.getKey(), List.of());
			for (Seen before : entry.getValue()) {
				if (still.stream().noneMatch(s -> s.anchor().distanceToSqr(before.anchor()) < 1.0E-4)) {
					gone(new Key(entry.getKey(), WebNet.HITCH_LINE, before.anchor()), before);
				}
			}
		}
		HITCHES.clear();
		HITCHES.putAll(now);
	}

	/** That hand's line is stuck to an entity (a yank, a whip), not to a fixed point. */
	static boolean onEntity(Minecraft minecraft, Player player, int hand) {
		WebLine line = player.getAttached(WebPower.line(hand));
		if (line != null) {
			return line.entityId() >= 0;
		}
		// the local player's own swing webs and zip pull are fixed points; else the Yank tether
		return hand == WebNet.MAIN && player.getAttached(WebPower.TETHER) != null
			&& !(player == minecraft.player && WebPowerClient.INSTANCE.swing.lineEnd(hand, 1.0F) != null);
	}

	private static void spend(Seen seen, WebNet.LineSnap snap) {
		if (seen.dark()) {
			// (0.17.149) a symbiote's cord retracts into the hand (WebEngulf): it never falls
			return;
		}
		Vec3 anchor = seen.anchor(), hand = seen.hand();
		Vec3 span = hand.subtract(anchor);
		double length = span.length();
		double t = length < 1.0E-4 ? 0.0 : Mth.clamp(snap.at().subtract(anchor).dot(span) / (length * length), 0.0, 1.0);
		if (snap.how() != WebNet.SNAPPED || length * Math.min(t, 1.0 - t) < 0.3) {
			// worn out, or what it was stuck to gone: one whole line (FREED: it falls at once)
			Spent line = new Spent(anchor, hand, seen.dark());
			if (seen.loose() || snap.how() == WebNet.FREED) {
				line.age = HANG_TICKS;
			}
			add(line);
			return;
		}
		// it parted at the break: the half still stuck hangs there, the slinger's half falls free; both whip back
		Vec3 at = anchor.add(span.scale(t));
		Spent stuck = new Spent(anchor, at, seen.dark());
		if (seen.loose()) {
			stuck.age = HANG_TICKS;
		}
		recoil(stuck);
		add(stuck);
		Spent free = new Spent(hand, at, seen.dark());
		free.age = HANG_TICKS;
		recoil(free);
		add(free);
	}

	/** A snapped half's free end (the last point) springs back toward its held end, less and less along it. */
	private static void recoil(Spent line) {
		Vec3 back = line.pos[0].subtract(line.pos[POINTS - 1]);
		double length = back.length();
		if (length < 1.0E-4) {
			return;
		}
		Vec3 dir = back.scale(1.0 / length);
		for (int i = 1; i < POINTS; i++) {
			double share = i / (double)(POINTS - 1);
			line.prev[i] = line.pos[i].subtract(dir.scale(RECOIL * share * share));
		}
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
				// (0.16.7) a symbiote's spent line is the same blackish grey as its live ones (WebLineRenderer.DARK), not violet
				int rgb = line.dark ? 0x121216 : 0xEDEDED;
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

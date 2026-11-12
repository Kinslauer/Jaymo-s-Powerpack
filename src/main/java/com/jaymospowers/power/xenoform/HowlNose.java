package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Bleeding;
import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Howl's nose: Howl picks one scent (the creature it looks at) and follows it. While it holds it, the prey's trail hangs
 * behind it as a thread of drifting motes, only Howl's own eyes seeing it, brighter the fresher it is; the bleeding in
 * range show as red shapes through walls on Howl's screen (the synced {@link #VIEW}, drawn by the client's
 * {@code HowlNoseClient}); and Howl hits the tracked prey for {@link #PREY_BONUS} more.
 */
public final class HowlNose {
	// ---- tuning ----
	/** How far a scent can be picked up (blocks), how far the thread and the bleeders show, how many bleeders at most. */
	public static final double PICK_REACH = 64.0, THREAD_RANGE = 64.0, BLEED_RANGE = 48.0;
	public static final int BLEEDERS_MAX = 24;
	/** A point of the trail is laid every this many ticks, if the prey moved at least {@link #STEP_MIN} blocks; this many are kept (about half a minute). */
	public static final int TRAIL_STEP = 5, TRAIL_KEEP = 120;
	public static final double STEP_MIN = 0.4;
	/** The thread is drawn this often (ticks); the prey out of sight this long is lost. */
	public static final int DRAW_EVERY = 6, LOST_TICKS = 200;
	/** The thread's colour, fresh to stale (RGB), and its motes' size, fresh to stale. */
	public static final int FRESH_COLOR = 0xFFC060, STALE_COLOR = 0x54402C;
	public static final float FRESH_SIZE = 1.1F, STALE_SIZE = 0.5F;
	/** Extra damage on every blow Howl lands on its tracked prey. */
	public static final float PREY_BONUS = 2.0F;

	/** What Howl's screen draws: the tracked prey (entity id, -1 none) and the bleeding near it. */
	public record Nose(int prey, List<Integer> bleeders) {
		public static final StreamCodec<ByteBuf, Nose> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Nose::prey,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Nose::bleeders,
			Nose::new);
	}

	/** Synced to its owner only. Absent: the nose is off. */
	public static final AttachmentType<Nose> VIEW = AttachmentRegistry.create(JaymosPowers.id("howl_nose"),
		builder -> builder.syncWith(Nose.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** One Howl's scent: the prey, when it was last seen, and its trail (x, y, z, game time). */
	private static final class Track {
		int prey;
		long lastSeen;
		final ArrayDeque<double[]> trail = new ArrayDeque<>();
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();

	private HowlNose() {
	}

	public static boolean tracking(ServerPlayer player) {
		return TRACKS.containsKey(player.getUUID());
	}

	/** Whether {@code target} is the prey {@code player} follows. */
	public static boolean isPrey(ServerPlayer player, Entity target) {
		Track t = TRACKS.get(player.getUUID());
		return t != null && t.prey == target.getId();
	}

	/** Picks {@code prey}'s scent (dropping any other). */
	public static void pick(ServerPlayer player, LivingEntity prey) {
		Track t = new Track();
		t.prey = prey.getId();
		t.lastSeen = player.level().getGameTime();
		t.trail.add(new double[] {prey.getX(), prey.getY() + 0.2, prey.getZ(), t.lastSeen});
		TRACKS.put(player.getUUID(), t);
	}

	/** Drops the scent. */
	public static void clear(ServerPlayer player) {
		TRACKS.remove(player.getUUID());
		if (player.hasAttached(VIEW)) {
			player.removeAttached(VIEW);
		}
	}

	/** Every tick as Howl: lays the prey's trail, draws the thread, and keeps the bleeders on Howl's screen. {@code sniffing}: V's scent is on. */
	public static void tick(ServerPlayer player, boolean sniffing) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Track t = TRACKS.get(player.getUUID());
		LivingEntity prey = null;
		if (t != null) {
			Entity e = level.getEntity(t.prey);
			if (e instanceof LivingEntity living && living.isAlive()) {
				prey = living;
				t.lastSeen = now;
			} else if (e != null || now - t.lastSeen > LOST_TICKS) {
				// dead, or out of reach for too long: the scent is lost
				TRACKS.remove(player.getUUID());
				t = null;
			}
		}
		if (t != null && prey != null && now % TRAIL_STEP == 0) {
			double[] last = t.trail.peekLast();
			if (last == null || prey.distanceToSqr(last[0], last[1], last[2]) >= STEP_MIN * STEP_MIN) {
				t.trail.addLast(new double[] {prey.getX(), prey.getY() + 0.2, prey.getZ(), now});
				while (t.trail.size() > TRAIL_KEEP) {
					t.trail.removeFirst();
				}
			}
		}
		if (t != null && now % DRAW_EVERY == 0) {
			thread(player, level, t, now);
		}
		if ((t != null || sniffing) && now % 10 == 0) {
			show(player, level, t == null ? -1 : t.prey);
		} else if (t == null && !sniffing && player.hasAttached(VIEW)) {
			player.removeAttached(VIEW);
		}
	}

	/** The trail as a thread of motes drifting up, only Howl sees it: the newer the point, the brighter. */
	private static void thread(ServerPlayer player, ServerLevel level, Track t, long now) {
		double span = (double)TRAIL_KEEP * TRAIL_STEP;
		for (double[] p : t.trail) {
			if (player.distanceToSqr(p[0], p[1], p[2]) > THREAD_RANGE * THREAD_RANGE) {
				continue;
			}
			float fresh = Mth.clamp(1.0F - (float)((now - (long)p[3]) / span), 0.0F, 1.0F);
			int color = lerp(STALE_COLOR, FRESH_COLOR, fresh);
			level.sendParticles(player, MagicFx.mote(color, Mth.lerp(fresh, STALE_SIZE, FRESH_SIZE)), true, false, p[0], p[1], p[2], 1, 0.08, 0.05, 0.08, 0.01);
		}
	}

	private static int lerp(int from, int to, float t) {
		int r = Mth.lerpInt(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
		int g = Mth.lerpInt(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
		int b = Mth.lerpInt(t, from & 0xFF, to & 0xFF);
		return r << 16 | g << 8 | b;
	}

	/** Tells Howl's screen who the prey is and who bleeds near (Wounds keeps others' bleeding off the client). */
	private static void show(ServerPlayer player, ServerLevel level, int prey) {
		List<Integer> bleeders = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BLEED_RANGE),
			e -> e != player && e.isAlive() && !(e instanceof ArmorStand) && Bleeding.bleeding(e) != 0 && e.distanceToSqr(player) <= BLEED_RANGE * BLEED_RANGE)) {
			bleeders.add(e.getId());
			if (bleeders.size() >= BLEEDERS_MAX) {
				break;
			}
		}
		Nose now = new Nose(prey, List.copyOf(bleeders));
		if (!now.equals(player.getAttached(VIEW))) {
			player.setAttached(VIEW, now);
		}
	}
}

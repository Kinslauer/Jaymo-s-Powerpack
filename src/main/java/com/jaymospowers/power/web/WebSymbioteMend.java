package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Scars;
import com.jaymospowers.power.blindness.Blind;
import com.jaymospowers.power.resonance.ResonancePower;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Andy: "when you lose a limb or anything for that matter, eyes parasites, with the symbiote attached it should repair it
 * for you, cording it into existince with the black goo, then it retakes form as a fully black pulsing limb, eventually
 * returning to the mca skin."
 * <p>
 * Whoever wears a symbiote (a web slinger or anyone it has taken), once it's awake in them (not dormant), gets back what
 * they've lost, one thing at a time with {@link #GAP_TICKS} between:
 * <ul>
 * <li><b>A lost arm or leg</b> ({@link Dismember}): black cords spill from the stump and braid outward into the shape of
 * the limb over {@link #LIMB_TICKS}; then it's there ({@link Dismember#regrowQuietly}), a wooden or iron one comes off as
 * for any regrowth.</li>
 * <li><b>A lost eye</b> (or both: {@link Blind}): fine cords knit into the socket over {@link #EYE_TICKS}. Not for a
 * Resonance holder, whose dark is the devotion their power stands on.</li>
 * <li><b>A parasite</b> (Jaymo's Kitchen's, from foul water): cords writhe over the belly over {@link #PARASITE_TICKS},
 * and it's eaten out of you.</li>
 * </ul>
 * A regrown limb (or eye) stays solid black and pulsing for {@link #BLACK_TICKS} ({@link #MENDED}, synced, drawn by the
 * client's {@code WebSymbioteMendLayer}), then fades back to the skin over {@link #FADE_TICKS}. It speaks as it starts
 * (its line drives the screen, as everything it does on its own does).
 */
public final class WebSymbioteMend {
	/** How long the cords take: a limb, an eye, a parasite (ticks). */
	public static final int LIMB_TICKS = 200, EYE_TICKS = 140, PARASITE_TICKS = 160;
	/** It rests this long between one repair and the next (ticks). */
	public static final int GAP_TICKS = 60;
	/** A regrown part stays black this long, then fades back to the skin over {@code FADE_TICKS} (ticks). */
	public static final int BLACK_TICKS = 2400, FADE_TICKS = 600;
	/** Cords drawn every this many ticks while it works. */
	static final int CORD_EVERY = 2;
	/** Lines it has as it starts each kind of repair, and once one is done. */
	static final int LIMB_LINES = 3, EYE_LINES = 2, PARASITE_LINES = 2, DONE_LINES = 3;
	/** {@link Piece#part} for the eyes ({@link Dismember}'s parts are the limbs and the head). */
	public static final int EYES = 32;
	/** As much of a parasite as there could be (ticks): all of it. */
	static final int WHOLE_PARASITE = Integer.MAX_VALUE / 2;

	/** One regrown part: which ({@link Dismember#RIGHT_ARM} ..., or {@link #EYES}), and when it was whole again (game time). */
	public record Piece(int part, long since) {
		public static final Codec<Piece> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("part").forGetter(Piece::part),
				Codec.LONG.fieldOf("since").forGetter(Piece::since))
			.apply(i, Piece::new));
		public static final StreamCodec<ByteBuf, Piece> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Piece::part, ByteBufCodecs.VAR_LONG, Piece::since, Piece::new);
	}

	/** The parts still black (saved, synced to everyone: they all see it). */
	public static final AttachmentType<List<Piece>> MENDED = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_mended"),
		builder -> builder.persistent(Piece.CODEC.listOf())
			.syncWith(Piece.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));

	/** What it's at now: the part ({@link #EYES}, or {@code PARASITE}), and when it began. Not saved: a reload starts it over. */
	private record Job(int part, long start) {
	}

	private static final int PARASITE = 64;
	private static final Map<UUID, Job> JOBS = new HashMap<>();
	/** When it may start the next one. */
	private static final Map<UUID, Long> REST = new HashMap<>();

	private WebSymbioteMend() {
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			JOBS.remove(handler.player.getUUID());
			REST.remove(handler.player.getUUID());
		});
	}

	/** Every tick of a host's symbiote ({@link WebSymbiote#tick}). */
	static void tick(ServerPlayer player, long now) {
		UUID id = player.getUUID();
		expire(player, now);
		if (!WebSymbiote.isBonded(player) || !player.isAlive() || player.isSpectator() || WebSymbioteBond.dormant(player)) {
			JOBS.remove(id);
			return;
		}
		Job job = JOBS.get(id);
		if (job == null) {
			if (now < REST.getOrDefault(id, 0L)) {
				return;
			}
			int part = next(player);
			if (part == 0) {
				return;
			}
			job = new Job(part, now);
			JOBS.put(id, job);
			begin(player, part);
		}
		if (!stillNeeded(player, job.part())) {
			// (mended some other way meanwhile)
			JOBS.remove(id);
			return;
		}
		int ticks = ticks(job.part());
		float progress = Mth.clamp((now - job.start()) / (float)ticks, 0.0F, 1.0F);
		if ((now - job.start()) % CORD_EVERY == 0) {
			cords(player, job.part(), progress, now);
		}
		if (now - job.start() >= ticks) {
			finish(player, job.part(), now);
			JOBS.remove(id);
			REST.put(id, now + GAP_TICKS);
		}
	}

	/** What it mends next: a limb first (arms, then legs), then the eyes, then a parasite; 0 if nothing. */
	private static int next(ServerPlayer player) {
		int missing = Dismember.missing(player);
		for (int part : Dismember.PARTS) {
			if (part != Dismember.HEAD && (missing & part) != 0) {
				return part;
			}
		}
		if (eyesToMend(player)) {
			return EYES;
		}
		if (KitchenCompat.purgeParasite(player, 0) > 0) {
			return PARASITE;
		}
		return 0;
	}

	private static boolean eyesToMend(ServerPlayer player) {
		return Blind.eyesLost(player) != 0 && !Powers.has(player, ResonancePower.INSTANCE);
	}

	private static boolean stillNeeded(ServerPlayer player, int part) {
		if (part == EYES) {
			return eyesToMend(player);
		}
		if (part == PARASITE) {
			return KitchenCompat.purgeParasite(player, 0) > 0;
		}
		return (Dismember.missing(player) & part) != 0;
	}

	private static int ticks(int part) {
		return part == EYES ? EYE_TICKS : part == PARASITE ? PARASITE_TICKS : LIMB_TICKS;
	}

	private static void begin(ServerPlayer player, int part) {
		String kind = part == EYES ? "eye" : part == PARASITE ? "parasite" : "limb";
		int lines = part == EYES ? EYE_LINES : part == PARASITE ? PARASITE_LINES : LIMB_LINES;
		WebSymbiote.whisper(player, "mend." + kind + "." + (1 + player.getRandom().nextInt(lines)));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	private static void finish(ServerPlayer player, int part, long now) {
		ServerLevel level = player.level();
		Vec3 at;
		if (part == PARASITE) {
			KitchenCompat.purgeParasite(player, WHOLE_PARASITE);
			at = belly(player);
		} else if (part == EYES) {
			Scars.remove(player, Scars.Kind.LEFT_EYE);
			Scars.remove(player, Scars.Kind.RIGHT_EYE);
			Scars.remove(player, Scars.Kind.CARVED_EYES);
			mark(player, EYES, now);
			at = player.getEyePosition();
		} else {
			Dismember.regrowQuietly(player, part);
			mark(player, part, now);
			Vec3[] limb = limb(player, part);
			at = limb[0].add(limb[1]).scale(0.5);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 16, 0.15, 0.2, 0.15, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.9F, 0.6F);
		WebSymbiote.whisper(player, "mend.done." + (1 + player.getRandom().nextInt(DONE_LINES)));
	}

	/** {@code part} is black from {@code now}. */
	private static void mark(ServerPlayer player, int part, long now) {
		List<Piece> pieces = new ArrayList<>(mended(player));
		pieces.removeIf(p -> p.part() == part);
		pieces.add(new Piece(part, now));
		player.setAttached(MENDED, List.copyOf(pieces));
	}

	/** Parts whose black has faded right out come off the list. */
	private static void expire(ServerPlayer player, long now) {
		List<Piece> pieces = player.getAttached(MENDED);
		if (pieces == null || player.tickCount % 20 != 0) {
			return;
		}
		List<Piece> left = pieces.stream().filter(p -> now - p.since() < BLACK_TICKS + FADE_TICKS && now >= p.since()).toList();
		if (left.size() == pieces.size()) {
			return;
		}
		if (left.isEmpty()) {
			player.removeAttached(MENDED);
		} else {
			player.setAttached(MENDED, left);
		}
	}

	/** The parts still black on {@code player} (either side). */
	public static List<Piece> mended(net.minecraft.world.entity.player.Player player) {
		List<Piece> pieces = player.getAttached(MENDED);
		return pieces == null ? List.of() : pieces;
	}

	/**
	 * How black {@code piece} is at {@code now}: 1 through its black time, down to 0 over the fade (for the client's
	 * layer).
	 */
	public static float black(Piece piece, long now) {
		long age = now - piece.since();
		if (age < 0 || age >= BLACK_TICKS + FADE_TICKS) {
			return 0.0F;
		}
		return age < BLACK_TICKS ? 1.0F : 1.0F - (age - BLACK_TICKS) / (float)FADE_TICKS;
	}

	// ---- the cords --------------------------------------------------------------------------------------------------

	/** Black cords braiding out from the stump (or into a socket, or over the belly) as far as it's got. */
	private static void cords(ServerPlayer player, int part, float progress, long now) {
		ServerLevel level = player.level();
		var dust = WebSymbioteStrains.dust(player, 0.55F);
		if (part == EYES) {
			Vec3 eye = player.getEyePosition();
			Vec3 forward = forward(player);
			Vec3 right = right(player);
			for (int side = -1; side <= 1; side += 2) {
				Vec3 socket = eye.add(forward.scale(0.27)).add(right.scale(side * 0.09));
				// fine threads drawn in from round the face to the socket
				for (int k = 0; k < 3; k++) {
					double a = (now * 0.35 + k * 2.1 + side) % (Math.PI * 2.0);
					double r = 0.22 * (1.0 - progress) + 0.03;
					Vec3 at = socket.add(right.scale(Math.cos(a) * r)).add(0.0, Math.sin(a) * r, 0.0);
					level.sendParticles(dust, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
			return;
		}
		if (part == PARASITE) {
			Vec3 belly = belly(player);
			Vec3 right = right(player);
			for (int k = 0; k < 4; k++) {
				double a = now * 0.4 + k * Math.PI * 0.5;
				Vec3 at = belly.add(right.scale(Math.cos(a) * 0.3)).add(forward(player).scale(Math.sin(a) * 0.18 + 0.05))
					.add(0.0, Math.sin(a * 1.7) * 0.15, 0.0);
				level.sendParticles(dust, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			if (player.getRandom().nextInt(10) == 0) {
				level.playSound(null, belly.x, belly.y, belly.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 0.6F);
			}
			return;
		}
		Vec3[] limb = limb(player, part);
		Vec3 root = limb[0], along = limb[1].subtract(limb[0]);
		double length = along.length() * progress;
		Vec3 dir = along.normalize();
		// two axes across the limb, for the cords to twist round it
		Vec3 across = right(player);
		Vec3 other = dir.cross(across).normalize();
		// three cords braiding round each other out to where it has got, tighter as the limb fills out
		for (int cord = 0; cord < 3; cord++) {
			for (double t = 0.0; t <= length; t += 0.07) {
				double a = t * 9.0 + cord * (Math.PI * 2.0 / 3.0) + now * 0.25;
				double r = 0.06 + 0.05 * progress;
				Vec3 at = root.add(dir.scale(t)).add(across.scale(Math.cos(a) * r)).add(other.scale(Math.sin(a) * r));
				level.sendParticles(dust, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (player.getRandom().nextInt(8) == 0) {
			level.playSound(null, root.x, root.y, root.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.5F, 0.5F + progress * 0.4F);
		}
	}

	/** Where {@code part} runs, from its joint to its end (a standing body's, hanging straight down). */
	private static Vec3[] limb(ServerPlayer player, int part) {
		Vec3 right = right(player);
		boolean arm = part == Dismember.RIGHT_ARM || part == Dismember.LEFT_ARM;
		int side = part == Dismember.RIGHT_ARM || part == Dismember.RIGHT_LEG ? 1 : -1;
		double scale = player.getBbHeight() / 1.8;
		Vec3 feet = player.position();
		Vec3 root = arm ? feet.add(right.scale(side * 0.32 * scale)).add(0.0, 1.38 * scale, 0.0)
			: feet.add(right.scale(side * 0.12 * scale)).add(0.0, 0.74 * scale, 0.0);
		Vec3 end = root.add(0.0, -0.72 * scale, 0.0);
		return new Vec3[] {root, end};
	}

	private static Vec3 belly(ServerPlayer player) {
		return player.position().add(forward(player).scale(0.14)).add(0.0, player.getBbHeight() * 0.55, 0.0);
	}

	private static Vec3 forward(ServerPlayer player) {
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	/** The body's right (facing south, that's west). */
	private static Vec3 right(ServerPlayer player) {
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
	}
}

package com.jaymoskingdoms.wither;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.PowersCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Wither's four moves in the hunt, each shown before it lands: a beam you hide from ({@link Kind#BEAM}), a charge a
 * raised shield stops ({@link Kind#CHARGE}), a rain of skulls on marked ground ({@link Kind#BARRAGE}) and blue skulls
 * ({@link Kind#BLUES}, which {@link WitherSkulls} lets a swing send back). The tells use Powers' attack-tell glint when
 * it is loaded, and the typed Fx beams, sigils and rings.
 */
final class WitherMoves {
	enum Kind {
		NONE, BEAM, CHARGE, BARRAGE, BLUES
	}

	/** One Wither's move in hand. */
	static final class Move {
		Kind kind = Kind.NONE;
		/** The last move picked, so it isn't picked twice running. */
		Kind previous = Kind.NONE;
		long start;
		long nextAt;
		Vec3 aim = Vec3.ZERO;
		final Set<UUID> struck = new HashSet<>();
		final List<Vec3> marks = new ArrayList<>();
		int thrown;
	}

	// ---- the beam ---------------------------------------------------------------------------------------------------
	/** Ticks of tell (the last {@code BEAM_LOCK} with the aim fixed, the line brighter), ticks the beam shows, and its reach (blocks). */
	static final int BEAM_TELL = 55, BEAM_LOCK = 18, BEAM_SHOW = 14;
	static final double BEAM_RANGE = 70.0, BEAM_WIDTH = 1.1;
	/** Damage to whoever it finds in its line (enough to kill), and the beam's colours: the thin tell and the beam. */
	static final float BEAM_DAMAGE = 1000.0F;
	static final int BEAM_TELL_COLOR = 0x9A5CFF, BEAM_COLOR = 0xE070FF;
	// ---- the charge -------------------------------------------------------------------------------------------------
	/** Ticks of tell, ticks of charge, and its speed (blocks a tick). */
	static final int CHARGE_TELL = 28, CHARGE_RUN = 26;
	static final double CHARGE_SPEED = 1.5, CHARGE_BACKOFF = 0.12, CHARGE_HIT = 1.0;
	/** What a charge does to whoever is in it (health) and how hard it throws them. */
	static final float CHARGE_DAMAGE = 18.0F;
	static final double CHARGE_KNOCK = 1.6;
	/** A shield or guard raised in its way stuns it this long (ticks); a guard raised only just now (a parry), longer. */
	static final int STUN = 80, STUN_PARRY = 140, PARRY_WINDOW = 8;
	/** Where in front of a player a charge counts as met (the dot of their look and the way to it). */
	static final double FRONT = 0.25;
	/** Its blast if it runs into a wall. */
	static final float CHARGE_BLAST = 2.5F;
	// ---- the rain ---------------------------------------------------------------------------------------------------
	/** Ticks to climb, ticks the marks show before the skulls, ticks between skulls, and how high it climbs above its quarry. */
	static final int CLIMB = 40, MARKED = 34, DROP_EVERY = 2;
	static final double CLIMB_HEIGHT = 14.0, DROP_FROM = 26.0;
	/** The marks: how many, how many when angry, and how far round the quarry (blocks). */
	static final int MARKS = 7, ANGRY_MARKS = 11;
	static final double MARK_NEAR = 3.0, MARK_FAR = 10.0;
	static final int MARK_COLOR = 0xFF5020;
	// ---- the blue skulls ----------------------------------------------------------------------------------------------
	/** How many it throws (angry), when the first goes and the gap between them (ticks), and how far ahead of a running quarry it aims (ticks). */
	static final int BLUE_COUNT = 3, ANGRY_BLUES = 5, BLUE_FIRST = 8, BLUE_GAP = 14;
	static final double BLUE_LEAD = 6.0;

	private WitherMoves() {
	}

	static boolean busy(Move move) {
		return move.kind != Kind.NONE;
	}

	/** Its first move comes a little after the hunt begins (and after a stun). */
	static void wake(Move move, long now) {
		move.kind = Kind.NONE;
		move.nextAt = now + 60;
	}

	static void end(Move move) {
		move.kind = Kind.NONE;
		move.struck.clear();
		move.marks.clear();
		move.thrown = 0;
	}

	private static void finish(WitherFight.Fight fight, long now, boolean angry) {
		end(fight.move);
		var random = java.util.concurrent.ThreadLocalRandom.current();
		int wait = WitherFight.WAIT_MIN + random.nextInt(WitherFight.WAIT_MAX - WitherFight.WAIT_MIN + 1);
		fight.move.nextAt = now + (angry ? (int)(wait * WitherFight.ANGRY_WAIT) : wait);
	}

	static void start(ServerLevel level, WitherBoss wither, WitherFight.Fight fight, ServerPlayer quarry, long now, boolean angry) {
		Move move = fight.move;
		Kind[] all = {Kind.BEAM, Kind.CHARGE, Kind.BARRAGE, Kind.BLUES};
		Kind pick;
		do {
			pick = all[level.getRandom().nextInt(all.length)];
		} while (pick == move.previous);
		move.previous = pick;
		end(move);
		move.kind = pick;
		move.start = now;
		move.aim = quarry.getEyePosition();
		Vec3 head = WitherFight.head(wither);
		switch (pick) {
			case BEAM -> {
				PowersCompat.tellGlint(level, head, true);
				level.playSound(null, head.x, head.y, head.z, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.5F);
			}
			case CHARGE -> {
				move.aim = quarry.position().add(0.0, quarry.getBbHeight() * 0.5, 0.0).subtract(wither.position().add(0.0, wither.getBbHeight() * 0.5, 0.0)).normalize();
				PowersCompat.tellGlint(level, head, true);
				level.playSound(null, head.x, head.y, head.z, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.7F);
			}
			case BARRAGE -> level.playSound(null, head.x, head.y, head.z, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.6F);
			default -> PowersCompat.tellGlint(level, head, false);
		}
	}

	static void run(ServerLevel level, WitherBoss wither, WitherFight.Fight fight, ServerPlayer quarry, long now, boolean angry) {
		Move move = fight.move;
		int t = (int)(now - move.start);
		switch (move.kind) {
			case BEAM -> beam(level, wither, fight, quarry, t, now, angry);
			case CHARGE -> charge(level, wither, fight, t, now, angry);
			case BARRAGE -> barrage(level, wither, fight, quarry, t, now, angry);
			case BLUES -> blues(level, wither, fight, quarry, t, now, angry);
			default -> end(move);
		}
	}

	/** Stunned: sparks off it, and nothing else. */
	static void dazed(ServerLevel level, WitherBoss wither, long now) {
		if (now % 6 == 0) {
			level.sendParticles(Fx.spark(0xFFD060, 0.8F), wither.getX(), wither.getY() + wither.getBbHeight(), wither.getZ(), 4, 0.8, 0.5, 0.8, 0.05);
		}
	}

	// ---- moves ----------------------------------------------------------------------------------------------------------

	private static void beam(ServerLevel level, WitherBoss wither, WitherFight.Fight fight, ServerPlayer quarry, int t, long now, boolean angry) {
		Move move = fight.move;
		Vec3 head = WitherFight.head(wither);
		WitherFight.drive(wither, Vec3.ZERO, 0.0);
		if (t < BEAM_TELL - BEAM_LOCK) {
			move.aim = move.aim.lerp(quarry.getEyePosition(), 0.3);
		} else if (t == BEAM_TELL - BEAM_LOCK) {
			PowersCompat.tellGlint(level, head, true);
		}
		if (t < BEAM_TELL) {
			if (t % 3 == 0) {
				Vec3 end = WitherFight.clip(level, wither, head, head.add(move.aim.subtract(head).normalize().scale(BEAM_RANGE)));
				Fx.beam(level, head, end, t < BEAM_TELL - BEAM_LOCK ? 0.04F : 0.1F, BEAM_TELL_COLOR, 5);
			}
			return;
		}
		if (t == BEAM_TELL) {
			Vec3 far = head.add(move.aim.subtract(head).normalize().scale(BEAM_RANGE));
			Vec3 end = WitherFight.clip(level, wither, head, far);
			Fx.beam(level, head, end, 0.5F, BEAM_COLOR, BEAM_SHOW);
			level.playSound(null, head.x, head.y, head.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 4.0F, 0.5F);
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, WitherFight.around(head, end, 2.0), e -> WitherFight.fair(wither, e))) {
				// (what's in the line, and not behind a block: the clip already stopped the beam at the first one)
				if (segment(victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0), head, end) <= BEAM_WIDTH + victim.getBbWidth() * 0.5) {
					victim.hurtServer(level, level.damageSources().indirectMagic(wither, wither), BEAM_DAMAGE);
				}
			}
		}
		if (t >= BEAM_TELL + BEAM_SHOW) {
			finish(fight, now, angry);
		}
	}

	/** How far {@code p} is from the line from {@code a} to {@code b}. */
	static double segment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double length = ab.lengthSqr();
		double along = length < 1.0E-6 ? 0.0 : Mth.clamp(p.subtract(a).dot(ab) / length, 0.0, 1.0);
		return p.distanceTo(a.add(ab.scale(along)));
	}

	private static void charge(ServerLevel level, WitherBoss wither, WitherFight.Fight fight, int t, long now, boolean angry) {
		Move move = fight.move;
		Vec3 dir = move.aim;
		if (t < CHARGE_TELL) {
			WitherFight.drive(wither, dir.scale(-CHARGE_BACKOFF), 1.0);
			if (t % 4 == 0) {
				Vec3 centre = wither.position().add(0.0, wither.getBbHeight() * 0.5, 0.0);
				Fx.beam(level, centre, centre.add(dir.scale(24.0)), 0.07F, 0xE02020, 5);
			}
			return;
		}
		if (t >= CHARGE_TELL + CHARGE_RUN || wither.horizontalCollision && t > CHARGE_TELL + 2) {
			if (wither.horizontalCollision) {
				level.explode(wither, wither.getX(), wither.getY() + 1.0, wither.getZ(), CHARGE_BLAST, false, Level.ExplosionInteraction.MOB);
			}
			finish(fight, now, angry);
			return;
		}
		WitherFight.drive(wither, dir.scale(CHARGE_SPEED), CHARGE_SPEED);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, wither.getBoundingBox().inflate(CHARGE_HIT),
			e -> WitherFight.fair(wither, e) && !move.struck.contains(e.getUUID()))) {
			move.struck.add(victim.getUUID());
			Vec3 at = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
			if (victim instanceof ServerPlayer player && player.isBlocking() && met(player, wither)) {
				// a shield (or a guard) raised to it: it's stopped, and stunned
				boolean parry = player.getTicksUsingItem() <= PARRY_WINDOW;
				player.push(dir.x * 0.9, 0.25, dir.z * 0.9);
				player.needsSync = true;
				Fx.ring(level, at, 2.5F, 0xFFFFFF);
				Fx.blast(level, at, 2.0F, 0xFFD060);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.5F, parry ? 1.6F : 0.8F);
				WitherFight.stun(wither, parry ? STUN_PARRY : STUN);
				return;
			}
			victim.hurtServer(level, level.damageSources().mobAttack(wither), CHARGE_DAMAGE);
			victim.push(dir.x * CHARGE_KNOCK, 0.5, dir.z * CHARGE_KNOCK);
			victim.needsSync = true;
		}
	}

	/** Whether the charge comes at the front of {@code player} (where a raised shield is). */
	private static boolean met(ServerPlayer player, WitherBoss wither) {
		Vec3 to = wither.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		return to.lengthSqr() < 1.0E-4 || look.lengthSqr() < 1.0E-4 || to.normalize().dot(look.normalize()) > FRONT;
	}

	private static void barrage(ServerLevel level, WitherBoss wither, WitherFight.Fight fight, ServerPlayer quarry, int t, long now, boolean angry) {
		Move move = fight.move;
		if (t < CLIMB) {
			// up and over them
			Vec3 over = new Vec3(quarry.getX(), quarry.getY() + CLIMB_HEIGHT, quarry.getZ());
			WitherFight.drive(wither, over.subtract(wither.position()).scale(0.15), 0.7);
			return;
		}
		WitherFight.drive(wither, Vec3.ZERO, 0.0);
		if (t == CLIMB) {
			var random = level.getRandom();
			int count = angry ? ANGRY_MARKS : MARKS;
			for (int i = 0; i < count; i++) {
				Vec3 spot;
				if (i == 0) {
					spot = quarry.position();
				} else {
					double angle = random.nextDouble() * Math.PI * 2.0;
					double distance = MARK_NEAR + random.nextDouble() * (MARK_FAR - MARK_NEAR);
					double x = quarry.getX() + Math.cos(angle) * distance;
					double z = quarry.getZ() + Math.sin(angle) * distance;
					double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
					spot = new Vec3(x, Math.abs(y - quarry.getY()) > 6.0 ? quarry.getY() : y, z);
				}
				move.marks.add(spot);
				Fx.sigil(level, spot, 2.4F, MARK_COLOR, MARKED + count * DROP_EVERY);
				if (i < 3) {
					PowersCompat.tellGlint(level, spot.add(0.0, 0.3, 0.0), true);
				}
			}
		}
		int drop = t - CLIMB - MARKED;
		if (drop >= 0 && move.thrown < move.marks.size() && drop >= move.thrown * DROP_EVERY) {
			WitherSkulls.rain(level, wither, move.marks.get(move.thrown), DROP_FROM);
			level.playSound(null, wither.getX(), wither.getY(), wither.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2.0F, 0.7F);
			move.thrown++;
		}
		if (move.thrown >= move.marks.size() && drop > move.marks.size() * DROP_EVERY + 30) {
			finish(fight, now, angry);
		}
	}

	private static void blues(ServerLevel level, WitherBoss wither, WitherFight.Fight fight, ServerPlayer quarry, int t, long now, boolean angry) {
		Move move = fight.move;
		fight.angle += (angry ? WitherFight.ANGRY_SPEED : WitherFight.ORBIT_SPEED) * fight.dir;
		WitherFight.circle(wither, quarry.position(), angry ? WitherFight.ANGRY_RADIUS : WitherFight.ORBIT_RADIUS, WitherFight.ORBIT_HEIGHT, fight.angle,
			angry ? WitherFight.ANGRY_TOP_SPEED : WitherFight.TOP_SPEED);
		int count = angry ? ANGRY_BLUES : BLUE_COUNT;
		int due = t - BLUE_FIRST;
		if (due >= 0 && move.thrown < count && due >= move.thrown * BLUE_GAP) {
			Vec3 head = WitherFight.head(wither);
			Vec3 to = quarry.getEyePosition().add(quarry.getDeltaMovement().scale(BLUE_LEAD));
			WitherSkulls.throwSkull(level, wither, head, to, true, null, to);
			level.playSound(null, head.x, head.y, head.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 3.0F, 1.2F);
			move.thrown++;
		}
		if (move.thrown >= count && due > (count - 1) * BLUE_GAP + 10) {
			finish(fight, now, angry);
		}
	}
}

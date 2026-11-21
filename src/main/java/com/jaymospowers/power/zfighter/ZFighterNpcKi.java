package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.brawl.Brawl;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (NPC fights 2) The ki attacks of a Z Fighter NPC (a tyrant, a Patrolman, a Z Fighter gifted villager), all aimed at any foe,
 * players too, and each on a cooldown that shrinks with the fighter's rank ({@code Brawl}):
 * <ul>
 * <li><b>Short beam</b> ({@link #fireBeam}): a glow gathers in the palms for {@link #BEAM_GATHER} ticks, then a
 * {@link #BEAM_LENGTH}-block line, every {@link #BEAM_COOL_SLOW} to {@link #BEAM_COOL_FAST} ticks (15 to 25 seconds).</li>
 * <li><b>Seeking volley</b> ({@link #volley}): {@link #VOLLEY_BOLTS} bolts that curve to the foe.</li>
 * <li><b>Charged blast</b> ({@link #charge}): a growing orb gathered for {@link #CHARGE_GATHER} ticks, then thrown as a slow ball for
 * big damage where it lands; a foe who moves out of its way is missed.</li>
 * </ul>
 * <b>For patch 3 (BeamClash):</b> every beam goes through {@link #fireBeam}, which builds an {@link NpcBeam} and offers it to
 * {@link #taker} before doing any damage or drawing: if the taker handles it (a beam struggle), nothing more happens here.
 */
public final class ZFighterNpcKi {
	// ---- beam ----
	static final int BEAM_GATHER = 12, BEAM_COOL_SLOW = 500, BEAM_COOL_FAST = 300;
	static final double BEAM_LENGTH = 20.0, BEAM_WIDTH = 1.2, BEAM_PUSH = 0.9, BEAM_LIFT = 0.3;
	static final float BEAM_SHARE = 1.4F, BEAM_RADIUS = 0.8F;
	// ---- seeking volley ----
	static final int VOLLEY_BOLTS = 5, VOLLEY_GAP = 3, VOLLEY_COOL_SLOW = 260, VOLLEY_COOL_FAST = 150, BOLT_LIFE = 40;
	static final double BOLT_SPEED = 0.75, BOLT_TURN = 0.22, BOLT_SPREAD = 0.6, BOLT_HIT = 0.9;
	static final float BOLT_SHARE = 0.35F;
	// ---- charged blast ----
	static final int CHARGE_GATHER = 20, CHARGE_COOL_SLOW = 420, CHARGE_COOL_FAST = 260, BALL_LIFE = 45;
	static final double BALL_SPEED = 0.8, BALL_RADIUS = 3.0, BALL_HIT = 1.2, BALL_PUSH = 1.2, BALL_ORB = 1.6;
	static final float BALL_SHARE = 3.0F;
	/** The range at which they use ki at all (blocks), and the checks' spacing (ticks). */
	static final double RANGE_MIN = 6.0, RANGE_MAX = 24.0;
	static final int DECIDE_EVERY = 10;
	/** A ki attack that has gathered holds the fighter this long past its gathering (ticks). */
	static final int AFTER = 6;
	/** Orbs gathered here use negative ids of their own, like the Wave Beam's. */
	private static final int ORB_ID = -(1 << 20);

	/** A beam as it leaves a fighter (before any damage): for a beam struggle to take over. */
	public record NpcBeam(Mob caster, @Nullable LivingEntity foe, Vec3 from, Vec3 to, float damage, int color) {
	}

	/** Patch 3's hook: given an {@link NpcBeam}, true if it handled it (a struggle), so {@link #fireBeam} does nothing more. */
	@FunctionalInterface
	public interface BeamTaker {
		boolean take(ServerLevel level, NpcBeam beam);
	}

	public static volatile @Nullable BeamTaker taker;

	private static final class Ki {
		long beamAt, volleyAt, chargeAt, decideAt, busyUntil;
	}

	private static final class Bolt {
		final ServerLevel level;
		final Mob owner;
		final @Nullable LivingEntity foe;
		Vec3 at, velocity;
		int life;
		final float damage;
		final int color;
		final boolean ball;
		final int id;

		Bolt(ServerLevel level, Mob owner, @Nullable LivingEntity foe, Vec3 at, Vec3 velocity, int life, float damage, int color, boolean ball, int id) {
			this.level = level;
			this.owner = owner;
			this.foe = foe;
			this.at = at;
			this.velocity = velocity;
			this.life = life;
			this.damage = damage;
			this.color = color;
			this.ball = ball;
			this.id = id;
		}
	}

	private static final Map<Mob, Ki> KI = new WeakHashMap<>();
	private static final List<Bolt> BOLTS = new ArrayList<>();
	private static int serial;

	private ZFighterNpcKi() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterNpcKi::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			KI.clear();
			BOLTS.clear();
		});
	}

	/** Whether {@code mob} is gathering a ki attack just now. */
	public static boolean busy(Mob mob) {
		Ki ki = KI.get(mob);
		return ki != null && mob.level() instanceof ServerLevel level && level.getGameTime() < ki.busyUntil;
	}

	/**
	 * The owner's call every so often: {@code mob} may start a ki attack on {@code foe} (in range, with a line, not in a string, none
	 * gathering): whichever of the beam, the volley and the charged blast is off cooldown, chosen at random. {@code fists}: its blow,
	 * which the attacks' damage is made of; {@code color}: its ki.
	 */
	public static void decide(ServerLevel level, Mob mob, LivingEntity foe, double fists, int color) {
		long now = level.getGameTime();
		Ki ki = KI.computeIfAbsent(mob, m -> new Ki());
		if (now < ki.decideAt || now < ki.busyUntil || ZFighterStrings.busy(mob) || ZFighterStrings.rushing(mob)) {
			return;
		}
		ki.decideAt = now + DECIDE_EVERY;
		double distance = mob.distanceTo(foe);
		if (distance < RANGE_MIN || distance > RANGE_MAX || !mob.hasLineOfSight(foe)) {
			return;
		}
		boolean beam = now >= ki.beamAt, volley = now >= ki.volleyAt, charge = now >= ki.chargeAt;
		int ready = (beam ? 1 : 0) + (volley ? 1 : 0) + (charge ? 1 : 0);
		if (ready == 0 || mob.getRandom().nextInt(2) != 0) {
			return;
		}
		int pick = mob.getRandom().nextInt(ready);
		if (beam && pick-- == 0) {
			fireBeam(level, mob, foe, fists, color);
		} else if (volley && pick-- == 0) {
			volley(level, mob, foe, fists, color);
		} else {
			charge(level, mob, foe, fists, color);
		}
	}

	private static int cooldown(Mob mob, int slow, int fast) {
		return Math.round(Mth.lerp(Brawl.rank(mob), (float)slow, (float)fast)) + mob.getRandom().nextInt(40);
	}

	// ================================================================ the short beam

	/**
	 * <b>The NPC beam's entry point</b> (patch 3 hooks {@link #taker}): {@code caster} gathers a glow in its palms, then fires a
	 * {@link #BEAM_LENGTH}-block beam at {@code foe}. Aims at players as well as NPCs. False if it couldn't start (busy, or the foe sits in
	 * its eyes).
	 */
	public static boolean fireBeam(ServerLevel level, Mob caster, LivingEntity foe, double fists, int color) {
		Ki ki = KI.computeIfAbsent(caster, m -> new Ki());
		long now = level.getGameTime();
		if (now < ki.busyUntil || foe.getBoundingBox().getCenter().distanceToSqr(caster.getEyePosition()) < 1.0E-4) {
			return false;
		}
		ki.beamAt = now + cooldown(caster, BEAM_COOL_SLOW, BEAM_COOL_FAST);
		ki.busyUntil = now + BEAM_GATHER + AFTER;
		ZFighterHands.pose(caster, ZFighterHands.PALMS);
		int id = ORB_ID - (serial++ & 0xFFFF);
		for (int i = 0; i < BEAM_GATHER; i++) {
			double size = 0.2 + 0.5 * i / (double)BEAM_GATHER;
			ZFighterHands.later(caster, i, () -> {
				if (caster.isAlive()) {
					caster.getLookControl().setLookAt(foe, 30.0F, 30.0F);
					Vec3 dir = caster.getLookAngle();
					ZFighterKi.orb(level, id, ZFighterHands.mainPalm(caster, dir), size, color);
				}
			});
		}
		ZFighterHands.later(caster, BEAM_GATHER, () -> {
			ZFighterHands.pose(caster, ZFighterHands.NONE);
			if (caster.isAlive() && foe.isAlive() && foe.level() == level) {
				shoot(level, caster, foe, fists, color);
			}
		});
		return true;
	}

	private static void shoot(ServerLevel level, Mob caster, LivingEntity foe, double fists, int color) {
		Vec3 aim = foe.getBoundingBox().getCenter().subtract(caster.getEyePosition());
		if (aim.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 dir = aim.normalize();
		Vec3 from = ZFighterHands.palm(caster, dir, false).lerp(ZFighterHands.palm(caster, dir, true), 0.5);
		Vec3 to = from.add(dir.scale(BEAM_LENGTH));
		float damage = (float)(fists * BEAM_SHARE);
		BeamTaker take = taker;
		if (take != null && take.take(level, new NpcBeam(caster, foe, from, to, damage, color))) {
			return;
		}
		ZFighterHands.thrust(caster, ZFighterHands.BOTH, ZFighterHands.PUSH);
		ZFighterHands.shout(caster);
		ZFighterKi.showBeam(level, from, to, BEAM_RADIUS, color);
		MagicFx.burst(level, to.x, to.y, to.z, color, 1.2F, 16, 0.3, 0.3, 0.3);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.2F, 1.4F);
		AABB box = new AABB(from, to).inflate(BEAM_WIDTH);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && e.isAlive() && hittable(e, foe))) {
			if (ZFighterKi.distanceToSegment(e.getBoundingBox().getCenter(), from, to) <= BEAM_WIDTH && caster.hasLineOfSight(e)) {
				e.setInvulnerableTime(0);
				if (e.hurtServer(level, level.damageSources().mobAttack(caster), damage)) {
					ZFighterKi.push(e, dir, BEAM_PUSH, BEAM_LIFT);
				}
			}
		}
	}

	/** Who a ki attack of {@code foe}'s fighter hurts: its foe, and any player in the way (not one in creative or spectator). */
	private static boolean hittable(LivingEntity e, @Nullable LivingEntity foe) {
		return e == foe || e instanceof Player p && !p.isCreative() && !p.isSpectator();
	}

	// ================================================================ the seeking volley

	/** A volley of {@link #VOLLEY_BOLTS} small bolts from the palms that curve to {@code foe}. */
	public static boolean volley(ServerLevel level, Mob caster, LivingEntity foe, double fists, int color) {
		Ki ki = KI.computeIfAbsent(caster, m -> new Ki());
		long now = level.getGameTime();
		if (now < ki.busyUntil) {
			return false;
		}
		ki.volleyAt = now + cooldown(caster, VOLLEY_COOL_SLOW, VOLLEY_COOL_FAST);
		ki.busyUntil = now + VOLLEY_GAP * VOLLEY_BOLTS + AFTER;
		ZFighterHands.pose(caster, ZFighterHands.VOLLEY);
		float damage = (float)(fists * BOLT_SHARE);
		for (int i = 0; i < VOLLEY_BOLTS; i++) {
			boolean left = i % 2 == 1;
			ZFighterHands.later(caster, i * VOLLEY_GAP, () -> {
				if (!caster.isAlive() || !foe.isAlive() || foe.level() != level) {
					return;
				}
				caster.getLookControl().setLookAt(foe, 30.0F, 30.0F);
				Vec3 aim = foe.getBoundingBox().getCenter().subtract(caster.getEyePosition());
				Vec3 dir = aim.lengthSqr() < 1.0E-4 ? caster.getLookAngle() : aim.normalize();
				// out to the side and curving in, so each bolt seeks
				Vec3 spread = new Vec3((caster.getRandom().nextDouble() - 0.5) * BOLT_SPREAD, (caster.getRandom().nextDouble() - 0.2) * BOLT_SPREAD,
					(caster.getRandom().nextDouble() - 0.5) * BOLT_SPREAD);
				Vec3 velocity = dir.add(spread).normalize().scale(BOLT_SPEED);
				ZFighterHands.thrust(caster, left ? ZFighterHands.LEFT : ZFighterHands.RIGHT, ZFighterHands.JAB);
				BOLTS.add(new Bolt(level, caster, foe, ZFighterHands.palm(caster, dir, left), velocity, BOLT_LIFE, damage, color, false, 0));
				level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.HOSTILE, 0.5F,
					1.6F + caster.getRandom().nextFloat() * 0.3F);
			});
		}
		ZFighterHands.later(caster, VOLLEY_GAP * VOLLEY_BOLTS, () -> ZFighterHands.pose(caster, ZFighterHands.NONE));
		return true;
	}

	// ================================================================ the charged blast

	/** A charged blast: an orb gathered for {@link #CHARGE_GATHER} ticks, then thrown slowly at where {@code foe} stands: dodgeable. */
	public static boolean charge(ServerLevel level, Mob caster, LivingEntity foe, double fists, int color) {
		Ki ki = KI.computeIfAbsent(caster, m -> new Ki());
		long now = level.getGameTime();
		if (now < ki.busyUntil) {
			return false;
		}
		ki.chargeAt = now + cooldown(caster, CHARGE_COOL_SLOW, CHARGE_COOL_FAST);
		ki.busyUntil = now + CHARGE_GATHER + AFTER;
		ZFighterHands.pose(caster, ZFighterHands.CUP);
		int id = ORB_ID - (serial++ & 0xFFFF);
		for (int i = 0; i < CHARGE_GATHER; i++) {
			double size = 0.25 + (BALL_ORB - 0.25) * i / (double)CHARGE_GATHER;
			ZFighterHands.later(caster, i, () -> {
				if (caster.isAlive()) {
					caster.getLookControl().setLookAt(foe, 30.0F, 30.0F);
					ZFighterKi.orb(level, id, ZFighterHands.cupped(caster), size, color);
					if (caster.tickCount % 4 == 0) {
						level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 0.8F,
							0.6F + 0.8F * (float)size / (float)BALL_ORB);
					}
				}
			});
		}
		float damage = (float)(fists * BALL_SHARE);
		ZFighterHands.later(caster, CHARGE_GATHER, () -> {
			ZFighterHands.pose(caster, ZFighterHands.NONE);
			if (!caster.isAlive() || foe.level() != level) {
				return;
			}
			// thrown at where the foe is now: a foe that moves is missed
			Vec3 from = ZFighterHands.cupped(caster).add(0.0, 0.3, 0.0);
			Vec3 aim = foe.getBoundingBox().getCenter().subtract(from);
			Vec3 dir = aim.lengthSqr() < 1.0E-4 ? caster.getLookAngle() : aim.normalize();
			ZFighterHands.thrust(caster, ZFighterHands.BOTH, ZFighterHands.PUSH);
			ZFighterHands.shout(caster);
			BOLTS.add(new Bolt(level, caster, foe, from, dir.scale(BALL_SPEED), BALL_LIFE, damage, color, true, ORB_ID - (serial++ & 0xFFFF)));
		});
		return true;
	}

	// ================================================================ bolts and balls

	private static void tick(MinecraftServer server) {
		if (BOLTS.isEmpty()) {
			return;
		}
		for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext();) {
			Bolt b = it.next();
			if (b.life-- <= 0 || !b.owner.isAlive() || b.level != b.owner.level()) {
				if (b.ball) {
					explode(b);
				}
				it.remove();
				continue;
			}
			if (!b.ball && b.foe != null && b.foe.isAlive()) {
				// seeks: turns toward the foe a little each tick
				Vec3 want = b.foe.getBoundingBox().getCenter().subtract(b.at);
				if (want.lengthSqr() > 1.0E-4) {
					b.velocity = b.velocity.lerp(want.normalize().scale(BOLT_SPEED), BOLT_TURN).normalize().scale(BOLT_SPEED);
				}
			}
			Vec3 next = b.at.add(b.velocity);
			boolean blocked = !b.level.noCollision(new AABB(next, next).inflate(0.15));
			b.at = next;
			if (b.ball) {
				ZFighterKi.orb(b.level, b.id, b.at, BALL_ORB, b.color);
				b.level.sendParticles(MagicFx.wisp(b.color, 1.4F), b.at.x, b.at.y, b.at.z, 3, 0.3, 0.3, 0.3, 0.02);
			} else {
				b.level.sendParticles(MagicFx.wisp(b.color, 0.9F), b.at.x, b.at.y, b.at.z, 2, 0.05, 0.05, 0.05, 0.0);
			}
			LivingEntity hit = struck(b);
			if (hit != null || blocked) {
				if (b.ball) {
					explode(b);
				} else if (hit != null) {
					hit.setInvulnerableTime(0);
					hit.hurtServer(b.level, b.level.damageSources().mobAttack(b.owner), b.damage);
					MagicFx.burst(b.level, b.at.x, b.at.y, b.at.z, b.color, 0.9F, 6, 0.15, 0.15, 0.15);
					b.level.playSound(null, b.at.x, b.at.y, b.at.z, SoundEvents.GENERIC_HURT, SoundSource.HOSTILE, 0.3F, 1.8F);
				} else {
					MagicFx.burst(b.level, b.at.x, b.at.y, b.at.z, b.color, 0.7F, 4, 0.1, 0.1, 0.1);
				}
				it.remove();
			}
		}
	}

	/** The foe (or a player in its way) the bolt has reached, or null. */
	private static @Nullable LivingEntity struck(Bolt b) {
		double reach = b.ball ? BALL_HIT : BOLT_HIT;
		for (LivingEntity e : b.level.getEntitiesOfClass(LivingEntity.class, new AABB(b.at, b.at).inflate(reach + 1.0),
			e -> e != b.owner && e.isAlive() && hittable(e, b.foe))) {
			if (e.getBoundingBox().inflate(reach * 0.5).contains(b.at)) {
				return e;
			}
		}
		return null;
	}

	/** The charged blast goes off: a burst, a boom, big damage to the foe and players near it, and a shove. */
	private static void explode(Bolt b) {
		ServerLevel level = b.level;
		Vec3 at = b.at;
		MagicFx.blast(level, at, (float)BALL_RADIUS * 0.8F, b.color);
		MagicFx.ring(level, at, (float)BALL_RADIUS, b.color);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 0.8F);
		com.jaymospowers.core.Shakes.around(level, at, 24.0, 1.0F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BALL_RADIUS), e -> e != b.owner && e.isAlive() && hittable(e, b.foe))) {
			double d = e.getBoundingBox().getCenter().distanceTo(at);
			if (d > BALL_RADIUS) {
				continue;
			}
			float scale = (float)(1.0 - d / (BALL_RADIUS * 1.5));
			e.setInvulnerableTime(0);
			if (e.hurtServer(level, level.damageSources().mobAttack(b.owner), b.damage * scale)) {
				ZFighterKi.push(e, e.position().subtract(at), BALL_PUSH, 0.5);
			}
			if (e instanceof ServerPlayer player) {
				com.jaymospowers.core.Shakes.send(player, 0.5F, at);
			}
		}
	}
}

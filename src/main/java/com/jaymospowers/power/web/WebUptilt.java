package com.jaymospowers.power.web;

import com.jaymospowers.core.Agility;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The web slinger's uptilt (Andy: "web users should get access to the uptilt attack as they get increased agility, their mid
 * air uptilts should involve using the webs to swing them up or pendulum kick the enemy higher, like the insomniac games").
 * <ul>
 * <li><b>Unlocking:</b> a web slinger with {@link #AGILITY_POINTS} Agility (Jaymo's Kingdoms; the points the longbow asks for
 * too) launches with the sneaking blow like anyone with Might's Air Combo ({@link com.jaymospowers.mortal.AirCombo}); that
 * perk still opens it for everyone else. Without Kingdoms Agility is nothing, so web slingers there keep the perk route.</li>
 * <li><b>In the air:</b> each further blow on a launched foe uses the webs to throw it higher than the plain juggle. Where
 * there is a block overhead of you, a web from it swings you up past the foe in a {@link WebPose#SWING_UP_KICK} (the foe goes
 * up with the kick) or in a {@link WebPose#PENDULUM_KICK}, a swing through it that kicks it up and ahead, the two taking turns. With nothing
 * above you but a block over the foe, a web on the foe yanks it up with you ({@link WebPose#WEB_YANK_UP}). In open sky there
 * is nothing to stick to, and the plain juggle goes on. The two lines stay out for the whole swing, which takes {@link #ARC_TICKS} ticks round the anchor with the kick part of the way through.</li>
 * </ul>
 */
public final class WebUptilt {
	/**
	 * The Agility points ({@code 0..100}) a web slinger needs for the launcher. Agility's share is {@code points * 0.0015}
	 * (Kingdoms' {@code Stats}: 100 points are +15%), which is what {@link Agility#factor} reports; 20 is what a longbow asks and
	 * is reached in the first world tier (whose cap is 40).
	 */
	public static final int AGILITY_POINTS = 20;
	/** {@link Agility#factor} at that many points (1 plus the share), the small slack covering float error. */
	public static final double AGILITY_FACTOR = 1.0 + AGILITY_POINTS * 0.0015 - 1.0E-6;

	/** How far up the webs look for something to stick to (blocks), and the least height over the head that counts. */
	static final double SWING_REACH = 24.0, LEAST_OVER = 3.0;
	/** The foe's throw up (blocks/tick) for each move (the plain juggle's is 0.5), and what it gains out along the swing. */
	static final double SWING_UP_BUMP = 0.85, PENDULUM_BUMP = 0.8, PENDULUM_OUT = 0.3, YANK_BUMP = 0.95;
	/** What the slinger gains: lift (blocks/tick) and the share of the way toward the anchor they swing. */
	static final double SWING_UP_LIFT = 0.8, PENDULUM_LIFT = 0.55, PENDULUM_FORWARD = 0.7, YANK_LIFT = 0.6;
	/** What the kick adds to the blow, and how long a layer of web stays on a foe yanked up. */
	static final float KICK_DAMAGE = 2.0F;
	static final int YANK_WEB_TICKS = 30;

	private WebUptilt() {
	}

	/** Whether {@code player} is a web slinger whose Agility opens the launcher. */
	public static boolean unlocked(Player player) {
		return Powers.has(player, WebPower.INSTANCE) && Agility.factor(player) >= AGILITY_FACTOR;
	}

	/** A swing's moves: round the anchor toward the foe (the pendulum), up and past it, or the foe yanked up on a web. */
	private static final int PENDULUM = 0, SWING_UP = 1, YANK = 2;
	/** How many ticks the swing round the anchor takes, the tick the kick lands on, and how far round it goes (radians). */
	static final int ARC_TICKS = 6, KICK_AT = 3;
	static final double SWEEP = 1.25;
	/** The swing's radius is never more than this (blocks): a web only a few blocks of it is swung on, the line still runs to the anchor. */
	static final double MAX_RADIUS = 7.0;

	/** One swing in progress: the line to {@code anchor}, the circle round it and the kick it ends in. */
	private static final class Arc {
		final int foe, kind, side;
		final long start;
		final Vec3 anchor, centre, v0, axis, toward;
		final double radius;
		boolean kicked;

		Arc(int foe, int kind, int side, long start, Vec3 anchor, Vec3 centre, Vec3 v0, Vec3 axis, Vec3 toward, double radius) {
			this.foe = foe;
			this.kind = kind;
			this.side = side;
			this.start = start;
			this.anchor = anchor;
			this.centre = centre;
			this.v0 = v0;
			this.axis = axis;
			this.toward = toward;
			this.radius = radius;
		}
	}

	private static final Map<UUID, Arc> ARCS = new HashMap<>();

	/**
	 * A juggle blow ({@code juggles} counting this one) from web slinger {@code player} on a foe in the air: one of the web moves
	 * if there is something to stick to. False in open sky or for anyone else, and the plain juggle goes on. The move runs for a
	 * few ticks ({@link #tick}): both lines stay drawn, the slinger swings round the anchor, and the kick lands part of the way round.
	 */
	public static boolean juggle(ServerLevel level, ServerPlayer player, LivingEntity target, int juggles) {
		if (!Powers.has(player, WebPower.INSTANCE) || WebPower.ragdolled(player) || WebSymbiote.driving(player)) {
			return false;
		}
		Vec3 foe = target.getBoundingBox().getCenter();
		Vec3 toward = foe.subtract(player.position()).multiply(1.0, 0.0, 1.0);
		toward = toward.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0) : toward.normalize();
		Vec3 over = overhead(player, player.getEyePosition(), toward);
		int side = player.getRandom().nextBoolean() ? 1 : -1;
		long now = level.getGameTime();
		if (over != null) {
			boolean pendulum = juggles % 2 == 0;
			// the swing is on a circle round a point of the line, no further out than MAX_RADIUS
			Vec3 start = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
			Vec3 line = over.subtract(start);
			double radius = Math.min(MAX_RADIUS, line.length());
			Vec3 centre = start.add(line.normalize().scale(radius));
			Vec3 v0 = start.subtract(centre);
			// round toward the foe (the pendulum), or round to the side so it hauls up and past
			Vec3 way = pendulum ? toward : new Vec3(-toward.z * side, 0.0, toward.x * side);
			Vec3 axis = v0.cross(way);
			if (axis.lengthSqr() < 1.0E-6) {
				return false;
			}
			ARCS.put(player.getUUID(), new Arc(target.getId(), pendulum ? PENDULUM : SWING_UP, side, now, over, centre, v0, axis.normalize(), toward, radius));
			WebStrikeLines.show(player, over, -1, over.add(-toward.z * 0.6, 0.0, toward.x * 0.6), -1);
			begin(level, player, pendulum ? WebPose.PENDULUM_KICK : WebPose.SWING_UP_KICK, side, over);
			return true;
		}
		Vec3 above = overhead(player, foe, toward);
		if (above == null) {
			return false;
		}
		// a web from the block over the foe, and another onto it: it is yanked up, and you with it
		Webbed.wrap(target, YANK_WEB_TICKS);
		if (!com.jaymospowers.mortal.AirCombo.throwLimp(target, new Vec3(0.0, YANK_BUMP, 0.0))) {
			WebHaul.push(target, new Vec3(0.0, YANK_BUMP, 0.0));
		}
		WebHaul.push(player, new Vec3(player.getDeltaMovement().x * 0.5, YANK_LIFT, player.getDeltaMovement().z * 0.5));
		ARCS.put(player.getUUID(), new Arc(target.getId(), YANK, side, now, above, foe, Vec3.ZERO, Vec3.ZERO, toward, 0.0));
		ARCS.get(player.getUUID()).kicked = true;
		WebStrikeLines.show(player, foe, target.getId(), above, -1);
		begin(level, player, WebPose.WEB_YANK_UP, side, above);
		hit(level, player, target);
		return true;
	}

	/** Whether {@code player} is swinging on one of these (the movement check leaves them be). */
	static boolean arcing(ServerPlayer player) {
		return ARCS.containsKey(player.getUUID());
	}

	/** Every tick, per web slinger: the lines are kept out, the slinger swung round, the kick landed. */
	static void tick(ServerPlayer player) {
		Arc arc = ARCS.get(player.getUUID());
		if (arc == null) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - arc.start;
		if (!player.isAlive() || player.isSpectator() || t > ARC_TICKS) {
			ARCS.remove(player.getUUID());
			return;
		}
		LivingEntity foe = level.getEntity(arc.foe) instanceof LivingEntity f && f.isAlive() ? f : null;
		if (arc.kind == YANK) {
			WebStrikeLines.show(player, foe == null ? arc.centre : foe.getBoundingBox().getCenter(), arc.foe, arc.anchor, -1);
			return;
		}
		WebStrikeLines.show(player, arc.anchor, -1, arc.anchor.add(-arc.toward.z * 0.6, 0.0, arc.toward.x * 0.6), -1);
		// round the circle: the spot this tick on it (feet), the slinger hauled to it
		double u = Math.min(1.0, (t + 1) / (double)ARC_TICKS);
		Vec3 spot = arc.centre.add(rotate(arc.v0, arc.axis, SWEEP * u * (arc.kind == PENDULUM ? 1.0 : 0.8)))
			.subtract(0.0, player.getBbHeight() * 0.5, 0.0);
		WebHaul.steer(player, spot);
		player.resetFallDistance();
		if (t == KICK_AT && !arc.kicked && foe != null && player.distanceTo(foe) < 6.0) {
			arc.kicked = true;
			// through the bottom of the swing: kicked up and on ahead (a mob limp, as its ragdoll: AirCombo)
			Vec3 kick = arc.kind == PENDULUM ? arc.toward.scale(PENDULUM_OUT).add(0.0, PENDULUM_BUMP, 0.0) : new Vec3(0.0, SWING_UP_BUMP, 0.0);
			if (!com.jaymospowers.mortal.AirCombo.throwLimp(foe, kick)) {
				WebHaul.push(foe, kick);
			}
			hit(level, player, foe);
		}
		if (t == ARC_TICKS) {
			// let go at the top of it: the swing's own speed, and a little more up or forward
			Vec3 v = player.getDeltaMovement();
			WebHaul.push(player, arc.kind == PENDULUM ? v.add(arc.toward.scale(PENDULUM_FORWARD * 0.3)).add(0.0, PENDULUM_LIFT * 0.3, 0.0)
				: v.add(0.0, SWING_UP_LIFT * 0.3, 0.0));
			ARCS.remove(player.getUUID());
		}
	}

	/** {@code v} turned by {@code angle} round the unit {@code axis} (Rodrigues' formula). */
	private static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
		double c = Math.cos(angle), s = Math.sin(angle);
		return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1.0 - c)));
	}

	static void forget(ServerPlayer player) {
		ARCS.remove(player.getUUID());
	}

	static void clearAll() {
		ARCS.clear();
	}

	private static void begin(ServerLevel level, ServerPlayer player, int kind, int side, Vec3 anchor) {
		WebPower.pose(player, kind, side);
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.ITEM_COBWEB, anchor.x, anchor.y, anchor.z, 6, 0.15, 0.15, 0.15, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.9F, 1.3F);
	}

	/** The kick itself: its damage and its sounds. */
	private static void hit(ServerLevel level, ServerPlayer player, LivingEntity target) {
		if (target.isAlive()) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, player.damageSources().mobAttack(player), KICK_DAMAGE);
		}
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.1F, 1.1F);
	}

	/** A block to stick a web to over {@code from}, straight up or leaning toward or away from the foe; null in open sky. */
	private static @Nullable Vec3 overhead(ServerPlayer player, Vec3 from, Vec3 toward) {
		double[] lean = {0.0, 0.5, -0.5};
		for (double l : lean) {
			Vec3 end = from.add(toward.x * l * SWING_REACH * 0.5, SWING_REACH, toward.z * l * SWING_REACH * 0.5);
			BlockHitResult hit = WebAnchors.clip(player, from, end);
			if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().y - from.y >= LEAST_OVER) {
				return hit.getLocation();
			}
		}
		return null;
	}
}

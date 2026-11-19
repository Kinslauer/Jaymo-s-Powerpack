package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.brawl.Brawl;
import com.jaymospowers.core.brawl.Feints;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (NPC fights 2) The blow-string runner every Z Fighter NPC throws its strings with: a tyrant or raider ({@link ZFighterRogue}),
 * a Galactic Patrolman ({@link ZFighterPatrol}) and a Z Fighter gifted villager ({@link ZFighterGift}). It was the rogue's own;
 * the rogue, the Patrolman and the gifted now share it. It sits in this package for the combo moves it reads
 * ({@link ZFighterCombo}); the shared brain is {@code core/brawl/Brawl}, whose rank it reads.
 * <p>
 * A string: quick, light blows ({@link ZFighterCombo#STRINGS}, 3 to 6 for a Patrolman or gifted villager by rank, a tyrant's
 * longer), the last a finisher that throws the foe 8 to 15 blocks by rank and into whatever is behind it
 * ({@link ZFighterImpact}), then a rush after it and, there, a follow-up: a second string in the air if the fighter flies
 * ({@code Brawl}'s combat flight takes it up after the foe) or a hammer blow that slams the foe back down. At rank 0.4 and up,
 * now and then a blow of a string is a feint ({@link Feints}), with a beat's pause after it, to bait a guard or a parry. All
 * of it works on any foe, players too.
 * <p>
 * The owner's AI calls {@link #engage} as often as it thinks; the string itself, the rush and the follow-up advance every
 * server tick here.
 */
public final class ZFighterStrings {
	/** He starts a string within this of his foe (centre to its edge), steps in after it within {@code LUNGE_GAP}, gives up past that. */
	static final double REACH = 2.2;
	static final double LUNGE_GAP = 4.5;
	static final double LUNGE_SPEED = 0.3;
	/** How much of a struck foe's sliding is left between blows (it reels on the spot, so the string lands). */
	static final double REEL = 0.15;
	/** The finisher's throw (blocks a tick, horizontal) at rank 0 and rank 1: 8 blocks and up to about 15; and its lift. */
	static final double THROW_MIN = 1.5, THROW_MAX = 2.4, THROW_LIFT = 0.55;
	/** After a finisher: a beat, then a rush after the foe for at most {@code RUSH_TICKS}, up to {@code RUSH_RANGE} blocks. */
	static final int RUSH_DELAY = 6, RUSH_TICKS = 16;
	static final double RUSH_RANGE = 20.0;
	/** The follow-up on arrival if the foe is in the air: a slam down (chance at rank 0 and 1) for this many times the slam's share, at this speed. */
	static final float SLAM_MIN = 0.35F, SLAM_MAX = 0.75F, SLAM_BONUS = 1.5F;
	static final double SLAM_SPEED = 1.6;
	/** A blow of a string is a feint at rank 0.4 and up: the chance (times rank), and the beat it takes after (ticks). */
	static final float FEINT_BLOW = 0.3F;
	static final int FEINT_PAUSE = 8;
	/** A state not asked about for this long (ticks) is dropped, its string ended. */
	static final int STALE = 30;
	/** A Patrolman's or gifted villager's strings: at most this tier (6 blows), the tier a rank 1 reaches. */
	static final int MAX_TIER = 2;

	/**
	 * What a fighter's strings are made of. {@code fists}: the blow it builds from (a rogue's attack attribute, a Patrolman's
	 * punch); {@code blow}, {@code finisher}, {@code slam}: shares of it; {@code blowTicks}, {@code rest}: a blow's length and
	 * the breath between strings; {@code picks}: indices of {@link ZFighterCombo#STRINGS} to pick from, or null for a tier by rank
	 * (at most {@link #MAX_TIER}); {@code dust}: whether each blow throws a puff of its colour.
	 */
	public record Cfg(double fists, float blow, float finisher, float slam, int blowTicks, int rest, int @Nullable [] picks, int color,
		double rushSpeed, boolean dust) {
		/** A Patrolman's: from his punch and his Patrol rank (0 Rookie to 4 Commander). */
		static Cfg patrol(double fists, int rank) {
			return new Cfg(fists, 0.35F, 1.2F, 0.5F, PATROL_BLOW_TICKS[rank], 24, null, 0xFFC830, 0.9 + 0.1 * rank, false);
		}

		/** A Z Fighter gifted villager's. */
		static Cfg gifted(double fists, int color) {
			return new Cfg(fists, 0.35F, 1.2F, 0.5F, 10, 24, null, color, 0.9, false);
		}
	}

	static final int[] PATROL_BLOW_TICKS = {10, 9, 8, 7, 6};

	private static final class Runner {
		Cfg cfg;
		@Nullable LivingEntity foe;
		long seen;
		int @Nullable [] string;
		int blow, blowLength, serial;
		long blowAt, restUntil;
		boolean struck;
		long rushFrom = -1L, rushUntil = -1L;
		@Nullable LivingEntity chase;

		Runner(Cfg cfg) {
			this.cfg = cfg;
		}
	}

	private static final Map<Mob, Runner> RUNNERS = new WeakHashMap<>();

	private ZFighterStrings() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterStrings::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RUNNERS.clear());
	}

	// ================================================================ the owner's calls

	/**
	 * The owner's call: {@code mob} fights {@code foe} with strings of {@code cfg}. Starts one if it is rested, in reach and has a line
	 * to the foe (and isn't gathering a ki attack). True if a string is running.
	 */
	public static boolean engage(ServerLevel level, Mob mob, LivingEntity foe, Cfg cfg) {
		long now = level.getGameTime();
		Runner r = RUNNERS.computeIfAbsent(mob, m -> new Runner(cfg));
		r.cfg = cfg;
		r.foe = foe;
		r.seen = now;
		if (r.string != null || r.rushUntil >= 0L) {
			return r.string != null;
		}
		double gap = mob.distanceTo(foe) - foe.getBbWidth() * 0.5;
		if (now < r.restUntil || gap > REACH || !mob.hasLineOfSight(foe) || ZFighterNpcKi.busy(mob)) {
			return false;
		}
		r.string = pick(mob, r);
		beginBlow(level, mob, r, 0);
		return true;
	}

	/** A gifted villager's string move: the same, at once (no rest first). False if it couldn't start. */
	static boolean start(ServerLevel level, Mob mob, LivingEntity foe, Cfg cfg) {
		Runner r = RUNNERS.get(mob);
		if (r != null) {
			r.restUntil = 0L;
		}
		return engage(level, mob, foe, cfg);
	}

	/** Whether {@code mob} is in the middle of a string. */
	public static boolean busy(Mob mob) {
		Runner r = RUNNERS.get(mob);
		return r != null && r.string != null;
	}

	/** Whether {@code mob} is in a string or gathering a ki attack: it holds its place in reach (the brain's {@code engaged}). */
	public static boolean engaged(Mob mob) {
		return busy(mob) || ZFighterNpcKi.busy(mob);
	}

	/** Whether {@code mob} is rushing after a foe it threw (the owner doesn't move it then; the brain's {@code driving}). */
	public static boolean rushing(Mob mob) {
		Runner r = RUNNERS.get(mob);
		return r != null && r.rushUntil >= 0L;
	}

	/** The next string may start now (after a vanish dodge, or when a rush arrives). */
	public static void rested(Mob mob) {
		Runner r = RUNNERS.get(mob);
		if (r != null) {
			r.restUntil = 0L;
		}
	}

	/** {@code mob} has no foe: its string ends. */
	public static void release(Mob mob) {
		Runner r = RUNNERS.get(mob);
		if (r != null) {
			end(mob, r);
			r.foe = null;
		}
	}

	// ================================================================ the string

	private static int[] pick(Mob mob, Runner r) {
		if (r.cfg.picks != null) {
			return ZFighterCombo.STRINGS[r.cfg.picks[mob.getRandom().nextInt(r.cfg.picks.length)]];
		}
		int tier = Math.clamp((int)(Brawl.rank(mob) * (MAX_TIER + 1)), 0, MAX_TIER);
		return ZFighterCombo.STRINGS[tier];
	}

	private static void beginBlow(ServerLevel level, Mob mob, Runner r, int blow) {
		boolean finisher = blow == r.string.length - 1;
		r.blow = blow;
		r.blowAt = level.getGameTime();
		r.blowLength = Math.max(2, Math.round(r.cfg.blowTicks * (finisher ? ZFighterCombo.FINISHER_LENGTH : 1.0F)));
		r.struck = false;
		r.serial = r.serial + 1 & 0x7FFF;
		// everyone's client draws the move, as it does a player's (a rogue's model; others swing)
		mob.setAttached(ZFighterCombo.MOVE, r.serial << 16 | Math.min(255, r.blowLength) << 8 | r.string[blow]);
	}

	private static void end(Mob mob, Runner r) {
		r.string = null;
		mob.removeAttached(ZFighterCombo.MOVE);
	}

	private static void tick(MinecraftServer server) {
		if (RUNNERS.isEmpty()) {
			return;
		}
		for (Map.Entry<Mob, Runner> e : new ArrayList<>(RUNNERS.entrySet())) {
			Mob mob = e.getKey();
			Runner r = e.getValue();
			if (mob == null || mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				RUNNERS.remove(mob);
				continue;
			}
			long now = level.getGameTime();
			if (now - r.seen > STALE) {
				end(mob, r);
				r.rushUntil = -1L;
				RUNNERS.remove(mob);
				continue;
			}
			if (r.rushUntil >= 0L) {
				rush(level, mob, r, now);
			} else if (r.string != null) {
				advance(level, mob, r, now);
			}
		}
	}

	/** One tick of a string: stepped after a foe that backs off, a blow landing halfway, the next blow or the finish. */
	private static void advance(ServerLevel level, Mob mob, Runner r, long now) {
		LivingEntity foe = r.foe;
		if (foe == null || !foe.isAlive() || foe.isRemoved() || foe.level() != level) {
			end(mob, r);
			return;
		}
		double gap = mob.distanceTo(foe) - foe.getBbWidth() * 0.5;
		if (gap > LUNGE_GAP) {
			// it got away: the string's over
			end(mob, r);
			r.restUntil = now + Brawl.tempo(mob, r.cfg.rest) / 2;
			return;
		}
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		long age = now - r.blowAt;
		if (age == 0 && gap > REACH * 0.7) {
			// a step in after it
			Vec3 to = foe.position().subtract(mob.position());
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			if (flat.lengthSqr() > 1.0E-4) {
				flat = flat.normalize().scale(LUNGE_SPEED);
				mob.setDeltaMovement(mob.getDeltaMovement().add(flat.x, 0.0, flat.z));
			}
		}
		if (!r.struck && age >= r.blowLength / 2) {
			r.struck = true;
			strike(level, mob, r, foe);
		}
		if (age >= r.blowLength && r.string != null) {
			if (r.blow >= r.string.length - 1) {
				end(mob, r);
				r.restUntil = now + Brawl.tempo(mob, r.cfg.rest);
			} else {
				beginBlow(level, mob, r, r.blow + 1);
			}
		}
	}

	/** The blow lands halfway through: light, the foe held reeling; the finisher throws it into whatever is behind it. A feint lands nothing. */
	private static void strike(ServerLevel level, Mob mob, Runner r, LivingEntity foe) {
		int move = r.string[r.blow];
		boolean finisher = r.blow == r.string.length - 1;
		Vec3 at = foe.getBoundingBox().getCenter();
		float rank = Brawl.rank(mob);
		if (!(mob instanceof ZFighterRogue)) {
			mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		if (!finisher && rank >= Feints.FEINT_RANK && mob.getRandom().nextFloat() < FEINT_BLOW * rank) {
			// a feint: the blow is thrown but not landed, and a beat's pause, to bait a guard or a parry
			Feints.feintAt(mob, foe);
			r.blowLength += FEINT_PAUSE;
			return;
		}
		if (mob.distanceTo(foe) - foe.getBbWidth() * 0.5 > REACH + 0.5) {
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 0.6F,
				ZFighterCombo.kick(move) ? 0.8F : 1.3F);
			return;
		}
		float fists = (float)r.cfg.fists;
		float damage = fists * (finisher ? r.cfg.finisher : r.cfg.blow * ZFighterCombo.MOVE_WEIGHT[move]);
		foe.setInvulnerableTime(0);
		if (!foe.hurtServer(level, level.damageSources().mobAttack(mob), damage)) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.HOSTILE, 0.8F, 1.0F);
			return;
		}
		mob.setLastHurtMob(foe);
		Vec3 look = at.subtract(mob.getEyePosition());
		look = look.lengthSqr() < 1.0E-4 ? mob.getLookAngle() : look.normalize();
		ParticleOptions dust = MagicFx.wisp(r.cfg.color, 1.2F);
		if (finisher) {
			Vec3 motion = foe.getDeltaMovement();
			foe.setDeltaMovement(motion.x * REEL, Math.max(0.0, motion.y), motion.z * REEL);
			// thrown further the higher the rank: 8 blocks up to about 15
			ZFighterKi.push(foe, look, Mth.lerp(rank, THROW_MIN, THROW_MAX), THROW_LIFT);
			ZFighterImpact.launched(foe, mob, fists * r.cfg.slam);
			level.sendParticles(ParticleTypes.EXPLOSION, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(dust, true, false, at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.7F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.4F, 1.6F);
			com.jaymospowers.core.Shakes.around(level, at, 12.0, 0.6F);
			r.chase = foe;
			r.rushFrom = level.getGameTime() + RUSH_DELAY;
			r.rushUntil = r.rushFrom + RUSH_TICKS;
			return;
		}
		Vec3 motion = foe.getDeltaMovement();
		foe.setDeltaMovement(motion.x * REEL, Math.min(motion.y, 0.1), motion.z * REEL);
		foe.syncVelocity = true;
		level.sendParticles(ParticleTypes.CRIT, true, false, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.25);
		if (r.cfg.dust) {
			level.sendParticles(dust, true, false, at.x, at.y, at.z, 2, 0.15, 0.15, 0.15, 0.05);
		}
		level.playSound(null, at.x, at.y, at.z, ZFighterCombo.kick(move) ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_CRIT,
			SoundSource.HOSTILE, 0.8F, 0.9F + mob.getRandom().nextFloat() * 0.2F);
		if (foe instanceof ServerPlayer player) {
			com.jaymospowers.core.Shakes.send(player, 0.15F, mob.position());
		}
	}

	// ================================================================ the chase

	/** After a finisher: a beat, then a rush through the air after the foe it sent flying; on arrival, the follow-up. */
	private static void rush(ServerLevel level, Mob mob, Runner r, long now) {
		LivingEntity foe = r.chase;
		if (foe == null || !foe.isAlive() || foe.isRemoved() || now >= r.rushUntil || mob.distanceTo(foe) > RUSH_RANGE) {
			r.rushUntil = -1L;
			r.chase = null;
			mob.setNoGravity(Brawl.flying(mob));
			return;
		}
		if (now < r.rushFrom) {
			return;
		}
		if (now == r.rushFrom) {
			ZFighterImpact.rushSound(mob);
		}
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		mob.setNoGravity(true);
		if (ZFighterImpact.rush(mob, foe, r.cfg.rushSpeed, REACH * 0.7)) {
			r.rushUntil = -1L;
			r.chase = null;
			mob.setNoGravity(Brawl.flying(mob));
			followUp(level, mob, r, foe, now);
		}
	}

	/** There: a hammer blow down if the foe is still in the air (and a flier doesn't rather go on in the air), else the next string at once. */
	private static void followUp(ServerLevel level, Mob mob, Runner r, LivingEntity foe, long now) {
		boolean air = !foe.onGround() && level.noCollision(foe, foe.getBoundingBox().expandTowards(0.0, -2.0, 0.0));
		r.restUntil = now;
		if (!air) {
			return;
		}
		float chance = Mth.lerp(Brawl.rank(mob), SLAM_MIN, SLAM_MAX);
		if (Brawl.flier(mob) && mob.getRandom().nextFloat() >= chance) {
			return; // both in the air: the next string is thrown there
		}
		float fists = (float)r.cfg.fists;
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		Vec3 at = foe.getBoundingBox().getCenter();
		foe.setInvulnerableTime(0);
		if (foe.hurtServer(level, level.damageSources().mobAttack(mob), fists * r.cfg.slam * SLAM_BONUS)) {
			Vec3 motion = foe.getDeltaMovement();
			foe.setDeltaMovement(motion.x * REEL, -SLAM_SPEED, motion.z * REEL);
			foe.syncVelocity = true;
			ZFighterImpact.launched(foe, mob, fists * r.cfg.slam);
			level.sendParticles(MagicFx.wisp(r.cfg.color, 1.4F), true, false, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.15);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.5F);
			if (foe instanceof ServerPlayer player) {
				com.jaymospowers.core.Shakes.send(player, 0.3F, mob.position());
			}
		}
	}
}

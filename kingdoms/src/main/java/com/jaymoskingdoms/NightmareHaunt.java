package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Andy (clip, 2026-11-24): "nightmare is broken, it needs to appear and disappear after every attack like an enderman or
 * something to diversify it, as of right now it is just a worse looking enderman." The Graveyard's Nightmare was the
 * enderman with a darker coat: it noticed you when you stared at it, walked up and swung, and blinked about with the
 * enderman's own teleport and sound. Its stare goal, its walk-up chase, its melee goal and both teleports are taken out
 * ({@code mixin.graveyard.NightmareHauntMixin}); this is what it does instead once it has someone to hunt.
 *
 * <p>The haunt, one blow at a time:
 * <ol>
 * <li><b>Dissolve</b>: it stops where it stands and comes apart into ink and smoke, with a soul's sigh. Gone.</li>
 * <li><b>Lurk</b>: unseen, unhurtable, silent, somewhere in the dark {@link #LURK_MIN}-{@link #LURK_MAX} blocks off. Its
 * prey feels it: their breath shows cold ({@link #BREATH_EVERY}), a whisper comes from where it waits, and once in a
 * while it lets itself be glimpsed at the edge of sight, gone the moment it is looked at.</li>
 * <li><b>Gather</b>: it chooses a dark spot an arm's length behind or beside its prey. For {@link #GATHER_TICKS} the dark
 * there thickens: smoke drawn in, a rising moan from that very spot, and the prey's heart hammering. That is the warning,
 * and where the sound comes from is where it will stand.</li>
 * <li><b>Wind-up</b>: it stands there, whole and hittable, and swings ({@link #WINDUP_TICKS}). Step out of reach and it
 * misses; strike it and the blow breaks it apart before it lands.</li>
 * <li><b>Recover</b>: after the blow it stays a breath longer ({@link #RECOVER_TICKS}), still hittable, then dissolves
 * and the haunt starts over.</li>
 * </ol>
 * <p>Light is the ward. It only gathers in the dark ({@link #DARK_MAX} or less), never beside prey who stands in light
 * ({@link #WARD_LIGHT}) or holds a torch or lantern. Warded long enough ({@link #WARD_GIVE_UP}), it gives the hunt up
 * and stands back out of the dark where it was, for a while unwilling to start again ({@link #GIVE_UP_REST}). Shots
 * still pass through it ({@link NightmareTell}): only steel up close bites, and only while it is there to bite.
 */
public final class NightmareHaunt {
	// ---- the pace (ticks) ----------------------------------------------------------------------------------------------
	/** Coming apart where it stands. */
	static final int DISSOLVE_TICKS = 12;
	/** How long it waits unseen between blows. */
	static final int LURK_TICKS_MIN = 70;
	static final int LURK_TICKS_MAX = 140;
	/** A longer wait after it was struck: a blow drives it off for a while. */
	static final int LURK_STRUCK_BONUS = 50;
	/** The warning: the dark thickening where it will stand. */
	static final int GATHER_TICKS = 20;
	/** Whole, swinging; the blow lands at the end (its own attack clip lands 10 ticks in). */
	static final int WINDUP_TICKS = 10;
	/** Still there after the blow, hittable. */
	static final int RECOVER_TICKS = 16;
	/** A glimpse at the edge of sight lasts at most this long (and ends the moment it is looked at). */
	static final int GLIMPSE_TICKS = 14;
	/** The chance that a wait holds a glimpse. */
	static final float GLIMPSE_CHANCE = 0.45F;
	/** Cold breath at the prey's mouth this often while it lurks near. */
	static final int BREATH_EVERY = 30;
	/** A whisper from where it waits this often. */
	static final int WHISPER_EVERY = 45;
	/** Warded by light this long in one hunt, it gives the hunt up... */
	static final int WARD_GIVE_UP = 20 * 30;
	/** ...and won't take up another for this long. */
	static final int GIVE_UP_REST = 20 * 20;
	/** Out of a spot: try again this soon. */
	static final int RETRY_TICKS = 20;

	// ---- the distances (blocks) ----------------------------------------------------------------------------------------
	/** Where it waits, from its prey. */
	static final double LURK_MIN = 10.0;
	static final double LURK_MAX = 18.0;
	/** Where it gathers, from its prey: an arm's length. */
	static final double GATHER_MIN = 2.6;
	static final double GATHER_MAX = 3.6;
	/** The blow reaches this far (centre to centre); a step back during the wind-up is a miss. */
	static final double STRIKE_REACH = 3.4;
	/** It won't gather within this angle of where its prey looks (degrees off the look direction): behind or beside. */
	static final double GATHER_MIN_ANGLE = 55.0;
	/** A glimpse stands this far off and this far round to the side (degrees). */
	static final double GLIMPSE_MIN = 9.0;
	static final double GLIMPSE_MAX = 14.0;
	static final double GLIMPSE_ANGLE_MIN = 60.0;
	static final double GLIMPSE_ANGLE_MAX = 95.0;
	/** Looked at this squarely (cosine), a glimpse goes. */
	static final double LOOKED_AT = 0.95;
	/** Prey farther than this is lost: it reforms where it waits and the hunt ends. */
	static final double LOSE_RANGE = 48.0;
	/** Breath and whispers reach this far. */
	static final double HUSH_RANGE = 24.0;

	// ---- light ---------------------------------------------------------------------------------------------------------
	/** It only gathers where the light (block and sky, as it is now) is this or less. */
	static final int DARK_MAX = 7;
	/** Prey standing in this much light, or holding a light this bright, can't be reached. */
	static final int WARD_LIGHT = 12;

	// ---- the blow ------------------------------------------------------------------------------------------------------
	/** The cold that follows its blow (the Darkness pulse), ticks. */
	static final int CHILL_TICKS = 50;

	private NightmareHaunt() {
	}

	/** What the Nightmare is doing in its haunt. */
	public enum Phase {
		/** No prey: it stands and wanders as it always did, in plain sight. */
		IDLE,
		DISSOLVE,
		LURK,
		GLIMPSE,
		GATHER,
		WINDUP,
		RECOVER
	}

	/** One Nightmare's haunt (kept on the entity; not saved: a reloaded one simply starts from {@link Phase#IDLE}). */
	public static final class State {
		Phase phase = Phase.IDLE;
		int timer;
		/** In a lurk, the tick left on {@link #timer} at which it shows itself, or -1. */
		int glimpseAt = -1;
		/** Ticks warded by light in this hunt. */
		int warded;
		/** Ticks left before it will take up a hunt again. */
		int rest;
		/** A blow drove it off: the next wait is longer. */
		boolean struck;
		/** Where it will stand (gather, glimpse) or waits (lurk). */
		Vec3 spot;
		/** Who it hunts. Kept here rather than as its target, which its target goals drop once it can't see them. */
		LivingEntity prey;
		/** Not yet ticked since it was loaded: a save made mid-haunt leaves it silent. */
		boolean fresh = true;

		public Phase phase() {
			return phase;
		}
	}

	/** What the Graveyard's Nightmare lends the haunt (its mixin). */
	public interface Body {
		State jaymoskingdoms$haunt();

		/** Starts its own attack clip, which lands its blow 10 ticks in. */
		void jaymoskingdoms$swing();
	}

	/** Gone into the dark: unseen and unhurtable. */
	public static boolean gone(Monster nightmare) {
		Phase p = ((Body)nightmare).jaymoskingdoms$haunt().phase;
		return p == Phase.LURK || p == Phase.GATHER;
	}

	/** Every server tick, after its own. */
	public static void tick(Monster nightmare, ServerLevel level) {
		State s = ((Body)nightmare).jaymoskingdoms$haunt();
		if (s.rest > 0) {
			s.rest--;
		}
		if (s.fresh) {
			s.fresh = false;
			appear(nightmare);
		}
		LivingEntity prey = s.phase == Phase.IDLE ? nightmare.getTarget() : s.prey;
		boolean hunting = prey != null && prey.isAlive() && !prey.isRemoved() && !prey.isSpectator() && !(prey instanceof ServerPlayer p && p.isCreative())
			&& prey.level() == level && prey.distanceToSqr(nightmare) < LOSE_RANGE * LOSE_RANGE;
		if (s.phase == Phase.IDLE) {
			if (hunting && s.rest > 0) {
				nightmare.setTarget(null);
			} else if (hunting) {
				s.warded = 0;
				s.prey = prey;
				begin(nightmare, level, s, Phase.DISSOLVE, DISSOLVE_TICKS);
			}
			return;
		}
		if (!hunting && s.phase != Phase.DISSOLVE) {
			// the hunt is over (prey dead, gone or too far): it stands back out of the dark where it is
			reform(nightmare, level, s);
			return;
		}
		hold(nightmare);
		s.timer--;
		switch (s.phase) {
			case DISSOLVE -> dissolving(nightmare, level, s, prey, hunting);
			case LURK -> lurking(nightmare, level, s, prey);
			case GLIMPSE -> glimpsing(nightmare, level, s, prey);
			case GATHER -> gathering(nightmare, level, s, prey);
			case WINDUP -> windingUp(nightmare, level, s, prey);
			case RECOVER -> {
				face(nightmare, prey);
				if (s.timer <= 0) {
					begin(nightmare, level, s, Phase.DISSOLVE, DISSOLVE_TICKS);
				}
			}
			default -> {
			}
		}
	}

	/** It was hurt (the blow went in): whole, it breaks apart now and stays away longer. */
	public static void hurt(Monster nightmare, ServerLevel level) {
		State s = ((Body)nightmare).jaymoskingdoms$haunt();
		if (!nightmare.isAlive()) {
			return;
		}
		if (s.phase == Phase.WINDUP || s.phase == Phase.RECOVER || s.phase == Phase.GLIMPSE) {
			s.struck = true;
			begin(nightmare, level, s, Phase.DISSOLVE, DISSOLVE_TICKS / 2);
		}
	}

	// ---- the phases ----------------------------------------------------------------------------------------------------

	private static void dissolving(Monster nightmare, ServerLevel level, State s, LivingEntity prey, boolean hunting) {
		RandomSource random = nightmare.getRandom();
		// ink and smoke sinking off it
		level.sendParticles(ParticleTypes.SQUID_INK, nightmare.getX(), nightmare.getY(0.5), nightmare.getZ(), 4, 0.25, 0.6, 0.25, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, nightmare.getX(), nightmare.getY(0.3 + random.nextDouble() * 0.6), nightmare.getZ(),
			2, 0.2, 0.3, 0.2, 0.005);
		if (s.timer > 0) {
			return;
		}
		if (!hunting) {
			reform(nightmare, level, s);
			return;
		}
		vanish(nightmare);
		Vec3 lurk = findSpot(nightmare, level, prey, LURK_MIN, LURK_MAX, 0.0, 180.0, false);
		if (lurk != null) {
			nightmare.teleportTo(lurk.x, lurk.y, lurk.z);
		}
		s.spot = nightmare.position();
		int wait = Mth.nextInt(nightmare.getRandom(), LURK_TICKS_MIN, LURK_TICKS_MAX) + (s.struck ? LURK_STRUCK_BONUS : 0);
		s.struck = false;
		s.phase = Phase.LURK;
		s.timer = wait;
		s.glimpseAt = random.nextFloat() < GLIMPSE_CHANCE ? Mth.nextInt(random, wait / 3, wait * 2 / 3) : -1;
	}

	private static void lurking(Monster nightmare, ServerLevel level, State s, LivingEntity prey) {
		RandomSource random = nightmare.getRandom();
		boolean near = prey.distanceToSqr(nightmare) < HUSH_RANGE * HUSH_RANGE;
		if (near && s.timer % BREATH_EVERY == 0) {
			// the edges of the world go cold: the prey's breath shows
			Vec3 mouth = prey.getEyePosition().add(prey.getLookAngle().scale(0.35)).add(0.0, -0.1, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 0, prey.getLookAngle().x * 0.02, 0.01, prey.getLookAngle().z * 0.02, 1.0);
			level.sendParticles(ParticleTypes.SNOWFLAKE, mouth.x, mouth.y, mouth.z, 2, 0.05, 0.05, 0.05, 0.005);
		}
		if (near && s.timer % WHISPER_EVERY == 0 && prey instanceof ServerPlayer player) {
			// a whisper from where it waits: the way to look
			Vec3 way = nightmare.position().subtract(player.position());
			Vec3 at = player.getEyePosition().add(way.normalize().scale(Math.min(5.0, way.length())));
			Exposure.hearAt(player, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), at, 0.45F, 0.55F + random.nextFloat() * 0.2F);
		}
		if (s.timer == s.glimpseAt) {
			Vec3 side = findSpot(nightmare, level, prey, GLIMPSE_MIN, GLIMPSE_MAX, GLIMPSE_ANGLE_MIN, GLIMPSE_ANGLE_MAX, true);
			if (side != null) {
				s.spot = side;
				nightmare.teleportTo(side.x, side.y, side.z);
				face(nightmare, prey);
				appear(nightmare);
				s.phase = Phase.GLIMPSE;
				s.timer = GLIMPSE_TICKS;
				return;
			}
		}
		if (s.timer > 0) {
			return;
		}
		if (warded(level, prey)) {
			s.warded += RETRY_TICKS;
			if (s.warded >= WARD_GIVE_UP) {
				s.rest = GIVE_UP_REST;
				reform(nightmare, level, s);
				nightmare.setTarget(null);
				return;
			}
			s.timer = RETRY_TICKS;
			return;
		}
		Vec3 spot = findSpot(nightmare, level, prey, GATHER_MIN, GATHER_MAX, GATHER_MIN_ANGLE, 180.0, true);
		if (spot == null) {
			s.warded += RETRY_TICKS / 2;
			s.timer = RETRY_TICKS;
			return;
		}
		s.spot = spot;
		nightmare.teleportTo(spot.x, spot.y, spot.z);
		face(nightmare, prey);
		s.phase = Phase.GATHER;
		s.timer = GATHER_TICKS;
		// the moan rises from the very spot it will stand
		level.playSound(null, spot.x, spot.y + 1.5, spot.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.4F, 0.45F);
		level.playSound(null, spot.x, spot.y + 1.0, spot.z, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(), SoundSource.HOSTILE, 1.2F, 0.5F);
	}

	private static void glimpsing(Monster nightmare, ServerLevel level, State s, LivingEntity prey) {
		face(nightmare, prey);
		boolean seen = lookedAt(prey, nightmare);
		if (s.timer > 0 && !seen) {
			return;
		}
		// looked at (or long enough): gone, no sound, a wisp of smoke
		level.sendParticles(ParticleTypes.SMOKE, nightmare.getX(), nightmare.getY(0.6), nightmare.getZ(), 6, 0.2, 0.6, 0.2, 0.01);
		vanish(nightmare);
		s.phase = Phase.LURK;
		s.timer = Math.max(s.timer, 0) + Mth.nextInt(nightmare.getRandom(), 20, 40);
		s.glimpseAt = -1;
	}

	private static void gathering(Monster nightmare, ServerLevel level, State s, LivingEntity prey) {
		RandomSource random = nightmare.getRandom();
		face(nightmare, prey);
		Vec3 at = s.spot != null ? s.spot : nightmare.position();
		// the dark thickening: ink and smoke drawn in to where it will stand
		for (int i = 0; i < 3; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = 1.2 + random.nextDouble() * 0.8;
			double y = at.y + random.nextDouble() * 2.6;
			double x = at.x + Math.cos(a) * r;
			double z = at.z + Math.sin(a) * r;
			ParticleOptions p = i == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SQUID_INK;
			// a count of 0 sends the spread as a velocity: inward
			level.sendParticles(p, x, y, z, 0, (at.x - x) * 0.12, 0.0, (at.z - z) * 0.12, 1.0);
		}
		if (prey instanceof ServerPlayer player && s.timer % 8 == 0) {
			// the heart hammers
			Exposure.hear(player, SoundEvents.WARDEN_HEARTBEAT, 0.9F, 1.15F);
		}
		if (s.timer > 0) {
			return;
		}
		appear(nightmare);
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.3, at.z, 14, 0.3, 0.8, 0.3, 0.04);
		((Body)nightmare).jaymoskingdoms$swing();
		s.phase = Phase.WINDUP;
		s.timer = WINDUP_TICKS;
	}

	private static void windingUp(Monster nightmare, ServerLevel level, State s, LivingEntity prey) {
		face(nightmare, prey);
		if (s.timer > 0) {
			return;
		}
		double reach = STRIKE_REACH + prey.getBbWidth() * 0.5;
		if (prey.distanceToSqr(nightmare) <= reach * reach && nightmare.hasLineOfSight(prey)) {
			Vec3 lunge = prey.position().subtract(nightmare.position()).normalize().scale(0.35);
			nightmare.setDeltaMovement(lunge.x, 0.05, lunge.z);
			if (nightmare.doHurtTarget(level, prey)) {
				prey.addEffect(new MobEffectInstance(MobEffects.DARKNESS, CHILL_TICKS, 0, false, false, true), nightmare);
			}
		} else {
			// stepped out of reach: the swing finds air
			level.playSound(null, nightmare.getX(), nightmare.getY(1.0), nightmare.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.7F, 0.5F);
		}
		s.phase = Phase.RECOVER;
		s.timer = RECOVER_TICKS;
	}

	// ---- helpers -------------------------------------------------------------------------------------------------------

	private static void begin(Monster nightmare, ServerLevel level, State s, Phase phase, int ticks) {
		s.phase = phase;
		s.timer = ticks;
		if (phase == Phase.DISSOLVE) {
			level.playSound(null, nightmare.getX(), nightmare.getY(), nightmare.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
			level.playSound(null, nightmare.getX(), nightmare.getY(), nightmare.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.35F, 0.5F);
		}
	}

	/** Back out of the dark, in plain sight, and no longer hunting. */
	private static void reform(Monster nightmare, ServerLevel level, State s) {
		if (nightmare.isInvisible()) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, nightmare.getX(), nightmare.getY(0.5), nightmare.getZ(), 10, 0.3, 0.8, 0.3, 0.01);
		}
		appear(nightmare);
		s.phase = Phase.IDLE;
		s.timer = 0;
		s.glimpseAt = -1;
		s.struck = false;
		s.prey = null;
	}

	private static void vanish(Monster nightmare) {
		nightmare.setInvisible(true);
		nightmare.setSilent(true);
		nightmare.getNavigation().stop();
	}

	private static void appear(Monster nightmare) {
		nightmare.setInvisible(false);
		nightmare.setSilent(false);
	}

	/** Mid-haunt it never walks: it is where the dark puts it. */
	private static void hold(Monster nightmare) {
		nightmare.getNavigation().stop();
		nightmare.getMoveControl().setWantedPosition(nightmare.getX(), nightmare.getY(), nightmare.getZ(), 0.0);
		Vec3 v = nightmare.getDeltaMovement();
		nightmare.setDeltaMovement(0.0, Math.min(v.y, 0.0), 0.0);
	}

	private static void face(Monster nightmare, LivingEntity prey) {
		nightmare.getLookControl().setLookAt(prey, 180.0F, 180.0F);
		double dx = prey.getX() - nightmare.getX();
		double dz = prey.getZ() - nightmare.getZ();
		float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		nightmare.setYRot(yaw);
		nightmare.setYHeadRot(yaw);
		nightmare.setYBodyRot(yaw);
	}

	/** Whether {@code who} is looking squarely at {@code e}, with nothing between. */
	private static boolean lookedAt(LivingEntity who, LivingEntity e) {
		Vec3 to = e.getEyePosition().subtract(who.getEyePosition());
		double len = to.length();
		if (len < 1.0E-3) {
			return true;
		}
		return who.getLookAngle().dot(to.scale(1.0 / len)) > LOOKED_AT && who.hasLineOfSight(e);
	}

	/** The prey is out of its reach: in light, or holding one. */
	static boolean warded(ServerLevel level, LivingEntity prey) {
		if (level.getRawBrightness(BlockPos.containing(prey.getEyePosition()), level.getSkyDarken()) >= WARD_LIGHT
			|| level.getRawBrightness(prey.blockPosition(), level.getSkyDarken()) >= WARD_LIGHT) {
			return true;
		}
		return lightHeld(prey.getItemInHand(InteractionHand.MAIN_HAND)) || lightHeld(prey.getItemInHand(InteractionHand.OFF_HAND));
	}

	private static boolean lightHeld(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BlockItem block
			&& block.getBlock().defaultBlockState().getLightEmission() >= WARD_LIGHT;
	}

	/**
	 * A spot it can stand on, {@code min}-{@code max} blocks from {@code prey}, {@code angleMin}-{@code angleMax} degrees
	 * round from where the prey looks; with {@code dark}, only where the light is {@link #DARK_MAX} or less. Null if none.
	 */
	private static Vec3 findSpot(Monster nightmare, ServerLevel level, LivingEntity prey, double min, double max, double angleMin,
		double angleMax, boolean dark) {
		RandomSource random = nightmare.getRandom();
		Vec3 look = prey.getLookAngle();
		double lookYaw = Math.atan2(look.z, look.x);
		for (int tries = 0; tries < 24; tries++) {
			double off = Math.toRadians(angleMin + random.nextDouble() * (angleMax - angleMin)) * (random.nextBoolean() ? 1 : -1);
			double a = lookYaw + off;
			double r = min + random.nextDouble() * (max - min);
			double x = prey.getX() + Math.cos(a) * r;
			double z = prey.getZ() + Math.sin(a) * r;
			int baseY = Mth.floor(prey.getY());
			for (int dy = 3; dy >= -4; dy--) {
				BlockPos feet = BlockPos.containing(x, baseY + dy, z);
				BlockPos below = feet.below();
				if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
					continue;
				}
				Vec3 at = new Vec3(x, feet.getY(), z);
				AABB box = nightmare.getType().getDimensions().makeBoundingBox(at);
				if (!level.noCollision(nightmare, box) || level.containsAnyLiquid(box)) {
					continue;
				}
				if (dark && level.getRawBrightness(feet.above(), level.getSkyDarken()) > DARK_MAX) {
					break;
				}
				return at;
			}
		}
		return null;
	}
}

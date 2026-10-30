package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.Breaks;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The hold combo (left mouse held, bare-handed): a run of martial-arts blows thrown one after another on their own,
 * ending in a finisher that sends the foe flying. It grows with him ({@link #tier}): a fresh fighter steps in with a jab,
 * a reverse punch, an elbow and a chi palm; a seasoned one adds knees, sweeps, crescent, axe and spinning kicks, throws
 * them faster and hits harder, up to a ten-blow string ending in the rising dragon fist. Holding on, he starts again
 * after a breath.
 * <p>
 * Every blow is stepped into ({@link #STEP_IN}): a small real step toward the foe (or ahead, at nothing), never over a
 * ledge; a foe a little out of reach gets a proper lunge. Each blow is a real punch (the fists' damage, ki per punch, TP,
 * mastery); between blows the foe is held reeling so the string lands. The leg sweep trips it, the double palm shoves it
 * back, knees pop it up. Everyone sees the moves ({@link #MOVE}); the client draws them ({@code ZFighterComboPose}), and
 * shadowboxing ({@code Shadowbox}) practises the same set.
 */
public final class ZFighterCombo {
	/**
	 * The moves (the martial-arts set the client's pose table and shadowboxing share): the lead jab (a step in), the reverse
	 * punch (hip turned through), the rear elbow, the chi palm (open-hand thrust, a puff of light), the rising knee, the
	 * front snap kick, the roundhouse, the leg sweep (trips the foe), the crescent kick, the axe kick, the spinning back
	 * kick, the spinning hook kick, the double palm (a heavy shove), the flying knee, the flying double kick and the rising
	 * dragon fist (a jumping uppercut that lifts the foe high).
	 */
	public static final int JAB = 0, REVERSE_PUNCH = 1, ELBOW = 2, CHI_PALM = 3, RISING_KNEE = 4, FRONT_KICK = 5, ROUNDHOUSE = 6,
		LEG_SWEEP = 7, CRESCENT_KICK = 8, AXE_KICK = 9, SPIN_BACK_KICK = 10, SPIN_HOOK_KICK = 11, DOUBLE_PALM = 12, FLYING_KNEE = 13,
		FLYING_DOUBLE_KICK = 14, DRAGON_FIST = 15;
	public static final int MOVES = 16;

	/** Each tier's string, the last move its finisher. */
	static final int[][] STRINGS = {
		{JAB, REVERSE_PUNCH, ELBOW, CHI_PALM},
		{JAB, REVERSE_PUNCH, FRONT_KICK, ROUNDHOUSE, DOUBLE_PALM},
		{JAB, REVERSE_PUNCH, RISING_KNEE, LEG_SWEEP, AXE_KICK, SPIN_BACK_KICK},
		{JAB, REVERSE_PUNCH, ELBOW, CRESCENT_KICK, ROUNDHOUSE, FLYING_KNEE, CHI_PALM, SPIN_HOOK_KICK},
		{JAB, REVERSE_PUNCH, ELBOW, RISING_KNEE, LEG_SWEEP, CRESCENT_KICK, SPIN_BACK_KICK, DOUBLE_PALM, FLYING_DOUBLE_KICK, DRAGON_FIST},
	};
	/**
	 * (0.18.25, Andy: "Kicks only") Each tier's string with both arms broken ({@link Breaks#armless}): the hands can't
	 * hit, so it's all knees and kicks, the last its finisher. Kicks never lose damage to broken arms ({@link #fists}).
	 */
	static final int[][] KICK_STRINGS = {
		{FRONT_KICK, FRONT_KICK, ROUNDHOUSE},
		{FRONT_KICK, ROUNDHOUSE, LEG_SWEEP, ROUNDHOUSE},
		{RISING_KNEE, FRONT_KICK, LEG_SWEEP, AXE_KICK, SPIN_BACK_KICK},
		{RISING_KNEE, CRESCENT_KICK, ROUNDHOUSE, FLYING_KNEE, LEG_SWEEP, SPIN_HOOK_KICK},
		{RISING_KNEE, LEG_SWEEP, CRESCENT_KICK, SPIN_BACK_KICK, FLYING_KNEE, AXE_KICK, ROUNDHOUSE, FLYING_DOUBLE_KICK},
	};
	/**
	 * Ticks a blow takes, by tier (the finisher takes {@link #FINISHER_LENGTH} times as long). (0.13.34) Twice as slow
	 * as they were: the blows ran into each other and the animations stumbled over one another. Finesse speeds them
	 * back up ({@link #speed}).
	 */
	static final int[] STEP_TICKS = {16, 14, 12, 10, 8};
	static final float FINISHER_LENGTH = 1.6F;
	/** Ticks to catch his breath after a finisher before the string starts over, by tier. */
	static final int[] RECOVER_TICKS = {24, 22, 20, 18, 16};
	/**
	 * (0.13.34) Finesse (Jaymo's Kingdoms' attribute, read off its attack-speed share: 2.5% a point, 10 at most)
	 * quickens the string: this much faster per share of attack speed it gives (6 makes each point 15% faster, 10
	 * points two and a half times as fast, past the old speed). A blow never takes fewer than {@code MIN_STEP_TICKS}.
	 */
	static final double FINESSE_SPEED = 6.0;
	static final int MIN_STEP_TICKS = 3;
	private static final net.minecraft.resources.Identifier FINESSE_MODIFIER =
		net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymoskingdoms", "stat_finesse");
	/**
	 * A blow hits for this share of his fists, by tier: less each tier, since the strings get longer and faster (about
	 * 1.5 fists a second at the start, 1.7 at the top, before kicks)...
	 */
	static final float[] HIT_SHARE = {0.6F, 0.52F, 0.45F, 0.4F, 0.34F};
	/** ...times the move's own weight (punches 1, kicks harder), in move order. */
	static final float[] MOVE_WEIGHT = {1.0F, 1.0F, 1.1F, 1.0F, 1.15F, 1.25F, 1.3F, 1.15F, 1.25F, 1.35F, 1.4F, 1.4F, 1.1F, 1.3F, 1.35F, 1.2F};
	/** The finisher hits for this many fists, by tier, and throws the foe this far (horizontal, up). */
	static final float[] FINISHER_SHARE = {1.4F, 1.45F, 1.5F, 1.55F, 1.6F};
	static final double[] FINISHER_PUSH = {1.4, 1.6, 1.9, 2.2, 2.7};
	static final double[] FINISHER_LIFT = {0.4, 0.45, 0.5, 0.55, 0.7};
	/** The rising dragon fist (a finisher) throws the foe mostly up: this far along, this high (about 5 blocks). */
	static final double DRAGON_PUSH = 0.5;
	static final double DRAGON_LIFT = 1.0;
	/** Knees pop the foe up this much (the rising knee, the flying knee). */
	static final double KNEE_LIFT = 0.3;
	static final double FLYING_KNEE_LIFT = 0.25;
	/** The leg sweep trips it: off its feet (popped up this much, its sliding stopped) and slowed hard this long. */
	static final double SWEEP_POP = 0.22;
	static final int SWEEP_TICKS = 20;
	static final int SWEEP_SLOW = 4;
	/** The double palm (mid-string) shoves the foe back this far and up; the next blow chases it. */
	static final double PALM_SHOVE = 0.7;
	static final double PALM_SHOVE_LIFT = 0.15;
	/** Reach past his own interaction range, and how far he steps in after a foe a little out of reach. */
	static final double REACH_BONUS = 0.3;
	static final double LUNGE_RANGE = 2.5;
	static final double LUNGE_SPEED = 0.28;
	static final double LUNGE_PER_TIER = 0.05;
	/**
	 * The step into every blow, by move (blocks a tick added to his motion; on the ground that carries him about
	 * {@link #STEP_PROBE} times as far: a fifth to a quarter of a block). Flying moves go further, through the air.
	 */
	static final double[] STEP_IN = {0.1, 0.08, 0.09, 0.1, 0.07, 0.06, 0.06, 0.04, 0.06, 0.07, 0.04, 0.05, 0.12, 0.08, 0.08, 0.1};
	/** Closer than this (centre to the foe's edge) he doesn't step any more: the step is all in the hips. */
	static final double STEP_MIN_GAP = 0.9;
	/** A hop up into the flying knee and the flying double kick, and the dragon fist's jump (about 3/4 of a block). */
	static final double HOP_FLYING = 0.25;
	static final double HOP_DRAGON = 0.34;
	/** How far ahead he looks for ground before a step (times the step), on the ground and for a hop. */
	static final double STEP_PROBE = 2.5;
	static final double HOP_PROBE = 5.0;
	/** A step is only taken if there's ground ahead no more than this many blocks down. */
	static final int LEDGE_DROP = 1;
	/** How much of a struck foe's sliding is left between blows (it reels on the spot). */
	static final double REEL = 0.15;
	/** Food each blow costs. */
	static final float EXHAUSTION = 0.06F;
	/** Screen shake: each blow (his own screen), and the finisher (everyone near). */
	static final float HIT_SHAKE = 0.12F;
	static final float HIT_SHAKE_PER_TIER = 0.03F;
	static final float FINISHER_SHAKE = 0.6F;
	static final float FINISHER_SHAKE_PER_TIER = 0.15F;
	/**
	 * (0.16.10, Andy: Viltrumite-style fights) The foe a finisher sends flying slams into the wall or ground it meets
	 * for this share of his fists more ({@link ZFighterImpact}); holding on, he rushes after it once he's caught his
	 * breath, if it's no further than {@code CHASE_RANGE} and he can see it, at this many blocks a tick by tier, for
	 * at most {@code RUSH_TICKS}, and the next string starts on it.
	 */
	static final float SLAM_SHARE = 0.6F;
	static final double CHASE_RANGE = 14.0;
	static final double[] RUSH_SPEED = {0.8, 0.9, 1.0, 1.15, 1.3};
	static final int RUSH_TICKS = 14;
	/** No fall damage this long after a rush (he may have rushed up into the air after a foe). */
	static final int RUSH_SAFE_TICKS = 60;

	/** Tier gates: abilities unlocked for tiers 1 and 2; tier 3 once ascended; tier 4 once Super 2 is learned. */
	static final int TIER1_UNLOCKED = 2;
	static final int TIER2_UNLOCKED = Mastery.ALL;
	static final int TIER4_FORMS = 2;

	/**
	 * The move he's throwing, for everyone's client: {@code serial << 16 | ticks << 8 | move}. The serial changes with
	 * every blow, so the same move twice still starts over. Not saved.
	 */
	public static final AttachmentType<Integer> MOVE = AttachmentRegistry.create(JaymosPowers.id("zfighter_combo"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	public static int move(int packed) {
		return packed & 0xFF;
	}

	public static int ticks(int packed) {
		return Math.max(1, packed >> 8 & 0xFF);
	}

	public static int serial(int packed) {
		return packed >>> 16;
	}

	/** Thrown with a leg (no hand swings in first person; a heavier sound). */
	public static boolean kick(int move) {
		return switch (move) {
			case RISING_KNEE, FRONT_KICK, ROUNDHOUSE, LEG_SWEEP, CRESCENT_KICK, AXE_KICK, SPIN_BACK_KICK, SPIN_HOOK_KICK, FLYING_KNEE,
				FLYING_DOUBLE_KICK -> true;
			default -> false;
		};
	}

	/** A string in progress. */
	private static final class Run {
		final int tier;
		/** The moves this string throws ({@link #STRINGS}, or {@link #KICK_STRINGS} with both arms broken). */
		final int[] string;
		int step;
		long stepAt;
		int length;
		boolean struck;
		/** Until when he catches his breath after a finisher (-1: he isn't). */
		long recoverUntil = -1L;
		@Nullable UUID target;
		int serial;
		/** A Verdant's blow stretching (the limb, {@link ZFighterStretchCombo}; -1: a plain blow), and whether it's snapped back. */
		int limb = -1;
		boolean snapped;
		/** The foe the finisher sent flying (chased after the breath), and until when he's rushing after it (-1: he isn't). */
		@Nullable UUID chase;
		long rushUntil = -1L;
		long rushedAt = -1L;

		Run(ServerPlayer player) {
			this.tier = tier(player);
			this.string = (Breaks.armless(player) ? KICK_STRINGS : STRINGS)[this.tier];
		}
	}

	private static final Map<UUID, Boolean> HELD = new HashMap<>();
	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private ZFighterCombo() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			HELD.remove(handler.player.getUUID());
			RUNS.remove(handler.player.getUUID());
		});
	}

	/** How far along his fists are (0-4): abilities unlocked, then ascension, then Super 2. */
	public static int tier(Player player) {
		int forms = ZFighterTraining.formsOpen(player);
		if (forms >= TIER4_FORMS) {
			return 4;
		}
		if (forms >= 1) {
			return 3;
		}
		int unlocked = Mastery.unlocked(player);
		return unlocked >= TIER2_UNLOCKED ? 2 : unlocked >= TIER1_UNLOCKED ? 1 : 0;
	}

	/** (0.13.34) How much faster than {@link #STEP_TICKS} his string runs: 1, plus his Finesse ({@link #FINESSE_SPEED}). */
	static double speed(Player player) {
		var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
		var finesse = speed == null ? null : speed.getModifier(FINESSE_MODIFIER);
		return 1.0 + Math.max(0.0, finesse == null ? 0.0 : finesse.amount()) * FINESSE_SPEED;
	}

	/** Whether a string is going (the old five-punch finisher stands aside). */
	static boolean running(ServerPlayer player) {
		return RUNS.containsKey(player.getUUID());
	}

	/** From the client: left mouse held (bare-handed, not at a block) or let go. */
	static void hold(ServerPlayer player, boolean down) {
		if (down) {
			HELD.put(player.getUUID(), true);
		} else {
			HELD.remove(player.getUUID());
		}
	}

	private static boolean able(ServerPlayer player, ZFighterPower.Live live) {
		return player.isAlive() && !player.isSpectator() && player.getMainHandItem().isEmpty() && !Powers.silenced(player)
			&& !ZFighterLineage.ape(player) && !live.guarding && !live.charging && live.spiritFrom < 0 && live.waveFrom < 0
			&& live.barrageFrom < 0 && !player.isSleeping()
			&& !ZFighterRush.active(player) && !ZFighterGrab.holding(player) && !ZFighterPoise.stunned(player);
	}

	/** Every tick, from {@link ZFighterPower#tick}. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		UUID id = player.getUUID();
		Run run = RUNS.get(id);
		boolean held = HELD.containsKey(id) && able(player, live);
		long now = player.level().getGameTime();
		if (run == null) {
			if (held) {
				run = new Run(player);
				RUNS.put(id, run);
				begin(player, run, 0, now);
			}
			return;
		}
		if (!held) {
			stop(player);
			return;
		}
		if (run.rushedAt >= 0 && now - run.rushedAt < RUSH_SAFE_TICKS) {
			player.resetFallDistance();
		}
		if (run.rushUntil >= 0) {
			LivingEntity foe = run.target == null ? null : player.level().getEntity(run.target) instanceof LivingEntity living ? living : null;
			if (foe == null || !valid(player, foe) || now >= run.rushUntil
				|| ZFighterImpact.rush(player, foe, RUSH_SPEED[run.tier], player.entityInteractionRange() * 0.6)) {
				// there (or it got away): the string starts on it
				run.rushUntil = -1L;
				begin(player, run, 0, now);
			}
			return;
		}
		if (run.recoverUntil >= 0) {
			if (now >= run.recoverUntil) {
				// a breath taken: the string starts over, as strong as he is now
				Run next = new Run(player);
				next.serial = run.serial;
				next.target = run.target;
				RUNS.put(id, next);
				LivingEntity chased = chased(player, run.chase);
				if (chased != null) {
					// after the foe he sent flying
					next.target = chased.getUUID();
					next.rushUntil = now + RUSH_TICKS;
					next.rushedAt = now;
					ZFighterImpact.rushSound(player);
				} else {
					begin(player, next, 0, now);
				}
			}
			return;
		}
		long age = now - run.stepAt;
		if (!run.struck && age >= run.length / 2) {
			run.struck = true;
			strike(player, live, run);
		}
		if (run.limb >= 0 && !run.snapped && age >= run.length * ZFighterStretchCombo.SNAP_AT) {
			run.snapped = true;
			ZFighterStretchCombo.snap(player);
		}
		if (age >= run.length) {
			int[] string = run.string;
			if (run.step >= string.length - 1) {
				run.recoverUntil = now + Math.max(MIN_STEP_TICKS, Math.round(RECOVER_TICKS[run.tier] / speed(player)));
			} else {
				begin(player, run, run.step + 1, now);
			}
		}
	}

	private static void begin(ServerPlayer player, Run run, int step, long now) {
		int[] string = run.string;
		boolean finisher = step == string.length - 1;
		run.step = step;
		run.stepAt = now;
		run.struck = false;
		run.snapped = false;
		run.length = Math.max(MIN_STEP_TICKS, (int)Math.round(STEP_TICKS[run.tier] * (finisher ? FINISHER_LENGTH : 1.0F) / speed(player)));
		run.serial = run.serial + 1 & 0x7FFF;
		int move = string[step];
		LivingEntity target = target(player, run);
		// a Verdant's blow may stretch out to the foe (a little slower, and different each time)
		run.limb = ZFighterStretchCombo.limb(player, target, move, step, finisher, REACH_BONUS);
		if (run.limb >= 0) {
			run.length = ZFighterStretchCombo.length(player, run.length);
			ZFighterStretchCombo.begin(player, run.limb, run.length, target);
		} else {
			ZFighterStretchCombo.clear(player);
		}
		player.setAttached(MOVE, run.serial << 16 | Math.min(255, run.length) << 8 | move);
		// the striking hand swings in first person too (kicks don't): the lead jab is the left
		if (!kick(move)) {
			InteractionHand hand = move == JAB ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			player.swing(hand, SwingAnimation.DEFAULT, true);
		}
		travel(player, run, move, target);
	}

	/**
	 * Every blow is stepped into: a little real travel toward the foe (or straight ahead, at nothing), a proper lunge
	 * after a foe a little out of reach, a hop for the flying moves. Never over a ledge; in the air only the lunge.
	 */
	private static void travel(ServerPlayer player, Run run, int move, @Nullable LivingEntity target) {
		boolean verdant = ZFighterStretchCombo.verdant(player);
		boolean grounded = player.onGround();
		double speed = grounded ? STEP_IN[move] : 0.0;
		Vec3 dir;
		if (target != null) {
			Vec3 to = target.position().subtract(player.position());
			dir = new Vec3(to.x, 0.0, to.z);
			double reach = player.entityInteractionRange();
			double gap = player.position().distanceTo(target.position()) - target.getBbWidth() * 0.5;
			if (run.limb >= 0) {
				// the arm does the reaching: only a lean into it
				speed = gap < STEP_MIN_GAP ? 0.0 : speed * ZFighterStretchCombo.STEP_SHARE;
			} else if (gap > reach * 0.6) {
				speed = Math.max(speed, (LUNGE_SPEED + LUNGE_PER_TIER * run.tier) * (verdant ? ZFighterStretchCombo.LUNGE_SHARE : 1.0));
			} else if (gap < STEP_MIN_GAP) {
				speed = 0.0;
			}
		} else {
			dir = Vec3.directionFromRotation(0.0F, player.getYRot());
			dir = new Vec3(dir.x, 0.0, dir.z);
		}
		double hop = !grounded ? 0.0 : move == DRAGON_FIST ? HOP_DRAGON : move == FLYING_KNEE || move == FLYING_DOUBLE_KICK ? HOP_FLYING : 0.0;
		if (dir.lengthSqr() < 1.0E-4) {
			speed = 0.0;
		} else {
			dir = dir.normalize();
		}
		if (speed > 0.0 && grounded && !footing(player, dir, speed * (hop > 0.0 ? HOP_PROBE : STEP_PROBE) + player.getBbWidth() * 0.5)) {
			speed = 0.0;
		}
		if (speed <= 0.0 && hop <= 0.0) {
			return;
		}
		player.setDeltaMovement(player.getDeltaMovement().add(dir.x * speed, hop, dir.z * speed));
		player.syncVelocity = true;
	}

	/** Ground to land on {@code ahead} blocks along {@code dir}: something solid underfoot there, at most a step down. */
	private static boolean footing(ServerPlayer player, Vec3 dir, double ahead) {
		ServerLevel level = player.level();
		double x = player.getX() + dir.x * ahead;
		double z = player.getZ() + dir.z * ahead;
		for (int down = 0; down <= LEDGE_DROP; down++) {
			BlockPos pos = BlockPos.containing(x, player.getY() - 0.5 - down, z);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** The blow lands (or whiffs) halfway through the move. */
	private static void strike(ServerPlayer player, ZFighterPower.Live live, Run run) {
		ServerLevel level = player.level();
		int[] string = run.string;
		int move = string[run.step];
		boolean finisher = run.step == string.length - 1;
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
		LivingEntity target = target(player, run);
		boolean stretched = run.limb >= 0;
		if (stretched && target != null && !ZFighterStretchCombo.reaches(player, target)) {
			target = null;
		} else if (!stretched && target != null && ZFighterStretchCombo.verdant(player)
			&& !player.isWithinEntityInteractionRange(target, REACH_BONUS + LUNGE_RANGE)) {
			// (a plain blow reaches no further than anyone's, however far he keeps hold of the foe)
			target = null;
		}
		if (target == null) {
			ZFighterStretchCombo.Reach reach = player.getAttached(ZFighterStretchCombo.REACH);
			if (stretched && reach != null) {
				ZFighterStretchCombo.whiff(player, reach);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.6F,
				kick(move) ? 0.8F : 1.3F);
			return;
		}
		run.target = target.getUUID();
		double fists = fists(player, move);
		float damage = (float)fists * (finisher ? FINISHER_SHARE[run.tier] : HIT_SHARE[run.tier] * MOVE_WEIGHT[move]);
		if (stretched && !finisher) {
			damage *= ZFighterStretchCombo.STRETCH_DAMAGE;
		}
		Vec3 look = player.getViewVector(1.0F);
		Vec3 at = target.getBoundingBox().getCenter();
		if (stretched) {
			// struck from afar: thrown along the line from him, not along wherever he's looking
			Vec3 line = at.subtract(player.getEyePosition());
			if (line.lengthSqr() > 1.0E-4) {
				look = line.normalize();
			}
		}
		target.setInvulnerableTime(0);
		boolean hurt = target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		Mastery.used(player);
		if (!hurt) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 1.0F);
			return;
		}
		if (move == CHI_PALM) {
			chi(level, live, at, look, finisher);
		}
		if (stretched) {
			ZFighterStretchCombo.impact(player, run.limb, at, finisher);
		}
		if (finisher) {
			if (move == DRAGON_FIST) {
				// up off the rising fist: its sliding stopped, then thrown high
				Vec3 motion = target.getDeltaMovement();
				target.setDeltaMovement(motion.x * REEL, Math.max(0.0, motion.y), motion.z * REEL);
				ZFighterKi.push(target, look, DRAGON_PUSH, DRAGON_LIFT);
				level.sendParticles(ZFighterPower.dust(live), true, false, at.x, at.y + 0.8, at.z, 20, 0.15, 1.0, 0.15, 0.05);
				level.sendParticles(ParticleTypes.CLOUD, true, false, player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.4, 0.05, 0.4, 0.05);
			} else {
				ZFighterKi.push(target, look, FINISHER_PUSH[run.tier], FINISHER_LIFT[run.tier]);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ZFighterPower.dust(live), true, false, at.x, at.y, at.z, 16 + 4 * run.tier, 0.3, 0.3, 0.3, 0.2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
			Shakes.felt(player, at, 12.0, FINISHER_SHAKE + FINISHER_SHAKE_PER_TIER * run.tier, Shakes.ticksFor(FINISHER_SHAKE));
			ZFighterSenses.impactFrame(level, at);
			ZFighterImpact.launched(target, player, (float)fists * SLAM_SHARE);
			run.chase = target.getUUID();
			run.target = null;
			return;
		}
		Vec3 motion = target.getDeltaMovement();
		if (move == LEG_SWEEP) {
			// tripped: swept off its feet, its sliding stopped, slowed hard while it gets up
			target.setDeltaMovement(motion.x * REEL, SWEEP_POP, motion.z * REEL);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SWEEP_TICKS, SWEEP_SLOW, false, false), player);
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, true, false, target.getX(), target.getY() + 0.2, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.8F);
		} else if (move == DOUBLE_PALM) {
			// a heavy shove: back it goes, and the next blow chases it
			target.setDeltaMovement(motion.x * REEL, Math.min(motion.y, 0.1), motion.z * REEL);
			ZFighterKi.push(target, look, PALM_SHOVE, PALM_SHOVE_LIFT);
			level.sendParticles(ParticleTypes.CLOUD, true, false, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.08);
		} else {
			// it reels where it stands, so the next blow lands; a knee pops it up
			double lift = move == RISING_KNEE ? KNEE_LIFT : move == FLYING_KNEE ? FLYING_KNEE_LIFT : Math.min(motion.y, 0.1);
			target.setDeltaMovement(motion.x * REEL, lift, motion.z * REEL);
		}
		target.syncVelocity = true;
		level.sendParticles(ParticleTypes.CRIT, true, false, at.x, at.y, at.z, 4 + run.tier, 0.2, 0.2, 0.2, 0.25);
		if (run.tier >= 3) {
			level.sendParticles(ZFighterPower.dust(live), true, false, at.x, at.y, at.z, 3, 0.15, 0.15, 0.15, 0.05);
		}
		level.playSound(null, at.x, at.y, at.z, kick(move) ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
			0.8F, 0.9F + 0.08F * run.tier + level.getRandom().nextFloat() * 0.15F);
		Shakes.send(player, HIT_SHAKE + HIT_SHAKE_PER_TIER * run.tier, at);
	}

	/** His fists' damage for {@code move}: a kick never loses any to broken arms (0.18.25). */
	static double fists(ServerPlayer player, int move) {
		double fists = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		double arms = kick(move) ? Breaks.armDamageShare(player) : 0.0;
		return arms < 0.0 && arms > -1.0 ? fists / (1.0 + arms) : fists;
	}

	/** The chi palm's puff of light off the open hand (bigger as a finisher). */
	private static void chi(ServerLevel level, ZFighterPower.Live live, Vec3 at, Vec3 look, boolean finisher) {
		Vec3 puff = at.subtract(look.scale(0.4));
		level.sendParticles(ParticleTypes.END_ROD, true, false, puff.x, puff.y, puff.z, finisher ? 14 : 6, 0.15, 0.15, 0.15, 0.06);
		level.sendParticles(ZFighterPower.dust(live), true, false, puff.x, puff.y, puff.z, finisher ? 10 : 5, 0.2, 0.2, 0.2, 0.02);
		level.playSound(null, puff.x, puff.y, puff.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, finisher ? 1.0F : 0.6F, 1.6F);
	}

	/** Who he's hitting: the foe he's been on if it's still in reach, else whatever is nearest in front of him. */
	private static @Nullable LivingEntity target(ServerPlayer player, Run run) {
		double keep = REACH_BONUS + LUNGE_RANGE;
		double far = far(player);
		// (0.17.45) locked on: he fights the one he locked
		LivingEntity locked = LockOn.target(player);
		if (locked != null && valid(player, locked) && player.isWithinEntityInteractionRange(locked, far)) {
			return locked;
		}
		if (ZFighterStretchCombo.verdant(player)) {
			// a Verdant keeps hold of a foe as far as his arms stretch
			keep = Math.max(keep, ZFighterStretchCombo.lockBonus(player));
			far = Math.max(far, ZFighterStretchCombo.STRETCH_RANGE);
		}
		if (run.target != null && player.level().getEntity(run.target) instanceof LivingEntity last && valid(player, last)
			&& player.isWithinEntityInteractionRange(last, keep)) {
			return last;
		}
		return inFront(player, far);
	}

	/** How far the hold combo reaches for a foe in front of him (plain reach, a little more, and the lunge). */
	public static double far(Player player) {
		return player.entityInteractionRange() + REACH_BONUS + LUNGE_RANGE;
	}

	/**
	 * (0.18.25) Whatever the hold combo would go after: the nearest foe in front of him within {@code far}, seen. Both
	 * sides: the client asks it too, so a race's left hold (Frost-born, Emberborn, Stormborn, Dawnborn) doesn't take the
	 * click from the combo just because the crosshair isn't on the foe.
	 */
	public static @Nullable LivingEntity inFront(Player player, double far) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity entity : player.level().getEntities(player, new AABB(eye, eye).inflate(far))) {
			if (!(entity instanceof LivingEntity living) || !valid(player, living)) {
				continue;
			}
			Vec3 to = living.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > far + living.getBbWidth() * 0.5 || distance < 1.0E-3) {
				continue;
			}
			double facing = to.scale(1.0 / distance).dot(look);
			if (facing < 0.55) {
				continue;
			}
			double score = distance * (2.0 - facing);
			if (score < bestScore && player.hasLineOfSight(living)) {
				best = living;
				bestScore = score;
			}
		}
		return best;
	}

	/** The foe he sent flying, if it's worth rushing after: alive, seen, not too far, and out of reach. */
	private static @Nullable LivingEntity chased(ServerPlayer player, @Nullable UUID id) {
		if (id == null || !(player.level().getEntity(id) instanceof LivingEntity foe) || !valid(player, foe)) {
			return null;
		}
		double distance = player.distanceTo(foe);
		if (distance > CHASE_RANGE || player.isWithinEntityInteractionRange(foe, REACH_BONUS) || !player.hasLineOfSight(foe)) {
			return null;
		}
		return foe;
	}

	private static boolean valid(Player player, LivingEntity living) {
		if (living == player || !living.isAlive() || !living.isAttackable() || living.isSpectator() || living.isPassengerOfSameVehicle(player)) {
			return false;
		}
		return !(living instanceof Player other) || player.canHarmPlayer(other);
	}

	/** Let go, or no longer able: the string ends where it is. */
	static void stop(ServerPlayer player) {
		RUNS.remove(player.getUUID());
		player.removeAttached(MOVE);
		ZFighterStretchCombo.clear(player);
	}

	/** The power went, or he died. */
	static void drop(ServerPlayer player) {
		HELD.remove(player.getUUID());
		stop(player);
	}
}

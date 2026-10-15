package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The hold combo (left mouse held, bare-handed): a run of punches and kicks thrown one after another on their own,
 * ending in a finisher that sends the foe flying. It grows with him ({@link #tier}): a fresh fighter throws three punches
 * and a palm strike; a seasoned one adds kicks, knees and spinning kicks, throws them faster and hits harder, up to a
 * ten-blow string ending in a flying double kick. Holding on, he starts again after a breath.
 * <p>
 * Each blow is a real punch (the fists' damage, ki per punch, TP, mastery); between blows the foe is held reeling so the
 * string lands. Everyone sees the moves ({@link #MOVE}); the client draws them ({@code ZFighterComboPose}).
 */
public final class ZFighterCombo {
	/** The moves, in the order the client's pose table lists them. */
	public static final int JAB = 0, CROSS = 1, HOOK = 2, UPPERCUT = 3, KNEE = 4, FRONT_KICK = 5, ROUNDHOUSE = 6, SPIN_KICK = 7,
		AXE_KICK = 8, PALM = 9, DRAGON_KICK = 10;
	public static final int MOVES = 11;

	/** Each tier's string, the last move its finisher. */
	static final int[][] STRINGS = {
		{JAB, CROSS, HOOK, PALM},
		{JAB, CROSS, FRONT_KICK, ROUNDHOUSE, PALM},
		{JAB, CROSS, KNEE, ROUNDHOUSE, AXE_KICK, SPIN_KICK},
		{JAB, CROSS, HOOK, FRONT_KICK, ROUNDHOUSE, KNEE, UPPERCUT, SPIN_KICK},
		{JAB, CROSS, HOOK, KNEE, FRONT_KICK, ROUNDHOUSE, AXE_KICK, UPPERCUT, SPIN_KICK, DRAGON_KICK},
	};
	/** Ticks a blow takes, by tier (the finisher takes {@link #FINISHER_LENGTH} times as long). */
	static final int[] STEP_TICKS = {8, 7, 6, 5, 4};
	static final float FINISHER_LENGTH = 1.6F;
	/** Ticks to catch his breath after a finisher before the string starts over, by tier. */
	static final int[] RECOVER_TICKS = {14, 12, 10, 9, 8};
	/** A blow hits for this share of his fists, by tier... */
	static final float[] HIT_SHARE = {0.8F, 0.9F, 1.0F, 1.1F, 1.2F};
	/** ...times the move's own weight (punches 1, kicks harder). */
	static final float[] MOVE_WEIGHT = {1.0F, 1.0F, 1.1F, 1.2F, 1.15F, 1.25F, 1.3F, 1.4F, 1.35F, 1.0F, 1.0F};
	/** The finisher hits for this many fists, by tier, and throws the foe this far (horizontal, up). */
	static final float[] FINISHER_SHARE = {1.6F, 1.8F, 2.1F, 2.4F, 3.0F};
	static final double[] FINISHER_PUSH = {1.4, 1.6, 1.9, 2.2, 2.7};
	static final double[] FINISHER_LIFT = {0.4, 0.45, 0.5, 0.55, 0.7};
	/** An uppercut pops the foe up this much. */
	static final double UPPERCUT_LIFT = 0.42;
	/** Reach past his own interaction range, and how far he steps in after a foe a little out of reach. */
	static final double REACH_BONUS = 0.3;
	static final double LUNGE_RANGE = 2.5;
	static final double LUNGE_SPEED = 0.28;
	static final double LUNGE_PER_TIER = 0.05;
	/** How much of a struck foe's sliding is left between blows (it reels on the spot). */
	static final double REEL = 0.15;
	/** Food each blow costs. */
	static final float EXHAUSTION = 0.06F;
	/** Screen shake: each blow (his own screen), and the finisher (everyone near). */
	static final float HIT_SHAKE = 0.12F;
	static final float HIT_SHAKE_PER_TIER = 0.03F;
	static final float FINISHER_SHAKE = 0.6F;
	static final float FINISHER_SHAKE_PER_TIER = 0.15F;

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

	public static boolean kick(int move) {
		return move == KNEE || move == FRONT_KICK || move == ROUNDHOUSE || move == SPIN_KICK || move == AXE_KICK || move == DRAGON_KICK;
	}

	/** A string in progress. */
	private static final class Run {
		final int tier;
		int step;
		long stepAt;
		int length;
		boolean struck;
		/** Until when he catches his breath after a finisher (-1: he isn't). */
		long recoverUntil = -1L;
		@Nullable UUID target;
		int serial;

		Run(int tier) {
			this.tier = tier;
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
			&& live.barrageFrom < 0 && !player.isSleeping();
	}

	/** Every tick, from {@link ZFighterPower#tick}. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		UUID id = player.getUUID();
		Run run = RUNS.get(id);
		boolean held = HELD.containsKey(id) && able(player, live);
		long now = player.level().getGameTime();
		if (run == null) {
			if (held) {
				run = new Run(tier(player));
				RUNS.put(id, run);
				begin(player, run, 0, now);
			}
			return;
		}
		if (!held) {
			stop(player);
			return;
		}
		if (run.recoverUntil >= 0) {
			if (now >= run.recoverUntil) {
				// a breath taken: the string starts over, as strong as he is now
				Run next = new Run(tier(player));
				next.serial = run.serial;
				next.target = run.target;
				RUNS.put(id, next);
				begin(player, next, 0, now);
			}
			return;
		}
		long age = now - run.stepAt;
		if (!run.struck && age >= run.length / 2) {
			run.struck = true;
			strike(player, live, run);
		}
		if (age >= run.length) {
			int[] string = STRINGS[run.tier];
			if (run.step >= string.length - 1) {
				run.recoverUntil = now + RECOVER_TICKS[run.tier];
			} else {
				begin(player, run, run.step + 1, now);
			}
		}
	}

	private static void begin(ServerPlayer player, Run run, int step, long now) {
		int[] string = STRINGS[run.tier];
		boolean finisher = step == string.length - 1;
		run.step = step;
		run.stepAt = now;
		run.struck = false;
		run.length = Math.round(STEP_TICKS[run.tier] * (finisher ? FINISHER_LENGTH : 1.0F));
		run.serial = run.serial + 1 & 0x7FFF;
		int move = string[step];
		player.setAttached(MOVE, run.serial << 16 | Math.min(255, run.length) << 8 | move);
		// the punching hand swings in first person too (kicks don't)
		if (!kick(move)) {
			InteractionHand hand = move == JAB || move == HOOK ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			player.swing(hand, SwingAnimation.DEFAULT, true);
		}
		// a step in after a foe just out of reach
		LivingEntity target = target(player, run);
		if (target != null) {
			double reach = player.entityInteractionRange();
			double gap = player.position().distanceTo(target.position()) - target.getBbWidth() * 0.5;
			if (gap > reach * 0.6) {
				Vec3 dir = target.position().subtract(player.position());
				dir = new Vec3(dir.x, 0.0, dir.z);
				if (dir.lengthSqr() > 1.0E-4) {
					double speed = LUNGE_SPEED + LUNGE_PER_TIER * run.tier + (move == DRAGON_KICK ? 0.4 : 0.0);
					dir = dir.normalize().scale(speed);
					player.setDeltaMovement(player.getDeltaMovement().add(dir.x, move == DRAGON_KICK ? 0.15 : 0.0, dir.z));
					player.syncVelocity = true;
				}
			}
		}
	}

	/** The blow lands (or whiffs) halfway through the move. */
	private static void strike(ServerPlayer player, ZFighterPower.Live live, Run run) {
		ServerLevel level = player.level();
		int[] string = STRINGS[run.tier];
		int move = string[run.step];
		boolean finisher = run.step == string.length - 1;
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
		LivingEntity target = target(player, run);
		if (target == null) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.6F,
				kick(move) ? 0.8F : 1.3F);
			return;
		}
		run.target = target.getUUID();
		double fists = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		float damage = (float)fists * (finisher ? FINISHER_SHARE[run.tier] : HIT_SHARE[run.tier] * MOVE_WEIGHT[move]);
		Vec3 look = player.getViewVector(1.0F);
		Vec3 at = target.getBoundingBox().getCenter();
		target.setInvulnerableTime(0);
		boolean hurt = target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		Mastery.used(player);
		if (!hurt) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 1.0F);
			return;
		}
		if (finisher) {
			ZFighterKi.push(target, look, FINISHER_PUSH[run.tier], FINISHER_LIFT[run.tier]);
			level.sendParticles(ParticleTypes.EXPLOSION, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ZFighterPower.dust(live), true, false, at.x, at.y, at.z, 16 + 4 * run.tier, 0.3, 0.3, 0.3, 0.2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
			Shakes.felt(player, at, 12.0, FINISHER_SHAKE + FINISHER_SHAKE_PER_TIER * run.tier, Shakes.ticksFor(FINISHER_SHAKE));
			run.target = null;
			return;
		}
		// it reels where it stands, so the next blow lands; an uppercut pops it up
		Vec3 motion = target.getDeltaMovement();
		target.setDeltaMovement(motion.x * REEL, move == UPPERCUT ? UPPERCUT_LIFT : Math.min(motion.y, 0.1), motion.z * REEL);
		target.syncVelocity = true;
		level.sendParticles(ParticleTypes.CRIT, true, false, at.x, at.y, at.z, 4 + run.tier, 0.2, 0.2, 0.2, 0.25);
		if (run.tier >= 3) {
			level.sendParticles(ZFighterPower.dust(live), true, false, at.x, at.y, at.z, 3, 0.15, 0.15, 0.15, 0.05);
		}
		level.playSound(null, at.x, at.y, at.z, kick(move) ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
			0.8F, 0.9F + 0.08F * run.tier + level.getRandom().nextFloat() * 0.15F);
		Shakes.send(player, HIT_SHAKE + HIT_SHAKE_PER_TIER * run.tier, at);
	}

	/** Who he's hitting: the foe he's been on if it's still in reach, else whatever is nearest in front of him. */
	private static @Nullable LivingEntity target(ServerPlayer player, Run run) {
		double reach = player.entityInteractionRange() + REACH_BONUS;
		if (run.target != null && player.level().getEntity(run.target) instanceof LivingEntity last && valid(player, last)
			&& player.isWithinEntityInteractionRange(last, REACH_BONUS + LUNGE_RANGE)) {
			return last;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double far = reach + LUNGE_RANGE;
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

	private static boolean valid(ServerPlayer player, LivingEntity living) {
		if (living == player || !living.isAlive() || !living.isAttackable() || living.isSpectator() || living.isPassengerOfSameVehicle(player)) {
			return false;
		}
		return !(living instanceof Player other) || player.canHarmPlayer(other);
	}

	/** Let go, or no longer able: the string ends where it is. */
	static void stop(ServerPlayer player) {
		RUNS.remove(player.getUUID());
		player.removeAttached(MOVE);
	}

	/** The power went, or he died. */
	static void drop(ServerPlayer player) {
		HELD.remove(player.getUUID());
		stop(player);
	}
}

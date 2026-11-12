package com.jaymospowers.boxing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.combat.Guard;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.wounds.Mortality;
import com.jaymospowers.power.darktide.DarktideStatus;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Jung) Boxing's evades and what they earn. While boxing ({@link Boxing#boxing}), the Dodge key throws the style's
 * evade in place of the roll (the client moves the boxer, as it does for a roll, and tells the server which way):
 * <ul>
 * <li><b>Arrow Slit</b>: a slip to the left or right, or a bob straight in.</li>
 * <li><b>Long Guard</b>: a pull, back out of reach, whichever way is held.</li>
 * <li><b>Bastion</b>: a shoulder roll in place, or a slip to either side.</li>
 * </ul>
 * A blow or shot that would land within {@link #EVADE_TICKS} of the press misses. One that would land within the
 * guard's perfect-parry window of it ({@link Guard#PERFECT_TICKS}, {@link Guard#LOCK_PERFECT_BONUS} more when the
 * boxer is locked onto the attacker) is a <b>perfect evade</b>, and the next punch inside {@link #STRONG_TICKS} is the
 * style's strong attack: the uppercut out of a bob, the pull counter out of a pull (the longest reach in boxing and a
 * hard shove), the roll straight out of a shoulder roll (the hardest hit). A shoulder roll that isn't perfect still
 * turns the blow to a glance ({@link #GLANCE} of it) and earns a weaker straight.
 *
 * <p>A strong attack earned by a perfect evade <b>blurs</b> a player it lands on ({@link #BLUR}, drawn by the client;
 * not again for {@link #BLUR_COOLDOWN}) and <b>dazes</b> a mob for as long (never the undead; a boss for
 * {@link #BOSS_DAZE} at most).
 *
 * <p>The <b>combo breaker</b>: {@link #BREAKER_HITS} punches landed in a row on a player or a boss, and they slip
 * back out of it; that boxer's punches can't reach them for {@link #BREAKER_TICKS}, and any blur on them clears.
 */
public final class BoxingEvades {
	// ---- tuning ----
	/** Ticks between evades, and how much earlier the server lets one through (clocks differ a little). */
	public static final int COOLDOWN = 14;
	static final int COOLDOWN_SLACK = 3;
	/**
	 * A blow that would land this many ticks or fewer after the press misses. Shorter than {@link #COOLDOWN}, so evades
	 * can't be chained into lasting immunity (the roll: 5 of every 10 ticks); a little longer than the perfect window
	 * locked on (7), so an evade can still be early or late and not perfect.
	 */
	public static final int EVADE_TICKS = 8;
	/** An evade's stamina (half-feathers; two to an icon) at level 1 and at the top level (two evades an icon). */
	public static final float COST_FIRST = 2.0F, COST_LAST = 1.0F;
	/** The evades' speed along the ground (blocks/tick), and the pull's small hop. The client moves the boxer. */
	public static final double SLIP_SPEED = 0.38, BOB_SPEED = 0.3, PULL_SPEED = 0.6, PULL_UP = 0.12;
	/** A strong attack waits this long (ticks) for its punch. */
	public static final int STRONG_TICKS = 30;
	/** A strong attack's stamina (half-feathers), at every level: one icon. */
	public static final float STRONG_COST = 2.0F;
	/** The uppercut, the pull counter, and the roll straight out of a perfect and out of a plain shoulder roll. */
	public static final float UPPERCUT_DAMAGE = 4.0F, PULL_COUNTER_DAMAGE = 3.0F, STRAIGHT_DAMAGE = 5.0F, STRAIGHT_WEAK_DAMAGE = 2.5F;
	/** The pull counter reaches this much further than a punch (blocks), and shoves this many times the usual knockback. */
	public static final double PULL_COUNTER_REACH = 1.0, PULL_COUNTER_KNOCKBACK = 1.8;
	/** The share of the usual knockback the other strong attacks keep. */
	static final double STRONG_KNOCKBACK = 0.6;
	/** How long each strong attack blurs or dazes (ticks): uppercut, pull counter, roll straight. */
	public static final int UPPERCUT_BLUR = 20, PULL_COUNTER_BLUR = 30, STRAIGHT_BLUR = 50;
	/** Once blurred or dazed, not again for this long (ticks, from when it began). */
	public static final int BLUR_COOLDOWN = 100;
	/** The longest a boss is dazed (ticks). */
	public static final int BOSS_DAZE = 30;
	/** The share of a blow a plain shoulder roll lets through. */
	public static final float GLANCE = 0.5F;
	/** The combo breaker: punches in a row, the pause (ticks) that ends a row, the shove away and how long the boxer can't reach them. */
	public static final int BREAKER_HITS = 6, BREAKER_RESET = 40, BREAKER_TICKS = 20;
	static final double BREAKER_SHOVE = 0.7, BREAKER_UP = 0.1;

	/** What the Dodge key did. */
	public enum Move { SLIP_LEFT, SLIP_RIGHT, BOB, PULL, ROLL }

	/** The way held as the Dodge key was pressed. */
	public static final int NONE = 0, LEFT = 1, RIGHT = 2, FORWARD = 3, BACK = 4;

	/** The evade of {@code style} with {@code direction} held (both sides). */
	public static Move move(Boxing.Style style, int direction) {
		return switch (style) {
			case ARROW_SLIT -> direction == LEFT ? Move.SLIP_LEFT : direction == RIGHT ? Move.SLIP_RIGHT : Move.BOB;
			case LONG_GUARD -> Move.PULL;
			case BASTION -> direction == LEFT ? Move.SLIP_LEFT : direction == RIGHT ? Move.SLIP_RIGHT : Move.ROLL;
		};
	}

	/** An evade's stamina (half-feathers) at {@code level}. */
	public static float costAt(int level) {
		int top = Boxing.MAX_LEVEL;
		return COST_FIRST + (COST_LAST - COST_FIRST) * (Math.clamp(level, 1, top) - 1) / (top - 1);
	}

	/** Client → server: the boxer evaded with {@code direction} held ({@link #NONE} to {@link #BACK}). */
	public record Evade(int direction) implements CustomPacketPayload {
		public static final Type<Evade> TYPE = new Type<>(JaymosPowers.id("boxing_evade"));
		public static final StreamCodec<ByteBuf, Evade> CODEC = ByteBufCodecs.VAR_INT.map(Evade::new, Evade::direction);

		@Override
		public Type<Evade> type() {
			return TYPE;
		}
	}

	/** A strong attack waiting for its punch: what it does. {@code blur}: ticks of blur or daze (0: none). */
	public record Strong(Boxing.Punch punch, float damage, double reach, double knockback, int blur) {
		/** Whether it is one of the three strong attacks (a hook out of a side slip is only the next punch, chosen). */
		public boolean heavy() {
			return damage > Boxing.PUNCH_DAMAGE;
		}
	}

	/** The game time until which a player's sight is blurred (told to them alone; not saved). */
	public static final AttachmentType<Long> BLUR = AttachmentRegistry.create(JaymosPowers.id("boxing_blur"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	private static final class State {
		/** The last evade: when, what, and whether it has earned its reward yet. */
		long evaded = Long.MIN_VALUE / 2;
		Move move = Move.BOB;
		boolean rewarded;
		/** A strong attack owed, and the game time it lapses. */
		@Nullable Strong strong;
		long strongUntil;
		/** The row of punches landed: on whom (entity id), how many, the last one's time. */
		int rowVictim = -1;
		int row;
		long rowLast;
		/** After the breaker: who slipped clear (entity id), and until when this boxer can't reach them. */
		int clearVictim = -1;
		long clearUntil;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** victim → the game time their last blur or daze began. */
	private static final Map<UUID, Long> BLURRED = new HashMap<>();
	/** True while a glanced blow's share is being dealt (so it isn't judged again). */
	private static boolean glancing;

	private BoxingEvades() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Evade.TYPE, Evade.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Evade.TYPE, (payload, ctx) -> evade(ctx.player(), payload.direction()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player && evades(player, source, amount)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			STATES.remove(handler.player.getUUID());
			BLURRED.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STATES.clear();
			BLURRED.clear();
		});
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	// ---- the evade ---------------------------------------------------------------------------------------------------

	private static void evade(ServerPlayer player, int direction) {
		Boxing.Style style = Boxing.style(player);
		if (style == null || !Boxing.boxing(player) || !player.isAlive() || direction < NONE || direction > BACK) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		State state = state(player);
		if (now - state.evaded < COOLDOWN - COOLDOWN_SLACK || !Boxing.spend(player, costAt(Boxing.level(player, style)))) {
			return;
		}
		state.evaded = now;
		state.move = move(style, direction);
		state.rewarded = false;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.6F, 1.6F);
	}

	/** Whether {@code player}'s evade makes this blow miss (or, for a plain shoulder roll, glance). */
	private static boolean evades(ServerPlayer player, DamageSource source, float amount) {
		if (glancing) {
			return false;
		}
		State state = STATES.get(player.getUUID());
		if (state == null) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long age = now - state.evaded;
		if (age < 0 || age > EVADE_TICKS || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		if (!(source.getDirectEntity() instanceof Projectile) && !(source.getDirectEntity() instanceof LivingEntity)) {
			return false;
		}
		LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
		int window = Guard.PERFECT_TICKS + (attacker != null && LockOn.target(player) == attacker ? Guard.LOCK_PERFECT_BONUS : 0);
		boolean perfect = age <= window;
		if (attacker instanceof ServerPlayer boxer) {
			// slipping a boxer's punch ends their row
			State theirs = STATES.get(boxer.getUUID());
			if (theirs != null && theirs.rowVictim == player.getId()) {
				theirs.row = 0;
			}
		}
		if (!state.rewarded) {
			state.rewarded = true;
			Strong strong = switch (state.move) {
				case BOB -> perfect ? new Strong(Boxing.Punch.UPPERCUT, UPPERCUT_DAMAGE, 0.0, STRONG_KNOCKBACK, UPPERCUT_BLUR) : null;
				case PULL -> perfect ? new Strong(Boxing.Punch.PULL_COUNTER, PULL_COUNTER_DAMAGE, PULL_COUNTER_REACH, PULL_COUNTER_KNOCKBACK, PULL_COUNTER_BLUR) : null;
				case ROLL -> perfect ? new Strong(Boxing.Punch.STRAIGHT, STRAIGHT_DAMAGE, 0.0, STRONG_KNOCKBACK, STRAIGHT_BLUR)
					: new Strong(Boxing.Punch.STRAIGHT, STRAIGHT_WEAK_DAMAGE, 0.0, STRONG_KNOCKBACK, 0);
				// a slip to the side sets up the hook on that side
				case SLIP_LEFT -> perfect ? new Strong(Boxing.Punch.LEAD_HOOK, Boxing.PUNCH_DAMAGE, 0.0, Boxing.PUNCH_KNOCKBACK, 0) : null;
				case SLIP_RIGHT -> perfect ? new Strong(Boxing.Punch.REAR_HOOK, Boxing.PUNCH_DAMAGE, 0.0, Boxing.PUNCH_KNOCKBACK, 0) : null;
			};
			if (strong != null) {
				state.strong = strong;
				state.strongUntil = now + STRONG_TICKS;
			}
			if (perfect) {
				level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 1.8F);
			}
		}
		if (state.move == Move.ROLL && !perfect) {
			// off the shoulder: a share of it gets through
			glancing = true;
			try {
				player.hurtServer(level, source, amount * GLANCE);
			} finally {
				glancing = false;
			}
		}
		return true;
	}

	// ---- the strong attacks ------------------------------------------------------------------------------------------

	/** The strong attack {@code boxer} is owed, taken (null if none, or it lapsed). */
	static @Nullable Strong takeStrong(ServerPlayer boxer, long now) {
		State state = STATES.get(boxer.getUUID());
		if (state == null || state.strong == null) {
			return null;
		}
		Strong strong = state.strong;
		state.strong = null;
		return now <= state.strongUntil ? strong : null;
	}

	/** A strong attack landed: the blur or the daze. */
	static void strongLanded(ServerPlayer boxer, LivingEntity target, Strong strong, long now) {
		if (strong.blur() <= 0) {
			return;
		}
		Long last = BLURRED.get(target.getUUID());
		if (last != null && now - last < BLUR_COOLDOWN) {
			return;
		}
		if (target instanceof ServerPlayer victim) {
			BLURRED.put(target.getUUID(), now);
			victim.setAttached(BLUR, now + strong.blur());
		} else if (!target.is(EntityTypeTags.UNDEAD)) {
			BLURRED.put(target.getUUID(), now);
			DarktideStatus.stun(target, Mortality.boss(target) ? Math.min(BOSS_DAZE, strong.blur()) : strong.blur());
		}
		if (BLURRED.size() > 512) {
			BLURRED.values().removeIf(began -> now - began >= BLUR_COOLDOWN);
		}
	}

	// ---- the combo breaker -------------------------------------------------------------------------------------------

	/** Whether {@code target} slipped clear of {@code boxer} with the combo breaker and can't be reached yet. */
	static boolean outOfReach(ServerPlayer boxer, LivingEntity target, long now) {
		State state = STATES.get(boxer.getUUID());
		return state != null && state.clearVictim == target.getId() && now < state.clearUntil;
	}

	/** A punch of {@code boxer}'s landed on {@code target}: the row grows, and at {@link #BREAKER_HITS} they slip out of it. */
	static void landed(ServerPlayer boxer, LivingEntity target, long now) {
		if (!(target instanceof Player) && !Mortality.boss(target)) {
			return;
		}
		State state = state(boxer);
		state.row = state.rowVictim == target.getId() && now - state.rowLast <= BREAKER_RESET ? state.row + 1 : 1;
		state.rowVictim = target.getId();
		state.rowLast = now;
		if (state.row < BREAKER_HITS) {
			return;
		}
		state.row = 0;
		state.clearVictim = target.getId();
		state.clearUntil = now + BREAKER_TICKS;
		Vec3 away = target.position().subtract(boxer.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? boxer.getLookAngle().multiply(1.0, 0.0, 1.0) : away;
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		double resist = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		target.setDeltaMovement(away.x * BREAKER_SHOVE * resist, BREAKER_UP * resist, away.z * BREAKER_SHOVE * resist);
		target.syncVelocity = true;
		if (target instanceof ServerPlayer victim) {
			victim.removeAttached(BLUR);
		}
		ServerLevel level = boxer.level();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.9F, 1.2F);
	}
}

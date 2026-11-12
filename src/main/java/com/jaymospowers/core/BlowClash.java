package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A clash of blows: the fists' answer to {@link BeamClash}. When two brutes throw a great blow at each other within a few
 * ticks (the Great Ape's charged fist, Bulwark's charge, the symbiote's big suit, a Z Fighter's finisher, a wild ape's
 * swing), neither lands: they lock where they meet, rooted and shaking, for {@link #LOCK_TICKS}, a ring of force between
 * them. Players can mash their attack (a swing, or the ape's punch key, {@link #tap}) to add to their side; the rest is
 * strength and what health is left. When the lock ends the stronger side stands and the other is staggered: knocked back,
 * slowed, bruised.
 * <p>
 * A brute calls {@link #brace} every tick while it winds up or rushes, and {@link #meets} at the moment its blow would
 * land (a rush calls it every tick instead): if another brute braced or swung at it, facing it, within {@link #WINDOW}
 * ticks and {@link #REACH} blocks, the clash starts and {@code meets} returns true (the caller skips its blow).
 * Server only. Player against player, player against a brute mob, mob against mob all work, wherever the mob's own blow
 * calls {@code meets}.
 */
public final class BlowClash {
	// ---- tuning ----
	/** A brace or swing counts for this many ticks. */
	public static final int WINDOW = 6;
	/** The two meet when within this far (blocks, plus half their widths) and each faces the other (cosine). */
	public static final double REACH = 4.0, FACING_COS = 0.5;
	/** The lock lasts this long (ticks) at most, and is not decided before {@link #MIN_TICKS}, unless one side leads by {@link #DECISIVE}. */
	public static final int LOCK_TICKS = 36, MIN_TICKS = 14;
	public static final float DECISIVE = 3.0F;
	/** A side's score: strength over {@link #STRENGTH_SCALE}, plus {@link #TAP_WEIGHT} a tap, plus {@link #HEALTH_WEIGHT} times its health share. */
	public static final float STRENGTH_SCALE = 20.0F, TAP_WEIGHT = 0.35F, HEALTH_WEIGHT = 0.8F, STRENGTH_CAP = 6.0F;
	/** Scores closer than this are a draw: both are pushed back. */
	public static final float DRAW = 0.15F;
	/** The one who gives: slowed (level, ticks), knocked back (horizontal, up) and bruised (damage). */
	public static final int STAGGER_SLOW = 3, STAGGER_TICKS = 50;
	public static final double KNOCK = 1.7, KNOCK_LIFT = 0.5, DRAW_KNOCK = 1.0;
	public static final float BRUISE = 3.0F;
	/** Neither can clash again for this long after (ticks). */
	public static final int REST_TICKS = 50;
	/** The lock's ring and shake: how often (ticks), and how hard the ground trembles. */
	public static final int FX_EVERY = 6;
	public static final float SHAKE = 0.5F, END_SHAKE = 1.4F;
	public static final int COLOR = 0xFFD890;

	private static final Identifier SLOW_ID = JaymosPowers.id("blow_clash_root");
	private static final Identifier JUMP_ID = JaymosPowers.id("blow_clash_jump");

	private record Intent(UUID id, Vec3 pos, Vec3 dir, float strength, long at, ServerLevel level) {
	}

	private static final class Clash {
		final ServerLevel level;
		final LivingEntity a, b;
		final float strengthA, strengthB;
		final long start;
		int tapsA, tapsB;
		boolean swungA, swungB;

		Clash(ServerLevel level, LivingEntity a, LivingEntity b, float strengthA, float strengthB, long start) {
			this.level = level;
			this.a = a;
			this.b = b;
			this.strengthA = strengthA;
			this.strengthB = strengthB;
			this.start = start;
		}

		float score(boolean first) {
			LivingEntity e = first ? a : b;
			float strength = Math.min(STRENGTH_CAP, (first ? strengthA : strengthB) / STRENGTH_SCALE);
			return strength + TAP_WEIGHT * (first ? tapsA : tapsB) + HEALTH_WEIGHT * Math.max(0.0F, e.getHealth() / Math.max(1.0F, e.getMaxHealth()));
		}
	}

	private static final Map<UUID, Intent> INTENTS = new HashMap<>();
	private static final List<Clash> CLASHES = new ArrayList<>();
	private static final Map<UUID, Clash> BY_ID = new HashMap<>();
	private static final Map<UUID, Long> RESTING = new HashMap<>();

	private BlowClash() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			INTENTS.clear();
			for (Clash clash : CLASHES) {
				free(clash.a);
				free(clash.b);
			}
			CLASHES.clear();
			BY_ID.clear();
			RESTING.clear();
		});
	}

	/** Whether {@code e} is locked in a clash now (it can't act, and its blows are lost). */
	public static boolean locked(LivingEntity e) {
		return BY_ID.containsKey(e.getUUID());
	}

	/** {@code e} is winding up a great blow toward {@code dir} (or rushing): a blow that meets it clashes. */
	public static void brace(LivingEntity e, Vec3 dir, float strength) {
		if (e.level() instanceof ServerLevel level) {
			INTENTS.put(e.getUUID(), new Intent(e.getUUID(), e.position(), flat(dir), strength, level.getGameTime(), level));
		}
	}

	/**
	 * {@code a} throws a great blow toward {@code dir}, {@code strength} strong (about its damage): true if it meets another
	 * brute's, and the two lock instead (the caller does not land its blow). Otherwise the swing is remembered for
	 * {@link #WINDOW} ticks, so the one swinging back a moment later clashes with it.
	 */
	public static boolean meets(LivingEntity a, Vec3 dir, float strength) {
		if (!(a.level() instanceof ServerLevel level)) {
			return false;
		}
		if (locked(a)) {
			return true;
		}
		long now = level.getGameTime();
		Intent mine = new Intent(a.getUUID(), a.position(), flat(dir), strength, now, level);
		if (now >= RESTING.getOrDefault(a.getUUID(), 0L)) {
			for (Intent other : INTENTS.values()) {
				if (other.id().equals(a.getUUID()) || other.level() != level || now - other.at() > WINDOW) {
					continue;
				}
				if (!(level.getEntity(other.id()) instanceof LivingEntity b) || !b.isAlive() || locked(b) || a.isAlliedTo(b)
					|| now < RESTING.getOrDefault(b.getUUID(), 0L)) {
					continue;
				}
				Vec3 toB = b.position().subtract(a.position()).multiply(1.0, 0.0, 1.0);
				double reach = REACH + (a.getBbWidth() + b.getBbWidth()) * 0.5;
				if (toB.lengthSqr() < 1.0E-4 || toB.length() > reach || Math.abs(a.getY() - b.getY()) > 2.5 + a.getBbHeight() * 0.5) {
					continue;
				}
				Vec3 n = toB.normalize();
				if (mine.dir().dot(n) < FACING_COS || other.dir().dot(n.scale(-1.0)) < FACING_COS) {
					continue;
				}
				INTENTS.remove(a.getUUID());
				INTENTS.remove(b.getUUID());
				start(level, a, b, strength, other.strength(), now);
				return true;
			}
		}
		INTENTS.put(a.getUUID(), mine);
		return false;
	}

	/** One mash of a player's attack while locked (a swing, or the ape's punch key). */
	public static void tap(ServerPlayer player) {
		Clash clash = BY_ID.get(player.getUUID());
		if (clash != null) {
			if (clash.a == player) {
				clash.tapsA++;
			} else {
				clash.tapsB++;
			}
		}
	}

	private static Vec3 flat(Vec3 dir) {
		Vec3 f = new Vec3(dir.x, 0.0, dir.z);
		return f.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : f.normalize();
	}

	private static void start(ServerLevel level, LivingEntity a, LivingEntity b, float strengthA, float strengthB, long now) {
		Clash clash = new Clash(level, a, b, strengthA, strengthB, now);
		CLASHES.add(clash);
		BY_ID.put(a.getUUID(), clash);
		BY_ID.put(b.getUUID(), clash);
		hold(a);
		hold(b);
		Vec3 mid = middle(clash);
		MagicFx.ring(level, mid, 3.0F, COLOR);
		MagicFx.blast(level, mid, 2.0F, COLOR);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0F, 0.6F);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2F, 0.5F);
		Shakes.around(level, mid, 32.0, END_SHAKE);
		for (LivingEntity e : new LivingEntity[] {a, b}) {
			if (e instanceof ServerPlayer player) {
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.blow_clash")
					.withStyle(net.minecraft.ChatFormatting.GOLD));
			}
		}
	}

	private static Vec3 middle(Clash clash) {
		return clash.a.getBoundingBox().getCenter().add(clash.b.getBoundingBox().getCenter()).scale(0.5);
	}

	private static void tick() {
		Iterator<Clash> it = CLASHES.iterator();
		while (it.hasNext()) {
			Clash clash = it.next();
			ServerLevel level = clash.level;
			long age = level.getGameTime() - clash.start;
			if (!clash.a.isAlive() || !clash.b.isAlive() || clash.a.level() != level || clash.b.level() != level) {
				finish(clash, 0);
				it.remove();
				continue;
			}
			hold(clash.a);
			hold(clash.b);
			for (LivingEntity e : new LivingEntity[] {clash.a, clash.b}) {
				// a swing begun since last tick is a mash
				if (e instanceof ServerPlayer player) {
					boolean now = player.isSwinging();
					boolean was = clash.a == e ? clash.swungA : clash.swungB;
					if (now && !was) {
						tap(player);
					}
					if (clash.a == e) {
						clash.swungA = now;
					} else {
						clash.swungB = now;
					}
				}
			}
			Vec3 mid = middle(clash);
			if (age % FX_EVERY == 0) {
				MagicFx.burst(level, mid.x, mid.y, mid.z, COLOR, 1.2F, 12, 0.5, 0.5, 0.5);
				level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0F, 0.5F + level.getRandom().nextFloat() * 0.3F);
				Shakes.around(level, mid, 24.0, SHAKE);
			}
			float lead = clash.score(true) - clash.score(false);
			if (age >= LOCK_TICKS || age >= MIN_TICKS && Math.abs(lead) >= DECISIVE) {
				finish(clash, lead);
				it.remove();
			}
		}
	}

	/** It ends: {@code lead} above 0 is the first side's win, below the second's, within {@link #DRAW} a draw (0 also when one side is gone). */
	private static void finish(Clash clash, float lead) {
		free(clash.a);
		free(clash.b);
		BY_ID.remove(clash.a.getUUID());
		BY_ID.remove(clash.b.getUUID());
		ServerLevel level = clash.level;
		long rest = level.getGameTime() + REST_TICKS;
		RESTING.put(clash.a.getUUID(), rest);
		RESTING.put(clash.b.getUUID(), rest);
		if (!clash.a.isAlive() || !clash.b.isAlive()) {
			return;
		}
		Vec3 mid = middle(clash);
		MagicFx.ring(level, mid, 5.0F, COLOR);
		MagicFx.blast(level, mid, 3.0F, COLOR);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.7F);
		Shakes.around(level, mid, 36.0, END_SHAKE);
		if (Math.abs(lead) < DRAW) {
			knock(clash.a, clash.b, DRAW_KNOCK, null);
			knock(clash.b, clash.a, DRAW_KNOCK, null);
			return;
		}
		LivingEntity winner = lead > 0 ? clash.a : clash.b;
		LivingEntity loser = lead > 0 ? clash.b : clash.a;
		loser.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, STAGGER_SLOW, false, false));
		knock(loser, winner, KNOCK, winner);
		if (winner instanceof ServerPlayer player) {
			Shakes.send(player, 0.6F, mid);
		}
	}

	private static void knock(LivingEntity from, LivingEntity away, double force, @Nullable LivingEntity by) {
		Vec3 dir = from.position().subtract(away.position()).multiply(1.0, 0.0, 1.0);
		dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
		double resist = 1.0 - from.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		from.setDeltaMovement(dir.x * force * resist, KNOCK_LIFT * resist, dir.z * force * resist);
		from.syncVelocity = true;
		from.needsSync = true;
		if (by != null && from.level() instanceof ServerLevel level) {
			from.setInvulnerableTime(0);
			from.hurtServer(level, by instanceof Player p ? level.damageSources().playerAttack(p) : level.damageSources().mobAttack(by), BRUISE);
		}
	}

	/** Pins {@code e} where it stands this tick. */
	private static void hold(LivingEntity e) {
		if (e instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (e instanceof ServerPlayer player) {
			root(player, true);
		}
		Vec3 motion = e.getDeltaMovement();
		e.setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
		e.syncVelocity = true;
	}

	private static void free(LivingEntity e) {
		if (e instanceof ServerPlayer player) {
			root(player, false);
		}
	}

	private static void root(ServerPlayer player, boolean on) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (speed != null) {
			speed.removeModifier(SLOW_ID);
			if (on) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		if (jump != null) {
			jump.removeModifier(JUMP_ID);
			if (on) {
				jump.addOrUpdateTransientModifier(new AttributeModifier(JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}
}

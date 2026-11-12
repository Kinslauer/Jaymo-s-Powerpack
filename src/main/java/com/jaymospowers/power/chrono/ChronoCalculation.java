package com.jaymospowers.power.chrono;

import com.jaymospowers.combat.Guard;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Wounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Calculation: a Chrono reads a fight the way a clock is read, and a clean read feeds a bar.
 * <ul>
 * <li><b>Feeding it:</b> a perfect parry ({@link Guard#PARRIED}), a parry, a blow that passed through you in Time Flow
 * ({@link ChronoFlow}) and a counter there each add a share ({@link #FEED_PERFECT} and the rest). It fades if left
 * alone ({@link #DECAY_AFTER}).</li>
 * <li><b>Full bar:</b> every blow you land is on a weak point: {@link #WEAK_BONUS} more damage (read by
 * {@code ChronoRunBlowMixin}) and the hit may open the creature's most hurt limb ({@link #WEAK_BLEED_CHANCE}).</li>
 * <li><b>Spending it (sneak + G, from {@link #MIN_TO_SPEND} of the bar):</b> for a while every blow and shot aimed at
 * you misses: the damage is cancelled and you slip a step aside. Falls, fire, magic and explosions are not
 * dodged. The longer the bar, the longer the dodging ({@link #DODGE_MIN_TICKS} to {@link #DODGE_MAX_TICKS}).</li>
 * </ul>
 * The bar is drawn on Chrono's meters ({@link ChronoMeters#CALC}, {@link ChronoMeters#CALC_RUN}).
 */
public final class ChronoCalculation {
	// ---- tuning ----
	/** Shares of the bar (0-1) a perfect parry, a parry, a blow passing through you and a counter give. */
	static final float FEED_PERFECT = 0.34F, FEED_PARRY = 0.12F, FEED_PASS = 0.2F, FEED_COUNTER = 0.15F;
	/** The bar starts to fade this many ticks after it was last fed, and empties over {@link #DECAY_TICKS}. */
	static final int DECAY_AFTER = 300, DECAY_TICKS = 600;
	/** The least share that can be spent, and how long the dodging lasts from that to a full bar (ticks). */
	static final float MIN_TO_SPEND = 0.4F;
	static final int DODGE_MIN_TICKS = 60, DODGE_MAX_TICKS = 180;
	/** Weak points (full bar): extra damage share on a blow, and the chance it opens the most hurt limb. */
	static final float WEAK_BONUS = 0.35F, WEAK_BLEED_CHANCE = 0.5F;
	/** A limb counts as hurt below this share of its health. */
	static final float HURT_LIMB = 0.85F;
	/** The side-step: how far it carries you sideways (blocks a tick of speed). */
	static final double STEP_PUSH = 0.45;
	static final int COLOR = 0xBFE8FF;

	private static final class Bar {
		float share;
		long lastFed;
		long activeUntil;
		long activeTotal;
		boolean wasFull;
	}

	private static final Map<UUID, Bar> BARS = new HashMap<>();

	private ChronoCalculation() {
	}

	static void init() {
		Guard.PARRIED.register((player, attacker, perfect) -> feed(player, perfect ? FEED_PERFECT : FEED_PARRY));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ChronoCalculation::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ChronoCalculation::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(ChronoCalculation::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BARS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> BARS.clear());
	}

	private static boolean chrono(ServerPlayer player) {
		return Powers.has(player, ChronoPower.INSTANCE) && !com.jaymospowers.core.Mastery.starterOnly(player);
	}

	/** Adds {@code amount} (a share of the bar) for a clean read. Nothing while dodging, or for a starter. */
	static void feed(ServerPlayer player, float amount) {
		if (!chrono(player) || !player.isAlive()) {
			return;
		}
		Bar bar = BARS.computeIfAbsent(player.getUUID(), id -> new Bar());
		ServerLevel level = player.level();
		if (level.getGameTime() < bar.activeUntil) {
			return;
		}
		bar.share = Math.min(1.0F, bar.share + amount);
		bar.lastFed = level.getGameTime();
		show(player, bar);
		if (bar.share >= 1.0F && !bar.wasFull) {
			bar.wasFull = true;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.7F);
			MagicFx.ring(level, player.position().add(0.0, 1.0, 0.0), 1.6F, COLOR);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_calc.full"));
		}
	}

	/** Whether the bar is full: every blow lands on a weak point. */
	public static boolean full(ServerPlayer player) {
		Bar bar = BARS.get(player.getUUID());
		return bar != null && bar.share >= 1.0F && player.level().getGameTime() >= bar.activeUntil;
	}

	/** Extra damage share of a blow struck by the source's attacker: {@link #WEAK_BONUS} while their bar is full. Read by {@code ChronoRunBlowMixin}. */
	public static float weakBonus(DamageSource source) {
		return source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() != null && full(player) ? WEAK_BONUS : 0.0F;
	}

	/** Sneak + G: spends the bar on dodging. False when it holds too little (Stasis Field then goes ahead as usual). */
	static boolean spend(ServerPlayer player) {
		Bar bar = BARS.get(player.getUUID());
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (bar == null || bar.share < MIN_TO_SPEND || now < bar.activeUntil || !player.isAlive()) {
			return false;
		}
		long ticks = Math.round(Mth.lerp((bar.share - MIN_TO_SPEND) / (1.0F - MIN_TO_SPEND), DODGE_MIN_TICKS, DODGE_MAX_TICKS));
		bar.activeUntil = now + ticks;
		bar.activeTotal = ticks;
		bar.share = 0.0F;
		bar.wasFull = false;
		show(player, bar);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.9F);
		MagicFx.ring(level, player.position().add(0.0, 0.2, 0.0), 2.2F, COLOR);
		return true;
	}

	static void forget(ServerPlayer player) {
		BARS.remove(player.getUUID());
		ChronoMeters.set(player, ChronoMeters.CALC, -1.0F);
		ChronoMeters.set(player, ChronoMeters.CALC_RUN, -1.0F);
	}

	private static void show(ServerPlayer player, Bar bar) {
		long now = player.level().getGameTime();
		if (now < bar.activeUntil) {
			ChronoMeters.set(player, ChronoMeters.CALC, -1.0F);
			ChronoMeters.set(player, ChronoMeters.CALC_RUN, (bar.activeUntil - now) / (float)Math.max(1L, bar.activeTotal));
		} else {
			ChronoMeters.set(player, ChronoMeters.CALC_RUN, -1.0F);
			ChronoMeters.set(player, ChronoMeters.CALC, bar.share > 0.0F ? bar.share : -1.0F);
		}
	}

	private static void tick(MinecraftServer server) {
		if (BARS.isEmpty()) {
			return;
		}
		for (var it = BARS.entrySet().iterator(); it.hasNext();) {
			var entry = it.next();
			Bar bar = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !chrono(player)) {
				it.remove();
				continue;
			}
			long now = player.level().getGameTime();
			if (now < bar.activeUntil) {
				if (player.tickCount % 2 == 0) {
					show(player, bar);
				}
				if (player.tickCount % 8 == 0) {
					MagicFx.burst(player.level(), player.getX(), player.getY() + 1.0, player.getZ(), COLOR, 0.5F, 4, 0.3, 0.6, 0.3);
				}
				continue;
			}
			if (bar.activeUntil > 0) {
				bar.activeUntil = 0;
				show(player, bar);
			}
			if (bar.share > 0.0F && now - bar.lastFed > DECAY_AFTER) {
				bar.share = Math.max(0.0F, bar.share - 1.0F / DECAY_TICKS);
				if (bar.share < 1.0F) {
					bar.wasFull = false;
				}
				if (player.tickCount % 5 == 0 || bar.share <= 0.0F) {
					show(player, bar);
				}
				if (bar.share <= 0.0F) {
					it.remove();
				}
			}
		}
	}

	// ---------------------------------------------------------------- dodging and weak points

	/** True for a blow, a shot or a sting aimed at someone: not a fall, fire, drowning, lightning, explosion or magic. */
	private static boolean aimed(DamageSource source) {
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
			|| source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_LIGHTNING)) {
			return false;
		}
		return source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
			|| source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.STING) || source.is(DamageTypes.SPIT);
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || BARS.isEmpty()) {
			return true;
		}
		Bar bar = BARS.get(player.getUUID());
		ServerLevel level = player.level();
		if (bar == null || level.getGameTime() >= bar.activeUntil || !aimed(source) || source.getEntity() == player) {
			return true;
		}
		sidestep(player, level, source);
		return false;
	}

	/** The miss: a step aside, away from where the blow came from, with a streak left where you were. */
	private static void sidestep(ServerPlayer player, ServerLevel level, DamageSource source) {
		Vec3 from = source.getDirectEntity() != null ? source.getDirectEntity().position() : player.position();
		Vec3 away = player.position().subtract(from);
		Vec3 flat = new Vec3(away.x, 0.0, away.z);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = new Vec3(-player.getLookAngle().x, 0.0, -player.getLookAngle().z);
		}
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
		double sign = player.getRandom().nextBoolean() ? 1.0 : -1.0;
		Vec3 side = new Vec3(-flat.z * sign, 0.0, flat.x * sign);
		Vec3 was = player.position().add(0.0, 1.0, 0.0);
		player.setDeltaMovement(player.getDeltaMovement().add(side.scale(STEP_PUSH)));
		player.syncVelocity = true;
		MagicFx.stroke(level, was, was.add(side.scale(1.4)), 0.35F, COLOR);
		MagicFx.burst(level, was.x, was.y, was.z, COLOR, 0.6F, 6, 0.3, 0.5, 0.3);
		player.connection.send(new ClientboundSoundPacket(net.minecraft.core.Holder.direct(SoundEvents.ILLUSIONER_MIRROR_MOVE), SoundSource.PLAYERS, player.getX(), player.getY(),
			player.getZ(), 0.9F, 1.8F, level.getRandom().nextLong()));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.9F);
	}

	/** A blow landed with the bar full opens the most hurt limb, now and then. */
	private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (blocked || damageTaken <= 0.0F || !(source.getEntity() instanceof ServerPlayer player) || victim == player
			|| source.getDirectEntity() == null || !full(player) || player.getRandom().nextFloat() >= WEAK_BLEED_CHANCE) {
			return;
		}
		BodyPlan plan = Wounds.plan(victim);
		if (plan == null || plan == BodyPlan.SIMPLE) {
			return;
		}
		BodyPart worst = null;
		float lowest = HURT_LIMB;
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			float share = Wounds.fraction(victim, part);
			if (!part.vital() && share < lowest) {
				lowest = share;
				worst = part;
			}
		}
		if (worst != null && victim.level() instanceof ServerLevel level) {
			Bleeding.open(victim, worst, false);
			MagicFx.burst(level, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), COLOR, 0.5F, 5, 0.3, 0.4, 0.3);
		}
	}
}

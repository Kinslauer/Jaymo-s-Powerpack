package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.AbilityPower;
import com.jaymospowers.core.fx.MagicFx;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.Nullable;

/**
 * Slow time at speed (hold the slow-time key while a speedster): the world drops to a crawl while you move normally, for
 * as long as a meter lasts ({@link #METER_TICKS}; it fills back over {@link #REFILL_TICKS} when you let go).
 *
 * <p>Done the way {@link ChronoTimeStop} does its stop, on vanilla's tick manager: the server is frozen (vanilla never
 * freezes players, so the runner and every other player keep moving at full rate) and one world tick is let through every
 * {@link #STEP_EVERY} ticks ({@code stepGameIfPaused}), so mobs, shots, fire and weather run at a fifth of their pace. A real
 * slowed tick rate would slow the runner's own movement too, so it is not used. Other players are not frozen either, so
 * those near you are dragged with Slowness instead. It never starts while a Time Stop, {@code /tick freeze} or a tick sprint
 * has the clock, only unfreezes what it froze itself, and ends if someone unfreezes, if you let go, run dry, stop being a
 * speedster, die or leave.
 *
 * <p>Andy (2026-11-25), on the Hand of God mod's super speed (ideas only; it is All Rights Reserved): "time manipulation
 * and speed has very good looking fx for chrono". While the world crawls, the runner's blows don't land at once: each is
 * held in time where it struck (a gold mark that stays on the body, a dull thud), up to {@link #MAX_HITS}, and they all
 * land together when time runs on again, with a burst on each body hit. Everyone near sees the world drained to old gold
 * with a slow heartbeat under it, and whatever moves leaves a trail ({@code ChronoSlowLook}, from {@link #SLOWING}).
 */
public final class ChronoSlowTime {
	// ---- tuning ----
	/** The meter: ticks of slow time a full one gives, ticks to refill it when not slowing, and the least it must hold to start (share). */
	static final int METER_TICKS = 140, REFILL_TICKS = 500;
	static final float MIN_TO_START = 0.15F;
	/** One world tick in this many gets through while time is slowed. */
	static final int STEP_EVERY = 5;
	/** Other players this close are dragged with Slowness (amplifier), refreshed each time. */
	static final double DRAG_RADIUS = 24.0;
	static final int DRAG_AMPLIFIER = 3;
	/** The most blows held in time at once; how often (ticks) the marks on held bodies are shown again; their colour. */
	static final int MAX_HITS = 48, MARK_EVERY = 10;
	static final int GOLD = 0xFFD27A;
	/** The held blows' landing can't throw a body faster than this (blocks/tick). */
	static final double MAX_RELEASE_SPEED = 2.5;

	/** On the runner while they slow the world, for every client (the gold grade, the heartbeat, the trails). */
	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Boolean> SLOWING =
		net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(JaymosPowers.id("chrono_slowing"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));

	private record Hit(net.minecraft.world.damagesource.DamageSource source, float amount) {
	}

	/** The runner's blows held in time, by body, and how many in all; true while they land (so they aren't held again). */
	private static final Map<net.minecraft.world.entity.LivingEntity, java.util.List<Hit>> BANKED = new java.util.LinkedHashMap<>();
	private static int hits;
	private static boolean releasing;

	/** Client to server: the slow-time key went down or up. */
	public record Hold(boolean down) implements CustomPacketPayload {
		public static final Type<Hold> TYPE = new Type<>(JaymosPowers.id("chrono_slow_time"));
		public static final StreamCodec<ByteBuf, Hold> CODEC = ByteBufCodecs.BOOL.map(Hold::new, Hold::down);

		@Override
		public Type<Hold> type() {
			return TYPE;
		}
	}

	/** The owner of the slow now, or null. Server thread only. */
	private static @Nullable UUID active;
	private static final Map<UUID, Float> METER = new HashMap<>();
	private static int stepIn;

	private ChronoSlowTime() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Hold.TYPE, Hold.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Hold.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (payload.down()) {
				start(player);
			} else if (player.getUUID().equals(active)) {
				end(player.level().getServer(), true);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(ChronoSlowTime::tick);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(ChronoSlowTime::allowDamage);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> end(server, true));
	}

	private static void start(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerTickRateManager ticks = server.tickRateManager();
		if (active != null || !ChronoSpeed.overclocked(player) || !player.isAlive() || ChronoTimeStop.running() || ticks.isFrozen() || ticks.isSprinting()
			|| METER.getOrDefault(player.getUUID(), 1.0F) < MIN_TO_START) {
			return;
		}
		active = player.getUUID();
		com.jaymospowers.core.trials.PowerTrials.count(player, "chrono.slow_time", 1);
		stepIn = STEP_EVERY;
		ticks.setFrozen(true);
		player.setAttached(SLOWING, Boolean.TRUE);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		MagicFx.ring(level, player.position().add(0.0, 0.2, 0.0), 3.0F, 0xBFE8FF);
	}

	private static void tick(MinecraftServer server) {
		ServerTickRateManager ticks = server.tickRateManager();
		UUID id = active;
		if (id != null) {
			ServerPlayer owner = server.getPlayerList().getPlayer(id);
			float meter = METER.getOrDefault(id, 1.0F) - 1.0F / METER_TICKS;
			if (!ticks.isFrozen()) {
				end(server, false); // someone else took the clock
			} else if (owner == null || !owner.isAlive() || owner.isSpectator() || !ChronoSpeed.overclocked(owner) || meter <= 0.0F) {
				METER.put(id, Math.max(0.0F, meter));
				end(server, true);
			} else {
				METER.put(id, meter);
				ChronoMeters.set(owner, ChronoMeters.SLOW, meter);
				if (owner.tickCount % MARK_EVERY == 0) {
					marks();
				}
				if (--stepIn <= 0) {
					stepIn = STEP_EVERY;
					ticks.stepGameIfPaused(1);
				}
				if (owner.tickCount % 5 == 0) {
					for (ServerPlayer other : owner.level().players()) {
						if (other != owner && other.distanceToSqr(owner) <= AbilityPower.size(owner, DRAG_RADIUS) * AbilityPower.size(owner, DRAG_RADIUS)) {
							other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, DRAG_AMPLIFIER, false, false, false));
						}
					}
				}
			}
			return;
		}
		// idle: every meter refills, and the owner's shows while it isn't full
		if (!METER.isEmpty()) {
			METER.replaceAll((uuid, share) -> Math.min(1.0F, share + 1.0F / REFILL_TICKS));
			for (Map.Entry<UUID, Float> e : METER.entrySet()) {
				ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
				if (player != null) {
					ChronoMeters.set(player, ChronoMeters.SLOW, e.getValue() < 1.0F && ChronoSpeed.overclocked(player) ? e.getValue() : -1.0F);
				}
			}
			METER.values().removeIf(share -> share >= 1.0F);
		}
	}

	private static void end(MinecraftServer server, boolean unfreeze) {
		UUID id = active;
		active = null;
		if (id == null) {
			return;
		}
		if (unfreeze && server.tickRateManager().isFrozen()) {
			server.tickRateManager().setFrozen(false);
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(id);
		if (owner != null) {
			owner.removeAttached(SLOWING);
			ServerLevel level = owner.level();
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		}
		land();
	}

	/** While time crawls the runner's blows are held where they struck; they land when it runs on ({@link #land}). */
	private static boolean allowDamage(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		UUID id = active;
		if (id == null || releasing || entity.getUUID().equals(id)) {
			return true;
		}
		net.minecraft.world.entity.Entity attacker = source.getEntity();
		if (attacker == null || !attacker.getUUID().equals(id) || hits >= MAX_HITS) {
			return true;
		}
		hits++;
		java.util.List<Hit> held = BANKED.computeIfAbsent(entity, e -> new java.util.ArrayList<>());
		held.add(new Hit(source, amount));
		if (entity.level() instanceof ServerLevel level) {
			// a dull thud, and the blow's mark held on the body
			net.minecraft.world.phys.Vec3 c = entity.getBoundingBox().getCenter();
			level.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.7F, 0.55F + hits * 0.01F);
			MagicFx.orb(level, c, mark(held.size()), GOLD, MARK_EVERY + 4);
			MagicFx.ring(level, c, attacker.position().subtract(entity.position()), 0.9F, GOLD, 8);
		}
		return false;
	}

	/** How big the gold mark on a body is, with this many blows held on it. */
	private static float mark(int count) {
		return Math.min(0.9F, 0.18F + count * 0.06F);
	}

	/** The held blows' marks, shown again (they'd fade on the clients, which keep their own time). */
	private static void marks() {
		for (Map.Entry<net.minecraft.world.entity.LivingEntity, java.util.List<Hit>> e : BANKED.entrySet()) {
			net.minecraft.world.entity.LivingEntity body = e.getKey();
			if (body.isAlive() && body.level() instanceof ServerLevel level) {
				MagicFx.orb(level, body.getBoundingBox().getCenter(), mark(e.getValue().size()), GOLD, MARK_EVERY + 4);
			}
		}
	}

	/** Time runs on: every held blow lands at once, a burst on each body hit. */
	private static void land() {
		if (BANKED.isEmpty()) {
			hits = 0;
			return;
		}
		Map<net.minecraft.world.entity.LivingEntity, java.util.List<Hit>> banked = new java.util.LinkedHashMap<>(BANKED);
		BANKED.clear();
		hits = 0;
		releasing = true;
		try {
			for (Map.Entry<net.minecraft.world.entity.LivingEntity, java.util.List<Hit>> e : banked.entrySet()) {
				net.minecraft.world.entity.LivingEntity body = e.getKey();
				if (body.isRemoved() || !(body.level() instanceof ServerLevel level)) {
					continue;
				}
				net.minecraft.world.phys.Vec3 c = body.getBoundingBox().getCenter();
				for (Hit hit : e.getValue()) {
					if (!body.isAlive()) {
						break;
					}
					body.setInvulnerableTime(0);
					body.hurtServer(level, hit.source(), hit.amount());
				}
				net.minecraft.world.phys.Vec3 push = body.getDeltaMovement();
				if (push.length() > MAX_RELEASE_SPEED) {
					body.setDeltaMovement(push.normalize().scale(MAX_RELEASE_SPEED));
				}
				body.needsSync = true;
				int n = e.getValue().size();
				MagicFx.blast(level, c, 0.8F + n * 0.25F, GOLD);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
				if (n >= 3) {
					level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
					com.jaymospowers.core.Shakes.around(level, c, 12.0, Math.min(1.5F, 0.3F + n * 0.1F));
				}
			}
		} finally {
			releasing = false;
		}
	}
}

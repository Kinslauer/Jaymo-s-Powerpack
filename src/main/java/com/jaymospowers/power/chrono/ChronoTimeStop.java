package com.jaymospowers.power.chrono;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Downed;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Time Stop, Timeless Chrono's second ascended ability (sneak + C): for {@link #DURATION} ticks the whole server stands
 * still, on vanilla's own tick freeze ({@code /tick freeze}, {@link ServerTickRateManager}). Mobs, projectiles in
 * flight, falling blocks, water, lava, fire, redstone, crops, the sun and the weather all stop, every client holds its
 * particles and animations mid-motion, and only the Chrono who stopped it moves and acts.
 *
 * <p>The tick freeze is one switch for the whole server (every dimension), so the stop is too; a radius isn't possible
 * on it. Vanilla never freezes players, so everyone else is held here: pinned where time caught them (no gravity, no
 * movement keys, so nothing pulls them back and they can't be kicked for floating), and they can't attack, mine, build,
 * use items or their powers ({@link #held}, through {@link StasisField#registerLocks} and a power silencer). Nothing
 * hurts them while held but the Chrono's own blows.
 *
 * <p>The Chrono's blows on anyone don't land at once: they pile up ({@link #MAX_HITS} at most) and all land when time
 * resumes, knockback and all (capped). Arrows and other shots they fire hang where they leave the bow, frozen like the
 * rest, and fly on when time resumes.
 *
 * <p>It always ends: when its time runs out, or the Chrono dies, goes down, logs out, loses the power or turns
 * spectator, or someone runs {@code /tick unfreeze} (or {@code /tick sprint}), and when the server stops. Only one stop
 * at a time: none starts while the server is frozen already (another Chrono's stop, or {@code /tick freeze}), and it
 * only unfreezes what it froze itself. The freeze isn't saved with the world, so a crash can't leave it stuck.
 */
public final class ChronoTimeStop {
	/** How long time stands still (ticks): 5 s. */
	static final int DURATION = 100;
	/** Its own cooldown, separate from Chrono Punch's: 3 minutes. */
	static final int COOLDOWN = 3600;
	/** Blows the Chrono can bank during one stop; past this, more swings do nothing. */
	static final int MAX_HITS = 64;
	/** Knockback released on a held creature is capped to this speed (blocks/tick), so a pile of blows can't fling it across the map. */
	static final double MAX_RELEASE_SPEED = 1.6;
	/** A held player drifting further than this (blocks, squared) from where time caught them is put back. */
	private static final double DRIFT_SQR = 0.04;

	/** The stop running now (only ever one), or null. Server thread only. */
	private static @Nullable Stop active;
	/** Each Chrono's time stop cooldown: the server tick it's ready at. */
	private static final Map<UUID, Integer> READY_AT = new HashMap<>();
	/** Set while the banked blows land, so they aren't banked again. */
	private static boolean releasing;

	private ChronoTimeStop() {
	}

	/** Called once from {@link ChronoPower#init()}. */
	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(ChronoTimeStopSync.TYPE, ChronoTimeStopSync.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(ChronoTimeStop::tick);
		// held players can't use their powers either
		Powers.addSilencer(ChronoTimeStop::held);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ChronoTimeStop::allowDamage);
		// someone joining mid-stop is caught in it too
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			Stop stop = active;
			if (stop != null && ServerPlayNetworking.canSend(handler.player, ChronoTimeStopSync.TYPE)) {
				ServerPlayNetworking.send(handler.player, new ChronoTimeStopSync(stop.ownerEntityId, stop.ticksLeft, DURATION));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (active != null) {
				end(server, false);
			}
			READY_AT.clear();
		});
	}

	/** Whether time is stopped right now by a Chrono (server side). */
	static boolean running() {
		return active != null;
	}

	/** Whether {@code player} is held by a stopped world: anyone but the Chrono who stopped it, and not a spectator. Server side. */
	static boolean held(Player player) {
		Stop stop = active;
		return stop != null && !player.getUUID().equals(stop.owner) && !player.isSpectator();
	}

	/** Ticks until {@code player}'s time stop is ready again. */
	static int cooldownLeft(ServerPlayer player) {
		Integer ready = READY_AT.get(player.getUUID());
		return ready == null ? 0 : Math.max(0, ready - player.level().getServer().getTickCount());
	}

	/** Sneak + C. Returns whether time stopped. */
	static boolean start(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		int left = cooldownLeft(player);
		if (left > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_time_stop_cooldown", (left + 19) / 20));
			return false;
		}
		ServerTickRateManager ticks = server.tickRateManager();
		if (active != null || ticks.isFrozen() || ticks.isSprinting()) {
			// another Chrono's stop, or /tick freeze or sprint: time is already someone else's
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_time_stop_taken"));
			return false;
		}
		Stop stop = new Stop(player.getUUID(), player.getId());
		active = stop;
		READY_AT.put(player.getUUID(), server.getTickCount() + COOLDOWN);
		ticks.setFrozen(true);
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (held(other)) {
				stop.hold(other);
			}
		}
		broadcast(server, new ChronoTimeStopSync(player.getId(), DURATION, DURATION));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.4F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		// these hang in the air for the whole stop (the clients' particles are frozen too)
		ChronoFx.particles(level, player, MagicFx.LIGHT, player.getX(), player.getY() + 1.0, player.getZ(), 60, 1.5, 1.0, 1.5, 0.3);
		return true;
	}

	private static void tick(MinecraftServer server) {
		Stop stop = active;
		if (stop == null) {
			return;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(stop.owner);
		if (!server.tickRateManager().isFrozen()) {
			end(server, false); // someone ran /tick unfreeze (or sprint): it's over, and not ours to unfreeze
			return;
		}
		if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.isSpectator() || Downed.isDowned(owner)
			|| !Powers.has(owner, ChronoPower.INSTANCE) || --stop.ticksLeft <= 0) {
			end(server, true);
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (held(player)) {
				stop.hold(player);
			}
		}
	}

	/**
	 * Damage while time stands still: the Chrono's blows are banked (they land when it resumes), and nothing else hurts
	 * a held player.
	 */
	private static boolean allowDamage(net.minecraft.world.entity.LivingEntity entity, DamageSource source, float amount) {
		Stop stop = active;
		if (stop == null || releasing) {
			return true;
		}
		if (entity.getUUID().equals(stop.owner)) {
			return true;
		}
		Entity attacker = source.getEntity();
		if (attacker != null && attacker.getUUID().equals(stop.owner)) {
			if (stop.hits < MAX_HITS) {
				stop.hits++;
				stop.banked.computeIfAbsent(entity, e -> new ArrayList<>()).add(new Hit(source, amount));
				// a dull thud: the blow is felt by time, not yet by them
				entity.level().playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
					SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.7F, 0.6F + stop.hits * 0.01F);
			}
			return false;
		}
		return !(entity instanceof Player player && held(player));
	}

	/** Time resumes: the vanilla freeze lifts (if {@code unfreeze}), held players go, and every banked blow lands. */
	private static void end(MinecraftServer server, boolean unfreeze) {
		Stop stop = active;
		active = null;
		if (stop == null) {
			return;
		}
		if (unfreeze && server.tickRateManager().isFrozen()) {
			server.tickRateManager().setFrozen(false);
		}
		for (Map.Entry<UUID, Pin> e : stop.pins.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player != null) {
				player.setNoGravity(e.getValue().noGravity);
				player.connection.resetFlyingTicks();
			}
		}
		broadcast(server, new ChronoTimeStopSync(stop.ownerEntityId, 0, DURATION));
		releasing = true;
		try {
			for (Map.Entry<Entity, List<Hit>> e : stop.banked.entrySet()) {
				Entity target = e.getKey();
				if (target.isRemoved() || !(target.level() instanceof ServerLevel level)) {
					continue;
				}
				for (Hit hit : e.getValue()) {
					if (!target.isAlive()) {
						break;
					}
					target.setInvulnerableTime(0);
					target.hurtServer(level, hit.source, hit.amount);
				}
				Vec3 push = target.getDeltaMovement();
				if (push.length() > MAX_RELEASE_SPEED) {
					target.setDeltaMovement(push.normalize().scale(MAX_RELEASE_SPEED));
				}
				target.needsSync = true;
				ChronoFx.particles(level, (Player) null, ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					6 + e.getValue().size() * 2, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.4);
			}
		} finally {
			releasing = false;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(stop.owner);
		if (owner != null) {
			ServerLevel level = owner.level();
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4F, 0.7F);
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 1.4F);
		}
	}

	private static void broadcast(MinecraftServer server, ChronoTimeStopSync sync) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, ChronoTimeStopSync.TYPE)) {
				ServerPlayNetworking.send(player, sync);
			}
		}
	}

	private record Hit(DamageSource source, float amount) {
	}

	private record Pin(ServerPlayer player, Vec3 spot, boolean noGravity) {
	}

	/** One stop: whose it is, how long is left, who it holds and where, and the blows banked. */
	private static final class Stop {
		final UUID owner;
		final int ownerEntityId;
		int ticksLeft = DURATION;
		int hits;
		/** Each held player: where time caught them, and whether they had no gravity before (to put back). */
		final Map<UUID, Pin> pins = new HashMap<>();
		final Map<Entity, List<Hit>> banked = new LinkedHashMap<>();

		Stop(UUID owner, int ownerEntityId) {
			this.owner = owner;
			this.ownerEntityId = ownerEntityId;
		}

		/** Every tick: pins a held player where time caught them (they can still look around). */
		void hold(ServerPlayer player) {
			Pin pin = pins.get(player.getUUID());
			if (pin == null || pin.player != player) {
				// caught now (or respawned, or back from a dimension change: a new player object), pinned where they are
				pin = new Pin(player, player.position(), pin != null ? pin.noGravity : player.isNoGravity());
				pins.put(player.getUUID(), pin);
				player.setNoGravity(true);
			}
			Vec3 spot = pin.spot;
			player.setDeltaMovement(Vec3.ZERO);
			player.resetFallDistance();
			player.connection.resetFlyingTicks();
			if (!player.isPassenger() && player.position().distanceToSqr(spot) > DRIFT_SQR) {
				player.connection.teleport(spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
			}
		}
	}
}

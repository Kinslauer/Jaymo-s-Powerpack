package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Afterimage decoys (sneak + V while overclocked): copies of you stand about the place, and the gear you are in says
 * how many ({@link #COUNT_BY_GEAR}; in the top gear it is an army, {@link #ARMY}).
 * <ul>
 * <li><b>Distraction:</b> hostile creatures near you lose their grip on you and make for the nearest copy. One that
 * reaches a copy pops it and is left slowed ({@link #POP_SLOW}).</li>
 * <li><b>Swaps:</b> now and then, with something hostile close, you and a copy change places and the foe goes for the
 * spot you left ({@link #SWAP_EVERY}).</li>
 * </ul>
 * The copies are only positions on the server (nothing to hit or kill); the clients draw them ({@link Sync}).
 * Chrono's running trail of afterimages is Overclock's own streak ({@code ChronoStreak}), not this.
 */
public final class ChronoDecoys {
	// ---- tuning ----
	/** The recharge, and how long the copies last (ticks). */
	static final int COOLDOWN = 360, LIFE = 200;
	/** How many copies each gear (1 to 5) makes, and the army in the top gear. */
	static final int[] COUNT_BY_GEAR = {2, 2, 3, 4, 5};
	static final int ARMY = 10;
	/** How far from you the copies stand (blocks): a ring of this width, and the army's wider ring. */
	static final double RING_MIN = 2.5, RING_MAX = 5.5, ARMY_RING_MIN = 3.0, ARMY_RING_MAX = 8.0;
	/** Hostile creatures this close (blocks) are drawn to the copies; they are re-pointed this often (ticks). */
	static final double DISTRACT_RANGE = 20.0;
	static final int DISTRACT_EVERY = 5;
	static final double CHASE_SPEED = 1.15;
	/** A creature this close to a copy pops it, and is slowed this long (ticks, Slowness amplifier). */
	static final double POP_RANGE = 1.4;
	static final int POP_SLOW = 40, POP_SLOW_AMPLIFIER = 2;
	/** Swaps: something hostile within this many blocks, then the wait before the next (ticks, plus up to the jitter). */
	static final double SWAP_THREAT_RANGE = 6.0;
	static final int SWAP_EVERY = 70, SWAP_JITTER = 40;
	/** Clients this far (blocks) from you are sent the copies. */
	static final int SYNC_RADIUS = 96;
	static final int COLOR = 0xBFE8FF;

	/** Server to clients: where {@code owner}'s copies stand (an empty list takes them away) and how long they last (ticks). */
	public record Sync(int owner, int life, List<Vec3> spots, List<Float> yaws) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosPowers.id("chrono_decoys"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.owner);
				buf.writeVarInt(p.life);
				buf.writeVarInt(p.spots.size());
				for (int i = 0; i < p.spots.size(); i++) {
					Vec3 v = p.spots.get(i);
					buf.writeDouble(v.x);
					buf.writeDouble(v.y);
					buf.writeDouble(v.z);
					buf.writeFloat(p.yaws.get(i));
				}
			},
			buf -> {
				int owner = buf.readVarInt();
				int life = buf.readVarInt();
				int n = Math.min(buf.readVarInt(), 64);
				List<Vec3> spots = new ArrayList<>();
				List<Float> yaws = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					spots.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
					yaws.add(buf.readFloat());
				}
				return new Sync(owner, life, spots, yaws);
			});

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	private static final class Copies {
		final List<Vec3> spots = new ArrayList<>();
		final List<Float> yaws = new ArrayList<>();
		long until;
		long swapReadyAt;
	}

	private static final Map<UUID, Copies> SETS = new HashMap<>();
	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	private ChronoDecoys() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(ChronoDecoys::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SETS.remove(handler.player.getUUID());
			READY_AT.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SETS.clear();
			READY_AT.clear();
		});
	}

	static void forget(ServerPlayer player) {
		if (SETS.remove(player.getUUID()) != null) {
			send(player, null);
		}
		READY_AT.remove(player.getUUID());
	}

	/** How many copies the player's gear makes. */
	static int count(ServerPlayer player) {
		int gear = ChronoGears.gear(player);
		return gear >= ChronoGears.GEARS ? ARMY : COUNT_BY_GEAR[Mth.clamp(gear, 1, COUNT_BY_GEAR.length) - 1];
	}

	/** Sneak + V: the copies step out. Says so when it is still recharging. */
	static void cast(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < READY_AT.getOrDefault(player.getUUID(), 0L)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_decoys.recharging"));
			return;
		}
		boolean army = ChronoGears.gear(player) >= ChronoGears.GEARS;
		Copies set = new Copies();
		int want = count(player);
		double min = army ? ARMY_RING_MIN : RING_MIN, max = army ? ARMY_RING_MAX : RING_MAX;
		for (int i = 0; i < want * 3 && set.spots.size() < want; i++) {
			double angle = player.getRandom().nextDouble() * Mth.TWO_PI;
			double radius = min + player.getRandom().nextDouble() * (max - min);
			Vec3 spot = standAt(level, player, player.getX() + Math.cos(angle) * radius, player.getZ() + Math.sin(angle) * radius);
			if (spot != null) {
				set.spots.add(spot);
				set.yaws.add(player.getYRot() + (player.getRandom().nextFloat() - 0.5F) * 120.0F);
				MagicFx.burst(level, spot.x, spot.y + 1.0, spot.z, COLOR, 0.6F, 5, 0.3, 0.6, 0.3);
			}
		}
		if (set.spots.isEmpty()) {
			return;
		}
		set.until = now + LIFE;
		set.swapReadyAt = now + SWAP_EVERY;
		SETS.put(player.getUUID(), set);
		READY_AT.put(player.getUUID(), now + COOLDOWN);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, army ? 0.8F : 1.3F);
		MagicFx.ring(level, player.position().add(0.0, 0.2, 0.0), army ? 5.0F : 3.0F, COLOR);
		send(player, set);
	}

	/** A spot to stand a copy near {@code x, z}: open air over a floor within a few blocks of {@code player}'s feet, or null. */
	private static Vec3 standAt(ServerLevel level, ServerPlayer player, double x, double z) {
		for (int dy = 2; dy >= -3; dy--) {
			double y = player.getY() + dy;
			AABB box = player.getBoundingBox().move(x - player.getX(), dy, z - player.getZ());
			if (level.noCollision(player, box) && !level.noCollision(player, box.move(0.0, -0.3, 0.0))) {
				return new Vec3(x, y, z);
			}
		}
		return null;
	}

	private static void send(ServerPlayer owner, Copies set) {
		Sync payload = set == null ? new Sync(owner.getId(), 0, List.of(), List.of())
			: new Sync(owner.getId(), (int)Math.max(1L, set.until - owner.level().getGameTime()), List.copyOf(set.spots), List.copyOf(set.yaws));
		if (ServerPlayNetworking.canSend(owner, Sync.TYPE)) {
			ServerPlayNetworking.send(owner, payload);
		}
		for (ServerPlayer other : PlayerLookup.tracking(owner)) {
			if (other != owner && ServerPlayNetworking.canSend(other, Sync.TYPE)) {
				ServerPlayNetworking.send(other, payload);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (SETS.isEmpty()) {
			return;
		}
		for (var it = SETS.entrySet().iterator(); it.hasNext();) {
			var entry = it.next();
			Copies set = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || !owner.isAlive() || !Powers.has(owner, ChronoPower.INSTANCE) || owner.level().getGameTime() >= set.until || set.spots.isEmpty()) {
				it.remove();
				if (owner != null) {
					send(owner, null);
				}
				continue;
			}
			ServerLevel level = owner.level();
			long now = level.getGameTime();
			boolean changed = false;
			if (now % DISTRACT_EVERY == 0) {
				changed = distract(level, owner, set);
			}
			if (now >= set.swapReadyAt && !set.spots.isEmpty()) {
				changed |= swap(level, owner, set, now);
			}
			if (changed) {
				if (set.spots.isEmpty()) {
					it.remove();
					send(owner, null);
				} else {
					send(owner, set);
				}
			}
		}
	}

	/** Hostile creatures near make for the nearest copy; one that reaches it pops it. True if a copy popped. */
	private static boolean distract(ServerLevel level, ServerPlayer owner, Copies set) {
		boolean popped = false;
		AABB area = owner.getBoundingBox().inflate(DISTRACT_RANGE);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> m instanceof Enemy && m.isAlive())) {
			if (mob.getTarget() != null && mob.getTarget() != owner) {
				continue;
			}
			int nearest = -1;
			double best = Double.MAX_VALUE;
			for (int i = 0; i < set.spots.size(); i++) {
				double d = mob.distanceToSqr(set.spots.get(i));
				if (d < best) {
					best = d;
					nearest = i;
				}
			}
			if (nearest < 0) {
				continue;
			}
			Vec3 spot = set.spots.get(nearest);
			if (best <= POP_RANGE * POP_RANGE) {
				pop(level, set, nearest);
				mob.setTarget(null);
				mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, POP_SLOW, POP_SLOW_AMPLIFIER, false, false, false), owner);
				popped = true;
				if (set.spots.isEmpty()) {
					break;
				}
				continue;
			}
			mob.setTarget(null);
			mob.getNavigation().moveTo(spot.x, spot.y, spot.z, CHASE_SPEED);
		}
		return popped;
	}

	private static void pop(ServerLevel level, Copies set, int index) {
		Vec3 spot = set.spots.remove(index);
		set.yaws.remove(index);
		MagicFx.burst(level, spot.x, spot.y + 1.0, spot.z, COLOR, 0.7F, 8, 0.3, 0.6, 0.3);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.9F, 1.6F);
	}

	/** With something hostile close, you and a copy change places; the foe goes for the spot you left. */
	private static boolean swap(ServerLevel level, ServerPlayer owner, Copies set, long now) {
		boolean threat = !level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(SWAP_THREAT_RANGE),
			m -> m instanceof Enemy && m.isAlive() && m.getTarget() == owner).isEmpty();
		if (!threat) {
			return false;
		}
		int pick = owner.getRandom().nextInt(set.spots.size());
		Vec3 there = set.spots.get(pick);
		Vec3 here = owner.position();
		float yaw = owner.getYRot();
		set.spots.set(pick, here);
		set.yaws.set(pick, yaw);
		set.swapReadyAt = now + SWAP_EVERY + owner.getRandom().nextInt(SWAP_JITTER);
		MagicFx.burst(level, here.x, here.y + 1.0, here.z, COLOR, 0.8F, 8, 0.3, 0.6, 0.3);
		owner.teleportTo(level, there.x, there.y, there.z, Set.of(), owner.getYRot(), owner.getXRot(), false);
		MagicFx.burst(level, there.x, there.y + 1.0, there.z, COLOR, 0.8F, 8, 0.3, 0.6, 0.3);
		level.playSound(null, there.x, there.y, there.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.8F);
		return true;
	}
}

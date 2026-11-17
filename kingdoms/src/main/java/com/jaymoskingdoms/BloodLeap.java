package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Andy (2026-11-17, from a clip of a vampire trying to reach prey on a tower): "the right click should have that
 * functionality when you are locked on, if not it should be a strong directional jump. all weakened in sunlight."
 * <p>
 * A vampire player's right-click with an empty main hand, when nothing else takes the click (the client's
 * {@code BloodLeapUseMixin} asks for it with {@link Leap}):
 * <ul>
 * <li>Locked on (Powers' lock-on, middle mouse) to something within {@link #POUNCE_RANGE} and in sight: they fly at it
 * ({@link #POUNCE_SPEED} a tick, steered every tick, for at most {@link #POUNCE_TICKS}) and, on reaching it, feed on it as
 * the skill bar's Feed does ({@link BloodGrab#feedOn}): the grab on people, the bite on the rest it takes. Already in reach,
 * they feed at once.</li>
 * <li>Not locked: a strong jump the way they look, from the ground or water.</li>
 * </ul>
 * In the open sun by day, or near a holy thing ({@link HolyDread#hallowed}), both are weaker ({@link #WEAK_SHARE}) and slower
 * to come back ({@link #WEAK_COOLDOWN}). A dhampir has no sun to fear. No fall damage until they land
 * ({@link NightGifts#thrown}).
 */
public final class BloodLeap {
	/** The plain jump: speed along the look and lift (blocks a tick); the cooldown (ticks). */
	static final double JUMP_ALONG = 1.4, JUMP_UP = 0.6;
	static final int JUMP_COOLDOWN = 30;
	/** The leap at a locked foe: how far it reaches (blocks), its speed (blocks a tick), the longest it flies (ticks), the cooldown. */
	static final double POUNCE_RANGE = 18.0, POUNCE_SPEED = 1.3;
	static final int POUNCE_TICKS = 25, POUNCE_COOLDOWN = 60;
	/** Arriving: this close (blocks, eyes to the prey's box) counts as reached; what's left of the speed on arrival. */
	static final double ARRIVE = BloodGrab.REACH - 0.4, ARRIVE_KEEP = 0.15;
	/** In the sun or near a holy thing: this share of the speed and reach, and the cooldown this many times as long. */
	static final double WEAK_SHARE = 0.6;
	static final int WEAK_COOLDOWN = 2;

	/** Client to server: right-click with an empty hand that nothing else took. */
	public record Leap() implements CustomPacketPayload {
		public static final Type<Leap> TYPE = new Type<>(JaymosKingdoms.id("blood_leap"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Leap> CODEC = StreamCodec.unit(new Leap());

		@Override
		public Type<Leap> type() {
			return TYPE;
		}
	}

	/** A leap in flight: at whom, how strong, and when it gives up (game time). */
	private record Pounce(LivingEntity target, double speed, long until) {
	}

	private static final Map<UUID, Pounce> POUNCING = new HashMap<>();
	/** When each vampire may leap again (game time). */
	private static final Map<UUID, Long> READY = new HashMap<>();

	private BloodLeap() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Leap.TYPE, Leap.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Leap.TYPE, (payload, context) -> leap(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(BloodLeap::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			POUNCING.clear();
			READY.clear();
		});
	}

	/** Weakened as by day: in the open sun (not a dhampir), or near a holy thing. */
	static boolean weak(ServerPlayer player) {
		return !VampirePlayers.dhampir(player) && !Vampires.night(player.level().getOverworldClockTime()) && VampirePlayers.sunlit(player)
			|| HolyDread.hallowed(player);
	}

	private static void leap(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!Vampires.isVampire(player) || !player.isAlive() || player.isSpectator() || Downed.isDowned(player) || player.isPassenger()
			|| !player.getMainHandItem().isEmpty() || BloodGrab.holding(player) || POUNCING.containsKey(player.getUUID())
			|| now < READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		boolean weak = weak(player);
		double share = weak ? WEAK_SHARE : 1.0;
		int slower = weak ? WEAK_COOLDOWN : 1;
		LivingEntity target = PowersCompat.lockTarget(player);
		if (target != null && target.isAlive() && player.hasLineOfSight(target)
			&& target.getBoundingBox().distanceToSqr(player.getEyePosition()) <= POUNCE_RANGE * share * POUNCE_RANGE * share) {
			READY.put(player.getUUID(), now + (long)POUNCE_COOLDOWN * slower);
			if (reached(player, target)) {
				arrive(level, player, target);
				return;
			}
			POUNCING.put(player.getUUID(), new Pounce(target, POUNCE_SPEED * share, now + POUNCE_TICKS));
			if (player.isFallFlying()) {
				player.stopFallFlying();
			}
			steer(player, target, POUNCE_SPEED * share);
			launched(level, player);
			return;
		}
		if (!player.onGround() && !player.isInWater()) {
			return;
		}
		Vec3 look = player.getLookAngle();
		double along = JUMP_ALONG * share;
		NightGifts.push(player, new Vec3(look.x * along, Math.max(look.y * along, 0.0) + JUMP_UP * share, look.z * along));
		READY.put(player.getUUID(), now + (long)JUMP_COOLDOWN * slower);
		launched(level, player);
	}

	private static void launched(ServerLevel level, ServerPlayer player) {
		NightGifts.thrown(player);
		level.sendParticles(PowersCompat.blood(0x8A0303, 1.2F), player.getX(), player.getY(0.5), player.getZ(), 16, 0.3, 0.4, 0.3, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.2), player.getZ(), 8, 0.25, 0.2, 0.25, 0.02);
		level.playSound(null, player.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 0.9F, 0.6F);
	}

	private static boolean reached(ServerPlayer player, LivingEntity target) {
		return target.getBoundingBox().distanceToSqr(player.getEyePosition()) <= ARRIVE * ARRIVE;
	}

	/** Straight at the middle of the target, at {@code speed}. */
	private static void steer(ServerPlayer player, LivingEntity target, double speed) {
		Vec3 to = target.getBoundingBox().getCenter().subtract(player.getBoundingBox().getCenter());
		if (to.lengthSqr() > 1.0E-4) {
			NightGifts.push(player, to.normalize().scale(speed));
		}
		NightGifts.thrown(player);
	}

	private static void arrive(ServerLevel level, ServerPlayer player, LivingEntity target) {
		NightGifts.push(player, player.getDeltaMovement().scale(ARRIVE_KEEP));
		if (BloodGrab.feedable(player, target)) {
			BloodGrab.feedOn(level, player, target);
		}
	}

	private static void tick(MinecraftServer server) {
		if (POUNCING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Pounce>> it = POUNCING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Pounce> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Pounce p = e.getValue();
			if (player == null || !player.isAlive() || Downed.isDowned(player) || player.isPassenger() || !p.target.isAlive() || p.target.isRemoved()
				|| p.target.level() != player.level() || player.level().getGameTime() > p.until) {
				it.remove();
				continue;
			}
			if (reached(player, p.target)) {
				it.remove();
				arrive(player.level(), player, p.target);
				continue;
			}
			steer(player, p.target, p.speed);
		}
	}
}
